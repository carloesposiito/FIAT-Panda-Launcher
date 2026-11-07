package it.panda.launcher.obd;

import java.util.Locale;

/**
 * Parametri OBD-II standard (modo 01) utili per la Panda 169 benzina.
 * Le formule sono quelle della norma SAE J1979.
 * Non tutte le centraline li supportano: quelli mancanti vengono saltati
 * (vedi {@link ObdParser#parseSupportedPids}).
 */
public enum ObdPid {
    ENGINE_LOAD(0x04, 1),
    COOLANT_TEMP(0x05, 1),
    INTAKE_PRESSURE(0x0B, 1),
    RPM(0x0C, 2),
    SPEED(0x0D, 1),
    TIMING_ADVANCE(0x0E, 1),
    INTAKE_TEMP(0x0F, 1),
    MAF(0x10, 2),
    THROTTLE(0x11, 1),
    FUEL_LEVEL(0x2F, 1),
    MODULE_VOLTAGE(0x42, 2),
    FUEL_RATE(0x5E, 2);

    /** Numero del PID (es. 0x0C per i giri). */
    public final int pid;
    /** Quanti byte di dati restituisce la centralina. */
    public final int dataBytes;

    ObdPid(int pid, int dataBytes) {
        this.pid = pid;
        this.dataBytes = dataBytes;
    }

    /** Comando da inviare all'ELM327, es. "010C". */
    public String command() {
        return String.format(Locale.US, "01%02X", pid);
    }

    /**
     * Converte i byte ricevuti nel valore fisico.
     * Unità: % per carico/farfalla/carburante, °C per le temperature, kPa per la pressione,
     * rpm, km/h, gradi per l'anticipo, g/s per il flusso d'aria, V per la tensione, l/h per il consumo.
     */
    public double decode(int[] d) {
        int a = d[0];
        int b = d.length > 1 ? d[1] : 0;
        switch (this) {
            case ENGINE_LOAD:
            case THROTTLE:
            case FUEL_LEVEL:
                return a * 100.0 / 255.0;
            case COOLANT_TEMP:
            case INTAKE_TEMP:
                return a - 40;
            case INTAKE_PRESSURE:
            case SPEED:
                return a;
            case RPM:
                return (256 * a + b) / 4.0;
            case TIMING_ADVANCE:
                return a / 2.0 - 64.0;
            case MAF:
                return (256 * a + b) / 100.0;
            case MODULE_VOLTAGE:
                return (256 * a + b) / 1000.0;
            case FUEL_RATE:
                return (256 * a + b) / 20.0;
            default:
                throw new IllegalStateException("PID senza formula: " + this);
        }
    }
}
