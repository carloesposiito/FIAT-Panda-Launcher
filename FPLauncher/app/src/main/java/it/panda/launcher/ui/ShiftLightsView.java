package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/**
 * Fila sottile di trattini romboidi inclinati come le luci di cambio marcia di una GT: spenta ai bassi regimi, poi si accende da
 * sinistra a destra, verde, giallo e rosso in prossimità del limite. Da spenta fa da separatore.
 */
public class ShiftLightsView extends View {

    private static final int SEGMENTS = 32;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int off, ok, amber, red;
    private float fraction;

    public ShiftLightsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        off = context.getColor(R.color.line_strong);
        ok = context.getColor(R.color.ok);
        amber = context.getColor(R.color.accent);
        red = context.getColor(R.color.racing);
    }

    /** 0..1: quanti segmenti sono accesi. */
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
        float skew = h * 0.7f; // inclinazione: ogni trattino è un parallelogramma
        float gap = getResources().getDisplayMetrics().density * 2.5f;
        float segW = (getWidth() - skew - gap * (SEGMENTS - 1)) / SEGMENTS;
        if (segW <= 0) {
            return;
        }
        int lit = Math.round(fraction * SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            float t = i / (float) (SEGMENTS - 1);
            int color = t < 0.5f ? ok : t < 0.78f ? amber : red;
            paint.setColor(i < lit ? color : off);
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
