package it.panda.launcher;

import android.content.Context;

import java.io.File;

/** L'unica auto del launcher: la FIAT Panda. */
final class CarStore {

    private CarStore() {
    }

    static CarProfile active() {
        return new CarProfile(CarProfile.DEFAULT_ID, "FIAT", "Panda");
    }

    static String activeId() {
        return CarProfile.DEFAULT_ID;
    }

    /** Cartella con le immagini importate. */
    static File imagesDir(Context c, String id) {
        return new File(c.getFilesDir(), "cars/" + id);
    }
}
