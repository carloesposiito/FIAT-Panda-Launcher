package it.panda.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import it.panda.launcher.apps.FavoriteSelection;

/** Impostazioni salvate del launcher. */
public final class Prefs {

    private static final String FILE = "panda_launcher";
    private static final String KEY_OBD_ADDRESS = "obd_address";
    private static final String KEY_OBD_NAME = "obd_name";
    private static final String KEY_DISPLACEMENT_CC = "displacement_cc";
    private static final String KEY_AUTO_ROTATE = "auto_rotate";
    private static final String KEY_FAVORITE_APPS = "favorite_apps";

    /** Cilindrate dei motori benzina della Panda 169. */
    public static final int CC_11 = 1108;
    public static final int CC_12 = 1242;
    public static final int CC_14 = 1368;

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
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

    /** App mostrate a sinistra (ordinate), oppure null se l'utente non ha ancora scelto. */
    public static List<String> getFavoriteApps(Context c) {
        return FavoriteSelection.decode(sp(c).getString(KEY_FAVORITE_APPS, null));
    }

    public static void setFavoriteApps(Context c, List<String> keys) {
        sp(c).edit().putString(KEY_FAVORITE_APPS, FavoriteSelection.encode(keys)).apply();
    }

    public static boolean getAutoRotate(Context c) {
        return sp(c).getBoolean(KEY_AUTO_ROTATE, true);
    }

    public static void setAutoRotate(Context c, boolean value) {
        sp(c).edit().putBoolean(KEY_AUTO_ROTATE, value).apply();
    }
}
