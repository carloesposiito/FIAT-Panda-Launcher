package it.panda.launcher.obd;

import java.util.Locale;

/**
 * Computer di bordo: somma distanza, benzina e tempo a partire dalle letture OBD.
 * La distanza si ricava integrando la velocità, la benzina integrando i litri/ora.
 *
 * Ne esistono due: il "viaggio" (azzerato a ogni nuovo collegamento, cioè a ogni accensione)
 * e il "trip" (salvato e azzerato solo a mano). I metodi sono sincronizzati perché il thread OBD
 * scrive e l'interfaccia può azzerare.
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class TripComputer {

    /** Sotto questa distanza la media in l/100 km è troppo ballerina per mostrarla. */
    static final double MIN_KM_FOR_AVERAGE = 0.5;
    /** Sotto questa velocità l'auto si considera ferma (non conta per la velocità media). */
    static final double MOVING_KMH = 3.0;

    private double fuelLiters;
    private double distanceKm;
    private double seconds;
    private double movingSeconds;
    private double maxSpeedKmh;

    /** Dati del computer di bordo in un istante (copia immutabile). */
    public static final class Summary {
        public final double fuelLiters;
        public final double distanceKm;
        public final double seconds;
        public final double movingSeconds;
        public final double maxSpeedKmh;

        public Summary(double fuelLiters, double distanceKm, double seconds, double movingSeconds, double maxSpeedKmh) {
            this.fuelLiters = fuelLiters;
            this.distanceKm = distanceKm;
            this.seconds = seconds;
            this.movingSeconds = movingSeconds;
            this.maxSpeedKmh = maxSpeedKmh;
        }

        /** Consumo medio in l/100 km, oppure null se la distanza è troppo breve. */
        public Double averageL100() {
            if (distanceKm < MIN_KM_FOR_AVERAGE) {
                return null;
            }
            return fuelLiters / distanceKm * 100.0;
        }

        /** Velocità media in movimento (soste escluse), oppure null all'inizio. */
        public Double averageSpeedKmh() {
            if (movingSeconds < 10 || distanceKm <= 0) {
                return null;
            }
            return distanceKm / (movingSeconds / 3600.0);
        }
    }

    /**
     * Aggiunge un intervallo di guida.
     *
     * @param litersPerHour consumo istantaneo, null se non disponibile
     * @param speedKmh      velocità, null se non disponibile
     * @param dtSeconds     durata dell'intervallo
     */
    public synchronized void add(Double litersPerHour, Double speedKmh, double dtSeconds) {
        if (dtSeconds <= 0) {
            return;
        }
        seconds += dtSeconds;
        if (litersPerHour != null) {
            fuelLiters += litersPerHour * dtSeconds / 3600.0;
        }
        if (speedKmh != null) {
            distanceKm += speedKmh * dtSeconds / 3600.0;
            if (speedKmh >= MOVING_KMH) {
                movingSeconds += dtSeconds;
            }
            maxSpeedKmh = Math.max(maxSpeedKmh, speedKmh);
        }
    }

    public synchronized Summary summary() {
        return new Summary(fuelLiters, distanceKm, seconds, movingSeconds, maxSpeedKmh);
    }

    public synchronized void reset() {
        fuelLiters = 0;
        distanceKm = 0;
        seconds = 0;
        movingSeconds = 0;
        maxSpeedKmh = 0;
    }

    /** Stato in forma di testo, per salvarlo nelle impostazioni. */
    public synchronized String serialize() {
        return String.format(Locale.US, "%.6f;%.6f;%.1f;%.1f;%.1f",
                fuelLiters, distanceKm, seconds, movingSeconds, maxSpeedKmh);
    }

    /** Ripristina lo stato salvato con {@link #serialize()}; un testo non valido lascia tutto a zero. */
    public synchronized void restore(String text) {
        reset();
        if (text == null) {
            return;
        }
        String[] p = text.split(";");
        if (p.length != 5) {
            return;
        }
        try {
            double[] v = new double[5];
            for (int i = 0; i < 5; i++) {
                v[i] = Double.parseDouble(p[i]);
            }
            fuelLiters = v[0];
            distanceKm = v[1];
            seconds = v[2];
            movingSeconds = v[3];
            maxSpeedKmh = v[4];
        } catch (NumberFormatException e) {
            reset();
        }
    }
}
