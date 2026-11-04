package it.panda.launcher.obd;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Interpreta le risposte testuali dell'ELM327.
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 *
 * Esempio: alla richiesta "010C" l'ELM risponde "41 0C 1A F8" (oppure "410C1AF8" con gli spazi spenti):
 * 41 = risposta al modo 01, 0C = PID dei giri, 1A F8 = dati.
 */
public final class ObdParser {

    private ObdParser() {
    }

    /** Risposte dell'ELM che indicano che il dato non è arrivato. */
    private static final String[] ERRORS = {
            "NO DATA", "UNABLE TO CONNECT", "BUS INIT", "ERROR", "STOPPED", "?", "CAN ERROR", "BUFFER FULL"
    };

    /** true se la risposta è un messaggio di errore dell'ELM anziché dati. */
    public static boolean isError(String raw) {
        if (raw == null) {
            return true;
        }
        String up = raw.toUpperCase(Locale.US);
        for (String e : ERRORS) {
            if (up.contains(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Estrae i byte di dati di una risposta al modo 01.
     *
     * @return i byte (0-255), oppure null se la risposta non contiene il PID richiesto
     */
    public static int[] parseMode01(String raw, int pid, int dataBytes) {
        if (raw == null) {
            return null;
        }
        String prefix = String.format(Locale.US, "41%02X", pid);
        // L'ELM può restituire più righe (es. "SEARCHING..." prima dei dati): si cerca quella giusta.
        for (String line : raw.toUpperCase(Locale.US).split("[\\r\\n>]+")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.matches("[0-9A-F ]+")) {
                continue;
            }
            String hex = trimmed.replace(" ", "");
            if (!hex.startsWith(prefix) || hex.length() < prefix.length() + dataBytes * 2) {
                continue;
            }
            int[] data = new int[dataBytes];
            for (int i = 0; i < dataBytes; i++) {
                int start = prefix.length() + i * 2;
                data[i] = Integer.parseInt(hex.substring(start, start + 2), 16);
            }
            return data;
        }
        return null;
    }

    /**
     * Interpreta la risposta a "0100", "0120", "0140"...: 4 byte in cui ogni bit dice
     * se il PID corrispondente è supportato. Il bit più alto corrisponde a base+1.
     */
    public static Set<Integer> parseSupportedPids(String raw, int basePid) {
        Set<Integer> result = new LinkedHashSet<>();
        int[] d = parseMode01(raw, basePid, 4);
        if (d == null) {
            return result;
        }
        long bits = ((long) d[0] << 24) | ((long) d[1] << 16) | ((long) d[2] << 8) | d[3];
        for (int i = 0; i < 32; i++) {
            if ((bits & (1L << (31 - i))) != 0) {
                result.add(basePid + i + 1);
            }
        }
        return result;
    }

    /**
     * Interpreta la risposta al comando "ATRV" (tensione letta dall'adattatore sul pin 16 della presa),
     * es. "14.1V" o "12,6V".
     *
     * @return la tensione in volt, oppure null se non leggibile
     */
    public static Double parseVoltage(String raw) {
        if (raw == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}[.,]\\d)\\s*V", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (!m.find()) {
            return null;
        }
        return Double.parseDouble(m.group(1).replace(',', '.'));
    }

    /** Ripulisce una risposta per mostrarla all'utente (una riga, senza prompt). */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(">", " ").replaceAll("\\s+", " ").trim();
    }
}
