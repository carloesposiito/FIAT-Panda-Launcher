package it.panda.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import it.panda.launcher.apps.AppEntry;
import it.panda.launcher.apps.AppLoader;
import it.panda.launcher.obd.BluetoothPermissions;
import it.panda.launcher.nav.NavReceiver;
import it.panda.launcher.obd.ObdManager;

/**
 * Impostazioni: app della colonna, OBD, motore (per la stima dei consumi),
 * rotazione automatica e scorciatoie verso le impostazioni di Android. L'interfaccia è costruita in codice
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
        Immersive.apply(this);
        // l'elenco dei dispositivi può cambiare dopo essere passati dalle impostazioni Bluetooth
        build();
    }

    private void build() {
        content.removeAllViews();
        buildAppsSection();
        buildObdSection();
        buildNavSection();
        buildEngineSection();
        buildGaugesSection();
        buildDisplaySection();
    }

    @Override
    protected void onDestroy() {
        if (appLoader != null) {
            appLoader.shutdown();
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- colonna app

    private void buildAppsSection() {
        addTitle(R.string.pref_apps_title);
        addHint(R.string.pref_apps_hint);
        int slots = Prefs.getAppSlots(this);
        if (slots > 0) {
            addHintText(getString(R.string.pref_apps_slots, slots));
        }
        Button edit = addButton(R.string.pref_apps_edit);
        edit.setOnClickListener(v -> startActivity(new Intent(this, AppPickerActivity.class)));
    }

    // ---------------------------------------------------------------- adattatore OBD

    @SuppressLint("MissingPermission")
    private void buildObdSection() {
        addTitle(R.string.pref_obd_title);

        boolean enabled = Prefs.getObdEnabled(this);
        Switch use = new Switch(this);
        use.setText(R.string.pref_obd_enable);
        use.setTextColor(getColor(R.color.text));
        use.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        use.setChecked(enabled);
        use.setPadding(0, dp(8), 0, dp(8));
        use.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setObdEnabled(this, checked);
            build();
        });
        content.addView(use);
        if (!enabled) {
            addHint(R.string.pref_obd_disabled_hint);
            return;
        }

        // stato del collegamento (protocollo usato o ultimo errore)
        String state = obdStateText(ObdManager.lastState());
        String detail = ObdManager.lastDetail();
        TextView status = addHintText(getString(R.string.pref_obd_status, detail != null && !detail.isEmpty() ? state + " (" + detail + ")" : state));
        status.setTextColor(getColor(ObdManager.lastState() == ObdManager.State.CONNECTED ? R.color.ok
                : ObdManager.lastState() == ObdManager.State.ERROR ? R.color.crit : R.color.muted));

        addSubtitle(R.string.pref_obd_adapter);
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

    private String obdStateText(ObdManager.State s) {
        switch (s) {
            case CONNECTED:
                return getString(R.string.obd_connected_long);
            case CONNECTING:
                return getString(R.string.obd_connecting);
            case ERROR:
                return getString(R.string.obd_error);
            default:
                return getString(R.string.obd_not_configured);
        }
    }

    // ---------------------------------------------------------------- navigazione (goESP)

    private void buildNavSection() {
        addTitle(R.string.pref_nav_title);
        boolean enabled = Prefs.getNavEnabled(this);
        Switch use = new Switch(this);
        use.setText(R.string.pref_nav_enable);
        use.setTextColor(getColor(R.color.text));
        use.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        use.setChecked(enabled);
        use.setPadding(0, dp(8), 0, dp(8));
        use.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setNavEnabled(this, checked);
            if (checked && !BluetoothPermissions.canAdvertise(this)) {
                requestPermissions(BluetoothPermissions.runtimePermissions(), REQUEST_BT);
            }
            build();
        });
        content.addView(use);
        addHint(R.string.pref_nav_hint);
        if (enabled) {
            NavReceiver.State s = NavReceiver.get(this).state();
            String text;
            int color = R.color.muted;
            switch (s) {
                case CONNECTED:
                    text = getString(R.string.pref_nav_state_connected, String.valueOf(NavReceiver.get(this).deviceName()));
                    color = R.color.ok;
                    break;
                case WAITING:
                    text = getString(R.string.pref_nav_state_waiting);
                    break;
                case STARTING:
                    text = getString(R.string.pref_nav_state_starting);
                    break;
                case UNSUPPORTED:
                    text = getString(R.string.pref_nav_state_unsupported);
                    color = R.color.crit;
                    break;
                case BT_OFF:
                    text = getString(R.string.pref_nav_state_bt_off);
                    color = R.color.crit;
                    break;
                case NO_BLUETOOTH:
                    text = getString(R.string.pref_nav_state_no_bt);
                    color = R.color.crit;
                    break;
                case NO_PERMISSION:
                    text = getString(R.string.pref_nav_state_permission);
                    color = R.color.crit;
                    break;
                case ERROR:
                    text = getString(R.string.pref_nav_state_error);
                    color = R.color.crit;
                    break;
                default:
                    text = getString(R.string.pref_nav_state_off);
                    break;
            }
            TextView status = addHintText(getString(R.string.pref_nav_status, text));
            status.setTextColor(getColor(color));
            if (s == NavReceiver.State.NO_PERMISSION) {
                Button ask = addButton(R.string.pref_grant_permission);
                ask.setOnClickListener(v -> requestPermissions(BluetoothPermissions.runtimePermissions(), REQUEST_BT));
            }
        }
        addHint(R.string.pref_nav_phone_settings);
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

    // ---------------------------------------------------------------- strumenti

    private void buildGaugesSection() {
        addTitle(R.string.pref_gauges_title);
        addHint(R.string.pref_gauges_hint);
        addStepper(R.string.pref_max_speed, Prefs.getMaxSpeed(this), 10, 60, 400, " km/h", v -> Prefs.setMaxSpeed(this, v));
        addStepper(R.string.pref_shift_rpm, Prefs.getShiftRpm(this), 100, 2000, 12000, " giri/min", v -> Prefs.setShiftRpm(this, v));
        addStepper(R.string.pref_red_rpm, Prefs.getRedRpm(this), 100, 2000, 12000, " giri/min", v -> Prefs.setRedRpm(this, v));
    }

    /** Riga con etichetta e tasti − / + (senza tastiera a schermo, che sulle autoradio spesso manca). */
    private void addStepper(int label, int value, int step, int min, int max, String unit,
                            java.util.function.IntConsumer save) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button minus = new Button(this);
        minus.setText("\u2212");
        TextView shown = new TextView(this);
        shown.setTextColor(getColor(R.color.accent));
        shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        shown.setGravity(android.view.Gravity.CENTER);
        Button plus = new Button(this);
        plus.setText("+");
        int[] current = {value};
        Runnable refresh = () -> shown.setText(current[0] + unit);
        refresh.run();
        minus.setOnClickListener(v -> {
            current[0] = Math.max(min, current[0] - step);
            save.accept(current[0]);
            refresh.run();
        });
        plus.setOnClickListener(v -> {
            current[0] = Math.min(max, current[0] + step);
            save.accept(current[0]);
            refresh.run();
        });
        row.addView(minus);
        row.addView(shown, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(plus);
        content.addView(row);
    }

    // ---------------------------------------------------------------- schermata

    private void buildDisplaySection() {
        addTitle(R.string.pref_display_title);
        addSwitch(R.string.pref_car_data, Prefs.getCarData(this), checked -> Prefs.setCarData(this, checked));
        addHint(R.string.pref_car_data_hint);

        addSwitch(R.string.pref_shift_lights, Prefs.getShiftLights(this), checked -> Prefs.setShiftLights(this, checked));
        addHint(R.string.pref_shift_lights_hint);

        addSwitch(R.string.pref_borders, Prefs.getDebugBorders(this), checked -> Prefs.setDebugBorders(this, checked));
        addHint(R.string.pref_borders_hint);

        addSwitch(R.string.pref_demo, Prefs.getDemo(this), checked -> Prefs.setDemo(this, checked));
        addHint(R.string.pref_demo_hint);

        Button home = addButton(R.string.pref_choose_home);
        home.setOnClickListener(v -> openSettings(Settings.ACTION_HOME_SETTINGS));
        Button system = addButton(R.string.pref_android_settings);
        system.setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
    }

    private void addSwitch(int label, boolean checked, java.util.function.Consumer<Boolean> save) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(getColor(R.color.text));
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setChecked(checked);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener((b, on) -> save.accept(on));
        content.addView(sw);
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

    private void addSubtitle(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(0, dp(12), 0, dp(4));
        content.addView(t);
    }

    private void addHint(int text) {
        addHintText(getString(text));
    }

    private TextView addHintText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, 0, 0, dp(8));
        content.addView(t);
        return t;
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Immersive.apply(this); // dopo dialoghi e tendine le barre vanno nascoste di nuovo
        }
    }
}
