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
    public Double batteryV;

    /** Trip: accumulato fino all'azzeramento manuale. */
    public TripComputer.Summary trip;
    /** Spia motore (MIL) accesa secondo la centralina; null se non letta. */
    public Boolean milOn;
    /** Quante volte spia e codici sono stati letti da questo collegamento; 0 = mai letti. */
    public int diagReads;
    /** Codici di errore memorizzati (modo 03) e in attesa di conferma (modo 07). */
    public java.util.List<String> dtcStored;
    public java.util.List<String> dtcPending;

    /** Velocità media ogni 10 s nell'ultima mezz'ora, per il grafico della Home. */
    public java.util.List<Double> speedHistory;
}
