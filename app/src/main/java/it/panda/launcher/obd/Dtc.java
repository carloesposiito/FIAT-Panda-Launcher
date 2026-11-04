package it.panda.launcher.obd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Codici errore della centralina (DTC): lettura della risposta al modo 03 e descrizioni in italiano.
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 *
 * Risposta tipica a "03": "43 01 33 03 02 00 00" = codici P0133 e P0302 (0000 = riempitivo).
 * Sulle linee CAN dopo "43" c'è anche il numero di codici. Non gestisce le risposte CAN su più
 * frame con l'indice "0:", "1:": sulla Panda 169 (ISO 9141 / KWP2000) non servono.
 */
public final class Dtc {

    private static final char[] SYSTEMS = {'P', 'C', 'B', 'U'};
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Set<String> CRITICAL = new LinkedHashSet<>();

    static {
        NAMES.put("P0105", "Sensore pressione collettore");
        NAMES.put("P0110", "Sensore temperatura aria aspirata");
        NAMES.put("P0115", "Sensore temperatura liquido motore");
        NAMES.put("P0120", "Sensore posizione farfalla");
        NAMES.put("P0130", "Sonda lambda a monte guasta");
        NAMES.put("P0133", "Sonda lambda lenta a rispondere");
        NAMES.put("P0135", "Riscaldatore sonda lambda");
        NAMES.put("P0171", "Miscela troppo povera");
        NAMES.put("P0172", "Miscela troppo ricca");
        NAMES.put("P0201", "Iniettore cilindro 1");
        NAMES.put("P0202", "Iniettore cilindro 2");
        NAMES.put("P0203", "Iniettore cilindro 3");
        NAMES.put("P0204", "Iniettore cilindro 4");
        NAMES.put("P0217", "Motore surriscaldato");
        NAMES.put("P0300", "Mancate accensioni (più cilindri)");
        NAMES.put("P0301", "Mancata accensione cilindro 1");
        NAMES.put("P0302", "Mancata accensione cilindro 2");
        NAMES.put("P0303", "Mancata accensione cilindro 3");
        NAMES.put("P0304", "Mancata accensione cilindro 4");
        NAMES.put("P0325", "Sensore di detonazione");
        NAMES.put("P0335", "Sensore giri albero motore");
        NAMES.put("P0340", "Sensore fase albero a camme");
        NAMES.put("P0420", "Catalizzatore poco efficiente");
        NAMES.put("P0440", "Impianto vapori carburante");
        NAMES.put("P0442", "Piccola perdita nei vapori carburante");
        NAMES.put("P0455", "Grossa perdita nei vapori carburante");
        NAMES.put("P0500", "Sensore velocità veicolo");
        NAMES.put("P0505", "Controllo del minimo");
        NAMES.put("P0562", "Tensione di bordo bassa");
        NAMES.put("P0563", "Tensione di bordo alta");

        // Possono danneggiare il motore o il catalizzatore: spia da trattare come rossa.
        CRITICAL.add("P0217");
        CRITICAL.add("P0300");
        CRITICAL.add("P0301");
        CRITICAL.add("P0302");
        CRITICAL.add("P0303");
        CRITICAL.add("P0304");
    }

    private Dtc() {
    }

    /** I codici presenti nella risposta al modo 03, senza duplicati e senza riempitivi. */
    public static List<String> parse(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return new ArrayList<>();
        }
        for (String line : raw.toUpperCase(Locale.US).split("[\\r\\n>]+")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.matches("[0-9A-F ]+")) {
                continue;
            }
            String hex = trimmed.replace(" ", "");
            if (!hex.startsWith("43") || hex.length() % 2 != 0) {
                continue;
            }
            hex = hex.substring(2);
            if ((hex.length() / 2) % 2 == 1) {
                hex = hex.substring(2); // numero di codici (linee CAN)
            }
            for (int i = 0; i + 4 <= hex.length(); i += 4) {
                int word = Integer.parseInt(hex.substring(i, i + 4), 16);
                if (word != 0) {
                    out.add(format(word));
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static String format(int word) {
        char system = SYSTEMS[(word >> 14) & 0x3];
        int digit = (word >> 12) & 0x3;
        return String.format(Locale.US, "%c%d%03X", system, digit, word & 0xFFF);
    }

    /** true se il codice indica un guasto che può danneggiare il motore. */
    public static boolean isCritical(String code) {
        return CRITICAL.contains(code);
    }

    public static String describe(String code) {
        String name = NAMES.get(code);
        return name != null ? name : "Codice non in elenco";
    }
}
