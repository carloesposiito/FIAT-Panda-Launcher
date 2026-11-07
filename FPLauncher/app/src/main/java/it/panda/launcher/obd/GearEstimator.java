package it.panda.launcher.obd;

/**
 * La centralina non dice in che marcia si è: la si ricava dal rapporto velocità/giri.
 * I valori (km/h ogni 1000 giri) sono indicativi per il cambio a 5 marce della Panda 169 Fire;
 * se sulla tua auto la marcia mostrata è sbagliata, vanno corretti qui.
 */
public final class GearEstimator {

    /** km/h a 1000 giri in 1ª, 2ª, 3ª, 4ª, 5ª. */
    static final double[] KMH_PER_1000_RPM = {7.2, 13.0, 19.4, 26.3, 32.6};

    /** Scarto massimo accettato rispetto al valore della tabella. */
    static final double TOLERANCE = 0.15;

    private GearEstimator() {
    }

    /**
     * @return la marcia (1-5), 0 se l'auto è ferma o in folle/frizione premuta, -1 se non si capisce
     */
    public static int estimate(double rpm, double speedKmh) {
        if (speedKmh < 3 || rpm < 400) {
            return 0;
        }
        double ratio = speedKmh / (rpm / 1000.0);
        int best = -1;
        double bestError = Double.MAX_VALUE;
        for (int i = 0; i < KMH_PER_1000_RPM.length; i++) {
            double error = Math.abs(ratio - KMH_PER_1000_RPM[i]) / KMH_PER_1000_RPM[i];
            if (error < bestError) {
                bestError = error;
                best = i + 1;
            }
        }
        return bestError <= TOLERANCE ? best : -1;
    }
}
