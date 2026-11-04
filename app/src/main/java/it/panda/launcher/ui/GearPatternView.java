package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Schema del cambio visto dall'alto (R 1 3 5 / 2 4) con la marcia inserita evidenziata.
 * Disegnato in uno spazio 120x72 e scalato per stare nella vista.
 */
public class GearPatternView extends View {

    private static final float W = 120f, H = 72f, RADIUS = 11f;
    private static final String[] LABELS = {"R", "1", "2", "3", "4", "5"};
    /** Posizioni dei cerchi, nello stesso ordine di LABELS. */
    private static final float[][] POS = {{14, 14}, {44, 14}, {44, 58}, {74, 14}, {74, 58}, {104, 14}};

    private final Paint gate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int accent = Color.rgb(0xF2, 0xA3, 0x3A);
    private final int bg = Color.rgb(0x0A, 0x0C, 0x0F);
    private final int muted = Color.rgb(0x8B, 0x92, 0x9D);
    private final int line = Color.rgb(0x22, 0x27, 0x2F);

    /** 1-5 = marcia inserita; qualsiasi altro valore = nessuna evidenziata. */
    private int gear = -1;

    public GearPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gate.setColor(line);
        gate.setStyle(Paint.Style.STROKE);
        gate.setStrokeWidth(5f);
        gate.setStrokeCap(Paint.Cap.ROUND);
        discEdge.setStyle(Paint.Style.STROKE);
        discEdge.setStrokeWidth(2f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text.setTextSize(15f);
    }

    public void setGear(int gear) {
        if (this.gear != gear) {
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

        canvas.drawLine(14, 36, 104, 36, gate);
        canvas.drawLine(14, 14, 14, 36, gate);
        canvas.drawLine(44, 14, 44, 58, gate);
        canvas.drawLine(74, 14, 74, 58, gate);
        canvas.drawLine(104, 14, 104, 36, gate);

        Paint.FontMetrics fm = text.getFontMetrics();
        float textOffset = -(fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < LABELS.length; i++) {
            boolean on = LABELS[i].equals(String.valueOf(gear));
            disc.setColor(on ? accent : bg);
            discEdge.setColor(on ? accent : line);
            canvas.drawCircle(POS[i][0], POS[i][1], RADIUS, disc);
            canvas.drawCircle(POS[i][0], POS[i][1], RADIUS, discEdge);
            text.setColor(on ? bg : muted);
            canvas.drawText(LABELS[i], POS[i][0], POS[i][1] + textOffset, text);
        }
        canvas.restore();
    }
}
