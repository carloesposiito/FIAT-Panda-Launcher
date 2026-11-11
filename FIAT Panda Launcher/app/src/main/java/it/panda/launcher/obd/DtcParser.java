package it.panda.launcher.obd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Codici di errore (DTC) dalla centralina: risposte ai modi 03 (memorizzati) e 07 (in attesa),
 * e stato della spia motore dal PID 01 01.
 *
 * Formati dell'ELM327 (spazi e intestazioni spenti):
 * - K-line (ISO 9141 / KWP2000, come sulla Panda 169): una riga per ogni 3 codici, es. "430133023400"
 *   = 43 + tre coppie di byte (0000 = vuoto);
 * - CAN: "43" + numero di codici + coppie, eventualmente su più righe "0:", "1:" precedute dalla lunghezza.
 * Classe "pura" (niente Android) così si può provare con i test JUnit.
 */
public final class DtcParser {

    private DtcParser() {
    }

    /**
     * @param raw          risposta dell'ELM
     * @param responseByte 0x43 per il modo 03, 0x47 per il modo 07
     * @return codici come "P0133", senza doppioni, nell'ordine ricevuto
     */
    public static List<String> parse(String raw, int responseByte) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || ObdParser.isError(raw) && !raw.toUpperCase(Locale.US).contains(String.format("%02X", responseByte))) {
            return new ArrayList<>(out);
        }
        String prefix = String.format(Locale.US, "%02X", responseByte);
        List<String> lines = new ArrayList<>();
        boolean multiFrame = false;
        for (String line : raw.toUpperCase(Locale.US).split("[\\r\\n>]+")) {
            String t = line.trim().replace(" ", "");
            if (t.isEmpty()) {
                continue;
            }
            if (t.matches("[0-9A-F]:[0-9A-F]+")) {
                multiFrame = true;
                lines.add(t.substring(2));
            } else if (t.matches("[0-9A-F]+")) {
                lines.add(t);
            }
        }

        if (multiFrame) {
            // CAN su più righe: si concatenano i dati delle righe "n:" (la riga corta iniziale è la lunghezza)
            StringBuilder all = new StringBuilder();
            for (String l : lines) {
                if (l.length() > 3) {
                    all.append(l);
                }
            }
            String hex = all.toString();
            int start = hex.indexOf(prefix);
            if (start >= 0) {
                addCan(hex.substring(start + 2), out);
            }
            return new ArrayList<>(out);
        }

        for (String hex : lines) {
            if (!hex.startsWith(prefix)) {
                continue;
            }
            String data = hex.substring(2);
            // dopo il 43 un numero dispari di byte = CAN (il primo è il numero di codici)
            if ((data.length() / 2) % 2 == 1) {
                addCan(data, out);
            } else {
                addPairs(data, out);
            }
        }
        return new ArrayList<>(out);
    }

    private static void addCan(String data, Set<String> out) {
        if (data.length() < 2) {
            return;
        }
        int count = Integer.parseInt(data.substring(0, 2), 16);
        String pairs = data.substring(2);
        addPairs(pairs.substring(0, Math.min(pairs.length(), count * 4)), out);
    }

    private static void addPairs(String hex, Set<String> out) {
        for (int i = 0; i + 4 <= hex.length(); i += 4) {
            int a = Integer.parseInt(hex.substring(i, i + 2), 16);
            int b = Integer.parseInt(hex.substring(i + 2, i + 4), 16);
            if (a == 0 && b == 0) {
                continue;
            }
            out.add(decode(a, b));
        }
    }

    /** Due byte → codice, es. 0x01 0x33 → "P0133". */
    public static String decode(int a, int b) {
        char letter = "PCBU".charAt((a >> 6) & 0x03);
        return String.format(Locale.US, "%c%d%X%02X", letter, (a >> 4) & 0x03, a & 0x0F, b);
    }

    /** Spia motore (MIL) accesa, dalla risposta a "0101"; null se non leggibile. */
    public static Boolean milOn(String raw) {
        int[] d = ObdParser.parseMode01(raw, 0x01, 4);
        return d == null ? null : (d[0] & 0x80) != 0;
    }
}
