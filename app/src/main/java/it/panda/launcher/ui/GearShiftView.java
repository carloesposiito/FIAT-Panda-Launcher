package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
    private final int accent, bg, muted, line;

    /** null = sconosciuta, 0 = folle, 1-5. */
    private Integer gear;

    public GearShiftView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.accent);
        bg = context.getColor(R.color.bg);
        muted = context.getColor(R.color.muted);
        line = context.getColor(R.color.line_strong);
        gate.setColor(line);
        gate.setStyle(Paint.Style.STROKE);
        gate.setStrokeWidth(5f);
        gate.setStrokeCap(Paint.Cap.ROUND);
        gateActive.set(gate);
        gateActive.setColor(accent);
        discEdge.setStyle(Paint.Style.STROKE);
        discEdge.setStrokeWidth(2f);
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

        // corridoi della leva; la barra centrale si accende in folle
        boolean neutral = gear != null && gear == 0;
        canvas.drawLine(14, 36, 74, 36, neutral ? gateActive : gate);
        canvas.drawLine(14, 14, 14, 36, isOn(1) ? gateActive : gate);
        canvas.drawLine(14, 36, 14, 58, isOn(2) ? gateActive : gate);
        canvas.drawLine(44, 14, 44, 36, isOn(3) ? gateActive : gate);
        canvas.drawLine(44, 36, 44, 58, isOn(4) ? gateActive : gate);
        canvas.drawLine(74, 14, 74, 36, isOn(5) ? gateActive : gate);

        Paint.FontMetrics fm = text.getFontMetrics();
        float textOffset = -(fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < LABELS.length; i++) {
            boolean on = isOn(GEARS[i]);
            disc.setColor(on ? accent : bg);
            discEdge.setColor(on ? accent : line);
            canvas.drawCircle(POS[i][0], POS[i][1], RADIUS, disc);
            canvas.drawCircle(POS[i][0], POS[i][1], RADIUS, discEdge);
            text.setColor(on ? bg : muted);
            canvas.drawText(LABELS[i], POS[i][0], POS[i][1] + textOffset, text);
        }
        canvas.restore();
    }

    private boolean isOn(int g) {
        return gear != null && gear > 0 && gear == g;
    }
}
