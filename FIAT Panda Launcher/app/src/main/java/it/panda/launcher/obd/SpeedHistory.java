package it.panda.launcher.obd;

import java.util.ArrayList;
import java.util.List;

/**
 * Storico della velocità per il grafico della Home: un punto ogni {@link #BUCKET_SECONDS} secondi
 * (velocità media di quell'intervallo, in km/h), gli ultimi {@link #CAPACITY} punti.
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class SpeedHistory {

    static final double BUCKET_SECONDS = 10.0;
    /** 180 punti da 10 s = ultima mezz'ora. */
    static final int CAPACITY = 180;

    private final List<Double> points = new ArrayList<>();
    private double bucketSeconds, bucketKm;

    /** @param speedKmh velocità letta, null se non disponibile (conta come ferma) */
    public synchronized void add(Double speedKmh, double dtSeconds) {
        if (dtSeconds <= 0) {
            return;
        }
        bucketSeconds += dtSeconds;
        if (speedKmh != null) {
            bucketKm += speedKmh * dtSeconds / 3600.0;
        }
        if (bucketSeconds >= BUCKET_SECONDS) {
            points.add(bucketKm / (bucketSeconds / 3600.0));
            if (points.size() > CAPACITY) {
                points.remove(0);
            }
            bucketSeconds = 0;
            bucketKm = 0;
        }
    }

    /** Copia dei punti, dal più vecchio al più recente. */
    public synchronized List<Double> points() {
        return new ArrayList<>(points);
    }

    public synchronized void reset() {
        points.clear();
        bucketSeconds = 0;
        bucketKm = 0;
    }
}