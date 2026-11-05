package it.panda.launcher.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.panda.launcher.R;

/**
 * Casella di un dato: icona (facoltativa) a sinistra, sopra l'etichetta piccola, sotto valore e unità.
 * Nel layout: {@code <it.panda.launcher.ui.MetricCellView app:label="Acqua" app:unit="°C" app:icon="water" />}.
 */
public class MetricCellView extends LinearLayout {

    private final MetricIconView icon;
    private final TextView value;
    private final TextView unit;
    private final int defaultValueColor;

    public MetricCellView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        if (getBackground() == null) { // sfondo diverso dichiarato nel layout, es. caselle semitrasparenti
            setBackgroundResource(R.drawable.bg_cell);
        }
        float d = getResources().getDisplayMetrics().density;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MetricCellView);
        boolean large = a.getBoolean(R.styleable.MetricCellView_large, false);
        int padH = Math.round((large ? 16 : 8) * d), padV = Math.round((large ? 8 : 5) * d);
        setPadding(padH, padV, padH, padV);

        String labelText = a.getString(R.styleable.MetricCellView_label);
        String unitText = a.getString(R.styleable.MetricCellView_unit);
        int iconIndex = a.getInt(R.styleable.MetricCellView_icon, 0);
        boolean accentValue = a.getBoolean(R.styleable.MetricCellView_accent, false);
        a.recycle();

        icon = new MetricIconView(context);
        icon.setType(MetricIconView.Type.values()[iconIndex]);
        int iconSize = Math.round((large ? 40 : 24) * d);
        LayoutParams iconLp = new LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(Math.round((large ? 14 : 8) * d));
        addView(icon, iconLp);
        icon.setVisibility(iconIndex == 0 ? GONE : VISIBLE);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextColor(context.getColor(R.color.muted));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 15 : 11);
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        texts.addView(label);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setBaselineAligned(true);
        texts.addView(row);

        value = new TextView(context);
        value.setText(R.string.no_value);
        defaultValueColor = context.getColor(accentValue ? R.color.accent : R.color.text);
        value.setTextColor(defaultValueColor);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 34 : 19);
        value.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        value.setFontFeatureSettings("tnum");
        value.setSingleLine(true);
        value.setIncludeFontPadding(false);
        row.addView(value);

        unit = new TextView(context);
        unit.setText(unitText);
        unit.setTextColor(context.getColor(R.color.muted));
        unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 16 : 11);
        unit.setSingleLine(true);
        unit.setPadding(Math.round((large ? 5 : 3) * d), 0, 0, 0);
        row.addView(unit);
    }

    public void setValue(CharSequence text) {
        value.setText(text);
    }

    /** Colore del valore; null = colore normale. */
    public void setValueColor(Integer color) {
        value.setTextColor(color != null ? color : defaultValueColor);
    }

    public void setUnit(CharSequence text) {
        unit.setText(text);
    }

    /** Stato del dato: colore dell'icona e livello di riempimento (0..1). */
    public void setStatus(int color, float level) {
        icon.setState(color, level, false);
    }

    /** Come {@link #setStatus} ma con il fulmine della ricarica (batteria). */
    public void setStatus(int color, float level, boolean charging) {
        icon.setState(color, level, charging);
    }

    /** Nessun dato: trattino e icona grigia vuota. */
    public void clear() {
        value.setText(R.string.no_value);
        value.setTextColor(defaultValueColor);
        icon.setState(getContext().getColor(R.color.muted), -1, false);
    }
}
