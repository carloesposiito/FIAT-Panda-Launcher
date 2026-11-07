package it.panda.launcher.obd;

/**
 * Stima del consumo per la Panda 169 benzina.
 *
 * La centralina del motore Fire non fornisce il consumo e di solito nemmeno il flusso d'aria (MAF):
 * misura la pressione nel collettore (MAP). Dalla pressione, dai giri e dalla temperatura dell'aria
 * si ricava l'aria aspirata con il metodo "speed-density"; il carburante è circa aria / 14,7.
 * È una stima: tende a sovrastimare in rilascio (quando l'iniezione è tagliata).
 */
public final class FuelEstimator {

    /** Rapporto aria/benzina stechiometrico. */
    static final double AIR_FUEL_RATIO = 14.7;
    /** Densità della benzina, g/l. */
    static final double FUEL_DENSITY_G_PER_L = 745.0;
    /** Massa molare dell'aria, g/mol. */
    static final double AIR_MOLAR_MASS = 28.97;
    /** Costante dei gas, J/(mol·K). */
    static final double GAS_CONSTANT = 8.314;
    /** Rendimento volumetrico tipico di un piccolo aspirato. */
    public static final double DEFAULT_VOLUMETRIC_EFFICIENCY = 0.80;

    private double fuelLiters;
    private double distanceKm;

    /**
     * Flusso d'aria stimato (g/s) da giri, pressione collettore e temperatura aria.
     *
     * @param displacementLiters cilindrata in litri (es. 1.242)
     */
    public static double airFlowFromMap(double rpm, double mapKpa, double intakeTempC,
                                        double displacementLiters, double volumetricEfficiency) {
        double intakeKelvin = intakeTempC + 273.15;
        // Un motore a 4 tempi aspira la sua cilindrata ogni 2 giri.
        double litersPerSecond = rpm / 60.0 / 2.0 * displacementLiters * volumetricEfficiency;
        // Legge dei gas: densità dell'aria (g/l) = P·M / (R·T), con P in kPa.
        double airDensity = mapKpa * AIR_MOLAR_MASS / (GAS_CONSTANT * intakeKelvin);
        return litersPerSecond * airDensity;
    }

    /** Litri di benzina all'ora a partire dal flusso d'aria in g/s. */
    public static double litersPerHour(double airFlowGs) {
        return airFlowGs / AIR_FUEL_RATIO / FUEL_DENSITY_G_PER_L * 3600.0;
    }

    /** Aggiunge un intervallo di guida al conteggio del viaggio. */
    public void add(double litersPerHour, double speedKmh, double seconds) {
        fuelLiters += litersPerHour * seconds / 3600.0;
        distanceKm += speedKmh * seconds / 3600.0;
    }

    /** Media del viaggio in l/100 km, disponibile dopo almeno mezzo chilometro. */
    public Double average() {
        if (distanceKm < 0.5) {
            return null;
        }
        return fuelLiters / distanceKm * 100.0;
    }

    public void reset() {
        fuelLiters = 0;
        distanceKm = 0;
    }
}
