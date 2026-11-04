package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Grafico del consumo istantaneo negli ultimi minuti: area ambra, linea tratteggiata sulla media
 * e punto sull'ultimo valore. Scala fissa 0-12 l/100 km.
 */
public class FuelChartView extends View {

    /** 40 campioni, uno ogni 15 secondi = 10 minuti. */
    public static final int CAPACITY = 40;
    private static final float MAX_VALUE = 12f;
    private static final float[] GRID = {4f, 8f, 12f};

    private final float[] values = new float[CAPACITY];
    private int count;
    private int start;
    private float average = Float.NaN;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint avg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path areaPath = new Path();

    public FuelChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        int accent = Color.rgb(0xF2, 0xA3, 0x3A);
        line.setColor(accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(Color.rgb(0x22, 0x27, 0x2F));
        grid.setStrokeWidth(1f);
        avg.setColor(Color.rgb(0x8B, 0x92, 0x9D));
        avg.setStyle(Paint.Style.STROKE);
        avg.setStrokeWidth(1f * d);
        avg.setPathEffect(new DashPathEffect(new float[]{4f * d, 4f * d}, 0));
        dot.setColor(accent);
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

    /** Consumo medio del viaggio; NaN = non disponibile (la linea non si disegna). */
    public void setAverage(float value) {
        average = value;
        invalidate();
    }

    public void clear() {
        count = 0;
        start = 0;
        average = Float.NaN;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        area.setShader(new LinearGradient(0, 0, 0, h,
                Color.argb(97, 0xF2, 0xA3, 0x3A), Color.argb(0, 0xF2, 0xA3, 0x3A), Shader.TileMode.CLAMP));
    }

    private float y(float v) {
        float h = getHeight();
        return h - 2 - Math.min(Math.max(v, 0f), MAX_VALUE) / MAX_VALUE * (h - 4);
    }

    /** I campioni più recenti stanno a destra. */
    private float x(int i) {
        return (i + (CAPACITY - count)) / (float) (CAPACITY - 1) * getWidth();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        for (float g : GRID) {
            float gy = Math.round(y(g)) + 0.5f;
            canvas.drawLine(0, gy, w, gy, grid);
        }
        if (!Float.isNaN(average)) {
            float ay = y(average);
            canvas.drawLine(0, ay, w, ay, avg);
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
        areaPath.lineTo(x(count - 1), getHeight());
        areaPath.lineTo(x(0), getHeight());
        areaPath.close();
        canvas.drawPath(areaPath, area);
        canvas.drawPath(linePath, line);

        float last = values[(start + count - 1) % CAPACITY];
        float r = 3f * getResources().getDisplayMetrics().density;
        canvas.drawCircle(x(count - 1) - r, y(last), r, dot);
    }
}
