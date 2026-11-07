package it.panda.launcher.radio;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

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

    /** Prima notifica di riproduzione multimediale (categoria trasporto o stile multimediale) con un titolo. */
    private static Info find() {
        for (StatusBarNotification sbn : RadioNotificationListener.active()) {
            Notification n = sbn.getNotification();
            if (n == null || !(Notification.CATEGORY_TRANSPORT.equals(n.category)
                    || n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))) {
                continue;
            }
            String title = text(n, Notification.EXTRA_TITLE);
            if (!title.isEmpty()) {
                return new Info(title, text(n, Notification.EXTRA_TEXT));
            }
        }
        return null;
    }

    private static String text(Notification n, String key) {
        CharSequence s = n.extras.getCharSequence(key);
        return s != null ? s.toString().trim() : "";
    }
}
