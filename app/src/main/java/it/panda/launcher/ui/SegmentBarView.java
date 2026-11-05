package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/** Barra a tacche inclinate, come un contagiri a LED: le ultime tacche sono la zona rossa. */
public class SegmentBarView extends View {

    private static final int SEGMENTS = 24;
    private static final float SKEW = 0.25f; // inclinazione, in frazione dell'altezza

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final int accent, crit, off, redOff;

    private float fraction;
    private float redFrom = 1f;

    public SegmentBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.accent);
        crit = context.getColor(R.color.crit);
        off = context.getColor(R.color.line);
        redOff = 0x55FF3B3B & 0x00FFFFFF | 0x33000000;
    }

    /** 0..1 */
    public void setFraction(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f != this.fraction) {
            this.fraction = f;
            invalidate();
        }
    }

    /** Da questa frazione (0..1) della scala le tacche sono rosse. */
    public void setRedFrom(float redFrom) {
        this.redFrom = redFrom;
        invalidate();
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
        int firstRed = Math.round(redFrom * SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            boolean red = i >= firstRed;
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
