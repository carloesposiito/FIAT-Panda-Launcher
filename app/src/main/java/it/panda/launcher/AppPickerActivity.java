package it.panda.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.panda.launcher.apps.AppEntry;
import it.panda.launcher.apps.AppLoader;

/**
 * Scelta delle app della colonna sinistra: in alto quelle scelte (con frecce per l'ordine),
 * sotto tutte le app installate da spuntare. Si salva a ogni modifica.
 */
public class AppPickerActivity extends Activity {

    private LinearLayout content;
    private AppLoader loader;
    private List<AppEntry> allApps = new ArrayList<>();
    private final List<ComponentName> chosen = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.picker_title);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(24));
        scroll.addView(content);
        setContentView(scroll);

        loader = new AppLoader(this);
        loader.load(apps -> {
            allApps = apps;
            List<ComponentName> saved = Prefs.getFavorites(this);
            if (saved == null) {
                // mai scelte: si parte da quelle che la Home mostra già (le prime in ordine alfabetico)
                int slots = Math.max(1, Prefs.getAppSlots(this));
                saved = new ArrayList<>();
                for (int i = 0; i < Math.min(slots, apps.size()); i++) {
                    saved.add(apps.get(i).component);
                }
            }
            // si tengono solo le app ancora installate
            Map<ComponentName, AppEntry> map = byComponent();
            for (ComponentName cn : saved) {
                if (map.containsKey(cn)) {
                    chosen.add(cn);
                }
            }
            build();
        });
    }

    @Override
    protected void onDestroy() {
        loader.shutdown();
        super.onDestroy();
    }

    private Map<ComponentName, AppEntry> byComponent() {
        Map<ComponentName, AppEntry> map = new HashMap<>();
        for (AppEntry a : allApps) {
            map.put(a.component, a);
        }
        return map;
    }

    private void save() {
        Prefs.setFavorites(this, chosen);
        build();
    }

    private void build() {
        content.removeAllViews();
        Map<ComponentName, AppEntry> map = byComponent();
        int slots = Prefs.getAppSlots(this);

        addTitle(getString(R.string.picker_selected));
        if (slots > 0) {
            addHint(getString(R.string.picker_selected_hint, slots));
        }
        if (chosen.isEmpty()) {
            addHint(getString(R.string.picker_none));
        }
        for (int i = 0; i < chosen.size(); i++) {
            final int index = i;
            AppEntry app = map.get(chosen.get(i));
            if (app == null) {
                continue;
            }
            LinearLayout row = row(app, slots > 0 && i >= slots);
            row.addView(arrow(R.drawable.ic_arrow_up, R.string.move_up, index > 0, v -> {
                chosen.add(index - 1, chosen.remove(index));
                save();
            }));
            row.addView(arrow(R.drawable.ic_arrow_down, R.string.move_down, index < chosen.size() - 1, v -> {
                chosen.add(index + 1, chosen.remove(index));
                save();
            }));
            row.addView(arrow(R.drawable.ic_close, R.string.remove, true, v -> {
                chosen.remove(index);
                save();
            }));
            content.addView(row);
        }

        addTitle(getString(R.string.picker_all));
        for (AppEntry app : allApps) {
            LinearLayout row = row(app, false);
            CheckBox check = new CheckBox(this);
            check.setChecked(chosen.contains(app.component));
            check.setClickable(false);
            row.addView(check);
            row.setOnClickListener(v -> {
                if (!chosen.remove(app.component)) {
                    chosen.add(app.component);
                }
                save();
            });
            content.addView(row);
        }
    }

    // ---------------------------------------------------------------- piccoli aiuti per l'interfaccia

    private LinearLayout row(AppEntry app, boolean dimmed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setAlpha(dimmed ? 0.45f : 1f);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMarginEnd(dp(16));
        row.addView(icon, lp);

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(getColor(R.color.text));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private ImageButton arrow(int icon, int description, boolean enabled, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(getString(description));
        b.setBackgroundResource(R.drawable.bg_icon_button);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.25f);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMarginStart(dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private void addTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.accent));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, dp(20), 0, dp(6));
        content.addView(t);
    }

    private void addHint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, 0, 0, dp(8));
        content.addView(t);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Immersive.apply(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Immersive.apply(this); // dopo dialoghi e tendine le barre vanno nascoste di nuovo
        }
    }
}
