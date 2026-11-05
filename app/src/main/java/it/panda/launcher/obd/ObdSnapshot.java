package it.panda.launcher.obd;

/**
 * Fotografia dei dati motore in un istante. I campi null indicano un dato non disponibile
 * (non supportato dalla centralina o non ancora letto).
 */
public final class ObdSnapshot {
    public Double rpm;
    public Double speedKmh;
    public Double coolantC;
    public Double oilC;
    public Double intakeC;
    public Double intakeKpa;
    public Double throttlePct;
    public Double loadPct;
    public Double fuelLevelPct;
    public Double batteryV;

    /** Marcia stimata: 1-5, 0 = folle/ferma, -1 = non determinabile, null = dati mancanti. */
    public Integer gear;
    /** Consumo istantaneo, litri/ora (misurato se la centralina lo fornisce, altrimenti stimato). */
    public Double fuelRateLph;
    /** Consumo istantaneo, l/100 km (null da fermi). */
    public Double consumptionL100;

    /** Viaggio corrente: dall'ultimo collegamento (di solito l'accensione). */
    public TripComputer.Summary drive;
    /** Trip: accumulato fino all'azzeramento manuale. */
    public TripComputer.Summary trip;
    /** Spia motore (MIL) accesa secondo la centralina; null se non letta. */
    public Boolean milOn;
    /** Codici di errore memorizzati (modo 03) e in attesa di conferma (modo 07). */
    /** Quante volte spia e codici sono stati letti da questo collegamento; 0 = mai letti. */
    public int diagReads;
    public java.util.List<String> dtcStored;
    public java.util.List<String> dtcPending;

    /** Consumo l/100 km ogni 10 s nell'ultima mezz'ora (null = auto ferma). */
    public java.util.List<Double> history;
}
