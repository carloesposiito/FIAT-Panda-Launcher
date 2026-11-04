package it.panda.launcher.apps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Logica pura della scelta delle app mostrate a sinistra.
 * La scelta è una lista ordinata di chiavi (componenti app); {@code null} significa
 * "mai configurata": si mostrano tutte le app.
 */
public final class FavoriteSelection {

    private static final String SEPARATOR = "\n";

    private FavoriteSelection() {
    }

    /** Le app scelte, nell'ordine salvato. Le chiavi senza app (disinstallate) si ignorano. */
    public static <T> List<T> select(List<T> all, List<String> saved, Function<T, String> key) {
        if (saved == null) {
            return new ArrayList<>(all);
        }
        Map<String, T> byKey = new HashMap<>();
        for (T item : all) {
            byKey.put(key.apply(item), item);
        }
        List<T> out = new ArrayList<>();
        for (String k : saved) {
            T item = byKey.get(k);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** Nuova scelta dopo aver spuntato o tolto una app. Senza scelta precedente parte da tutte. */
    public static List<String> toggle(List<String> saved, List<String> allKeys, String key, boolean on) {
        List<String> out = saved == null ? new ArrayList<>(allKeys) : new ArrayList<>(saved);
        if (on) {
            if (!out.contains(key)) {
                out.add(key);
            }
        } else {
            out.remove(key);
        }
        return out;
    }

    public static String encode(List<String> keys) {
        return String.join(SEPARATOR, keys);
    }

    /** {@code null} se non c'è nulla di salvato; lista vuota se l'utente ha tolto tutto. */
    public static List<String> decode(String stored) {
        if (stored == null) {
            return null;
        }
        if (stored.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(stored.split(SEPARATOR)));
    }
}
