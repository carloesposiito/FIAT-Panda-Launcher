package it.panda.launcher.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.panda.launcher.Prefs;
import it.panda.launcher.R;

/**
 * Gestisce in un thread separato il collegamento all'ELM327 e la lettura ciclica dei dati.
 * Se il collegamento cade, riprova da solo con attese crescenti (2 s, 4 s, ... fino a 30 s).
 * I risultati arrivano al {@link Listener} sul thread principale.
 */
public final class ObdManager {

    public enum State { DISABLED, NOT_CONFIGURED, CONNECTING, CONNECTED, ERROR }

    /** Ultimo stato e dettaglio (protocollo o errore), per mostrarli nelle impostazioni. */
    private static volatile State lastState = State.NOT_CONFIGURED;
    private static volatile String lastDetail;

    public static State lastState() {
        return lastState;
    }

    public static String lastDetail() {
        return lastDetail;
    }

    public interface Listener {
        void onObdState(State state, String detail);

        void onObdData(ObdSnapshot snapshot);
    }

    /** Letti a ogni giro: cambiano in fretta (la pressione collettore serve per il consumo istantaneo). */
    private static final ObdPid[] FAST = {ObdPid.RPM, ObdPid.SPEED, ObdPid.INTAKE_PRESSURE, ObdPid.MAF, ObdPid.FUEL_RATE};
    /** Letti un giro ogni {@link #SLOW_EVERY}: cambiano lentamente. */
    private static final ObdPid[] SLOW = {
            ObdPid.COOLANT_TEMP, ObdPid.OIL_TEMP, ObdPid.INTAKE_TEMP,
            ObdPid.THROTTLE, ObdPid.ENGINE_LOAD, ObdPid.FUEL_LEVEL
    };
    private static final int SLOW_EVERY = 5;
    /** Ogni quanto salvare il trip, per non perderlo se l'autoradio si spegne di colpo. */
    private static final long SAVE_TRIP_EVERY_MS = 20_000;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Viaggio corrente: si azzera a ogni nuovo collegamento. */
    private final TripComputer drive = new TripComputer();
    /** Trip: si azzera solo a mano ed è salvato nelle impostazioni. */
    private final TripComputer trip = new TripComputer();
    /** Consumo dell'ultima mezz'ora del viaggio corrente, per il grafico. */
    private final ConsumptionHistory history = new ConsumptionHistory();
    private volatile long lastTripSaveMs;

    // ultima lettura della diagnostica (scritta solo dal thread OBD)
    private Boolean milOn;
    private java.util.List<String> dtcStored, dtcPending;
    private int diagReads;
    /** Spia e codici si leggono solo su richiesta (tasto "Leggi allarmi"), non a intervalli. */
    private volatile boolean diagRequested;

    private volatile Listener listener;
    private volatile boolean running;
    private volatile boolean visible = true;
    private volatile ElmConnection current;
    private Thread worker;

    public ObdManager(Context context) {
        this.app = context.getApplicationContext();
        trip.restore(Prefs.getTripState(app));
    }

    public TripComputer.Summary driveSummary() {
        return drive.summary();
    }

    public TripComputer.Summary tripSummary() {
        return trip.summary();
    }

    public java.util.List<Double> consumptionHistory() {
        return history.points();
    }

    /** Chiede la lettura di spia motore e codici di errore al prossimo giro. */
    public void requestDiagnostics() {
        diagRequested = true;
    }

    /** Azzera il trip (tasto nel riquadro consumi). */
    public void resetTrip() {
        trip.reset();
        saveTrip();
    }

    private void saveTrip() {
        Prefs.setTripState(app, trip.serialize());
        lastTripSaveMs = SystemClock.elapsedRealtime();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "obd");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        saveTrip();
        ElmConnection c = current;
        if (c != null) {
            c.close();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    /**
     * Quando il launcher non è visibile (es. si usa il navigatore) la lettura continua,
     * così viaggio e trip restano giusti, ma non si mandano aggiornamenti all'interfaccia.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            saveTrip();
        }
    }

    /** Da chiamare dopo aver cambiato adattatore nelle impostazioni: chiude e si ricollega. */
    public void restart() {
        ElmConnection c = current;
        if (c != null) {
            c.close();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    // ---------------------------------------------------------------- thread di lettura

    private void runLoop() {
        long backoff = 2000;
        while (running) {
            String address = Prefs.getObdAddress(app);
            if (!Prefs.getObdEnabled(app)) {
                postState(State.DISABLED, null);
                sleepQuietly(2000);
                continue;
            }
            if (address == null) {
                postState(State.NOT_CONFIGURED, null);
                sleepQuietly(2000);
                continue;
            }
            if (!BluetoothPermissions.canConnect(app)) {
                postState(State.ERROR, app.getString(R.string.obd_err_permission));
                sleepQuietly(3000);
                continue;
            }
            BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
            if (adapter == null || !adapter.isEnabled()) {
                postState(State.ERROR, app.getString(R.string.obd_err_bt_off));
                sleepQuietly(3000);
                continue;
            }

            postState(State.CONNECTING, Prefs.getObdName(app));
            try (ElmConnection c = ElmConnection.open(adapter.getRemoteDevice(address))) {
                current = c;
                Set<Integer> supported = initialize(c);
                backoff = 2000;
                drive.reset(); // nuovo collegamento = di solito nuova accensione
                milOn = null;
                dtcStored = null;
                dtcPending = null;
                diagReads = 0;
                diagRequested = false;
                history.reset();
                postState(State.CONNECTED, ObdParser.clean(c.send("ATDP", 1000)));
                poll(c, supported);
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                if (!running) {
                    break;
                }
                postState(State.ERROR, e.getMessage());
                sleepQuietly(backoff);
                backoff = Math.min(backoff * 2, 30000);
            } finally {
                current = null;
                saveTrip();
            }
        }
    }

    /** Prepara l'ELM327 e chiede alla centralina quali PID supporta. */
    private Set<Integer> initialize(ElmConnection c) throws IOException {
        c.send("ATZ", 3000);   // reset
        c.send("ATE0", 1000);  // niente eco dei comandi
        c.send("ATL0", 1000);  // niente a capo extra
        c.send("ATS0", 1000);  // niente spazi tra i byte
        c.send("ATH0", 1000);  // niente intestazioni
        c.send("ATSP0", 1000); // protocollo automatico (la Panda 169 usa di solito ISO 9141 / KWP2000)

        String r = c.send("0100", 15000);
        if (ObdParser.isError(r)) {
            throw new IOException(app.getString(R.string.obd_err_ecu));
        }
        Set<Integer> supported = new HashSet<>(ObdParser.parseSupportedPids(r, 0x00));
        for (int base = 0x20; base <= 0x40 && supported.contains(base); base += 0x20) {
            String more = c.send(String.format("01%02X", base), 3000);
            supported.addAll(ObdParser.parseSupportedPids(more, base));
        }
        return supported;
    }

    private void poll(ElmConnection c, Set<Integer> supported) throws IOException {
        List<ObdPid> fast = filter(FAST, supported);
        List<ObdPid> slow = filter(SLOW, supported);
        Map<ObdPid, Double> values = new EnumMap<>(ObdPid.class);
        Double battery = null;
        int cycle = 0;
        long last = SystemClock.elapsedRealtime();

        while (running && current == c) {
            if (diagRequested) {
                diagRequested = false;
                readDiagnostics(c, supported);
                diagReads++;
            }
            for (ObdPid p : fast) {
                read(c, p, values);
            }
            if (cycle % SLOW_EVERY == 0) {
                for (ObdPid p : slow) {
                    read(c, p, values);
                }
                Double v = ObdParser.parseVoltage(c.send("ATRV", 1000));
                if (v != null) {
                    battery = v;
                }
            }
            cycle++;

            long now = SystemClock.elapsedRealtime();
            double seconds = (now - last) / 1000.0;
            last = now;
            ObdSnapshot snapshot = buildSnapshot(values, battery, seconds);
            if (now - lastTripSaveMs > SAVE_TRIP_EVERY_MS) {
                saveTrip();
            }
            if (visible) {
                postData(snapshot);
            } else {
                sleepQuietly(150); // in sottofondo basta una lettura un po' più rada
            }
        }
    }

    /** Spia motore (PID 01 01) e codici di errore memorizzati (modo 03) e in attesa (modo 07). */
    private void readDiagnostics(ElmConnection c, Set<Integer> supported) throws IOException {
        if (supported.contains(0x01)) {
            Boolean mil = DtcParser.milOn(c.send("0101", 2000));
            if (mil != null) {
                milOn = mil;
            }
        }
        dtcStored = DtcParser.parse(c.send("03", 4000), 0x43);
        dtcPending = DtcParser.parse(c.send("07", 4000), 0x47);
    }

    private static List<ObdPid> filter(ObdPid[] pids, Set<Integer> supported) {
        List<ObdPid> out = new ArrayList<>();
        for (ObdPid p : pids) {
            if (supported.contains(p.pid)) {
                out.add(p);
            }
        }
        return out;
    }

    private static void read(ElmConnection c, ObdPid p, Map<ObdPid, Double> values) throws IOException {
        String r = c.send(p.command(), 2000);
        int[] d = ObdParser.parseMode01(r, p.pid, p.dataBytes);
        if (d != null) {
            values.put(p, p.decode(d));
        }
    }

    private ObdSnapshot buildSnapshot(Map<ObdPid, Double> v, Double battery, double seconds) {
        ObdSnapshot s = new ObdSnapshot();
        s.rpm = v.get(ObdPid.RPM);
        s.speedKmh = v.get(ObdPid.SPEED);
        s.coolantC = v.get(ObdPid.COOLANT_TEMP);
        s.oilC = v.get(ObdPid.OIL_TEMP);
        s.intakeC = v.get(ObdPid.INTAKE_TEMP);
        s.intakeKpa = v.get(ObdPid.INTAKE_PRESSURE);
        s.throttlePct = v.get(ObdPid.THROTTLE);
        s.loadPct = v.get(ObdPid.ENGINE_LOAD);
        s.fuelLevelPct = v.get(ObdPid.FUEL_LEVEL);
        s.batteryV = battery != null ? battery : v.get(ObdPid.MODULE_VOLTAGE);
        s.milOn = milOn;
        s.diagReads = diagReads;
        s.dtcStored = dtcStored;
        s.dtcPending = dtcPending;

        if (s.rpm != null && s.speedKmh != null) {
            s.gear = GearEstimator.estimate(s.rpm, s.speedKmh);
        }

        // Consumo: misurato se la centralina lo fornisce, altrimenti ricavato dall'aria aspirata
        // (MAF se c'è, altrimenti stimata dalla pressione nel collettore).
        Double lph = v.get(ObdPid.FUEL_RATE);
        if (lph == null) {
            Double airFlow = v.get(ObdPid.MAF);
            if (airFlow == null && s.rpm != null && s.intakeKpa != null) {
                double intake = s.intakeC != null ? s.intakeC : 25.0;
                airFlow = FuelEstimator.airFlowFromMap(s.rpm, s.intakeKpa, intake,
                        Prefs.getDisplacementCc(app) / 1000.0, FuelEstimator.DEFAULT_VOLUMETRIC_EFFICIENCY);
            }
            if (airFlow != null) {
                lph = FuelEstimator.litersPerHour(airFlow);
            }
        }
        s.fuelRateLph = lph;
        if (lph != null && s.speedKmh != null) {
            s.consumptionL100 = FuelEstimator.litersPer100Km(lph, s.speedKmh);
        }
        // intervalli troppo lunghi (es. una lettura bloccata) falserebbero i conti: si saltano
        if (seconds > 0 && seconds < 5) {
            drive.add(lph, s.speedKmh, seconds);
            trip.add(lph, s.speedKmh, seconds);
            history.add(lph, s.speedKmh, seconds);
        }
        s.drive = drive.summary();
        s.trip = trip.summary();
        s.history = history.points();
        return s;
    }

    // ---------------------------------------------------------------- utilità

    private void postState(State state, String detail) {
        lastState = state;
        lastDetail = detail;
        main.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onObdState(state, detail);
            }
        });
    }

    private void postData(ObdSnapshot s) {
        main.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onObdData(s);
            }
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // risveglio anticipato (stop o restart): va bene
        }
    }
}
