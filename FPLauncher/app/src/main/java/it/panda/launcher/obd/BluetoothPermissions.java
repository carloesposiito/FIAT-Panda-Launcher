package it.panda.launcher.obd;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Da Android 12 per usare il Bluetooth serve il permesso BLUETOOTH_CONNECT, chiesto all'utente. */
public final class BluetoothPermissions {

    private BluetoothPermissions() {
    }

    public static boolean needsRuntimePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean canConnect(Context c) {
        if (!needsRuntimePermission()) {
            return true;
        }
        return c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public static String[] runtimePermissions() {
        return new String[]{Manifest.permission.BLUETOOTH_CONNECT};
    }
}
