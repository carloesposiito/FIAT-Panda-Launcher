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
    private static final String KEY_DISPLACEMENT_CC = "displacement_cc";
    private static final String KEY_FAVORITES = "favorite_apps";
    private static final String KEY_APP_SLOTS = "app_slots";
    private static final String KEY_TRIP = "trip_state";
    private static final String KEY_TRIP_TAB = "trip_tab";
    private static final String KEY_PAGE = "home_page";
    private static final String KEY_OBD_ENABLED = "obd_enabled";
    private static final String KEY_NAV_ENABLED = "nav_enabled";
    private static final String KEY_DEMO = "demo_data";
    private static final String KEY_CAR_DATA = "car_data";
    private static final String KEY_BORDERS = "debug_borders";
    private static final String KEY_MAX_SPEED = "max_speed";
    private static final String KEY_RED_RPM = "red_rpm";
    private static final String KEY_SHIFT_RPM = "shift_rpm";

    /** Cilindrate dei motori benzina della Panda 169. */
    public static final int CC_11 = 1108;
    public static final int CC_12 = 1242;
    public static final int CC_14 = 1368;

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Ricezione della navigazione dall'app goESP sul telefono. */
    public static boolean getNavEnabled(Context c) {
        return sp(c).getBoolean(KEY_NAV_ENABLED, false);
    }

    public static void setNavEnabled(Context c, boolean enabled) {
        sp(c).edit().putBoolean(KEY_NAV_ENABLED, enabled).apply();
    }

    /** OBD attivo; se mai impostato, attivo solo se era già stato scelto un adattatore. */
    public static boolean getObdEnabled(Context c) {
        SharedPreferences p = sp(c);
        return p.getBoolean(KEY_OBD_ENABLED, p.getString(KEY_OBD_ADDRESS, null) != null);
    }

    /** Fondo scala del tachimetro (km/h), da far corrispondere a quello dell'auto. */
    public static int getMaxSpeed(Context c) {
        return sp(c).getInt(KEY_MAX_SPEED, 160);
    }

    public static void setMaxSpeed(Context c, int kmh) {
        sp(c).edit().putInt(KEY_MAX_SPEED, kmh).apply();
    }

    /** Giri/min oltre i quali la freccia diventa rossa (fuori giri). */
    public static int getRedRpm(Context c) {
        return sp(c).getInt(KEY_RED_RPM, 6500);
    }

    public static void setRedRpm(Context c, int rpm) {
        sp(c).edit().putInt(KEY_RED_RPM, rpm).apply();
    }

    /** Giri/min da cui compare la freccia "cambia marcia". */
    public static int getShiftRpm(Context c) {
        return sp(c).getInt(KEY_SHIFT_RPM, 6000);
    }

    public static void setShiftRpm(Context c, int rpm) {
        sp(c).edit().putInt(KEY_SHIFT_RPM, rpm).apply();
    }

    /** Mostra i quattro dati (acqua, batteria, olio, carburante) con le linee che partono dall'auto. */
    public static boolean getCarData(Context c) {
        return sp(c).getBoolean(KEY_CAR_DATA, true);
    }

    public static void setCarData(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_CAR_DATA, on).apply();
    }

    /** Contorni colorati attorno ai blocchi della Home, per vedere gli spazi. */
    public static boolean getDebugBorders(Context c) {
        return sp(c).getBoolean(KEY_BORDERS, false);
    }

    public static void setDebugBorders(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_BORDERS, on).apply();
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
        return sp(c).getString(KEY_OBD_ADDRESS, null);
    }

    public static String getObdName(Context c) {
        return sp(c).getString(KEY_OBD_NAME, null);
    }

    public static void setObdDevice(Context c, String address, String name) {
        sp(c).edit().putString(KEY_OBD_ADDRESS, address).putString(KEY_OBD_NAME, name).apply();
    }

    public static int getDisplacementCc(Context c) {
        return sp(c).getInt(KEY_DISPLACEMENT_CC, CC_12);
    }

    public static void setDisplacementCc(Context c, int cc) {
        sp(c).edit().putInt(KEY_DISPLACEMENT_CC, cc).apply();
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

    /** Quante icone entrano nella colonna (lo calcola la Home, lo mostra l'editor). 0 = non ancora noto. */
    public static int getAppSlots(Context c) {
        return sp(c).getInt(KEY_APP_SLOTS, 0);
    }

    public static void setAppSlots(Context c, int slots) {
        sp(c).edit().putInt(KEY_APP_SLOTS, slots).apply();
    }

    // ---------------------------------------------------------------- trip

    public static String getTripState(Context c) {
        return sp(c).getString(KEY_TRIP, null);
    }

    public static void setTripState(Context c, String state) {
        sp(c).edit().putString(KEY_TRIP, state).apply();
    }

    /** true se nel riquadro consumi è selezionata la scheda "Trip" invece di "Viaggio". */
    public static boolean getTripTab(Context c) {
        return sp(c).getBoolean(KEY_TRIP_TAB, false);
    }

    public static void setTripTab(Context c, boolean trip) {
        sp(c).edit().putBoolean(KEY_TRIP_TAB, trip).apply();
    }

    /** Pagina della Home mostrata per ultima (0 = auto 3D, 1 = dati auto, 2 = consumi). */
    public static int getPage(Context c) {
        return sp(c).getInt(KEY_PAGE, 0);
    }

    public static void setPage(Context c, int page) {
        sp(c).edit().putInt(KEY_PAGE, page).apply();
    }
}
