package it.panda.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Impostazioni salvate del launcher. */
public final class Prefs {

    private static final String FILE = "panda_launcher";
    private static final String KEY_OBD_ADDRESS = "obd_address";
    private static final String KEY_OBD_NAME = "obd_name";
    static final String KEY_DISPLACEMENT_CC = "displacement_cc";
    private static final String KEY_FAVORITES = "favorite_apps";
    private static final String KEY_APP_SLOTS = "app_slots";
    private static final String KEY_RADIO_APP = "radio_app";
    static final String KEY_FUEL_TYPE = "fuel_type";
    private static final String KEY_OBD_DELAY = "obd_delay_s";
    private static final String KEY_TRIP = "trip_state";
    private static final String KEY_PAGE = "home_page";
    private static final String KEY_OBD_ENABLED = "obd_enabled";
    private static final String KEY_DEMO = "demo_data";
    static final String KEY_MAX_SPEED = "max_speed";
    static final String KEY_RED_RPM = "red_rpm";
    static final String KEY_SHIFT_RPM = "shift_rpm";
    static final String KEY_MIN_RPM = "min_rpm";
    private static final String KEY_DETAILED = "detailed_consumption";
    private static final String KEY_FUEL_PRICE = "fuel_price_cents";
    private static final String KEY_CALIB_ON = "fuel_calibration";
    static final String KEY_TANK_LITERS = "tank_liters";
    private static final String KEY_CALIB_STATE = "fuel_calibration_state";
    private static final String KEY_CALIB_EPOCH = "fuel_calibration_epoch";

    /** Cilindrata della Panda di serie, la Fiat Panda 169 AXF1A 08B: 1242 cm³ (dal libretto). */
    public static final int CC_12 = 1242;

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return shared(c);
    }

    /** Id dell'auto attiva (per chi non è nel pacchetto del launcher). */
    public static String activeCarId(Context c) {
        return CarStore.activeId(c);
    }

    /** Le preferenze del launcher, per CarStore. */
    static SharedPreferences shared(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Chiave di un valore dell'auto attiva. */
    private static String k(Context c, String key) {
        return CarProfile.keyFor(CarStore.activeId(c), key);
    }

    /** OBD attivo; se mai impostato, attivo solo se era già stato scelto un adattatore. */
    public static boolean getObdEnabled(Context c) {
        SharedPreferences p = sp(c);
        return p.getBoolean(KEY_OBD_ENABLED, p.getString(k(c, KEY_OBD_ADDRESS), null) != null);
    }

    /** Secondi da aspettare prima di collegarsi all'OBD (avvio, risveglio, collegamento caduto): il telefono si collega per primo. */
    public static int getObdDelaySeconds(Context c) {
        return sp(c).getInt(KEY_OBD_DELAY, 15);
    }

    public static void setObdDelaySeconds(Context c, int seconds) {
        sp(c).edit().putInt(KEY_OBD_DELAY, seconds).apply();
    }

    /** Fondo scala del tachimetro (km/h), da far corrispondere a quello dell'auto. */
    public static int getMaxSpeed(Context c) {
        return sp(c).getInt(k(c, KEY_MAX_SPEED), 160);
    }

    public static void setMaxSpeed(Context c, int kmh) {
        sp(c).edit().putInt(k(c, KEY_MAX_SPEED), kmh).apply();
    }

    /** Giri/min oltre i quali la freccia diventa rossa (fuori giri). */
    public static int getRedRpm(Context c) {
        return sp(c).getInt(k(c, KEY_RED_RPM), 6500);
    }

    public static void setRedRpm(Context c, int rpm) {
        sp(c).edit().putInt(k(c, KEY_RED_RPM), rpm).apply();
    }

    /** "Consumo dettagliato": nella Home compare la riga con velocità media e costo in euro. */
    public static boolean getDetailedConsumption(Context c) {
        return sp(c).getBoolean(KEY_DETAILED, false);
    }

    public static void setDetailedConsumption(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_DETAILED, on).apply();
    }

    /** Prezzo del carburante in centesimi di euro al litro (per il costo del consumo dettagliato). */
    public static int getFuelPriceCents(Context c) {
        return sp(c).getInt(k(c, KEY_FUEL_PRICE), getFuelType(c).defaultPriceCents);
    }

    public static void setFuelPriceCents(Context c, int cents) {
        sp(c).edit().putInt(k(c, KEY_FUEL_PRICE), cents).apply();
    }

    /** Carburante dell'auto attiva (benzina se non scelto): decide come si stima il consumo e il prezzo di partenza. */
    public static it.panda.launcher.obd.FuelType getFuelType(Context c) {
        return it.panda.launcher.obd.FuelType.fromName(sp(c).getString(k(c, KEY_FUEL_TYPE), null));
    }

    public static void setFuelType(Context c, it.panda.launcher.obd.FuelType type) {
        sp(c).edit().putString(k(c, KEY_FUEL_TYPE), type.name()).apply();
    }

    /** Giri/min sotto i quali, in marcia, compare la freccia "scala marcia". */
    public static int getMinRpm(Context c) {
        return sp(c).getInt(k(c, KEY_MIN_RPM), 1500);
    }

    public static void setMinRpm(Context c, int rpm) {
        sp(c).edit().putInt(k(c, KEY_MIN_RPM), rpm).apply();
    }

    /** Giri/min da cui compare la freccia "cambia marcia". */
    public static int getShiftRpm(Context c) {
        return sp(c).getInt(k(c, KEY_SHIFT_RPM), 6000);
    }

    public static void setShiftRpm(Context c, int rpm) {
        sp(c).edit().putInt(k(c, KEY_SHIFT_RPM), rpm).apply();
    }

    /** Dati finti al posto di quelli dell'auto, per provare la grafica senza adattatore OBD. */
    public static boolean getDemo(Context c) {
        return sp(c).getBoolean(KEY_DEMO, false);
    }

    public static void setDemo(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_DEMO, on).apply();
    }

    public static void setObdEnabled(Context c, boolean enabled) {
        sp(c).edit().putBoolean(KEY_OBD_ENABLED, enabled).apply();
    }

    /** Indirizzo Bluetooth dell'ELM327, oppure null se non scelto. */
    public static String getObdAddress(Context c) {
        return sp(c).getString(k(c, KEY_OBD_ADDRESS), null);
    }

    public static String getObdName(Context c) {
        return sp(c).getString(k(c, KEY_OBD_NAME), null);
    }

    public static void setObdDevice(Context c, String address, String name) {
        sp(c).edit().putString(k(c, KEY_OBD_ADDRESS), address).putString(k(c, KEY_OBD_NAME), name).apply();
    }

    public static int getDisplacementCc(Context c) {
        return sp(c).getInt(k(c, KEY_DISPLACEMENT_CC), CC_12);
    }

    public static void setDisplacementCc(Context c, int cc) {
        sp(c).edit().putInt(k(c, KEY_DISPLACEMENT_CC), cc).apply();
    }

    // ---------------------------------------------------------------- taratura consumi

    /** Corregge i consumi stimati confrontandoli con il calo del livello nel serbatoio. */
    public static boolean getFuelCalibration(Context c) {
        return sp(c).getBoolean(KEY_CALIB_ON, true);
    }

    public static void setFuelCalibration(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_CALIB_ON, on).apply();
    }

    /** Capacità del serbatoio in litri (Panda 169: 38). */
    public static int getTankLiters(Context c) {
        return sp(c).getInt(k(c, KEY_TANK_LITERS), 38);
    }

    public static void setTankLiters(Context c, int liters) {
        sp(c).edit().putInt(k(c, KEY_TANK_LITERS), liters).apply();
    }

    // Taratura e trip hanno anche una versione con l'id dell'auto: chi li tiene in memoria (ObdManager) li salva
    // sull'auto a cui appartengono, anche se nel frattempo dalle impostazioni è stata scelta un'altra.

    public static String getCalibrationState(Context c) {
        return getCalibrationState(c, CarStore.activeId(c));
    }

    public static String getCalibrationState(Context c, String carId) {
        return sp(c).getString(CarProfile.keyFor(carId, KEY_CALIB_STATE), null);
    }

    public static void setCalibrationState(Context c, String carId, String state) {
        sp(c).edit().putString(CarProfile.keyFor(carId, KEY_CALIB_STATE), state).apply();
    }

    /** Cresce a ogni azzeramento dalle impostazioni: chi tiene la taratura in memoria riparte da zero. */
    public static int getCalibrationEpoch(Context c) {
        return getCalibrationEpoch(c, CarStore.activeId(c));
    }

    public static int getCalibrationEpoch(Context c, String carId) {
        return sp(c).getInt(CarProfile.keyFor(carId, KEY_CALIB_EPOCH), 0);
    }

    public static void resetCalibration(Context c) {
        SharedPreferences p = sp(c);
        p.edit().remove(k(c, KEY_CALIB_STATE)).putInt(k(c, KEY_CALIB_EPOCH), p.getInt(k(c, KEY_CALIB_EPOCH), 0) + 1).apply();
    }

    // ---------------------------------------------------------------- app nella colonna

    /** App scelte per la colonna, in ordine; null se l'utente non le ha mai scelte. */
    public static List<ComponentName> getFavorites(Context c) {
        String raw = sp(c).getString(KEY_FAVORITES, null);
        if (raw == null) {
            return null;
        }
        List<ComponentName> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            ComponentName cn = ComponentName.unflattenFromString(line.trim());
            if (cn != null) {
                out.add(cn);
            }
        }
        return out;
    }

    public static void setFavorites(Context c, List<ComponentName> apps) {
        StringBuilder b = new StringBuilder();
        for (ComponentName cn : apps) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(cn.flattenToString());
        }
        sp(c).edit().putString(KEY_FAVORITES, b.toString()).apply();
    }

    /** App da aprire toccando il blocco radio della Home; null = non ancora scelta. */
    public static ComponentName getRadioApp(Context c) {
        String raw = sp(c).getString(KEY_RADIO_APP, null);
        return raw == null ? null : ComponentName.unflattenFromString(raw);
    }

    public static void setRadioApp(Context c, ComponentName app) {
        sp(c).edit().putString(KEY_RADIO_APP, app == null ? null : app.flattenToString()).apply();
    }

    /** Quante icone entrano nella colonna (lo calcola la Home, lo mostra l'editor). 0 = non ancora noto. */
    public static int getAppSlots(Context c) {
        return sp(c).getInt(KEY_APP_SLOTS, 0);
    }

    public static void setAppSlots(Context c, int slots) {
        sp(c).edit().putInt(KEY_APP_SLOTS, slots).apply();
    }

    // ---------------------------------------------------------------- trip

    public static String getTripState(Context c, String carId) {
        return sp(c).getString(CarProfile.keyFor(carId, KEY_TRIP), null);
    }

    public static void setTripState(Context c, String carId, String state) {
        sp(c).edit().putString(CarProfile.keyFor(carId, KEY_TRIP), state).apply();
    }

    /** Pagina della Home mostrata per ultima (0 = auto 3D, 1 = dati auto, 2 = consumi). */
    public static int getPage(Context c) {
        return sp(c).getInt(KEY_PAGE, 0);
    }

    public static void setPage(Context c, int page) {
        sp(c).edit().putInt(KEY_PAGE, page).apply();
    }
}
