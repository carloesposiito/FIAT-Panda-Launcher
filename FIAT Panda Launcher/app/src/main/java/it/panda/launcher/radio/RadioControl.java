package it.panda.launcher.radio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import it.panda.launcher.Prefs;

/**
 * Comandi alla radio (stazione avanti e indietro). Ogni autoradio cinese si comporta a modo suo, quindi i metodi
 * sono più d'uno: {@link #detect} dice quali sembrano disponibili per l'app radio scelta, l'utente ne sceglie uno
 * nelle impostazioni (e lo prova), {@link #send} lo usa.
 */
public final class RadioControl {

    public enum Method {
        /** Nessun comando: toccando la radio si apre solo l'app. */
        NONE,
        /** Sessione multimediale pubblicata dall'app radio (serve l'accesso alle notifiche). */
        MEDIA_SESSION,
        /** Tasto multimediale inviato al pacchetto dell'app radio. */
        MEDIA_BUTTON_PACKAGE,
        /** Tasto multimediale di sistema: lo riceve l'app che in quel momento gestisce i media. */
        MEDIA_KEY_SYSTEM
    }

    public enum Command {
        NEXT(KeyEvent.KEYCODE_MEDIA_NEXT), PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        final int keyCode;

        Command(int keyCode) {
            this.keyCode = keyCode;
        }
    }

    /** Esito del controllo per un metodo: trovato o no, e perché. */
    public static final class Detection {
        public final Method method;
        public final boolean found;
        /** Id della stringa che spiega l'esito. */
        public final int noteRes;

        Detection(Method method, boolean found, int noteRes) {
            this.method = method;
            this.found = found;
            this.noteRes = noteRes;
        }
    }

    private RadioControl() {
    }

    /** Metodo scelto nelle impostazioni (NONE se mai scelto). */
    public static Method current(Context c) {
        try {
            return Method.valueOf(Prefs.getRadioControl(c));
        } catch (IllegalArgumentException e) {
            return Method.NONE;
        }
    }

    public static void setCurrent(Context c, Method method) {
        Prefs.setRadioControl(c, method.name());
    }

    /** Controlla i metodi possibili per il pacchetto dato. Nessun comando viene inviato. */
    public static List<Detection> detect(Context c, String pkg) {
        List<Detection> out = new ArrayList<>();
        if (pkg == null) {
            return out;
        }
        // sessione multimediale
        if (!RadioWatcher.hasAccess(c)) {
            out.add(new Detection(Method.MEDIA_SESSION, false, it.panda.launcher.R.string.radio_detect_no_access));
        } else if (sessionOf(c, pkg) != null) {
            out.add(new Detection(Method.MEDIA_SESSION, true, it.panda.launcher.R.string.radio_detect_session_ok));
        } else {
            out.add(new Detection(Method.MEDIA_SESSION, false, it.panda.launcher.R.string.radio_detect_session_none));
        }
        // ricevitore dei tasti multimediali nell'app radio
        boolean receiver = !c.getPackageManager().queryBroadcastReceivers(mediaButton(pkg, null), 0).isEmpty();
        out.add(new Detection(Method.MEDIA_BUTTON_PACKAGE, receiver,
                receiver ? it.panda.launcher.R.string.radio_detect_button_ok : it.panda.launcher.R.string.radio_detect_button_none));
        // tasto di sistema: non si può verificare senza provarlo
        out.add(new Detection(Method.MEDIA_KEY_SYSTEM, true, it.panda.launcher.R.string.radio_detect_key_unknown));
        return out;
    }

    /** Invia il comando col metodo scelto. False se non c'è un metodo o non è utilizzabile adesso. */
    public static boolean send(Context c, Command command) {
        ComponentName app = Prefs.getRadioApp(c);
        String pkg = app == null ? null : app.getPackageName();
        return send(c, current(c), pkg, command);
    }

    public static boolean send(Context c, Method method, String pkg, Command command) {
        try {
            switch (method) {
                case MEDIA_SESSION: {
                    MediaController session = pkg == null ? null : sessionOf(c, pkg);
                    if (session == null) {
                        return false;
                    }
                    if (command == Command.NEXT) {
                        session.getTransportControls().skipToNext();
                    } else {
                        session.getTransportControls().skipToPrevious();
                    }
                    return true;
                }
                case MEDIA_BUTTON_PACKAGE:
                    if (pkg == null) {
                        return false;
                    }
                    c.sendBroadcast(mediaButton(pkg, new KeyEvent(KeyEvent.ACTION_DOWN, command.keyCode)));
                    c.sendBroadcast(mediaButton(pkg, new KeyEvent(KeyEvent.ACTION_UP, command.keyCode)));
                    return true;
                case MEDIA_KEY_SYSTEM: {
                    AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, command.keyCode));
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, command.keyCode));
                    return true;
                }
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            return false; // permesso revocato, servizio non collegato...
        }
    }

    private static Intent mediaButton(String pkg, KeyEvent event) {
        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg);
        if (event != null) {
            i.putExtra(Intent.EXTRA_KEY_EVENT, event);
        }
        return i;
    }

    private static MediaController sessionOf(Context c, String pkg) {
        try {
            MediaSessionManager manager = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> sessions = manager.getActiveSessions(new ComponentName(c, RadioNotificationListener.class));
            for (MediaController s : sessions) {
                if (pkg.equals(s.getPackageName())) {
                    return s;
                }
            }
        } catch (SecurityException e) {
            // accesso alle notifiche non concesso
        }
        return null;
    }
}
