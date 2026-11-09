package it.panda.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
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
import it.panda.launcher.obd.FuelType;
import it.panda.launcher.obd.ObdManager;

/**
 * Impostazioni: app della colonna, OBD, motore (per la stima dei consumi),
 * rotazione automatica e scorciatoie verso le impostazioni di Android. L'interfaccia è costruita in codice
 * perché è solo un elenco di opzioni.
 */
public class SettingsActivity extends Activity {

    private static final int REQUEST_BT = 1;
    private static final int REQUEST_STORAGE = 2;
    private static final String PICTURES = "Pictures\\";

    private LinearLayout content;
    /** Modulo "Nuova auto" aperto, oppure null. */
    private CarDraft draft;
    /** Auto di cui si voleva importare le immagini quando è stato chiesto il permesso. */
    private CarProfile pendingImport;
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
        if (draft != null) {
            buildCarForm();
            return;
        }
        buildAppsSection();
        buildDataSection();
        buildRadioSection();
        buildCarSection();
        buildDevSection();
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
        Button edit = addButton(R.string.pref_apps_edit);
        edit.setOnClickListener(v -> startActivity(new Intent(this, AppPickerActivity.class)));
    }

    // ---------------------------------------------------------------- adattatore OBD

    private void buildDataSection() {
        addTitle(R.string.pref_data_title);

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
        if (enabled) {
            addStepper(R.string.pref_obd_delay, Prefs.getObdDelaySeconds(this), 5, 0, 120, " s", v -> Prefs.setObdDelaySeconds(this, v));
            buildObdAdapter();
        }
    }

    /** Stato del collegamento e scelta dell'adattatore ELM327. */
    @SuppressLint("MissingPermission")
    private void buildObdAdapter() {
        // stato del collegamento (protocollo usato o ultimo errore)
        String state = obdStateText(ObdManager.lastState());
        String detail = ObdManager.lastDetail();
        TextView status = addHintText(getString(R.string.pref_obd_status, detail != null && !detail.isEmpty() ? state + " (" + detail + ")" : state));
        status.setTextColor(getColor(ObdManager.lastState() == ObdManager.State.CONNECTED ? R.color.ok
                : ObdManager.lastState() == ObdManager.State.ERROR ? R.color.crit : R.color.muted));

        addSubtitle(R.string.pref_obd_adapter);
        addHintText(getString(R.string.pref_obd_for_car, CarStore.active(this).displayName()));

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

    // ---------------------------------------------------------------- radio

    private void buildRadioSection() {
        addTitle(R.string.pref_radio_title);
        buildRadioAccess();
    }

    /** Per la radio serve l'accesso alle notifiche, che si concede dalle impostazioni di sistema. */
    private void buildRadioAccess() {
        boolean granted = it.panda.launcher.radio.RadioWatcher.hasAccess(this);
        TextView status = addHintText(getString(granted ? R.string.pref_radio_access_on : R.string.pref_radio_access_off));
        status.setTextColor(getColor(granted ? R.color.ok : R.color.muted));
        buildRadioApp();
        if (!granted) {
            Button grant = addButton(R.string.pref_radio_grant);
            grant.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (android.content.ActivityNotFoundException e) {
                    android.widget.Toast.makeText(this, R.string.pref_radio_unavailable, android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /** App che si apre toccando la radio nella Home: si sceglie qui perché le autoradio cinesi hanno pacchetti diversi. */
    private void buildRadioApp() {
        android.content.ComponentName chosen = Prefs.getRadioApp(this);
        String name = getString(R.string.pref_radio_app_none);
        if (chosen != null) {
            try {
                name = String.valueOf(getPackageManager().getActivityInfo(chosen, 0).loadLabel(getPackageManager()));
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                name = chosen.getPackageName(); // disinstallata: si mostra il pacchetto
            }
        }
        addHintText(getString(R.string.pref_radio_app, name));
        Button pick = addButton(R.string.pref_radio_app_choose);
        pick.setOnClickListener(v -> {
            if (appLoader == null) {
                appLoader = new AppLoader(this);
            }
            appLoader.load(apps -> {
                String[] labels = new String[apps.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = apps.get(i).label + "  ·  " + apps.get(i).component.getPackageName();
                }
                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_radio_app_choose)
                        .setItems(labels, (d, which) -> {
                            Prefs.setRadioApp(this, apps.get(which).component);
                            build();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        });
    }

    // ---------------------------------------------------------------- motore

    private void buildCarSection() {
        addTitle(R.string.pref_car_title);
        CarProfile active = CarStore.active(this);
        RadioGroup cars = new RadioGroup(this);
        for (CarProfile car : CarStore.list(this)) {
            RadioButton rb = radio(car.displayName());
            cars.addView(rb);
            rb.setChecked(car.id.equals(active.id));
            rb.setOnClickListener(v -> {
                CarStore.setActive(this, car.id);
                build();
            });
        }
        content.addView(cars);
        Button add = addButton(R.string.pref_car_new);
        add.setOnClickListener(v -> {
            draft = new CarDraft();
            build();
        });
        if (!active.isBuiltIn()) {
            Button delete = addButton(R.string.pref_car_delete);
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.pref_car_delete_confirm, active.displayName()))
                    .setPositiveButton(R.string.reset, (d, w) -> {
                        CarStore.delete(this, active.id);
                        build();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show());
        }
        addHintText(getString(R.string.pref_car_active_hint, active.displayName()));
        buildCarImages(active);

        addSubtitle(R.string.pref_engine_title);
        addStepper(R.string.pref_displacement, Prefs.getDisplacementCc(this), 50, 500, 6000, " cm³", v -> Prefs.setDisplacementCc(this, v));

        addSubtitle(R.string.pref_gauges_title);
        addStepper(R.string.pref_max_speed, Prefs.getMaxSpeed(this), 10, 60, 400, " km/h", v -> Prefs.setMaxSpeed(this, v));
        addStepper(R.string.pref_shift_rpm, Prefs.getShiftRpm(this), 100, 2000, 12000, " giri/min", v -> Prefs.setShiftRpm(this, v));
        addStepper(R.string.pref_min_rpm, Prefs.getMinRpm(this), 100, 500, 4000, " giri/min", v -> Prefs.setMinRpm(this, v));
        addStepper(R.string.pref_red_rpm, Prefs.getRedRpm(this), 100, 2000, 12000, " giri/min", v -> Prefs.setRedRpm(this, v));

        addSubtitle(R.string.pref_fuel_title);
        addFuelTypes(Prefs.getFuelType(this), t -> {
            Prefs.setFuelType(this, t);
            build(); // il prezzo di partenza e il suggerimento cambiano col carburante
        });
        if (!Prefs.getFuelType(this).estimatesFromAir()) {
            addHint(R.string.pref_fuel_diesel_hint);
        }
        addSwitch(R.string.pref_calib, Prefs.getFuelCalibration(this), checked -> Prefs.setFuelCalibration(this, checked));
        addStepper(R.string.pref_tank, Prefs.getTankLiters(this), 1, 20, 100, " l", v -> Prefs.setTankLiters(this, v));
        addSwitch(R.string.pref_detailed, Prefs.getDetailedConsumption(this), checked -> Prefs.setDetailedConsumption(this, checked));
        addStepper(R.string.pref_fuel_price, Prefs.getFuelPriceCents(this), 1, 100, 400,
                v -> String.format(java.util.Locale.ITALIAN, "%.2f €/l", v / 100.0), v -> Prefs.setFuelPriceCents(this, v));
        TextView factor = addHintText(calibrationText());
        Button reset = addButton(R.string.pref_calib_reset);
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setMessage(R.string.pref_calib_reset_confirm)
                .setPositiveButton(R.string.reset, (d, w) -> {
                    Prefs.resetCalibration(this);
                    factor.setText(calibrationText());
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ---------------------------------------------------------------- immagini dell'auto

    private void buildCarImages(CarProfile car) {
        addSubtitle(R.string.pref_car_images);
        for (CarImages.Kind kind : CarImages.Kind.values()) {
            boolean custom = CarImages.has(this, car.id, kind);
            addHintText(getString(R.string.pref_car_image_status, kind.importName,
                    getString(custom ? R.string.pref_car_image_custom : R.string.pref_car_image_default)));
        }
        addHintText(imagesHelp(car.folderName()));
        Button import_ = addButton(R.string.pref_car_images_import);
        import_.setOnClickListener(v -> importImages(car));
    }

    /** Dove mettere i file e di che misura, con l'indicazione di dove compaiono. */
    private String imagesHelp(String folder) {
        CarImages.Kind top = CarImages.Kind.TOP;
        CarImages.Kind side = CarImages.Kind.SIDE;
        return getString(R.string.car_images_help, PICTURES + (folder.isEmpty() ? "MARCA_MODELLO" : folder),
                top.recommendedWidth, top.recommendedHeight, side.recommendedWidth, side.recommendedHeight);
    }

    private String storagePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /** Copia le immagini da Pictures\MARCA_MODELLO\ (memoria interna o chiavetta USB). */
    private void importImages(CarProfile car) {
        if (checkSelfPermission(storagePermission()) != PackageManager.PERMISSION_GRANTED) {
            pendingImport = car;
            requestPermissions(new String[]{storagePermission()}, REQUEST_STORAGE);
            return;
        }
        Toast.makeText(this, R.string.car_import_running, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            CarImages.Result r = CarImages.importFor(this, car);
            runOnUiThread(() -> {
                Toast.makeText(this, importMessage(car, r), Toast.LENGTH_LONG).show();
                build();
            });
        }, "car-images").start();
    }

    private String importMessage(CarProfile car, CarImages.Result r) {
        if (!r.folderFound) {
            return getString(R.string.car_import_no_folder, PICTURES + car.folderName());
        }
        StringBuilder b = new StringBuilder();
        if (!r.imported.isEmpty()) {
            b.append(getString(R.string.car_import_done, names(r.imported)));
        }
        if (!r.missing.isEmpty()) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(getString(R.string.car_import_missing, names(r.missing)));
        }
        return b.toString();
    }

    private static String names(List<CarImages.Kind> kinds) {
        StringBuilder b = new StringBuilder();
        for (CarImages.Kind k : kinds) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(k.importName);
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- nuova auto

    /** Valori inseriti nel modulo "Nuova auto", prima di crearla. */
    private static final class CarDraft {
        String brand = "";
        String model = "";
        final CarStore.Settings settings = new CarStore.Settings();
    }

    private void buildCarForm() {
        addTitle(R.string.pref_car_new);
        TextView[] help = new TextView[1];
        addTextField(R.string.car_brand, draft.brand, v -> {
            draft.brand = v;
            help[0].setText(imagesHelp(CarProfile.folderName(draft.brand, draft.model)));
        });
        addTextField(R.string.car_model, draft.model, v -> {
            draft.model = v;
            help[0].setText(imagesHelp(CarProfile.folderName(draft.brand, draft.model)));
        });
        CarStore.Settings s = draft.settings;
        addSubtitle(R.string.pref_fuel_type);
        addFuelTypes(s.fuelType, t -> s.fuelType = t);
        addStepper(R.string.pref_displacement, s.displacementCc, 50, 500, 6000, " cm³", v -> s.displacementCc = v);
        addStepper(R.string.pref_tank, s.tankLiters, 1, 10, 150, " l", v -> s.tankLiters = v);
        addStepper(R.string.pref_max_speed, s.maxSpeed, 10, 60, 400, " km/h", v -> s.maxSpeed = v);
        addStepper(R.string.pref_min_rpm, s.minRpm, 100, 500, 4000, " giri/min", v -> s.minRpm = v);
        addStepper(R.string.pref_shift_rpm, s.shiftRpm, 100, 2000, 12000, " giri/min", v -> s.shiftRpm = v);
        addStepper(R.string.pref_red_rpm, s.redRpm, 100, 2000, 12000, " giri/min", v -> s.redRpm = v);
        help[0] = addHintText(imagesHelp(CarProfile.folderName(draft.brand, draft.model)));

        Button create = addButton(R.string.car_create);
        create.setOnClickListener(v -> {
            if (draft.brand.trim().isEmpty() || draft.model.trim().isEmpty()) {
                Toast.makeText(this, R.string.car_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            CarStore.create(this, draft.brand, draft.model, draft.settings);
            draft = null;
            build();
        });
        Button cancel = addButton(R.string.cancel);
        cancel.setOnClickListener(v -> {
            draft = null;
            build();
        });
    }

    /** Scelta del carburante: benzina, GPL o gasolio. */
    private void addFuelTypes(FuelType current, java.util.function.Consumer<FuelType> onPick) {
        RadioGroup group = new RadioGroup(this);
        for (FuelType type : FuelType.values()) {
            RadioButton rb = radio(getString(fuelLabel(type)));
            group.addView(rb);
            rb.setChecked(type == current);
            rb.setOnClickListener(v -> onPick.accept(type));
        }
        content.addView(group);
    }

    private static int fuelLabel(FuelType type) {
        switch (type) {
            case LPG:
                return R.string.fuel_lpg;
            case DIESEL:
                return R.string.fuel_diesel;
            default:
                return R.string.fuel_petrol;
        }
    }

    private void addTextField(int label, String value, java.util.function.Consumer<String> onChange) {
        addSubtitle(label);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        field.setTextColor(getColor(R.color.text));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onChange.accept(s.toString());
            }
        });
        content.addView(field);
    }

    @Override
    public void onBackPressed() {
        if (draft != null) {
            draft = null; // il modulo si chiude senza creare nulla
            build();
        } else {
            super.onBackPressed();
        }
    }

    private String calibrationText() {
        double f = it.panda.launcher.obd.FuelCalibrator.factorOf(Prefs.getCalibrationState(this));
        return getString(R.string.pref_calib_factor, f);
    }

    /** Riga con etichetta e tasti − / + (senza tastiera a schermo, che sulle autoradio spesso manca). */
    private void addStepper(int label, int value, int step, int min, int max, String unit,
                            java.util.function.IntConsumer save) {
        addStepper(label, value, step, min, max, v -> v + unit, save);
    }

    /** Come sopra, ma il valore mostrato lo scrive {@code format} (es. 1,85 €/l). */
    private void addStepper(int label, int value, int step, int min, int max,
                            java.util.function.IntFunction<String> format, java.util.function.IntConsumer save) {
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
        Runnable refresh = () -> shown.setText(format.apply(current[0]));
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

    // ---------------------------------------------------------------- sviluppo

    private void buildDevSection() {
        addTitle(R.string.pref_dev_title);
        addSwitch(R.string.pref_demo, Prefs.getDemo(this), checked -> Prefs.setDemo(this, checked));
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
        if (requestCode == REQUEST_STORAGE) {
            CarProfile car = pendingImport;
            pendingImport = null;
            if (car != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                importImages(car);
            } else {
                Toast.makeText(this, R.string.car_import_denied, Toast.LENGTH_LONG).show();
            }
            return;
        }
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
