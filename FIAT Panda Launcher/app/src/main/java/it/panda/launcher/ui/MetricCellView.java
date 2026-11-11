package it.panda.launcher.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.panda.launcher.R;

/**
 * Casella di un dato: icona (facoltativa) a sinistra, sopra l'etichetta piccola (facoltativa), sotto valore e unità.
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
        if (getPaddingLeft() == 0 && getPaddingRight() == 0) { // altrimenti vale quello del layout
            setPadding(padH, padV, padH, padV);
        }

        String labelText = a.getString(R.styleable.MetricCellView_label);
        String unitText = a.getString(R.styleable.MetricCellView_unit);
        int iconIndex = a.getInt(R.styleable.MetricCellView_icon, 0);
        boolean centered = a.getBoolean(R.styleable.MetricCellView_centered, false);
        boolean compact = a.getBoolean(R.styleable.MetricCellView_compact, false);
        boolean centerContent = a.getBoolean(R.styleable.MetricCellView_centerContent, false);
        String valueTemplate = a.getString(R.styleable.MetricCellView_valueTemplate);
        boolean accentValue = a.getBoolean(R.styleable.MetricCellView_accent, false);
        float valueSizePx = a.getDimension(R.styleable.MetricCellView_valueSize, -1f);
        int iconPx = a.getDimensionPixelSize(R.styleable.MetricCellView_iconSize, Math.round((large ? 40 : 24) * d));
        a.recycle();

        icon = new MetricIconView(context);
        icon.setType(MetricIconView.Type.values()[iconIndex]);
        icon.setVisibility(iconIndex == 0 ? GONE : VISIBLE);

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextColor(context.getColor(R.color.muted));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 15 : 11);
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);

        value = new TextView(context);
        value.setText(R.string.no_value);
        defaultValueColor = context.getColor(accentValue ? R.color.accent : R.color.text);
        value.setTextColor(defaultValueColor);
        if (valueSizePx > 0) {
            value.setTextSize(TypedValue.COMPLEX_UNIT_PX, valueSizePx);
        } else {
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 32 : large ? 34 : 19);
        }
        value.setTypeface(Fonts.bold(context));
        value.setFontFeatureSettings("tnum");
        value.setSingleLine(true);
        value.setIncludeFontPadding(false);

        if (valueTemplate != null) { // larghezza fissa, numero allineato a destra: icona e unità non si muovono
            value.setMinWidth((int) Math.ceil(value.getPaint().measureText(valueTemplate)));
            value.setGravity(Gravity.END);
        }

        unit = new TextView(context);
        unit.setText(unitText);
        unit.setTextColor(context.getColor(R.color.muted));
        if (valueSizePx > 0) {
            unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(11 * d, valueSizePx * 0.5f));
        } else {
            unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 15 : large ? 16 : 11);
        }
        unit.setSingleLine(true);
        unit.setPadding(Math.round((large ? 5 : 3) * d), 0, 0, 0);

        LayoutParams iconLp = new LayoutParams(iconPx, iconPx);
        iconLp.setMarginEnd(Math.round((compact ? 8 : large ? 14 : 8) * d));
        addView(icon, iconLp);

        // a destra: etichetta (se c'è) sopra, valore e unità sotto
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        if (centerContent) { // contenuto raggruppato al centro: niente spazio che si allarga
            setGravity(Gravity.CENTER);
            addView(texts, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        } else {
            addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        if (labelText != null) {
            texts.addView(label);
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setBaselineAligned(true);
        if (centered) {
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        texts.addView(row);
        row.addView(value);
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
