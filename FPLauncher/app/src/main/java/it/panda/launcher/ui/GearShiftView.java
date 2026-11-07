package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/**
 * Schema del cambio della Panda 169 visto dall'alto (dal vecchio launcher, GearPatternView),
 * senza la retromarcia, che dall'OBD non si riconosce:
 * <pre>
 *   1   3   5
 *   |___|___|
 *   2   4
 * </pre>
 * La marcia inserita è evidenziata in ambra; in folle si accende la barra centrale.
 * Disegnato in uno spazio 88x72 e scalato per stare nella vista.
 */
public class GearShiftView extends View {

    private static final float W = 88f, H = 72f, RADIUS = 11f;
    private static final String[] LABELS = {"1", "2", "3", "4", "5"};
    private static final int[] GEARS = {1, 2, 3, 4, 5};
    /** Posizioni dei cerchi, nello stesso ordine di LABELS. */
    private static final float[][] POS = {{14, 14}, {14, 58}, {44, 14}, {44, 58}, {74, 14}};

    private final Paint gate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gateActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, bg, muted, line, discTop, accentLight;

    /** null = sconosciuta, 0 = folle, 1-5. */
    private Integer gear;

    public GearShiftView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.accent);
        bg = context.getColor(R.color.bg);
        muted = context.getColor(R.color.muted);
        line = context.getColor(R.color.line_strong);
        discTop = 0xFF1E2530; // riflesso in alto sui dischi spenti
        accentLight = 0xFFFFE27A; // riflesso sul disco acceso
        gate.setColor(line);
        gate.setStyle(Paint.Style.STROKE);
        gate.setStrokeWidth(4f);
        gate.setStrokeCap(Paint.Cap.ROUND);
        gateActive.set(gate);
        gateActive.setColor(accent);
        discEdge.setStyle(Paint.Style.STROKE);
        discEdge.setStrokeWidth(1.6f);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Fonts.bold(context));
        text.setTextSize(15f);
    }

    /** L'altezza segue la larghezza (proporzioni del disegno) quando non è fissata dal layout. */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.EXACTLY && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(w, Math.round(w * H / W));
        } else {
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    public void setGear(Integer gear) {
        if (!java.util.Objects.equals(gear, this.gear)) {
            this.gear = gear;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth() / W, getHeight() / H);
        if (scale <= 0) {
            return;
        }
        canvas.save();
        canvas.translate((getWidth() - W * scale) / 2f, (getHeight() - H * scale) / 2f);
        canvas.scale(scale, scale);

        // corridoi della leva; la barra centrale si accende in folle, quello della marcia inserita ha un alone
        boolean neutral = gear != null && gear == 0;
        drawGate(canvas, 14, 36, 74, 36, neutral);
        drawGate(canvas, 14, 14, 14, 36, isOn(1));
        drawGate(canvas, 14, 36, 14, 58, isOn(2));
        drawGate(canvas, 44, 14, 44, 36, isOn(3));
        drawGate(canvas, 44, 36, 44, 58, isOn(4));
        drawGate(canvas, 74, 14, 74, 36, isOn(5));

        Paint.FontMetrics fm = text.getFontMetrics();
        float textOffset = -(fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < LABELS.length; i++) {
            boolean on = isOn(GEARS[i]);
            float x = POS[i][0], y = POS[i][1];
            if (on) { // alone dietro al disco acceso
                disc.setShader(null);
                disc.setColor(accent);
                disc.setAlpha(40);
                canvas.drawCircle(x, y, RADIUS + 5f, disc);
                disc.setAlpha(70);
                canvas.drawCircle(x, y, RADIUS + 2.5f, disc);
            }
            // disco con riflesso in alto a sinistra
            disc.setShader(new RadialGradient(x - RADIUS * 0.35f, y - RADIUS * 0.45f, RADIUS * 1.5f,
                    on ? accentLight : discTop, on ? accent : bg, Shader.TileMode.CLAMP));
            disc.setAlpha(255);
            canvas.drawCircle(x, y, RADIUS, disc);
            disc.setShader(null);
            discEdge.setColor(on ? 0xFFFFF3C2 : line);
            canvas.drawCircle(x, y, RADIUS, discEdge);
            text.setColor(on ? bg : muted);
            canvas.drawText(LABELS[i], x, y + textOffset, text);
        }
        canvas.restore();
    }

    private void drawGate(Canvas canvas, float x1, float y1, float x2, float y2, boolean active) {
        if (active) {
            glow.setColor(accent);
            glow.setAlpha(60);
            glow.setStrokeWidth(9f);
            canvas.drawLine(x1, y1, x2, y2, glow);
        }
        canvas.drawLine(x1, y1, x2, y2, active ? gateActive : gate);
    }

    private boolean isOn(int g) {
        return gear != null && gear > 0 && gear == g;
    }
}
