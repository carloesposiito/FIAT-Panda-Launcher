package it.panda.launcher.apps;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** Un'app avviabile: nome, icona e componente da lanciare. */
public final class AppEntry {
    public final String label;
    public final ComponentName component;
    public final Drawable icon;

    public AppEntry(String label, ComponentName component, Drawable icon) {
        this.label = label;
        this.component = component;
        this.icon = icon;
    }
}
