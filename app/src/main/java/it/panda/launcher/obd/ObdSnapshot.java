package it.panda.launcher.obd;

/**
 * Fotografia dei dati motore in un istante. I campi null indicano un dato non disponibile
 * (non supportato dalla centralina o non ancora letto).
 */
public final class ObdSnapshot {
    public Double rpm;
    public Double speedKmh;
    public Double coolantC;
    public Double intakeC;
    public Double intakeKpa;
    public Double throttlePct;
    public Double loadPct;
    public Double fuelLevelPct;
    public Double ambientC;
    public Double batteryV;

    /** Marcia stimata: 1-5, 0 = folle/ferma, -1 = non determinabile, null = dati mancanti. */
    public Integer gear;
    /** Consumo istantaneo stimato, l/100 km. */
    public Double consumptionL100;
    /** Consumo medio del viaggio, l/100 km. */
    public Double averageL100;
    /** Codici errore presenti nella centralina; null = non ancora letti. */
    public java.util.List<String> dtcs;
}
