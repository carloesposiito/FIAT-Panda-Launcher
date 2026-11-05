package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/** Grafico dei giri nell'ultimo minuto: linea con area sfumata e punto finale. */
public class SparklineView extends View {

    /** 120 punti, uno ogni mezzo secondo = un minuto. */
    private static final int CAPACITY = 120;

    private final float[] values = new float[CAPACITY];
    private int count;
    private int start;
    private float max = 7000f;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private final int accent;

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        accent = context.getColor(R.color.accent);
        line.setColor(accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(context.getColor(R.color.line));
        grid.setStrokeWidth(1f);
        dot.setColor(accent);
    }

    /** Fondo scala del grafico (giri). */
    public void setMax(float max) {
        this.max = Math.max(1f, max);
        invalidate();
    }

    public void add(float value) {
        if (count < CAPACITY) {
            values[(start + count) % CAPACITY] = value;
            count++;
        } else {
            values[start] = value;
            start = (start + 1) % CAPACITY;
        }
        invalidate();
    }

    public void clear() {
        count = 0;
        start = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        area.setShader(new LinearGradient(0, 0, 0, h,
                Color.argb(77, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)), Shader.TileMode.CLAMP));
    }

    private float y(float v) {
        float h = getHeight();
        return h - 2 - Math.min(v, max) / max * (h - 4);
    }

    private float x(int i) {
        // i dati piu' recenti sono a destra
        return (i + (CAPACITY - count)) / (float) (CAPACITY - 1) * getWidth();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        for (float g = 2000f; g < max; g += 2000f) {
            float gy = Math.round(y(g)) + 0.5f;
            canvas.drawLine(0, gy, w, gy, grid);
        }
        if (count < 2) {
            return;
        }
        linePath.reset();
        for (int i = 0; i < count; i++) {
            float v = values[(start + i) % CAPACITY];
            if (i == 0) {
                linePath.moveTo(x(i), y(v));
            } else {
                linePath.lineTo(x(i), y(v));
            }
        }
        areaPath.set(linePath);
        areaPath.lineTo(x(count - 1), h);
        areaPath.lineTo(x(0), h);
        areaPath.close();
        canvas.drawPath(areaPath, area);
        canvas.drawPath(linePath, line);

        float last = values[(start + count - 1) % CAPACITY];
        float r = 3f * getResources().getDisplayMetrics().density;
        canvas.drawCircle(x(count - 1) - r, y(last), r, dot);
    }
}
