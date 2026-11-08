package it.panda.launcher.radio;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Serve solo per leggere la notifica dell'app radio dell'autoradio (frequenza nel titolo e nella
 * descrizione). Non legge nient'altro.
 */
public class RadioNotificationListener extends NotificationListenerService {
    private static volatile RadioNotificationListener instance;

    /** Le notifiche attive, o un elenco vuoto se il servizio non è collegato. */
    static StatusBarNotification[] active() {
        RadioNotificationListener s = instance;
        if (s == null) {
            return new StatusBarNotification[0];
        }
        try {
            StatusBarNotification[] list = s.getActiveNotifications();
            return list != null ? list : new StatusBarNotification[0];
        } catch (SecurityException | NullPointerException e) {
            return new StatusBarNotification[0];
        }
    }

    static boolean isConnected() {
        return instance != null;
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        RadioWatcher.notifyChanged();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        RadioWatcher.notifyChanged();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        RadioWatcher.notifyChanged();
    }
}
