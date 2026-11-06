package it.panda.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import it.panda.launcher.apps.AppEntry;
import it.panda.launcher.apps.AppLoader;
import it.panda.launcher.nav.NavData;
import it.panda.launcher.nav.NavReceiver;
import it.panda.launcher.obd.BluetoothPermissions;
import it.panda.launcher.obd.Diagnostics;
import it.panda.launcher.obd.ObdManager;
import it.panda.launcher.obd.ObdSnapshot;
import it.panda.launcher.obd.TripComputer;
import it.panda.launcher.ui.ConsumptionChartView;
import it.panda.launcher.ui.Fonts;
import it.panda.launcher.ui.GaugeView;
import it.panda.launcher.ui.GearShiftView;
import it.panda.launcher.ui.MetricCellView;

/**
 * Schermata Home: app e volume a sinistra; a destra la barra in alto (ora) e le pagine da scorrere
 * con le frecce in basso: auto (foto con i dati OBD ai lati), consumi, navigazione, avvisi.
 */
public class LauncherActivity extends Activity
        implements ObdManager.Listener, NavReceiver.Listener {

    private static final int REQUEST_BT = 1;
    /** Da questa frazione del fondo scala i giri sono "fuori giri" (6000 su 7000). */
    private static final Locale IT = Locale.ITALIAN;

    // colonna app
    private AppLoader appLoader;
    private List<AppEntry> allApps;
    private LinearLayout appList;
    private int appListHeight;

    // volume
    private AudioManager audio;

    // pagine
    private static final int PAGE_CAR = 0, PAGE_TRIP = 1, PAGE_NAV = 2, PAGE_ALERTS = 3;
    private View[] pages;
    private int page;
    private GestureDetector swipe;
    private LinearLayout pageDots;
    private boolean activityResumed;
    private boolean obdEnabled, navEnabled;
    // dati dimostrativi (impostazioni → Schermata)
    private boolean demo;
    private final Handler demoHandler = new Handler(Looper.getMainLooper());
    private long demoStartMs;

    // navigazione (goESP)
    private NavReceiver nav;
    private View navActive, navIdle;
    private ImageView navArrow;
    private TextView navDistance, navDirection, navIdleTitle, navIdleText;
    private MetricCellView navArrival, navRemaining, navTime;
    private NavReceiver.State navState = NavReceiver.State.OFF;
    private String navDevice;
    private String navArrowShown;

    // dati ai lati della foto dell'auto

    // ora
    private TextView date;

    // dati OBD
    private ObdManager obd;
    private String obdAddressAtPause;
    private boolean pausedOnce;
    private GearShiftView gearShift;
    private GaugeView speedGauge;
    private int shiftRpm = 6000, redRpm = 6500;
    private boolean shiftLightsOn = true;
    private android.widget.ImageView shiftArrow;
    private MetricCellView cellInstantCar;
    private MetricCellView cellCoolant, cellOil, cellBattery, cellFuel;

    // avvisi
    private Button alertsRead;
    private int diagReadsSeen;
    private boolean reading;
    private ObdSnapshot lastSnapshot;
    private boolean obdConnected;

    // consumi
    private View tripReset;
    private MetricCellView cellInstant, cellAvg, cellDistance, cellTime, cellAvgSpeed, cellFuelUsed;
    private ConsumptionChartView chart;
    private List<Double> lastHistory;

    /** Aggiorna la colonna quando si installa o si disinstalla un'app. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    /** Ogni minuto e al cambio di data: aggiorna giorno e data. */
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
        Fonts.applyTo(findViewById(android.R.id.content));

        setupAppColumn();
        setupVolume();
        setupClock();
        setupData();
        setupTrip();
        setupNav();
        setupPages();

        obd = new ObdManager(this);
        obd.setListener(this);
        if (!BluetoothPermissions.canAdvertise(this)) {
            requestPermissions(BluetoothPermissions.runtimePermissions(), REQUEST_BT);
        }
        obd.start();
        showTrip(obd.tripSummary());
    }

    // ================================================================ colonna app

    private void setupAppColumn() {
        appList = findViewById(R.id.app_list);
        // quante icone entrano dipende dall'altezza: si ricalcola quando cambia
        appList.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h != appListHeight) {
                appListHeight = h;
                appList.post(this::fillAppColumn);
            }
        });

        findViewById(R.id.settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));

        appLoader = new AppLoader(this);
        loadApps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        register(packageReceiver, filter);
    }

    private void loadApps() {
        appLoader.load(apps -> {
            allApps = apps;
            fillAppColumn();
        });
    }

    /** Riempie la colonna con le app scelte, fino a quante ne entrano. */
    private void fillAppColumn() {
        if (allApps == null || appListHeight <= 0) {
            return;
        }
        int cell = getResources().getDimensionPixelSize(R.dimen.app_cell);
        int icon = getResources().getDimensionPixelSize(R.dimen.app_icon);
        int slots = Math.max(1, appListHeight / cell);
        if (slots != Prefs.getAppSlots(this)) {
            Prefs.setAppSlots(this, slots);
        }

        appList.removeAllViews();
        int pad = Math.max(0, (cell - icon) / 2);
        for (AppEntry app : chosenApps(slots)) {
            ImageView v = new ImageView(this);
            v.setImageDrawable(app.icon);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setPadding(pad, pad, pad, pad);
            v.setBackgroundResource(R.drawable.bg_app_item);
            v.setContentDescription(app.label);
            v.setOnClickListener(x -> openApp(app));
            v.setOnLongClickListener(x -> {
                // pressione lunga: informazioni sull'app (disinstalla, permessi...)
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", app.component.getPackageName(), null)));
                return true;
            });
            appList.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cell));
        }
    }

    /** Le app scelte nelle impostazioni (quelle ancora installate); se mai scelte, le prime in ordine alfabetico. */
    private List<AppEntry> chosenApps(int max) {
        List<ComponentName> favorites = Prefs.getFavorites(this);
        if (favorites == null) {
            return allApps.subList(0, Math.min(max, allApps.size()));
        }
        Map<ComponentName, AppEntry> byComponent = new HashMap<>();
        for (AppEntry a : allApps) {
            byComponent.put(a.component, a);
        }
        List<AppEntry> out = new ArrayList<>();
        for (ComponentName cn : favorites) {
            AppEntry a = byComponent.get(cn);
            if (a != null && out.size() < max) {
                out.add(a);
            }
        }
        return out;
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

    // ================================================================ volume

    private void setupVolume() {
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setupRepeat(findViewById(R.id.volume_down), AudioManager.ADJUST_LOWER);
        setupRepeat(findViewById(R.id.volume_up), AudioManager.ADJUST_RAISE);
    }

    /** Tocco: un passo. Tenendo premuto: dopo 350 ms un passo ogni 110 ms, che diventa ogni 50 ms dopo 8 passi. */
    @SuppressWarnings("ClickableViewAccessibility")
    private void setupRepeat(View button, int direction) {
        int[] steps = {0};
        Runnable repeat = new Runnable() {
            @Override
            public void run() {
                changeVolume(direction);
                steps[0]++;
                button.postDelayed(this, steps[0] < 8 ? 110 : 50);
            }
        };
        button.setOnClickListener(v -> {
            // il passo è già stato fatto alla pressione (serve per l'accessibilità e le tastiere)
        });
        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    steps[0] = 0; // riparte dai passi lenti
                    changeVolume(direction);
                    v.postDelayed(repeat, 350);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    v.removeCallbacks(repeat);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void changeVolume(int direction) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
    }

    // ================================================================ pagine

    private void setupPages() {
        pages = new View[]{findViewById(R.id.page_car), findViewById(R.id.page_trip),
                findViewById(R.id.page_nav), findViewById(R.id.page_alerts)};
        pageDots = findViewById(R.id.page_dots);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < pages.length; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_page_dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(8 * density), Math.round(8 * density));
            lp.setMargins(Math.round(5 * density), 0, Math.round(5 * density), 0);
            pageDots.addView(dot, lp);
        }
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) {
                    return false;
                }
                float d = getResources().getDisplayMetrics().density;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                boolean horizontal = Math.abs(dx) > 100 * d && Math.abs(dx) > 1.5f * Math.abs(dy);
                boolean fromPages = e1.getX() > getResources().getDimension(R.dimen.app_column);
                if (horizontal && fromPages && Math.abs(vx) > 300 * d) {
                    // dita verso sinistra = pagina successiva
                    int dir = dx < 0 ? 1 : -1;
                    showPage(step(dir), dir);
                    return true;
                }
                return false;
            }
        });

        page = Math.max(0, Math.min(pages.length - 1, Prefs.getPage(this)));
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == page ? View.VISIBLE : View.INVISIBLE);
        }
        applyAvailability();
    }

    /** Pagine disponibili: tutte, tranne la navigazione se spenta. */
    private boolean isAvailable(int p) {
        return p != PAGE_NAV || navEnabled;
    }

    /** Pagina disponibile successiva (dir = 1) o precedente (dir = -1), girando in tondo. */
    private int step(int dir) {
        int p = page;
        for (int i = 0; i < pages.length; i++) {
            p = (p + dir + pages.length) % pages.length;
            if (isAvailable(p)) {
                return p;
            }
        }
        return page;
    }

    /**
     * Applica le impostazioni: pagine disponibili, caselle dati (con l'OBD spento mostrano il trattino),
     * e se la pagina mostrata non è più disponibile si torna all'auto (la pagina salvata resta,
     * così riattivando la funzione si torna lì).
     */
    private void applyAvailability() {
        obdEnabled = Prefs.getObdEnabled(this);
        navEnabled = Prefs.getNavEnabled(this);
        int available = 0;
        for (int i = 0; i < pages.length; i++) {
            boolean on = isAvailable(i);
            pageDots.getChildAt(i).setVisibility(on ? View.VISIBLE : View.GONE);
            available += on ? 1 : 0;
        }
        pageDots.setVisibility(available > 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.car_data_row).setVisibility(Prefs.getCarData(this) ? View.VISIBLE : View.GONE); // con i separatori
        if (!obdEnabled && !demo) {
            showNoLiveData();
        }

        int saved = Math.max(0, Math.min(pages.length - 1, Prefs.getPage(this)));
        int wanted = isAvailable(saved) ? saved : PAGE_CAR;
        if (wanted != page) {
            for (int i = 0; i < pages.length; i++) {
                pages[i].setVisibility(i == wanted ? View.VISIBLE : View.INVISIBLE);
            }
            page = wanted;
        }
        updatePageDots();

        // ricevitore della navigazione acceso/spento come da impostazioni
        if (navEnabled) {
            nav.start();
        } else {
            nav.stop();
        }
    }

    /** Cambia pagina con una breve animazione; dir = 1 avanti (entra da destra), -1 indietro. */
    private void showPage(int next, int dir) {
        if (next == page) {
            return;
        }
        View out = pages[page];
        View in = pages[next];
        float shift = getResources().getDisplayMetrics().density * 48 * dir;
        out.animate().cancel();
        in.animate().cancel();
        out.animate().alpha(0f).translationX(-shift).setDuration(160).withEndAction(() -> {
            out.setVisibility(View.INVISIBLE);
            out.setAlpha(1f);
            out.setTranslationX(0);
        }).start();
        in.setAlpha(0f);
        in.setTranslationX(shift);
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).translationX(0).setDuration(200).start();

        page = next;
        Prefs.setPage(this, page); // all'avvio si riapre l'ultima pagina vista
        updatePageDots();
    }

    private void updatePageDots() {
        for (int i = 0; i < pageDots.getChildCount(); i++) {
            pageDots.getChildAt(i).setSelected(i == page);
        }
    }

    // ================================================================ navigazione (goESP)

    private void setupNav() {
        navActive = findViewById(R.id.nav_active);
        navIdle = findViewById(R.id.nav_idle);
        navArrow = findViewById(R.id.nav_arrow);
        navDistance = findViewById(R.id.nav_distance);
        navDirection = findViewById(R.id.nav_direction);
        navIdleTitle = findViewById(R.id.nav_idle_title);
        navIdleText = findViewById(R.id.nav_idle_text);
        navArrival = findViewById(R.id.nav_cell_arrival);
        navRemaining = findViewById(R.id.nav_cell_distance);
        navTime = findViewById(R.id.nav_cell_time);
        nav = NavReceiver.get(this);
        nav.setListener(this);
    }

    @Override
    public void onNavState(NavReceiver.State state, String deviceName) {
        navState = state;
        navDevice = deviceName;
        onNavData(nav.last());
    }

    @Override
    public void onNavData(NavData d) {
        if (!demo) {
            showNav(d);
        }
    }

    private void showNav(NavData d) {
        if (d == null) {
            navActive.setVisibility(View.GONE);
            navIdle.setVisibility(View.VISIBLE);
            navArrowShown = null;
            int text;
            switch (navState) {
                case CONNECTED:
                    text = R.string.nav_idle_waiting;
                    break;
                case UNSUPPORTED:
                    text = R.string.nav_unsupported;
                    break;
                case BT_OFF:
                case NO_BLUETOOTH:
                    text = R.string.nav_bt_off;
                    break;
                case NO_PERMISSION:
                    text = R.string.nav_no_permission;
                    break;
                case ERROR:
                    text = R.string.nav_error;
                    break;
                default:
                    text = R.string.nav_idle_off;
                    break;
            }
            navIdleTitle.setText(navState == NavReceiver.State.CONNECTED && navDevice != null
                    ? getString(R.string.nav_connected, navDevice) : getString(R.string.nav_idle_title));
            navIdleText.setText(text);
            return;
        }
        navIdle.setVisibility(View.GONE);
        navActive.setVisibility(View.VISIBLE);
        if (!d.image.equals(navArrowShown)) {
            navArrowShown = d.image;
            navArrow.setImageBitmap(loadArrow(d.image));
        }
        navDistance.setText(d.distanceText());
        navDirection.setText(d.direction);
        navArrival.setValue(d.arrival.isEmpty() ? dash() : d.arrival);
        navRemaining.setValue(d.remainingDistance.isEmpty() ? dash() : d.remainingDistance);
        navTime.setValue(d.remainingTime.isEmpty() ? dash() : d.remainingTime);
    }

    /** Freccia dalle immagini di goESP (assets/directions); se sconosciuta, la freccia "dritto". */
    private android.graphics.Bitmap loadArrow(String name) {
        for (String n : new String[]{name, "CONTINUE_STRAIGHT"}) {
            if (n == null || n.isEmpty() || n.contains("/") || n.contains("..")) {
                continue;
            }
            try (java.io.InputStream in = getAssets().open("directions/" + n + ".png")) {
                return android.graphics.BitmapFactory.decodeStream(in);
            } catch (java.io.IOException ignored) {
                // prova la successiva
            }
        }
        return null;
    }

    // ================================================================ ora e data

    private void setupClock() {
        date = findViewById(R.id.date);
        date.setTypeface(Fonts.display(this));
        clock = findViewById(R.id.clock);
        clock.setTypeface(Fonts.display(this));
        shiftLights = findViewById(R.id.shift_lights);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        register(timeReceiver, f);
        updateDate();
    }

    private TextView clock;
    private it.panda.launcher.ui.ShiftLightsView shiftLights;

    private void updateDate() {
        // ora con i due punti in rosso racing
        String time = new SimpleDateFormat("HH:mm", IT).format(new Date());
        android.text.SpannableString spannable = new android.text.SpannableString(time);
        int colon = time.indexOf(':');
        if (colon >= 0) {
            spannable.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.racing)), colon, colon + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        clock.setText(spannable);
        String text = new SimpleDateFormat("EEEE d MMMM", IT).format(new Date());
        date.setText(text.isEmpty() ? text : text.substring(0, 1).toUpperCase(IT) + text.substring(1));
    }

    // ================================================================ dati motore

    private void setupData() {
        speedGauge = findViewById(R.id.speed_gauge);
        speedGauge.setUnit(getString(R.string.unit_kmh));
        gearShift = findViewById(R.id.gear_shift);
        shiftArrow = findViewById(R.id.shift_arrow);
        // la freccia sta nel posto vuoto dello schema, alla stessa altezza della 4ª (centro in 74/88, 58/72)
        gearShift.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) shiftArrow.getLayoutParams();
            int x = Math.round(gearShift.getWidth() * 74f / 88f - lp.width / 2f);
            int y = Math.round(gearShift.getHeight() * 58f / 72f - lp.height / 2f);
            if (lp.leftMargin != x || lp.topMargin != y) {
                lp.leftMargin = x;
                lp.topMargin = y;
                shiftArrow.setLayoutParams(lp);
            }
        });
        cellInstantCar = findViewById(R.id.cell_instant_car);
        cellCoolant = findViewById(R.id.cell_coolant);
        cellOil = findViewById(R.id.cell_oil);
        cellBattery = findViewById(R.id.cell_battery);
        cellFuel = findViewById(R.id.cell_fuel);
        for (MetricCellView c : new MetricCellView[]{cellInstantCar, cellCoolant, cellOil, cellBattery, cellFuel}) {
            c.clear();
        }

        alertsRead = findViewById(R.id.alerts_read);
        alertsRead.setOnClickListener(v -> {
            reading = true;
            alertsRead.setEnabled(false);
            alertsRead.setText(R.string.alerts_reading);
            if (demo) { // senza auto: dopo un attimo compaiono avvisi finti
                demoHandler.postDelayed(() -> {
                    demoFaults = true;
                    reading = false;
                    updateAlerts();
                }, 1200);
            } else {
                obd.requestDiagnostics();
            }
        });
        updateAlerts();
    }

    @Override
    public void onObdState(ObdManager.State state, String detail) {
        if (demo) {
            return;
        }
        // lo stato si legge nelle impostazioni (sezione OBD)
        obdConnected = state == ObdManager.State.CONNECTED;
        if (!obdConnected) {
            showNoLiveData();
            lastSnapshot = null;
            diagReadsSeen = 0;
            reading = false;
        }
        updateAlerts();
    }

    @Override
    public void onObdData(ObdSnapshot s) {
        if (!demo) {
            showSnapshot(s);
        }
    }

    private void showSnapshot(ObdSnapshot s) {
        // marcia, velocità, giri
        speedGauge.setValue(s.speedKmh);
        // la marcia si legge sullo schema della leva (folle = barra centrale accesa)
        gearShift.setGear(s.gear == null || s.gear < 0 ? null : s.gear);
        // freccia: giri oltre la soglia scelta e una marcia più alta da inserire (non in folle, non in quinta);
        // oltre il massimo è rossa e compare comunque
        boolean canShiftUp = s.gear == null || s.gear < 0 || (s.gear >= 1 && s.gear < 5);
        boolean over = s.rpm != null && s.rpm >= redRpm;
        boolean shift = s.rpm != null && s.rpm >= shiftRpm && canShiftUp;
        shiftArrow.setColorFilter(getColor(over ? R.color.crit : R.color.accent));
        shiftArrow.setVisibility(shift || over ? View.VISIBLE : View.GONE);
        // luci di cambio marcia: si accendono da 2500 giri fino al massimo
        shiftLights.setFraction(s.rpm == null || !shiftLightsOn ? 0f : (float) ((s.rpm - 2500.0) / Math.max(1, redRpm - 2500)));


        // dati auto: icona colorata e riempita in base al valore
        if (s.coolantC != null) {
            cellCoolant.setValue(String.valueOf(Math.round(s.coolantC)));
            cellCoolant.setStatus(getColor(s.coolantC < 70 ? R.color.cold : s.coolantC >= 103 ? R.color.crit : R.color.ok),
                    level(s.coolantC, 40, 120));
        } else {
            cellCoolant.clear();
        }
        if (s.oilC != null) {
            cellOil.setValue(String.valueOf(Math.round(s.oilC)));
            cellOil.setStatus(getColor(s.oilC < 60 ? R.color.cold : s.oilC >= 130 ? R.color.crit : R.color.ok),
                    level(s.oilC, 40, 140));
        } else {
            cellOil.clear();
        }
        if (s.batteryV != null) {
            cellBattery.setValue(oneDecimal(s.batteryV));
            boolean running = s.rpm != null && s.rpm > 500;
            // >= 13,2 V alternatore in carica (fulmine); a motore spento 12,7 V = carica piena, 11,8 V = scarica
            boolean charging = s.batteryV >= 13.2;
            int color = charging ? R.color.ok
                    : running ? R.color.crit
                    : s.batteryV >= 12.2 ? R.color.muted : R.color.crit;
            cellBattery.setStatus(getColor(color), charging ? 1f : level(s.batteryV, 11.8, 12.7), charging);
        } else {
            cellBattery.clear();
        }
        if (s.fuelLevelPct != null) {
            cellFuel.setValue(String.valueOf(Math.round(s.fuelLevelPct)));
            cellFuel.setStatus(getColor(s.fuelLevelPct < 10 ? R.color.crit : s.fuelLevelPct < 20 ? R.color.accent : R.color.ok),
                    (float) (s.fuelLevelPct / 100.0));
        } else {
            cellFuel.clear();
        }
        // consumo istantaneo: l/100 km in movimento, l/h da fermi
        if (s.consumptionL100 != null) {
            showInstant(oneDecimal(Math.min(99.9, s.consumptionL100)), R.string.unit_l100);
        } else if (s.fuelRateLph != null) {
            showInstant(oneDecimal(s.fuelRateLph), R.string.unit_lh);
        } else {
            showInstant(dash(), R.string.unit_l100);
        }
        lastHistory = s.history;
        showTrip(s.trip);

        lastSnapshot = s;
        if (s.diagReads != diagReadsSeen) { // lettura di spia e codici completata
            diagReadsSeen = s.diagReads;
            reading = false;
        }
        updateAlerts();
    }

    // ================================================================ dati dimostrativi

    private final Runnable demoTick = new Runnable() {
        @Override
        public void run() {
            double t = (SystemClock.elapsedRealtime() - demoStartMs) / 1000.0;
            showSnapshot(demoSnapshot(t, demoFaults));
            showNav(demoNav(t));
            demoHandler.postDelayed(this, 500);
        }
    };

    /** Dopo "Leggi allarmi" in demo compaiono spia e codici finti. */
    private boolean demoFaults;

    private void startDemo() {
        demo = true;
        demoFaults = false;
        obdConnected = true;
        diagReadsSeen = 1;
        demoStartMs = SystemClock.elapsedRealtime();
        demoHandler.removeCallbacks(demoTick);
        demoHandler.post(demoTick);
    }

    private void stopDemo() {
        demo = false;
        demoFaults = false;
        demoHandler.removeCallbacks(demoTick);
        showNav(nav.last());
        obdConnected = false;
        diagReadsSeen = 0;
        lastSnapshot = null;
        showNoLiveData();
        updateAlerts();
    }

    /** Valori che variano piano, anche a tre cifre, per vedere come stanno nelle caselle. */
    private static ObdSnapshot demoSnapshot(double t, boolean faults) {
        ObdSnapshot s = new ObdSnapshot();
        double wave = 0.5 + 0.5 * Math.sin(t / 6);
        s.speedKmh = 150 * wave;
        s.rpm = 800 + 5400 * wave;
        s.gear = s.speedKmh < 5 ? 0 : (int) Math.min(5, 1 + s.speedKmh / 32);
        s.coolantC = 95 + 10 * Math.sin(t / 9);
        s.oilC = 105 + 20 * Math.sin(t / 11);
        s.batteryV = 14.0 + 0.4 * Math.sin(t / 5);
        s.fuelLevelPct = 100 - (t % 100);
        s.loadPct = 20 + 60 * wave;
        s.consumptionL100 = 4 + 8 * wave;
        s.milOn = faults;
        s.diagReads = 1;
        s.dtcStored = new ArrayList<>();
        s.dtcPending = new ArrayList<>();
        if (faults) {
            s.dtcStored.add("P0301");
            s.dtcStored.add("P0420");
            s.dtcPending.add("P0171");
        }
        // trip finto: cresce col tempo, così le sei caselle e il grafico hanno qualcosa da mostrare
        double km = t * 0.03;
        s.trip = new TripComputer.Summary(km * 0.065, km, t * 2, t * 1.8, 150);
        s.history = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            double w = 0.5 + 0.5 * Math.sin((t - (120 - i) * 3) / 6);
            s.history.add(4 + 8 * w);
        }
        return s;
    }

    /** Indicazione finta: la distanza scende e ogni 20 secondi cambia svolta. */
    private static NavData demoNav(double t) {
        String[][] steps = {
                {"Via Roma verso Piazza Duomo", "ROTATORY_RIGHT"},
                {"Strada Provinciale 123 verso San Giovanni in Persiceto Centro", "CONTINUE_LEFT"},
                {"Viale dei Mille", "SLIGHT_RIGHT"},
        };
        int i = (int) (t / 20) % steps.length;
        int meters = 50 * (int) ((1000 - (t % 20) * 45) / 50);
        double remainingKm = Math.max(1, 18 - t * 0.01);
        return NavData.parse(steps[i][0] + "#" + steps[i][1] + "#" + meters + "#m#17:39#"
                + String.format(IT, "%.0f km", remainingKm) + "#22 min");
    }

    /** Consumo istantaneo: sia nella casella accanto all'auto sia in quella della pagina consumi. */
    private void showInstant(String value, int unitRes) {
        for (MetricCellView c : new MetricCellView[]{cellInstant, cellInstantCar}) {
            c.setValue(value);
            c.setUnit(getString(unitRes));
        }
    }

    /** Valore riportato tra 0 e 1 per riempire le icone. */
    private static float level(double v, double min, double max) {
        return (float) Math.max(0, Math.min(1, (v - min) / (max - min)));
    }

    /** OBD scollegato: via i dati in tempo reale (il trip resta). */
    private void showNoLiveData() {
        speedGauge.setValue(null);
        gearShift.setGear(null);
        shiftArrow.setVisibility(View.GONE);
        shiftLights.setFraction(0f);
        showInstant(dash(), R.string.unit_l100);
    }

    /** Fondo scala del tachimetro e soglie dei giri, come scelti nelle impostazioni. */
    private void applyGaugeScales() {
        int maxSpeed = Prefs.getMaxSpeed(this);
        speedGauge.configure(maxSpeed, 0, maxSpeed > 210 ? 40 : 20);
        shiftRpm = Prefs.getShiftRpm(this);
        redRpm = Prefs.getRedRpm(this);
        shiftLightsOn = Prefs.getShiftLights(this);
        if (!shiftLightsOn) {
            shiftLights.setFraction(0f);
        }
    }

    // ================================================================ avvisi

    /** Aggiorna il tasto "Leggi allarmi" e la lista della pagina Avvisi. */
    private void updateAlerts() {
        alertsRead.setEnabled(obdConnected && !reading);
        alertsRead.setText(reading ? R.string.alerts_reading : R.string.alerts_read);
        fillAlertsPage(Diagnostics.evaluate(lastSnapshot));
    }

    /** Firma degli avvisi mostrati: la lista si ricostruisce solo quando cambia. */
    private String alertsShown;

    /** Pagina Avvisi: una riga per avviso (spia motore, codici, avvisi dai valori). */
    private void fillAlertsPage(List<Diagnostics.Alert> alerts) {
        StringBuilder sig = new StringBuilder(obdConnected ? "on" : "off").append(diagReadsSeen > 0 ? "R" : "U");
        for (Diagnostics.Alert a : alerts) {
            sig.append('|').append(a.severity).append(a.code).append(a.title).append(a.detail);
        }
        if (sig.toString().equals(alertsShown)) {
            return;
        }
        alertsShown = sig.toString();

        LinearLayout list = findViewById(R.id.alerts_list);
        list.removeAllViews(); // nessun avviso (o OBD scollegato): lista vuota e nient'altro

        float d = getResources().getDisplayMetrics().density;
        for (Diagnostics.Alert a : alerts) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(0, Math.round(10 * d), 0, Math.round(10 * d));

            View bar = new View(this);
            bar.setBackgroundColor(getColor(a.severity == Diagnostics.Severity.RED ? R.color.crit : R.color.accent));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(Math.round(4 * d), LinearLayout.LayoutParams.MATCH_PARENT);
            barLp.setMarginEnd(Math.round(14 * d));
            item.addView(bar, barLp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            String title = a.code != null ? a.code + "  ·  " + a.title : a.title;
            texts.addView(alertText(title, R.color.text, 18, true));
            texts.addView(alertText(a.detail, R.color.muted, 15, false));
            item.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            list.addView(item);
            View line = new View(this);
            line.setBackgroundColor(getColor(R.color.line));
            list.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private TextView alertText(String text, int color, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(color));
        t.setTextSize(sp);
        t.setLineSpacing(0, 1.15f);
        if (bold) {
            t.setTypeface(Fonts.bold(this));
        } else {
            t.setTypeface(Fonts.regular(this));
        }
        t.setPadding(0, Math.round(2 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    // ================================================================ consumi e trip

    private void setupTrip() {
        tripReset = findViewById(R.id.trip_reset);
        cellInstant = findViewById(R.id.cell_instant);
        cellAvg = findViewById(R.id.cell_avg);
        cellDistance = findViewById(R.id.cell_distance);
        cellTime = findViewById(R.id.cell_time);
        cellAvgSpeed = findViewById(R.id.cell_avg_speed);
        cellFuelUsed = findViewById(R.id.cell_fuel_used);
        chart = findViewById(R.id.consumption_chart);

        // azzeramento solo con conferma: in auto un tocco per sbaglio è facile
        tripReset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.trip_reset_confirm)
                .setPositiveButton(R.string.reset, (d, w) -> {
                    obd.resetTrip();
                    showTrip(obd.tripSummary());
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showTrip(TripComputer.Summary s) {
        if (s == null) {
            return;
        }
        Double avg = s.averageL100();
        cellAvg.setValue(avg != null ? oneDecimal(avg) : dash());
        cellDistance.setValue(s.distanceKm < 100 ? oneDecimal(s.distanceKm) : String.valueOf(Math.round(s.distanceKm)));
        long minutes = Math.round(s.seconds / 60.0);
        if (minutes < 60) {
            cellTime.setValue(String.valueOf(minutes));
            cellTime.setUnit("min");
        } else {
            cellTime.setValue(String.format(IT, "%d:%02d", minutes / 60, minutes % 60));
            cellTime.setUnit("h");
        }
        Double speed = s.averageSpeedKmh();
        cellAvgSpeed.setValue(speed != null ? String.valueOf(Math.round(speed)) : dash());
        cellFuelUsed.setValue(String.format(IT, "%.2f", s.fuelLiters));
        // grafico: consumo dell'ultima mezz'ora, con la media del trip
        chart.setData(lastHistory, avg);
    }

    // ================================================================ utilità

    private String dash() {
        return getString(R.string.no_value);
    }

    private static String oneDecimal(double v) {
        return String.format(IT, "%.1f", v);
    }

    private void register(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    // ================================================================ ciclo di vita

    @Override
    protected void onResume() {
        super.onResume();
        Immersive.apply(this);
        activityResumed = true;
        obd.setVisible(true);
        applyGaugeScales();
        boolean wantDemo = Prefs.getDemo(this);
        if (wantDemo) { // onPause ferma il timer: si riparte a ogni ritorno nella Home
            startDemo();
        } else if (demo) {
            stopDemo();
        }
        updateDate();
        fillAppColumn(); // le app scelte possono essere cambiate nelle impostazioni
        lastHistory = obd.consumptionHistory();
        showTrip(obd.tripSummary());
        // se nelle impostazioni è stato scelto un altro adattatore, ricollegati
        String address = Prefs.getObdAddress(this);
        boolean wasEnabled = obdEnabled;
        applyAvailability();
        if (pausedOnce && (!java.util.Objects.equals(obdAddressAtPause, address) || wasEnabled != obdEnabled)) {
            obd.restart();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        demoHandler.removeCallbacks(demoTick);
        obd.setVisible(false);
        obdAddressAtPause = Prefs.getObdAddress(this);
        pausedOnce = true;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        unregisterReceiver(timeReceiver);
        appLoader.shutdown();
        obd.stop();
        nav.setListener(null);
        nav.stop();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        swipe.onTouchEvent(ev); // scorrimento tra le pagine con il dito, senza bloccare i tocchi normali
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Immersive.apply(this); // dopo dialoghi e tendine le barre vanno nascoste di nuovo
        }
    }

    @Override
    public void onBackPressed() {
        // è la schermata Home: "indietro" non deve chiuderla
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT) {
            obd.restart();
            if (navEnabled) {
                nav.stop();
                nav.start();
            }
        }
    }
}
