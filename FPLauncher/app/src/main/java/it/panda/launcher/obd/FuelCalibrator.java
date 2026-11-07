package it.panda.launcher.obd;

/**
 * Taratura automatica della stima dei consumi: confronta i litri stimati dal modello con quelli che il
 * livello del serbatoio dice di aver consumato e ne ricava un fattore di correzione.
 *
 * Il livello è rumoroso (galleggiante, curve, pendenze), quindi si usa filtrato e solo su tratti con un calo
 * di almeno {@link #MIN_DROP_POINTS} punti percentuali. Se il livello sale (rifornimento) il riferimento
 * riparte. Il fattore si aggiorna piano e resta tra {@link #MIN_FACTOR} e {@link #MAX_FACTOR}.
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class FuelCalibrator {

    static final double MIN_DROP_POINTS = 10.0;
    static final double REFUEL_RISE_POINTS = 2.0;
    static final double MIN_FACTOR = 0.6;
    static final double MAX_FACTOR = 1.6;
    /** Rapporti misurato/stimato fuori da questi limiti si considerano rumore e si tagliano. */
    static final double MIN_RATIO = 0.5;
    static final double MAX_RATIO = 2.0;
    /** Costante di tempo del filtro sul livello, in secondi. */
    static final double LEVEL_TAU_SECONDS = 60.0;
    static final double FIRST_WEIGHT = 0.5;
    static final double NEXT_WEIGHT = 0.3;

    private double factor = 1.0;
    private int updates;
    /** Livello (%) da cui si misura il calo e litri stimati (non corretti) da allora; NaN = nessun riferimento. */
    private double refLevel = Double.NaN;
    private double refRawLiters;
    /** Livello filtrato dall'ultimo collegamento; NaN finché non c'è una lettura. */
    private double smoothed = Double.NaN;

    public synchronized double factor() {
        return factor;
    }

    /**
     * Registra un intervallo di guida.
     *
     * @param levelPct      livello serbatoio letto (0-100), null se non disponibile
     * @param rawLiters     litri stimati in questo intervallo, senza correzione
     * @param seconds       durata dell'intervallo
     * @param capacityLiters capacità del serbatoio
     * @return true se il fattore è cambiato
     */
    public synchronized boolean add(Double levelPct, double rawLiters, double seconds, double capacityLiters) {
        if (levelPct == null || seconds <= 0 || capacityLiters <= 0) {
            return false;
        }
        if (Double.isNaN(smoothed)) {
            smoothed = levelPct;
        } else {
            smoothed += (levelPct - smoothed) * (1.0 - Math.exp(-seconds / LEVEL_TAU_SECONDS));
        }
        if (Double.isNaN(refLevel)) {
            refLevel = smoothed;
            refRawLiters = 0;
            return false;
        }
        refRawLiters += Math.max(0, rawLiters);
        if (smoothed > refLevel + REFUEL_RISE_POINTS) { // il livello sale: rifornimento, si riparte da qui
            refLevel = smoothed;
            refRawLiters = 0;
            return false;
        }
        double drop = refLevel - smoothed;
        if (drop < MIN_DROP_POINTS) {
            return false;
        }
        double measured = drop / 100.0 * capacityLiters;
        double ratio = refRawLiters > 0 ? measured / refRawLiters : MAX_RATIO;
        ratio = Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio));
        double weight = updates == 0 ? FIRST_WEIGHT : NEXT_WEIGHT;
        factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, (1 - weight) * factor + weight * ratio));
        updates++;
        refLevel = smoothed;
        refRawLiters = 0;
        return true;
    }

    public synchronized void reset() {
        factor = 1.0;
        updates = 0;
        refLevel = Double.NaN;
        refRawLiters = 0;
        smoothed = Double.NaN;
    }

    /** "fattore;aggiornamenti;livello di riferimento;litri stimati da allora". */
    public synchronized String serialize() {
        return factor + ";" + updates + ";" + refLevel + ";" + refRawLiters; // Double.toString: senza arrotondamenti né lingua
    }

    /** Ripristina uno stato salvato; se è nullo o rovinato non cambia nulla. */
    public synchronized void restore(String state) {
        if (state == null) {
            return;
        }
        String[] p = state.split(";");
        if (p.length != 4) {
            return;
        }
        try {
            double f = Double.parseDouble(p[0]);
            int u = Integer.parseInt(p[1]);
            double ref = Double.parseDouble(p[2]);
            double raw = Double.parseDouble(p[3]);
            if (Double.isNaN(f) || f < MIN_FACTOR || f > MAX_FACTOR || u < 0 || raw < 0) {
                return;
            }
            factor = f;
            updates = u;
            refLevel = ref;
            refRawLiters = raw;
        } catch (NumberFormatException e) {
            // stato rovinato: si resta com'è
        }
    }

    /** Solo il fattore di uno stato salvato (per mostrarlo nelle impostazioni); 1,0 se non valido. */
    public static double factorOf(String state) {
        FuelCalibrator c = new FuelCalibrator();
        c.restore(state);
        return c.factor();
    }
}
