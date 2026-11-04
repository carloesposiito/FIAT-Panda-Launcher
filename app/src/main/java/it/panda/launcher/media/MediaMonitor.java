package it.panda.launcher.media;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

/**
 * Legge dalla sessione multimediale attiva (radio, musica...) il titolo e l'artista o la stazione.
 * Serve l'accesso alle notifiche, che l'utente concede una volta nelle impostazioni di Android.
 */
public final class MediaMonitor {

    public interface Listener {
        /** Titolo e sottotitolo del brano o della stazione; entrambi null se non c'è nulla in corso. */
        void onMedia(String title, String subtitle);

        /** Manca l'accesso alle notifiche. */
        void onNoAccess();
    }

    private final Context context;
    private final Listener listener;
    private final ComponentName service;
    private MediaSessionManager manager;
    private MediaController current;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pick;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publish();
        }
    };

    public MediaMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.service = new ComponentName(this.context, MediaListenerService.class);
    }

    public void start() {
        manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, service);
            pick(manager.getActiveSessions(service));
        } catch (SecurityException e) {
            listener.onNoAccess();
        }
    }

    public void stop() {
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (RuntimeException ignored) {
                // già rimosso o accesso revocato
            }
        }
        detach();
    }

    /** Sceglie la sessione in riproduzione, altrimenti la prima. */
    private void pick(List<MediaController> controllers) {
        MediaController chosen = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    chosen = c;
                    break;
                }
                if (chosen == null) {
                    chosen = c;
                }
            }
        }
        detach();
        current = chosen;
        if (current != null) {
            current.registerCallback(callback);
        }
        publish();
    }

    private void detach() {
        if (current != null) {
            current.unregisterCallback(callback);
            current = null;
        }
    }

    private void publish() {
        MediaMetadata m = current != null ? current.getMetadata() : null;
        if (m == null) {
            listener.onMedia(null, null);
            return;
        }
        String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) {
            artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        }
        listener.onMedia(title, artist);
    }
}
