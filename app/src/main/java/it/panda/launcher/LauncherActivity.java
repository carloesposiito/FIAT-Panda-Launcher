package it.panda.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import it.panda.launcher.apps.AppEntry;
import it.panda.launcher.apps.AppGridAdapter;
import it.panda.launcher.apps.AppLoader;
import it.panda.launcher.apps.FavoriteSelection;
import it.panda.launcher.car.CarRenderer;
import it.panda.launcher.media.MediaMonitor;
import it.panda.launcher.obd.BluetoothPermissions;
import it.panda.launcher.obd.Dtc;
import it.panda.launcher.obd.ObdManager;
import it.panda.launcher.obd.ObdSnapshot;
import it.panda.launcher.ui.FuelChartView;
import it.panda.launcher.ui.GearPatternView;
import it.panda.launcher.ui.SegmentBarView;
import it.panda.launcher.ui.SparklineView;
import it.panda.launcher.ui.StudioBackdropView;

/** Schermata Home: app a sinistra, media e auto 3D al centro, dati dell'auto a destra. */
public class LauncherActivity extends Activity
        implements CarRenderer.Callback, ObdManager.Listener, MediaMonitor.Listener {

    private static final int REQUEST_BT = 1;
    private static final long SPARK_INTERVAL_MS = 500;
    private static final long FUEL_INTERVAL_MS = 15_000;
    private static final float RPM_FULL_SCALE = 6000f;

    private CarRenderer car;
    private StudioBackdropView backdrop;
    private ImageButton rotateButton;

    private AppLoader appLoader;
    private final AppGridAdapter appAdapter = new AppGridAdapter();

    private AudioManager audio;
    private TextView volumeValue;
    private TextView dateView;
    private TextView mediaTitle, mediaSub;
    private MediaMonitor media;
    private boolean mediaNoAccess;

    private ObdManager obd;
    private String obdAddressAtPause;
    private boolean pausedOnce;
    private ObdManager.State obdState = ObdManager.State.NOT_CONFIGURED;
    private String obdDetail;
    private List<String> dtcs;

    private TextView speedValue, rpmValue, gearSub;
    private GearPatternView gearView;
    private SegmentBarView rpmBar;
    private SparklineView rpmSpark;
    private long lastSparkMs;
    private TextView batteryValue, coolantValue, loadValue, intakeValue, ambientValue;
    private ImageView batteryIcon, coolantIcon, loadIcon, intakeIcon;
    private View dataDot;
    private TextView consumptionValue, consumptionAvg;
    private FuelChartView fuelChart;
    private long lastFuelMs;

    private ImageButton warnButton;
    private TextView warnBadge;
    private LinearLayout diagPanel, diagList;
    private TextView diagEmptyTitle, diagEmptySub;
    private Button diagClear;
    private boolean diagOpen;

    /** Aggiorna la griglia quando si installa o si disinstalla un'app. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    /** Aggiorna data (e volume) quando cambia il minuto o il giorno. */
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDate();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        setupApps();
        setupCar();
        setupMedia();
        setupData();
        setupWarnings();

        findViewById(R.id.settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.screen_off_button).setOnClickListener(
                v -> startActivity(new Intent(this, BlankActivity.class)));

        obd = new ObdManager(this);
        obd.setListener(this);
        if (!BluetoothPermissions.canConnect(this)) {
            requestPermissions(BluetoothPermissions.runtimePermissions(), REQUEST_BT);
        }
        obd.start();
        showNoData();
        refreshWarnings();
    }

    // ---------------------------------------------------------------- app

    private void setupApps() {
        GridView grid = findViewById(R.id.app_grid);
        grid.setAdapter(appAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> openApp(appAdapter.getItem(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            // pressione lunga: informazioni sull'app (disinstalla, permessi...)
            AppEntry app = appAdapter.getItem(position);
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.component.getPackageName(), null));
            startActivity(i);
            return true;
        });

        appLoader = new AppLoader(this);
        loadApps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
    }

    private void loadApps() {
        appLoader.load(all -> appAdapter.setApps(
                FavoriteSelection.select(all, Prefs.getFavoriteApps(this), AppEntry::key)));
    }

    private void openApp(AppEntry app) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.app_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- auto 3D

    private void setupCar() {
        backdrop = findViewById(R.id.backdrop);
        TextureView carView = findViewById(R.id.car_view);
        car = new CarRenderer(this, carView, "models/panda.glb", this);
        car.setAutoRotate(Prefs.getAutoRotate(this));

        rotateButton = findViewById(R.id.rotate_button);
        rotateButton.setSelected(car.isAutoRotate());
        rotateButton.setOnClickListener(v -> {
            boolean on = !car.isAutoRotate();
            car.setAutoRotate(on);
            Prefs.setAutoRotate(this, on);
            rotateButton.setSelected(on);
        });
    }

    /** Se si trascina l'auto la rotazione automatica si spegne e resta spenta finché non si preme il tasto. */
    @Override
    public void onFreeRotation() {
        car.setAutoRotate(false);
        rotateButton.setSelected(false);
    }

    @Override
    public void onGroundProjected(float cx, float cy, float rx, float ry) {
        backdrop.setGround(cx, cy, rx, ry);
    }

    @Override
    public void onModelError(String message) {
        TextView error = findViewById(R.id.model_error);
        error.setText(getString(R.string.model_missing) + "\n(" + message + ")");
        error.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- media, volume, data

    private void setupMedia() {
        mediaTitle = findViewById(R.id.media_title);
        mediaSub = findViewById(R.id.media_sub);
        volumeValue = findViewById(R.id.volume_value);
        dateView = findViewById(R.id.date);
        media = new MediaMonitor(this, this);

        findViewById(R.id.media_card).setOnClickListener(v -> {
            if (mediaNoAccess) {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (RuntimeException e) {
                    Toast.makeText(this, R.string.app_open_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.volume_up).setOnClickListener(v -> changeVolume(AudioManager.ADJUST_RAISE));
        findViewById(R.id.volume_down).setOnClickListener(v -> changeVolume(AudioManager.ADJUST_LOWER));
    }

    private void changeVolume(int direction) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        updateVolume();
    }

    private void updateVolume() {
        volumeValue.setText(String.valueOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)));
    }

    @Override
    public void onMedia(String title, String subtitle) {
        mediaNoAccess = false;
        mediaTitle.setText(title != null ? title : getString(R.string.media_idle));
        mediaSub.setText(subtitle != null ? subtitle : "");
    }

    @Override
    public void onNoAccess() {
        mediaNoAccess = true;
        mediaTitle.setText(R.string.media_idle);
        mediaSub.setText(R.string.media_need_access);
    }

    private void updateDate() {
        String s = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(new Date());
        dateView.setText(s.substring(0, 1).toUpperCase(Locale.ITALIAN) + s.substring(1));
    }

    // ---------------------------------------------------------------- dati dell'auto

    private void setupData() {
        speedValue = findViewById(R.id.speed_value);
        gearView = findViewById(R.id.gear_view);
        gearSub = findViewById(R.id.gear_sub);
        rpmValue = findViewById(R.id.rpm_value);
        rpmBar = findViewById(R.id.rpm_bar);
        rpmSpark = findViewById(R.id.rpm_spark);
        batteryValue = findViewById(R.id.battery_value);
        batteryIcon = findViewById(R.id.battery_icon);
        coolantValue = findViewById(R.id.coolant_value);
        coolantIcon = findViewById(R.id.coolant_icon);
        loadValue = findViewById(R.id.load_value);
        loadIcon = findViewById(R.id.load_icon);
        intakeValue = findViewById(R.id.intake_value);
        intakeIcon = findViewById(R.id.intake_icon);
        ambientValue = findViewById(R.id.ambient_value);
        dataDot = findViewById(R.id.data_dot);
        consumptionValue = findViewById(R.id.consumption_value);
        consumptionAvg = findViewById(R.id.consumption_avg);
        fuelChart = findViewById(R.id.fuel_chart);
    }

    @Override
    public void onObdState(ObdManager.State state, String detail) {
        obdState = state;
        obdDetail = detail;
        int color;
        switch (state) {
            case CONNECTED:
                color = R.color.ok;
                break;
            case CONNECTING:
                color = R.color.accent;
                break;
            case NOT_CONFIGURED:
                color = R.color.muted;
                break;
            default:
                color = R.color.crit;
                break;
        }
        dataDot.setBackgroundTintList(ColorStateList.valueOf(getColor(color)));
        if (state != ObdManager.State.CONNECTED) {
            dtcs = null;
            showNoData();
        }
        refreshWarnings();
    }

    @Override
    public void onObdData(ObdSnapshot s) {
        speedValue.setText(s.speedKmh != null ? String.valueOf(Math.round(s.speedKmh)) : dash());
        if (s.gear == null || s.gear < 0) {
            gearView.setGear(-1);
            gearSub.setText(dash());
        } else {
            gearView.setGear(s.gear);
            gearSub.setText(s.gear == 0 ? getString(R.string.gear_neutral) : getString(R.string.gear_format, s.gear));
        }

        if (s.rpm != null) {
            long rounded = Math.round(s.rpm / 10.0) * 10;
            rpmValue.setText(String.format(Locale.ITALY, "%,d", rounded));
            rpmBar.setFraction((float) (s.rpm / RPM_FULL_SCALE));
            long now = SystemClock.uptimeMillis();
            if (now - lastSparkMs >= SPARK_INTERVAL_MS) {
                lastSparkMs = now;
                rpmSpark.add(s.rpm.floatValue());
            }
        } else {
            rpmValue.setText(dash());
            rpmBar.setFraction(0);
        }

        if (s.batteryV != null) {
            batteryValue.setText(oneDecimal(s.batteryV));
            // 11,8 V = vuota, 14,4 V = piena
            double fill = Math.max(0.05, Math.min(1.0, (s.batteryV - 11.8) / 2.6));
            batteryIcon.setImageLevel((int) Math.round(fill * 10000));
            tint(batteryIcon, s.batteryV >= 12.6 ? R.color.ok : s.batteryV >= 12.0 ? R.color.warn : R.color.crit);
        } else {
            batteryValue.setText(dash());
        }

        if (s.coolantC != null) {
            coolantValue.setText(String.valueOf(Math.round(s.coolantC)));
            tint(coolantIcon, s.coolantC < 70 ? R.color.cold : s.coolantC > 105 ? R.color.crit : R.color.ok);
        } else {
            coolantValue.setText(dash());
        }

        loadValue.setText(s.loadPct != null ? String.valueOf(Math.round(s.loadPct)) : dash());
        tint(loadIcon, R.color.accent);
        intakeValue.setText(s.intakeC != null ? String.valueOf(Math.round(s.intakeC)) : dash());
        tint(intakeIcon, R.color.cold);
        ambientValue.setText(s.ambientC != null ? String.valueOf(Math.round(s.ambientC)) : dash());

        consumptionValue.setText(s.consumptionL100 != null ? oneDecimal(s.consumptionL100) : dash());
        consumptionAvg.setText(s.averageL100 != null ? oneDecimal(s.averageL100) : dash());
        fuelChart.setAverage(s.averageL100 != null ? s.averageL100.floatValue() : Float.NaN);
        long now = SystemClock.uptimeMillis();
        if (s.consumptionL100 != null && now - lastFuelMs >= FUEL_INTERVAL_MS) {
            lastFuelMs = now;
            fuelChart.add(s.consumptionL100.floatValue());
        }

        dtcs = s.dtcs;
        refreshWarnings();
    }

    /** Senza collegamento tutti i campi restano visibili, con i trattini al posto dei valori. */
    private void showNoData() {
        String dash = dash();
        speedValue.setText(dash);
        rpmValue.setText(dash);
        rpmBar.setFraction(0);
        gearView.setGear(-1);
        gearSub.setText(dash);
        batteryValue.setText(dash);
        batteryIcon.setImageLevel(0);
        coolantValue.setText(dash);
        loadValue.setText(dash);
        intakeValue.setText(dash);
        ambientValue.setText(dash);
        consumptionValue.setText(dash);
        consumptionAvg.setText(dash);
        fuelChart.setAverage(Float.NaN);
        tint(batteryIcon, R.color.line);
        tint(coolantIcon, R.color.line);
        tint(loadIcon, R.color.line);
        tint(intakeIcon, R.color.line);
    }

    private void tint(ImageView icon, int colorRes) {
        icon.setImageTintList(ColorStateList.valueOf(getColor(colorRes)));
    }

    private String dash() {
        return getString(R.string.no_value);
    }

    private static String oneDecimal(double v) {
        return String.format(Locale.ITALY, "%.1f", v);
    }

    // ---------------------------------------------------------------- avvisi ed errori

    private void setupWarnings() {
        warnButton = findViewById(R.id.warn_button);
        warnBadge = findViewById(R.id.warn_badge);
        diagPanel = findViewById(R.id.diag_panel);
        diagList = findViewById(R.id.diag_list);
        diagEmptyTitle = findViewById(R.id.diag_empty_title);
        diagEmptySub = findViewById(R.id.diag_empty_sub);
        diagClear = findViewById(R.id.diag_clear);

        warnButton.setOnClickListener(v -> setDiagOpen(!diagOpen));
        findViewById(R.id.diag_close).setOnClickListener(v -> setDiagOpen(false));
        diagClear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.diag_clear_title)
                .setMessage(R.string.diag_clear_msg)
                .setNegativeButton(R.string.diag_clear_cancel, null)
                .setPositiveButton(R.string.diag_clear_confirm, (d, w) -> clearFaults())
                .show());
    }

    private void clearFaults() {
        boolean accepted = obd.requestClearFaults();
        Toast.makeText(this, accepted ? R.string.diag_clear_sent : R.string.diag_clear_refused,
                Toast.LENGTH_LONG).show();
    }

    private void setDiagOpen(boolean open) {
        if (open == diagOpen) {
            return;
        }
        diagOpen = open;
        warnButton.setSelected(open);
        float hidden = 340 * getResources().getDisplayMetrics().density;
        if (open) {
            diagPanel.setTranslationX(hidden);
            diagPanel.setVisibility(View.VISIBLE);
            diagPanel.animate().translationX(0).setDuration(220);
        } else {
            diagPanel.animate().translationX(hidden).setDuration(200).withEndAction(() -> {
                if (!diagOpen) {
                    diagPanel.setVisibility(View.GONE);
                }
            });
        }
        refreshWarnings(); // il colore dell'icona cambia sul tasto selezionato
    }

    /**
     * Triangolo: giallo o rosso con il numero se ci sono errori, grigio altrimenti.
     * Pannello: elenco degli errori, oppure il motivo per cui non ce n'è nessuno da mostrare.
     */
    private void refreshWarnings() {
        List<String> faults = obdState == ObdManager.State.CONNECTED && dtcs != null
                ? dtcs : Collections.<String>emptyList();
        boolean critical = false;
        for (String code : faults) {
            critical |= Dtc.isCritical(code);
        }
        int tintColor = faults.isEmpty() ? R.color.muted : critical ? R.color.crit : R.color.warn;
        // sul tasto selezionato (fondo ambra) l'icona è scura
        warnButton.setImageTintList(ColorStateList.valueOf(getColor(diagOpen ? R.color.bg : tintColor)));
        if (faults.isEmpty()) {
            warnBadge.setVisibility(View.GONE);
        } else {
            warnBadge.setText(String.valueOf(faults.size()));
            warnBadge.setBackgroundTintList(ColorStateList.valueOf(getColor(tintColor)));
            warnBadge.setVisibility(View.VISIBLE);
        }

        diagList.removeAllViews();
        if (faults.isEmpty()) {
            if (obdState != ObdManager.State.CONNECTED) {
                diagEmptyTitle.setText(R.string.diag_off);
                diagEmptyTitle.setTextColor(getColor(R.color.muted));
                diagEmptySub.setText(obdDetail != null ? obdDetail : "");
            } else if (dtcs == null) {
                diagEmptyTitle.setText(R.string.diag_reading);
                diagEmptyTitle.setTextColor(getColor(R.color.muted));
                diagEmptySub.setText("");
            } else {
                diagEmptyTitle.setText(R.string.diag_none);
                diagEmptyTitle.setTextColor(getColor(R.color.ok));
                diagEmptySub.setText(R.string.diag_none_sub);
            }
            diagList.addView(diagEmptyTitle);
            diagList.addView(diagEmptySub);
        } else {
            for (String code : faults) {
                diagList.addView(faultRow(code));
            }
        }
        diagClear.setVisibility(faults.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View faultRow(String code) {
        boolean critical = Dtc.isCritical(code);
        int color = getColor(critical ? R.color.crit : R.color.warn);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        int pad = Math.round(10 * d);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = Math.round(8 * d);
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_warning);
        icon.setImageTintList(ColorStateList.valueOf(color));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Math.round(32 * d);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textsLp.leftMargin = Math.round(10 * d);
        row.addView(texts, textsLp);

        TextView title = new TextView(this);
        title.setText(Dtc.describe(code));
        title.setTextColor(getColor(R.color.text));
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        texts.addView(title);

        TextView sub = new TextView(this);
        sub.setText(code + " · " + getString(critical ? R.string.diag_critical : R.string.diag_warning));
        sub.setTextColor(getColor(R.color.muted));
        sub.setTextSize(13);
        texts.addView(sub);
        return row;
    }

    // ---------------------------------------------------------------- ciclo di vita

    @Override
    protected void onResume() {
        super.onResume();
        car.resume();
        loadApps(); // la scelta delle app può essere cambiata nelle impostazioni
        obd.setVisible(true);
        updateVolume();
        updateDate();
        IntentFilter time = new IntentFilter();
        time.addAction(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        time.addAction(Intent.ACTION_DATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, time, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timeReceiver, time);
        }
        media.start(); // l'accesso alle notifiche può essere stato concesso nel frattempo
        // se nelle impostazioni è stato scelto un altro adattatore, ricollegati
        String address = Prefs.getObdAddress(this);
        if (pausedOnce && !java.util.Objects.equals(obdAddressAtPause, address)) {
            obd.restart();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        car.pause();
        obd.setVisible(false);
        media.stop();
        unregisterReceiver(timeReceiver);
        obdAddressAtPause = Prefs.getObdAddress(this);
        pausedOnce = true;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        appLoader.shutdown();
        obd.stop();
        car.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // è la schermata Home: "indietro" non deve chiuderla
        if (diagOpen) {
            setDiagOpen(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT) {
            obd.restart();
        }
    }
}
