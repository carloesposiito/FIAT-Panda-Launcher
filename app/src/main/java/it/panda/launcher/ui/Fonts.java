package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Carattere del cruscotto: Barlow Condensed (licenza OFL, in assets/fonts).
 * Medium per etichette e testi, Bold per i valori. Se il file manca si usa il condensed di sistema.
 * Data e ora usano Chakra Petch Bold (OFL), più squadrato e sportivo.
 */
public final class Fonts {

    private static Typeface regular, bold, display;

    private Fonts() {
    }

    public static synchronized Typeface regular(Context c) {
        if (regular == null) {
            regular = load(c, "fonts/BarlowCondensed-Medium.ttf", Typeface.NORMAL);
        }
        return regular;
    }

    public static synchronized Typeface bold(Context c) {
        if (bold == null) {
            bold = load(c, "fonts/BarlowCondensed-Bold.ttf", Typeface.BOLD);
        }
        return bold;
    }

    /** Carattere di data e ora. */
    public static synchronized Typeface display(Context c) {
        if (display == null) {
            display = load(c, "fonts/ChakraPetch-Bold.ttf", Typeface.BOLD);
        }
        return display;
    }

    private static Typeface load(Context c, String path, int style) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (RuntimeException e) {
            return Typeface.create("sans-serif-condensed", style);
        }
    }

    /** Applica il carattere a tutti i testi sotto la vista: in grassetto se lo erano, altrimenti normale. */
    public static void applyTo(View root) {
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            Context c = t.getContext();
            Typeface current = t.getTypeface();
            if (current != bold(c) && current != regular(c) && current != display(c)) {
                t.setTypeface(current != null && current.isBold() ? bold(c) : regular(c));
            }
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTo(g.getChildAt(i));
            }
        }
    }
}
