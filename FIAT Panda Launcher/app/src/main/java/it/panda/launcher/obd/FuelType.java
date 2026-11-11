package it.panda.launcher.obd;

/** Carburante dell'auto: da lui dipendono densità, rapporto aria/carburante e se il consumo si può stimare dall'aria. */
public enum FuelType {

    /** Benzina: rapporto stechiometrico 14,7, densità 745 g/l. */
    PETROL(14.7, 745.0, 190, true),
    /** GPL: rapporto 15,5, densità circa 540 g/l. */
    LPG(15.5, 540.0, 80, true),
    /**
     * Gasolio: densità 832 g/l. Il motore lavora sempre con molta aria in eccesso, quindi dall'aria aspirata
     * non si ricava il carburante: il consumo c'è solo se la centralina lo dà (PID 5E).
     */
    DIESEL(14.5, 832.0, 180, false);

    final double airFuelRatio;
    final double densityGPerL;
    /** Prezzo di partenza in centesimi al litro. */
    public final int defaultPriceCents;
    private final boolean estimatesFromAir;

    FuelType(double airFuelRatio, double densityGPerL, int defaultPriceCents, boolean estimatesFromAir) {
        this.airFuelRatio = airFuelRatio;
        this.densityGPerL = densityGPerL;
        this.defaultPriceCents = defaultPriceCents;
        this.estimatesFromAir = estimatesFromAir;
    }

    /** True se dal flusso d'aria (MAF o stima dalla pressione) si può ricavare il carburante. */
    public boolean estimatesFromAir() {
        return estimatesFromAir;
    }

    /** Il carburante salvato; benzina se manca o non si riconosce. */
    public static FuelType fromName(String name) {
        for (FuelType t : values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return PETROL;
    }
}
