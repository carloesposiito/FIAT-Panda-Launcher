package it.panda.launcher.radio;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosa mostra l'app radio dell'autoradio: titolo e descrizione della sua notifica di riproduzione
 * (la frequenza compare in entrambi). Serve l'accesso alle notifiche, concesso dall'utente nelle
 * impostazioni di sistema.
 */
public final class RadioWatcher {

    /** Testo della notifica: il sottotitolo può essere vuoto. */
    public static final class Info {
        public final String title;
        public final String subtitle;

        public Info(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public interface Listener {
        /** info == null: nessuna notifica della radio; hasAccess false: manca l'accesso alle notifiche. */
        void onRadio(Info info, boolean hasAccess);
    }

    /** Frequenza FM (88.5, 98,5 MHz) o AM (999 kHz), con l'unità se presente. */
    private static final java.util.regex.Pattern FREQUENCY = java.util.regex.Pattern.compile(
            "\\d{2,3}(?:[.,]\\d{1,2})?\\s*MHz|\\d{3,4}\\s*kHz|\\d{2,3}[.,]\\d{1,2}",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Solo la frequenza ("88.5Mhz") dal testo della notifica, senza prefissi come "In ascolto: ".
     * Null se la notifica non ne contiene una: la radio è spenta (es. "Clicca per aprire").
     */
    public static String frequencyOf(Info info) {
        return frequencyOf(info.subtitle, info.title);
    }

    static String frequencyOf(String... texts) {
        for (String t : texts) {
            java.util.regex.Matcher m = FREQUENCY.matcher(t);
            if (m.find()) {
                return m.group().trim();
            }
        }
        return null;
    }

    private static volatile RadioWatcher current;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started;

    public RadioWatcher(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** True se l'utente ha concesso l'accesso alle notifiche a questa app. */
    public static boolean hasAccess(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(c.getPackageName());
    }

    static void notifyChanged() {
        RadioWatcher w = current;
        if (w != null) {
            w.main.post(w::refresh);
        }
    }

    /** Parte (o riparte, per riprendere un accesso appena concesso). */
    public void start() {
        started = true;
        current = this;
        if (hasAccess(context) && !RadioNotificationListener.isConnected()) {
            // accesso concesso ma servizio non collegato (alcune ROM non lo agganciano da sole)
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                        new android.content.ComponentName(context, RadioNotificationListener.class));
            } catch (RuntimeException e) {
                // ROM senza supporto: resta il collegamento automatico
            }
        }
        refresh();
    }

    public void stop() {
        started = false;
        if (current == this) {
            current = null;
        }
    }

    private void refresh() {
        if (!started) {
            return;
        }
        boolean access = hasAccess(context);
        listener.onRadio(access ? find() : null, access);
    }

    /** Pacchetti con notifiche permanenti che non sono la radio. */
    private static final String[] IGNORED = {
            "android", "com.android.systemui", "com.android.settings", "com.google.android.apps.maps",
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
    };

    /**
     * La notifica più probabile della radio. Punteggio: riproduzione multimediale (categoria trasporto o
     * sessione media) > pacchetto che si chiama radio/fm/tuner > notifica permanente di un'app di sistema.
     * Le altre si scartano. Il testo viene dagli extra o, se vuoti, dalle view di una notifica personalizzata.
     */
    private Info find() {
        Info best = null;
        int bestScore = 0;
        for (StatusBarNotification sbn : RadioNotificationListener.active()) {
            Notification n = sbn.getNotification();
            if (n == null) {
                continue;
            }
            int score = score(sbn, n);
            if (score <= bestScore) {
                continue;
            }
            Info info = read(sbn, n);
            if (info != null) {
                best = info;
                bestScore = score;
            }
        }
        return best;
    }

    private int score(StatusBarNotification sbn, Notification n) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(context.getPackageName()) || isIgnored(pkg)) {
            return 0;
        }
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)
                || (n.extras != null && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))) {
            return 3;
        }
        String lower = pkg.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("radio") || lower.contains("tuner") || lower.endsWith(".fm") || lower.contains(".fm.")) {
            return 2;
        }
        boolean ongoing = (n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0;
        return ongoing && isSystemApp(pkg) ? 1 : 0;
    }

    private static boolean isIgnored(String pkg) {
        for (String p : IGNORED) {
            if (p.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSystemApp(String pkg) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Titolo e descrizione dagli extra; se non c'è titolo, dalle view personalizzate della notifica. */
    private Info read(StatusBarNotification sbn, Notification n) {
        String title = first(n, Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG);
        String sub = first(n, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT);
        if (!title.isEmpty()) {
            return new Info(title, sub);
        }
        List<String> texts = new ArrayList<>();
        collect(n.contentView, texts);
        collect(n.bigContentView, texts);
        if (texts.isEmpty() && !sub.isEmpty()) {
            return new Info(sub, "");
        }
        if (texts.isEmpty()) {
            return null;
        }
        return new Info(texts.get(0), texts.size() > 1 ? texts.get(1) : sub);
    }

    private static String first(Notification n, String... keys) {
        if (n.extras == null) {
            return "";
        }
        for (String key : keys) {
            CharSequence s = n.extras.getCharSequence(key);
            if (s != null && s.toString().trim().length() > 0) {
                return s.toString().trim();
            }
        }
        return "";
    }

    /** Testi non vuoti e distinti delle TextView di una notifica personalizzata, in ordine di apparizione. */
    private void collect(RemoteViews rv, List<String> out) {
        if (rv == null) {
            return;
        }
        try {
            View root = rv.apply(context, new FrameLayout(context));
            walk(root, out);
        } catch (RuntimeException e) {
            // view non inflabile da qui: si passa oltre
        }
    }

    private static void walk(View v, List<String> out) {
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            String s = cs != null ? cs.toString().trim() : "";
            if (!s.isEmpty() && !out.contains(s)) {
                out.add(s);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                walk(g.getChildAt(i), out);
            }
        }
    }
}
