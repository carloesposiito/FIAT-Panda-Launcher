package it.panda.launcher.obd;

import java.util.ArrayList;
import java.util.List;

/**
 * Storico del consumo per il grafico del riquadro consumi: un punto ogni {@link #BUCKET_SECONDS}
 * secondi (benzina e strada di quell'intervallo → l/100 km), gli ultimi {@link #CAPACITY} punti.
 * Un punto null vuol dire "auto ferma" (il grafico lascia un buco).
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class ConsumptionHistory {

    static final double BUCKET_SECONDS = 10.0;
    /** 180 punti da 10 s = ultima mezz'ora. */
    static final int CAPACITY = 180;
    /** Sotto questa strada nell'intervallo (≈ 7 km/h di media) l'auto è considerata ferma. */
    static final double MIN_BUCKET_KM = 0.02;
    /** Valori oltre questo (partenze da fermo) schiacerebbero il grafico. */
    static final double MAX_L100 = 30.0;

    private final List<Double> points = new ArrayList<>();
    private double bucketSeconds, bucketFuel, bucketKm;

    public synchronized void add(Double litersPerHour, Double speedKmh, double dtSeconds) {
        if (dtSeconds <= 0) {
            return;
        }
        bucketSeconds += dtSeconds;
        if (litersPerHour != null) {
            bucketFuel += litersPerHour * dtSeconds / 3600.0;
        }
        if (speedKmh != null) {
            bucketKm += speedKmh * dtSeconds / 3600.0;
        }
        if (bucketSeconds >= BUCKET_SECONDS) {
            points.add(bucketKm >= MIN_BUCKET_KM ? Math.min(MAX_L100, bucketFuel / bucketKm * 100.0) : null);
            if (points.size() > CAPACITY) {
                points.remove(0);
            }
            bucketSeconds = 0;
            bucketFuel = 0;
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
        bucketFuel = 0;
        bucketKm = 0;
    }
}
