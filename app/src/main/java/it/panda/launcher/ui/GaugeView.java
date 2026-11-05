package it.panda.launcher.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Locale;

import it.panda.launcher.R;

/**
 * Strumento circolare come quelli del quadro: arco di 240° aperto in basso, tacche, zona rossa
 * facoltativa, valore al centro e unità nell'apertura in basso. Usato per velocità e giri.
 *
 * Nel layout: {@code app:gaugeMax="7000" app:gaugeRedFrom="6000" app:gaugeTickStep="1000"
 * app:gaugeUnit="rpm" app:gaugeRoundTo="10"}.
 */
public class GaugeView extends View {

    /** Grandezza minima del numero; negli strumenti grandi cresce col raggio. */
    public static final float NUMBER_SP = 32f;
    private static final float START = 150f;
    private static final float SWEEP = 240f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;
    private final int accent, crit;

    private final float max, redFrom, tickStep;
    private final int roundTo;
    private final String unitText;

    private Float target;
    private float shown;

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = context.getColor(R.color.accent);
        crit = context.getColor(R.color.crit);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GaugeView);
        max = a.getFloat(R.styleable.GaugeView_gaugeMax, 100f);
        redFrom = a.getFloat(R.styleable.GaugeView_gaugeRedFrom, 0f);
        tickStep = a.getFloat(R.styleable.GaugeView_gaugeTickStep, 0f);
        roundTo = Math.max(1, a.getInt(R.styleable.GaugeView_gaugeRoundTo, 1));
        String u = a.getString(R.styleable.GaugeView_gaugeUnit);
        unitText = u != null ? u : "";
        a.recycle();

        float stroke = 6 * density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF1D2128);
        red.set(track);
        red.setStrokeCap(Paint.Cap.BUTT);
        red.setColor(0x66E5534B);
        fill.set(track);
        tick.setStrokeWidth(1.2f * density);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(context.getColor(R.color.line_strong));

        number.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        number.setTextAlign(Paint.Align.CENTER);
        number.setColor(context.getColor(R.color.text));
        number.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, NUMBER_SP, getResources().getDisplayMetrics()));
        number.setFontFeatureSettings("tnum");
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setColor(context.getColor(R.color.muted));
        unit.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));
    }

    /** @param value valore da mostrare, null se non disponibile (trattino e arco vuoto) */
    public void setValue(Double value) {
        target = value != null ? (float) Math.min(max, Math.max(0, value)) : null;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // testo e spessore proporzionati allo strumento (stessa misura per tachimetro e contagiri)
        float base = Math.min(getWidth() / 2f, getHeight() / 1.75f);
        float minNumber = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, NUMBER_SP, getResources().getDisplayMetrics());
        number.setTextSize(Math.max(minNumber, base * 0.42f));
        unit.setTextSize(Math.max(12 * density, base * 0.15f));
        track.setStrokeWidth(Math.max(6 * density, base * 0.075f));
        red.setStrokeWidth(track.getStrokeWidth());
        fill.setStrokeWidth(track.getStrokeWidth());
        float stroke = track.getStrokeWidth();
        // l'arco aperto in basso è alto 1,5 raggi (sin 30° = 0,5); sotto resta lo spazio per l'unità
        float unitBelow = unit.descent() + 2 * density;
        float radius = Math.min((getWidth() - stroke) / 2f - 1, (getHeight() - stroke - 2 - unitBelow) / 1.5f);
        float cx = getWidth() / 2f;
        float cy = stroke / 2f + radius + 1;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawArc(oval, START, SWEEP, false, track);
        if (redFrom > 0 && redFrom < max) {
            float from = START + SWEEP * redFrom / max;
            canvas.drawArc(oval, from, START + SWEEP - from, false, red);
        }

        // tacche all'interno dell'arco; una ogni due più lunga
        if (tickStep > 0) {
            float inner = radius - stroke / 2f - 3 * density;
            int count = Math.round(max / tickStep);
            for (int k = 0; k <= count; k++) {
                double ang = Math.toRadians(START + SWEEP * k / count);
                float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                float len = (k % 2 == 0 ? 6f : 3f) * density * Math.max(1f, base / (60 * density));
                canvas.drawLine(cx + c * inner, cy + s * inner, cx + c * (inner - len), cy + s * (inner - len), tick);
            }
        }

        // avvicinamento morbido al valore letto
        float goal = target != null ? target : 0f;
        shown += (goal - shown) * 0.25f;
        if (Math.abs(goal - shown) > max / 1000f) {
            postInvalidateOnAnimation();
        } else {
            shown = goal;
        }
        if (shown > max / 500f) {
            fill.setColor(redFrom > 0 && shown >= redFrom ? crit : accent);
            canvas.drawArc(oval, START, SWEEP * shown / max, false, fill);
        }

        // valore centrato sul centro dell'arco, unità nell'apertura in basso
        String text = target == null ? getContext().getString(R.string.no_value)
                : String.format(Locale.ITALIAN, "%,d", Math.round(target / roundTo) * roundTo);
        float numberCenterOffset = -(number.ascent() + number.descent()) / 2f;
        canvas.drawText(text, cx, cy + numberCenterOffset, number);
        float unitY = Math.min(cy + radius * 0.5f + unit.getTextSize() * 0.35f, getHeight() - unit.descent() - 1);
        canvas.drawText(unitText, cx, unitY, unit);
    }
}
