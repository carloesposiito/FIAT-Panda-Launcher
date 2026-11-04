package it.panda.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import it.panda.launcher.apps.AppEntry;
import it.panda.launcher.apps.AppLoader;
import it.panda.launcher.apps.FavoriteSelection;
import it.panda.launcher.obd.BluetoothPermissions;

/**
 * Impostazioni: adattatore OBD, motore (per la stima dei consumi), rotazione automatica
 * e scorciatoie verso le impostazioni di Android. L'interfaccia è costruita in codice
 * perché è solo un elenco di opzioni.
 */
public class SettingsActivity extends Activity {

    private static final int REQUEST_BT = 1;

    private LinearLayout content;
    private AppLoader appLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        content.setPadding(pad, dp(12), pad, pad);
        scroll.addView(content);
        setContentView(scroll);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // l'elenco dei dispositivi può cambiare dopo essere passati dalle impostazioni Bluetooth
        build();
    }

    private void build() {
        content.removeAllViews();
        buildAppsSection();
        buildObdSection();
        buildEngineSection();
        buildDisplaySection();
    }

    // ---------------------------------------------------------------- app a sinistra

    private void buildAppsSection() {
        addTitle(R.string.pref_apps_title);
        addHint(R.string.pref_apps_hint);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);

        if (appLoader == null) {
            appLoader = new AppLoader(this);
        }
        appLoader.load(apps -> {
            if (box.getParent() == null) {
                return; // la schermata è stata ricostruita nel frattempo
            }
            List<String> allKeys = new ArrayList<>();
            for (AppEntry app : apps) {
                allKeys.add(app.key());
            }
            List<String> saved = Prefs.getFavoriteApps(this);
            for (AppEntry app : apps) {
                CheckBox cb = new CheckBox(this);
                cb.setText(app.label);
                cb.setTextColor(getColor(R.color.text));
                cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                cb.setPadding(dp(8), dp(10), 0, dp(10));
                cb.setCompoundDrawablesRelativeWithIntrinsicBounds(app.icon, null, null, null);
                cb.setCompoundDrawablePadding(dp(12));
                cb.setChecked(saved == null || saved.contains(app.key()));
                cb.setOnCheckedChangeListener((b, checked) -> Prefs.setFavoriteApps(this,
                        FavoriteSelection.toggle(Prefs.getFavoriteApps(this), allKeys, app.key(), checked)));
                box.addView(cb);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appLoader != null) {
            appLoader.shutdown();
        }
    }

    // ---------------------------------------------------------------- adattatore OBD

    @SuppressLint("MissingPermission")
    private void buildObdSection() {
        addTitle(R.string.pref_obd_title);
        addHint(R.string.pref_obd_hint);

        if (!BluetoothPermissions.canConnect(this)) {
            addHint(R.string.pref_need_permission);
            Button ask = addButton(R.string.pref_grant_permission);
            ask.setOnClickListener(v -> requestPermissions(BluetoothPermissions.runtimePermissions(), REQUEST_BT));
            return;
        }

        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        List<BluetoothDevice> devices = new ArrayList<>();
        if (adapter != null) {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                devices.addAll(bonded);
            }
        }

        RadioGroup group = new RadioGroup(this);
        String current = Prefs.getObdAddress(this);

        RadioButton none = radio(getString(R.string.pref_obd_none));
        group.addView(none);
        none.setChecked(current == null);
        none.setOnClickListener(v -> Prefs.setObdDevice(this, null, null));

        for (BluetoothDevice d : devices) {
            String name = d.getName() != null ? d.getName() : d.getAddress();
            RadioButton rb = radio(name + "  ·  " + d.getAddress());
            group.addView(rb);
            rb.setChecked(d.getAddress().equals(current));
            rb.setOnClickListener(v -> Prefs.setObdDevice(this, d.getAddress(), name));
        }
        content.addView(group);
        if (devices.isEmpty()) {
            addHint(R.string.pref_obd_no_devices);
        }

        Button bt = addButton(R.string.pref_bt_settings);
        bt.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    }

    // ---------------------------------------------------------------- motore

    private void buildEngineSection() {
        addTitle(R.string.pref_engine_title);
        addHint(R.string.pref_engine_hint);
        RadioGroup group = new RadioGroup(this);
        int current = Prefs.getDisplacementCc(this);
        int[] cc = {Prefs.CC_11, Prefs.CC_12, Prefs.CC_14};
        int[] labels = {R.string.pref_engine_11, R.string.pref_engine_12, R.string.pref_engine_14};
        for (int i = 0; i < cc.length; i++) {
            final int value = cc[i];
            RadioButton rb = radio(getString(labels[i]));
            group.addView(rb);
            rb.setChecked(value == current);
            rb.setOnClickListener(v -> Prefs.setDisplacementCc(this, value));
        }
        content.addView(group);
    }

    // ---------------------------------------------------------------- schermata

    private void buildDisplaySection() {
        addTitle(R.string.pref_display_title);
        Button home = addButton(R.string.pref_choose_home);
        home.setOnClickListener(v -> openSettings(Settings.ACTION_HOME_SETTINGS));
        Button system = addButton(R.string.pref_android_settings);
        system.setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (RuntimeException e) {
            // alcune autoradio non hanno la pagina "app predefinite": apri le impostazioni generali
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        build();
    }

    // ---------------------------------------------------------------- piccoli aiuti per l'interfaccia

    private void addTitle(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.accent));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(0.1f);
        t.setAllCaps(true);
        t.setPadding(0, dp(24), 0, dp(6));
        content.addView(t);
    }

    private void addHint(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, 0, 0, dp(8));
        content.addView(t);
    }

    private Button addButton(int text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        content.addView(b);
        return b;
    }

    private RadioButton radio(String text) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(text);
        rb.setTextColor(getColor(R.color.text));
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rb.setPadding(dp(8), dp(10), 0, dp(10));
        return rb;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
