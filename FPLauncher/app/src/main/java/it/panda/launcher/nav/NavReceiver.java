package it.panda.launcher.nav;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import it.panda.launcher.obd.BluetoothPermissions;

/**
 * Riceve le indicazioni di Google Maps dall'app goESP sul telefono.
 *
 * Il launcher si comporta come il display goESP (ESP32): pubblica via Bluetooth Low Energy lo stesso
 * servizio con le stesse caratteristiche, e il telefono vi scrive i pacchetti di navigazione.
 * Le immagini della mappa (caratteristica "map") vengono accettate e ignorate.
 * Serve un'autoradio che supporti la modalità "periferica" BLE (pubblicità): se non c'è, lo stato è UNSUPPORTED.
 */
public final class NavReceiver {

    public enum State { OFF, NO_BLUETOOTH, UNSUPPORTED, NO_PERMISSION, BT_OFF, STARTING, WAITING, CONNECTED, ERROR }

    public interface Listener {
        void onNavState(State state, String deviceName);

        void onNavData(NavData data);
    }

    // Stessi UUID dell'app goESP (config/Constants.java) e dello sketch ESP32
    static final UUID SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    static final UUID NAVIGATION = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    static final UUID SECONDARY = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    static final UUID MAP = UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9E");
    static final UUID SETTINGS = UUID.fromString("12344321-1221-1221-1221-abc987654321");

    private static NavReceiver instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Scritture "lunghe" (prepared write) da ricomporre, per dispositivo e caratteristica. */
    private final Map<String, ByteArrayOutputStream> pending = new HashMap<>();

    private Listener listener;
    private BluetoothGattServer server;
    private BluetoothLeAdvertiser advertiser;
    private boolean advertising;
    private boolean withName = true;
    private State state = State.OFF;
    private String deviceName;
    private NavData last;

    private NavReceiver(Context context) {
        this.app = context.getApplicationContext();
    }

    public static synchronized NavReceiver get(Context c) {
        if (instance == null) {
            instance = new NavReceiver(c);
        }
        return instance;
    }

    public State state() {
        return state;
    }

    public String deviceName() {
        return deviceName;
    }

    /** Ultima indicazione ricevuta (null se nessuna). */
    public NavData last() {
        return last;
    }

    public void setListener(Listener l) {
        listener = l;
        if (l != null) {
            l.onNavState(state, deviceName);
            l.onNavData(last);
        }
    }

    /** true se l'autoradio può farsi trovare via Bluetooth LE (null = Bluetooth assente o spento: non si sa). */
    public static Boolean isPeripheralSupported(Context c) {
        BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter a = bm != null ? bm.getAdapter() : null;
        if (a == null || !a.isEnabled()) {
            return null;
        }
        return a.isMultipleAdvertisementSupported() && a.getBluetoothLeAdvertiser() != null;
    }

    // ---------------------------------------------------------------- avvio e arresto

    @SuppressLint("MissingPermission")
    public synchronized void start() {
        if (server != null) {
            return;
        }
        BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        if (adapter == null) {
            setState(State.NO_BLUETOOTH);
            return;
        }
        if (!BluetoothPermissions.canAdvertise(app)) {
            setState(State.NO_PERMISSION);
            return;
        }
        if (!adapter.isEnabled()) {
            setState(State.BT_OFF);
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null || !adapter.isMultipleAdvertisementSupported()) {
            setState(State.UNSUPPORTED);
            return;
        }
        setState(State.STARTING);
        try {
            server = bm.openGattServer(app, callback);
        } catch (SecurityException e) {
            setState(State.NO_PERMISSION);
            return;
        }
        if (server == null) {
            setState(State.ERROR);
            return;
        }
        BluetoothGattService service = new BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(writable(NAVIGATION, false));
        service.addCharacteristic(writable(SECONDARY, false));
        service.addCharacteristic(writable(SETTINGS, false));
        service.addCharacteristic(writable(MAP, true));
        server.addService(service); // la pubblicità parte in onServiceAdded
    }

    @SuppressLint("MissingPermission")
    public synchronized void stop() {
        try {
            if (advertiser != null && advertising) {
                advertiser.stopAdvertising(advertiseCallback);
            }
        } catch (RuntimeException ignored) {
            // Bluetooth spento nel frattempo
        }
        advertising = false;
        if (server != null) {
            try {
                server.close();
            } catch (RuntimeException ignored) {
                // idem
            }
            server = null;
        }
        pending.clear();
        deviceName = null;
        last = null;
        setState(State.OFF);
        postData(null);
    }

    private static BluetoothGattCharacteristic writable(UUID uuid, boolean noResponse) {
        int props = BluetoothGattCharacteristic.PROPERTY_WRITE
                | (noResponse ? BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE : 0);
        return new BluetoothGattCharacteristic(uuid, props, BluetoothGattCharacteristic.PERMISSION_WRITE);
    }

    @SuppressLint("MissingPermission")
    private synchronized void startAdvertising() {
        if (advertiser == null || server == null) {
            return;
        }
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(SERVICE))
                .build();
        // il nome va nella risposta alla scansione; se è troppo lungo si riprova senza
        AdvertiseData scanResponse = new AdvertiseData.Builder().setIncludeDeviceName(withName).build();
        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
        } catch (RuntimeException e) {
            setState(State.ERROR);
        }
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            advertising = true;
            if (state != State.CONNECTED) {
                setState(State.WAITING);
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            advertising = false;
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && withName) {
                withName = false;
                startAdvertising();
            } else if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                advertising = true;
                setState(state == State.CONNECTED ? State.CONNECTED : State.WAITING);
            } else if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                setState(State.UNSUPPORTED);
            } else {
                setState(State.ERROR);
            }
        }
    };

    // ---------------------------------------------------------------- server GATT

    private final BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                startAdvertising();
            } else {
                setState(State.ERROR);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                String name = null;
                try {
                    name = device.getName();
                } catch (SecurityException ignored) {
                    // senza permesso si mostra l'indirizzo
                }
                deviceName = name != null ? name : device.getAddress();
                setState(State.CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                deviceName = null;
                last = null;
                postData(null);
                setState(State.WAITING);
                // alcune autoradio smettono di pubblicizzarsi dopo una connessione: si riparte
                synchronized (NavReceiver.this) {
                    if (server != null && advertiser != null) {
                        try {
                            advertiser.stopAdvertising(advertiseCallback);
                        } catch (RuntimeException ignored) {
                            // già fermo
                        }
                        advertising = false;
                        startAdvertising();
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                 boolean responseNeeded, int offset, byte[] value) {
            UUID uuid = characteristic.getUuid();
            if (preparedWrite) {
                // scrittura lunga: si accumula fino a onExecuteWrite
                String key = device.getAddress() + "/" + uuid;
                ByteArrayOutputStream buf = pending.get(key);
                if (buf == null) {
                    buf = new ByteArrayOutputStream();
                    pending.put(key, buf);
                }
                buf.write(value, 0, value.length);
            } else {
                handle(uuid, value);
            }
            if (responseNeeded && server != null) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            String prefix = device.getAddress() + "/";
            for (Map.Entry<String, ByteArrayOutputStream> e : new HashMap<>(pending).entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    if (execute) {
                        handle(UUID.fromString(e.getKey().substring(prefix.length())), e.getValue().toByteArray());
                    }
                    pending.remove(e.getKey());
                }
            }
            if (server != null) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
        }
    };

    /** Solo i pacchetti di navigazione interessano: ora/batteria, impostazioni e mappa si ignorano. */
    private void handle(UUID uuid, byte[] value) {
        if (!NAVIGATION.equals(uuid) || value == null) {
            return;
        }
        NavData data = NavData.parse(new String(value, StandardCharsets.UTF_8));
        last = data.isEmpty() ? null : data;
        postData(last);
    }

    // ---------------------------------------------------------------- avvisi all'interfaccia

    private void setState(State s) {
        state = s;
        String name = deviceName;
        main.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onNavState(s, name);
            }
        });
    }

    private void postData(NavData d) {
        main.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onNavData(d);
            }
        });
    }
}
