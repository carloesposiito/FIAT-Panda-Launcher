package it.panda.launcher.media;

import android.service.notification.NotificationListenerService;

/**
 * Non fa nulla: serve solo perché Android conceda a {@link MediaMonitor} di leggere le sessioni
 * multimediali (titolo del brano, stazione) dopo l'accesso alle notifiche.
 */
public class MediaListenerService extends NotificationListenerService {
}
