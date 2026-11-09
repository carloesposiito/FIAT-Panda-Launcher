package it.panda.launcher.obd;

/**
 * Ritardo prima di collegarsi all'OBD: lascia al Bluetooth della radio il tempo di collegare il telefono.
 * Si arma all'avvio, al risveglio e quando cade un collegamento; il tempo è quello dell'orologio passato dal chiamante.
 */
final class ConnectDelay {

    private long notBefore;

    /** Da {@code now} bisogna aspettare {@code delayMs} (0 o meno: nessuna attesa). */
    synchronized void arm(long now, long delayMs) {
        notBefore = now + Math.max(0, delayMs);
    }

    /** Millisecondi ancora da aspettare. */
    synchronized long remaining(long now) {
        return Math.max(0, notBefore - now);
    }
}
