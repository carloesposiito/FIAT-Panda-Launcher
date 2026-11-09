package it.panda.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import it.panda.launcher.obd.FuelType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Elenco delle auto e auto attiva. La Panda c'è sempre e non si elimina; le altre sono salvate come righe
 * "id TAB marca TAB modello".
 */
final class CarStore {

    private static final String KEY_CARS = "cars";
    private static final String KEY_ACTIVE = "active_car";

    private CarStore() {
    }

    /** Valori di partenza di una nuova auto. */
    static final class Settings {
        int displacementCc = 1000;
        int tankLiters = 40;
        int maxSpeed = 180;
        int minRpm = 1500;
        int shiftRpm = 5500;
        int redRpm = 6000;
        FuelType fuelType = FuelType.PETROL;
    }

    static CarProfile builtIn() {
        return new CarProfile(CarProfile.DEFAULT_ID, "FIAT", "Panda");
    }

    /** La Panda e poi le altre auto, nell'ordine in cui sono state create. */
    static List<CarProfile> list(Context c) {
        List<CarProfile> all = new ArrayList<>();
        all.add(builtIn());
        all.addAll(decode(Prefs.shared(c).getString(KEY_CARS, null)));
        return all;
    }

    /** Id dell'auto attiva: letto spesso (a ogni lettura dell'OBD), quindi senza controllare l'elenco. */
    static String activeId(Context c) {
        return Prefs.shared(c).getString(KEY_ACTIVE, CarProfile.DEFAULT_ID);
    }

    static CarProfile active(Context c) {
        String id = activeId(c);
        for (CarProfile car : list(c)) {
            if (car.id.equals(id)) {
                return car;
            }
        }
        return builtIn();
    }

    static void setActive(Context c, String id) {
        Prefs.shared(c).edit().putString(KEY_ACTIVE, id).apply();
    }

    /** Crea un'auto con i valori dati e la rende quella attiva. */
    static CarProfile create(Context c, String brand, String model, Settings s) {
        List<CarProfile> custom = decode(Prefs.shared(c).getString(KEY_CARS, null));
        Set<String> taken = new HashSet<>();
        taken.add(CarProfile.DEFAULT_ID);
        for (CarProfile car : custom) {
            taken.add(car.id);
        }
        CarProfile car = new CarProfile(uniqueId(brand, model, taken), brand, model);
        custom.add(car);
        Prefs.shared(c).edit()
                .putString(KEY_CARS, encode(custom))
                .putString(KEY_ACTIVE, car.id)
                .putInt(CarProfile.keyFor(car.id, Prefs.KEY_DISPLACEMENT_CC), s.displacementCc)
                .putInt(CarProfile.keyFor(car.id, Prefs.KEY_TANK_LITERS), s.tankLiters)
                .putInt(CarProfile.keyFor(car.id, Prefs.KEY_MAX_SPEED), s.maxSpeed)
                .putInt(CarProfile.keyFor(car.id, Prefs.KEY_MIN_RPM), s.minRpm)
                .putInt(CarProfile.keyFor(car.id, Prefs.KEY_SHIFT_RPM), s.shiftRpm)
                .putInt(CarProfile.keyFor(car.id, Prefs.KEY_RED_RPM), s.redRpm)
                .putString(CarProfile.keyFor(car.id, Prefs.KEY_FUEL_TYPE), s.fuelType.name())
                .apply();
        return car;
    }

    /** Elimina un'auto con i suoi dati e le sue immagini; la Panda non si elimina. */
    static void delete(Context c, String id) {
        if (CarProfile.DEFAULT_ID.equals(id)) {
            return;
        }
        List<CarProfile> custom = decode(Prefs.shared(c).getString(KEY_CARS, null));
        for (int i = custom.size() - 1; i >= 0; i--) {
            if (custom.get(i).id.equals(id)) {
                custom.remove(i);
            }
        }
        SharedPreferences p = Prefs.shared(c);
        SharedPreferences.Editor e = p.edit().putString(KEY_CARS, encode(custom));
        String prefix = CarProfile.keyFor(id, "");
        for (String key : p.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                e.remove(key);
            }
        }
        if (id.equals(activeId(c))) {
            e.putString(KEY_ACTIVE, CarProfile.DEFAULT_ID);
        }
        e.apply();
        CarImages.deleteAll(c, id);
    }

    // ---------------------------------------------------------------- formato salvato

    static String encode(List<CarProfile> cars) {
        StringBuilder b = new StringBuilder();
        for (CarProfile car : cars) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(car.id).append('\t').append(car.brand).append('\t').append(car.model);
        }
        return b.toString();
    }

    static List<CarProfile> decode(String raw) {
        List<CarProfile> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            String[] part = line.split("\t", -1);
            if (part.length == 3 && !part[0].isEmpty() && !CarProfile.DEFAULT_ID.equals(part[0])) {
                out.add(new CarProfile(part[0], part[1], part[2]));
            }
        }
        return out;
    }

    /** Id derivato da marca e modello, diverso da quelli già presi. */
    static String uniqueId(String brand, String model, Set<String> taken) {
        String base = CarProfile.folderName(brand, model).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        base = base.replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "auto";
        }
        String id = base;
        for (int n = 2; taken.contains(id); n++) {
            id = base + "_" + n;
        }
        return id;
    }

    /** Cartella con le immagini importate di un'auto. */
    static File imagesDir(Context c, String id) {
        return new File(c.getFilesDir(), "cars/" + id);
    }
}
