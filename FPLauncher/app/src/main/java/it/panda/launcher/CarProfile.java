package it.panda.launcher;

import java.text.Normalizer;

/**
 * Un'auto gestita dal launcher. I suoi valori (serbatoio, fondo scala, adattatore OBD, trip...) stanno nelle
 * preferenze con chiavi che iniziano con l'id dell'auto: vedi {@link #keyFor}.
 */
final class CarProfile {

    /** La Panda di serie: usa le chiavi di sempre, così i dati di prima restano suoi senza migrazione. */
    static final String DEFAULT_ID = "panda";

    final String id;
    final String brand;
    final String model;

    CarProfile(String id, String brand, String model) {
        this.id = id;
        this.brand = clean(brand);
        this.model = clean(model);
    }

    boolean isBuiltIn() {
        return DEFAULT_ID.equals(id);
    }

    String displayName() {
        return (brand + " " + model).trim();
    }

    /** Cartella dove cercare le immagini: Pictures\MARCA_MODELLO. */
    String folderName() {
        return folderName(brand, model);
    }

    static String folderName(String brand, String model) {
        String b = fileSafe(brand);
        String m = fileSafe(model);
        if (b.isEmpty() || m.isEmpty()) {
            return b + m;
        }
        return b + "_" + m;
    }

    /**
     * Chiave delle preferenze per un valore di questa auto. L'id non contiene mai '.', quindi il prefisso
     * "car.id." di un'auto non può coincidere con quello di un'altra ("fiat_panda" e "fiat_panda_2").
     */
    static String keyFor(String carId, String key) {
        return DEFAULT_ID.equals(carId) ? key : "car." + carId + "." + key;
    }

    /** Solo lettere, numeri, '.', '_' e '-': niente accenti, spazi o separatori di percorso. */
    private static String fileSafe(String s) {
        if (s == null) {
            return "";
        }
        String plain = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        plain = plain.replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9._-]", "");
        return plain.replaceAll("^[._-]+", "").replaceAll("_+$", "");
    }

    /** Tabulazioni e a capo romperebbero l'elenco salvato. */
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ").trim();
    }
}
