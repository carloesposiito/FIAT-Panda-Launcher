package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Barra a tacche inclinate, come un contagiri a LED: le ultime tacche sono la zona rossa. */
public class SegmentBarView extends View {

    private static final int SEGMENTS = 30;
    private static final int RED_SEGMENTS = 3;
    private static final float SKEW = 0.25f; // inclinazione, in frazione dell'altezza

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final int accent = Color.rgb(0xF2, 0xA3, 0x3A);
    private final int crit = Color.rgb(0xE5, 0x53, 0x4B);
    private final int off = Color.rgb(0x22, 0x27, 0x2F);
    private final int redOff = Color.rgb(0x4A, 0x2A, 0x2E);

    private float fraction;

    public SegmentBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** 0..1 */
    public void setFraction(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f != this.fraction) {
            this.fraction = f;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float skew = h * SKEW;
        float gap = h * 0.28f;
        float segW = (getWidth() - skew - gap * (SEGMENTS - 1)) / SEGMENTS;
        if (segW <= 0) {
            return;
        }
        int lit = Math.round(fraction * SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            boolean red = i >= SEGMENTS - RED_SEGMENTS;
            boolean on = i < lit;
            paint.setColor(on ? (red ? crit : accent) : (red ? redOff : off));
            float x = i * (segW + gap);
            path.reset();
            path.moveTo(x + skew, 0);
            path.lineTo(x + skew + segW, 0);
            path.lineTo(x + segW, h);
            path.lineTo(x, h);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
