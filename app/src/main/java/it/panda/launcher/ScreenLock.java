package it.panda.launcher;

import android.app.Activity;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Blocco dello schermo (tasto in fondo alla colonna app).
 * Android lo permette solo alle app "amministratore del dispositivo" con il permesso "blocca schermo":
 * al primo uso si chiede l'autorizzazione, poi {@link DevicePolicyManager#lockNow()} spegne subito lo schermo.
 * Per disinstallare il launcher bisogna prima togliere l'autorizzazione (Impostazioni → Schermata).
 */
public final class ScreenLock {

    /** Riceve gli avvisi di Android sull'autorizzazione; non deve fare altro. */
    public static class AdminReceiver extends DeviceAdminReceiver {
    }

    public static final int REQUEST_ADMIN = 7;

    private ScreenLock() {
    }

    private static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    private static DevicePolicyManager dpm(Context c) {
        return (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isAllowed(Context c) {
        DevicePolicyManager d = dpm(c);
        return d != null && d.isAdminActive(admin(c));
    }

    /** Blocca lo schermo; se manca l'autorizzazione la chiede (il risultato arriva in onActivityResult). */
    public static void lockOrAsk(Activity activity) {
        if (isAllowed(activity)) {
            lockNow(activity);
            return;
        }
        Intent ask = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(activity))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.lock_admin_explanation));
        try {
            activity.startActivityForResult(ask, REQUEST_ADMIN);
        } catch (RuntimeException e) {
            Toast.makeText(activity, R.string.lock_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    public static void lockNow(Context c) {
        try {
            dpm(c).lockNow();
        } catch (SecurityException e) {
            Toast.makeText(c, R.string.lock_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    /** Toglie l'autorizzazione (serve per poter disinstallare il launcher). */
    public static void revoke(Context c) {
        DevicePolicyManager d = dpm(c);
        if (d != null && d.isAdminActive(admin(c))) {
            d.removeActiveAdmin(admin(c));
        }
    }
}
