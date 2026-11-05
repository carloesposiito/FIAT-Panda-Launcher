package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
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
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Maschera: toglie l'alone sotto il centro, dissolvendolo, così non si vede il taglio in basso. */
    private final Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int racing;
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix rotate = new Matrix();
    private final RectF oval = new RectF();
    private final RectF innerOval = new RectF();
    private Shader sweep;
    private float sweepCx = -1, sweepCy = -1;
    private final int hot;
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
        racing = context.getColor(R.color.racing);
        hot = 0xFFFF8A3D; // arancio a metà scala: l'arco scalda con la velocità

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
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        knob.setColor(0xFFFFFFFF);
        knobCore.setColor(0xFF080A0D);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1f * density);
        ring.setColor(context.getColor(R.color.line));

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

    /** Imposta testi e spessori in base alla vista e restituisce il raggio dell'arco. */
    private float measureArc() {
        // testo e spessore proporzionati allo strumento (stessa misura per tachimetro e contagiri)
        float base = Math.min(getWidth() / 2f, getHeight() / 1.75f);
        number.setTextSize(base * 0.72f);
        unit.setTextSize(Math.max(10 * density, base * 0.17f));
        track.setStrokeWidth(Math.max(4 * density, base * 0.085f));
        red.setStrokeWidth(track.getStrokeWidth());
        fill.setStrokeWidth(track.getStrokeWidth());
        float stroke = track.getStrokeWidth();
        // l'arco aperto in basso è alto 1,5 raggi (sin 30° = 0,5); sotto resta lo spazio per l'unità
        float unitBelow = unit.descent() + 2 * density;
        return Math.min((getWidth() - stroke) / 2f - 1, (getHeight() - stroke - 2 - unitBelow) / 1.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = measureArc();
        float stroke = track.getStrokeWidth();
        float base = Math.min(getWidth() / 2f, getHeight() / 1.75f);
        float cx = getWidth() / 2f;
        float cy = stroke / 2f + radius + 1;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // alone rosso dietro lo strumento, come la retroilluminazione di un quadro
        // morbido: tre passaggi di opacità che arrivano a zero prima del bordo della vista, e dissolvenza verso il basso
        float haloR = Math.min(cx, cy);
        int rgb = racing & 0x00FFFFFF;
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        halo.setShader(new RadialGradient(cx, cy, haloR,
                new int[]{0x2A000000 | rgb, 0x12000000 | rgb, rgb},
                new float[]{0f, 0.6f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, haloR, halo);
        fade.setShader(new android.graphics.LinearGradient(0, cy, 0, getHeight(), 0xFF000000, 0x00000000,
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, cy, getWidth(), getHeight(), fade);
        canvas.restoreToCount(layer);

        // anello interno sottile: dà profondità allo strumento
        float ringR = radius - stroke * 1.9f;
        innerOval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawArc(innerOval, START, SWEEP, false, ring);

        canvas.drawArc(oval, START, SWEEP, false, track);
        if (redFrom > 0 && redFrom < max) {
            float from = START + SWEEP * redFrom / max;
            canvas.drawArc(oval, from, START + SWEEP - from, false, red);
        }

        // avvicinamento morbido al valore letto
        float goal = target != null ? target : 0f;
        shown += (goal - shown) * 0.25f;
        if (Math.abs(goal - shown) > max / 1000f) {
            postInvalidateOnAnimation();
        } else {
            shown = goal;
        }

        // tacche all'interno dell'arco: quelle già "passate" si accendono; una ogni due più lunga
        if (tickStep > 0) {
            float inner = radius - stroke / 2f - 3 * density;
            int count = Math.round(max / tickStep);
            for (int k = 0; k <= count; k++) {
                boolean major = k % 2 == 0;
                boolean passed = target != null && max * k / count <= shown + max / 400f;
                tick.setColor(passed ? 0xCCF4F6F8 : getContext().getColor(major ? R.color.muted : R.color.line_strong));
                tick.setStrokeWidth((major ? 1.8f : 1.2f) * density);
                double ang = Math.toRadians(START + SWEEP * k / count);
                float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                float len = (major ? 6f : 3.5f) * density * Math.max(1f, base / (60 * density));
                canvas.drawLine(cx + c * inner, cy + s * inner, cx + c * (inner - len), cy + s * (inner - len), tick);
            }
        }

        if (shown > max / 500f) {
            // colore che scalda lungo la scala: giallo, arancio, rosso
            if (sweep == null || sweepCx != cx || sweepCy != cy) {
                sweep = new SweepGradient(cx, cy, new int[]{accent, hot, crit}, new float[]{0f, 0.35f, SWEEP / 360f});
                rotate.setRotate(START, cx, cy);
                sweep.setLocalMatrix(rotate);
                sweepCx = cx;
                sweepCy = cy;
            }
            float sweepAngle = SWEEP * shown / max;
            fill.setShader(sweep);
            glow.setShader(sweep);
            glow.setStrokeWidth(stroke * 2.4f);
            glow.setAlpha(46);
            canvas.drawArc(oval, START, sweepAngle, false, glow);
            canvas.drawArc(oval, START, sweepAngle, false, fill);

            // pallino all'estremità dell'arco
            double end = Math.toRadians(START + sweepAngle);
            float kx = cx + (float) Math.cos(end) * radius, ky = cy + (float) Math.sin(end) * radius;
            canvas.drawCircle(kx, ky, stroke * 0.78f, knob);
            canvas.drawCircle(kx, ky, stroke * 0.42f, knobCore);
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
