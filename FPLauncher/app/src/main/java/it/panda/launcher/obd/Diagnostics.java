package it.panda.launcher.obd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Avvisi da mostrare nel pannello del triangolo: spia motore e codici di errore della centralina,
 * più gli avvisi ricavati dai valori letti (acqua troppo calda, batteria che non carica...).
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class Diagnostics {

    public enum Severity { RED, ORANGE }

    public static final class Alert {
        public final Severity severity;
        /** Codice (es. "P0133") oppure null per gli avvisi ricavati dai valori. */
        public final String code;
        public final String title;
        public final String detail;

        Alert(Severity severity, String code, String title, String detail) {
            this.severity = severity;
            this.code = code;
            this.title = title;
            this.detail = detail;
        }
    }

    private Diagnostics() {
    }

    public static List<Alert> evaluate(ObdSnapshot s) {
        List<Alert> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        boolean mil = Boolean.TRUE.equals(s.milOn);
        boolean running = s.rpm != null && s.rpm > 500;

        if (mil) {
            out.add(new Alert(Severity.RED, null, "Spia motore accesa",
                    "La centralina ha rilevato un guasto alle emissioni o al motore. Falla controllare."));
        }
        if (s.dtcStored != null) {
            for (String c : s.dtcStored) {
                out.add(new Alert(mil ? Severity.RED : Severity.ORANGE, c, DtcCatalog.describe(c), "Memorizzato"));
            }
        }
        if (s.dtcPending != null) {
            for (String c : s.dtcPending) {
                if (s.dtcStored == null || !s.dtcStored.contains(c)) {
                    out.add(new Alert(Severity.ORANGE, c, DtcCatalog.describe(c),
                            "In attesa: rilevato una volta, da confermare"));
                }
            }
        }

        if (s.coolantC != null && s.coolantC >= 110) {
            out.add(new Alert(Severity.RED, null, "Temperatura acqua troppo alta",
                    String.format(Locale.ITALIAN, "%d °C: fermati e lascia raffreddare il motore.", Math.round(s.coolantC))));
        } else if (s.coolantC != null && s.coolantC >= 103) {
            out.add(new Alert(Severity.ORANGE, null, "Temperatura acqua alta",
                    String.format(Locale.ITALIAN, "%d °C: controlla ventola e livello del liquido.", Math.round(s.coolantC))));
        }
        if (s.batteryV != null) {
            if (running && s.batteryV < 12.8) {
                out.add(new Alert(Severity.RED, null, "La batteria non si ricarica",
                        String.format(Locale.ITALIAN, "%.1f V a motore acceso: possibile guasto all'alternatore.", s.batteryV)));
            } else if (running && s.batteryV > 15.0) {
                out.add(new Alert(Severity.ORANGE, null, "Tensione di ricarica alta",
                        String.format(Locale.ITALIAN, "%.1f V: possibile guasto al regolatore.", s.batteryV)));
            } else if (!running && s.batteryV < 11.9) {
                out.add(new Alert(Severity.ORANGE, null, "Batteria scarica",
                        String.format(Locale.ITALIAN, "%.1f V a motore spento.", s.batteryV)));
            }
        }
        if (s.fuelLevelPct != null && s.fuelLevelPct < 10) {
            out.add(new Alert(Severity.ORANGE, null, "Riserva carburante",
                    String.format(Locale.ITALIAN, "Restano circa il %d%%.", Math.round(s.fuelLevelPct))));
        }
        return out;
    }

    /** La gravità più alta tra gli avvisi, oppure null se non ce ne sono. */
    public static Severity worst(List<Alert> alerts) {
        Severity w = null;
        for (Alert a : alerts) {
            if (a.severity == Severity.RED) {
                return Severity.RED;
            }
            w = Severity.ORANGE;
        }
        return w;
    }
}
