package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/**
 * Strumento circolare come quelli del quadro: arco di 240° aperto in basso, tacche, zona rossa
 * facoltativa, valore al centro e unità nell'apertura in basso. Scala e unità si impostano da codice
 * con {@link #configure} e {@link #setUnit}.
 */
public class GaugeView extends View {

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

    private float max = 100f, redFrom = 0f, tickStep = 0f;
    private String unitText = "";

    private Float target;
    private float shown;

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = context.getColor(R.color.accent);
        crit = context.getColor(R.color.crit);

        float stroke = 6 * density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(context.getColor(R.color.line));
        red.set(track);
        red.setStrokeCap(Paint.Cap.BUTT);
        red.setColor(0x66FF3B3B);
        fill.set(track);
        tick.setStrokeWidth(1.2f * density);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(context.getColor(R.color.line_strong));

        number.setTypeface(Fonts.bold(context));
        number.setTextAlign(Paint.Align.CENTER);
        number.setColor(context.getColor(R.color.text));
        number.setFontFeatureSettings("tnum");
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setColor(context.getColor(R.color.muted));
    }

    /**
     * @param max      fondo scala
     * @param redFrom  da qui in su l'arco è rosso (0 = nessuna zona rossa)
     * @param tickStep distanza tra le tacche (0 = nessuna)
     */
    public void configure(float max, float redFrom, float tickStep) {
        this.max = Math.max(1f, max);
        this.redFrom = redFrom;
        this.tickStep = tickStep;
        postInvalidateOnAnimation();
    }

    public void setUnit(String unit) {
        this.unitText = unit != null ? unit : "";
        postInvalidateOnAnimation();
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
        number.setTextSize(base * 0.46f);
        unit.setTextSize(Math.max(10 * density, base * 0.17f));
        track.setStrokeWidth(Math.max(4 * density, base * 0.085f));
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
                : String.valueOf(Math.round(target));
        float numberCenterOffset = -(number.ascent() + number.descent()) / 2f;
        canvas.drawText(text, cx, cy + numberCenterOffset, number);
        float unitY = Math.min(cy + radius * 0.5f + unit.getTextSize() * 0.35f, getHeight() - unit.descent() - 1);
        canvas.drawText(unitText, cx, unitY, unit);
    }
}
