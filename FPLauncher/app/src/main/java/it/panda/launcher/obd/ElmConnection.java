package it.panda.launcher.obd;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Collegamento seriale Bluetooth con un adattatore ELM327.
 * Si manda un comando terminato da "\r" e si legge fino al prompt ">".
 */
public final class ElmConnection implements Closeable {

    /** UUID standard del profilo seriale (SPP) usato da tutti gli ELM327 Bluetooth. */
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothSocket socket;
    private final InputStream in;
    private final OutputStream out;
    private volatile boolean closed;

    private ElmConnection(BluetoothSocket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /** Apre il collegamento. Richiede il permesso Bluetooth (vedi {@link BluetoothPermissions}). */
    @SuppressLint("MissingPermission")
    public static ElmConnection open(BluetoothDevice device) throws IOException {
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        try {
            socket.connect();
        } catch (IOException first) {
            closeQuietly(socket);
            // Molti adattatori economici rispondono solo sul canale 1: secondo tentativo diretto.
            socket = createOnChannel1(device);
            if (socket == null) {
                throw first;
            }
            try {
                socket.connect();
            } catch (IOException second) {
                closeQuietly(socket);
                throw first;
            }
        }
        return new ElmConnection(socket);
    }

    private static BluetoothSocket createOnChannel1(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            return (BluetoothSocket) m.invoke(device, 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Invia un comando e restituisce la risposta (senza il prompt finale).
     *
     * @param timeoutMs tempo massimo di attesa; la prima richiesta OBD può impiegare anche 10 secondi
     *                  perché l'ELM prova tutti i protocolli
     */
    public synchronized String send(String command, long timeoutMs) throws IOException {
        if (closed) {
            throw new IOException("Collegamento chiuso");
        }
        // scarta eventuali avanzi di risposte precedenti
        while (in.available() > 0) {
            if (in.read() < 0) {
                throw new IOException("Collegamento interrotto");
            }
        }
        out.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();

        StringBuilder sb = new StringBuilder();
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (closed) {
                throw new IOException("Collegamento chiuso");
            }
            if (in.available() > 0) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("Collegamento interrotto");
                }
                if (b == '>') {
                    return sb.toString();
                }
                if (b != 0) {
                    sb.append((char) b);
                }
            } else {
                SystemClock.sleep(5);
            }
        }
        throw new IOException("Nessuna risposta a " + command);
    }

    @Override
    public void close() {
        closed = true;
        closeQuietly(socket);
    }

    private static void closeQuietly(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException ignored) {
            // niente da fare
        }
    }
}
