package it.panda.launcher.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.panda.launcher.R;

/**
 * Mini grafico del consumo (l/100 km) nell'ultima mezz'ora: area ambra, griglia leggera ogni 5 l/100,
 * linea tratteggiata della media e punto sull'ultimo valore. I tratti da fermi restano vuoti.
 */
public class ConsumptionChartView extends View {

    /** Il grafico piccolo mostra almeno 30 punti (5 minuti), poi si allarga fino alla mezz'ora. */
    private static final int MIN_SLOTS = 30;
    /** Con l'asse del tempo la scala è sempre la mezz'ora intera: 180 punti da 10 s. */
    private static final int FULL_SLOTS = 180;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint gridText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean timeAxis;
    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private final float density;
    private final int accent;

    private List<Double> points = new ArrayList<>();
    private Double average;
    private int lastHeight;

    public ConsumptionChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = context.getColor(R.color.accent);
        float sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, getResources().getDisplayMetrics());
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ConsumptionChartView);
        timeAxis = a.getBoolean(R.styleable.ConsumptionChartView_timeAxis, false);
        a.recycle();
        float big = timeAxis ? 1.25f : 1f;

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * density * big);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(accent);
        grid.setColor(context.getColor(R.color.line));
        grid.setStrokeWidth(1);
        gridText.setColor(context.getColor(R.color.line_strong));
        gridText.setTextSize(10 * sp * big);
        avgLine.setStyle(Paint.Style.STROKE);
        avgLine.setStrokeWidth(1.2f * density);
        avgLine.setColor(context.getColor(R.color.muted));
        avgLine.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));
        avgText.setColor(context.getColor(R.color.muted));
        avgText.setTextSize(11 * sp * big);
        avgText.setTextAlign(Paint.Align.RIGHT);
        dot.setColor(accent);
        timeText.setColor(context.getColor(R.color.muted));
        timeText.setTextSize(11 * sp * big);
        timeText.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * @param points  consumo ogni 10 s, dal più vecchio (null = fermi)
     * @param average media da disegnare tratteggiata, oppure null
     */
    public void setData(List<Double> points, Double average) {
        this.points = points != null ? points : new ArrayList<>();
        this.average = average;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float top = 4 * density;
        // con l'asse del tempo si lascia spazio sotto per le etichette
        float bottom = timeAxis ? h - timeText.getTextSize() - 6 * density : h - 2 * density;
        if (h != lastHeight) {
            lastHeight = (int) h;
            area.setShader(new LinearGradient(0, top, 0, bottom,
                    (accent & 0x00FFFFFF) | 0x59000000, accent & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        // scala: da 0 al massimo arrotondato ai 5 l/100 (almeno 10)
        double max = 10;
        for (Double p : points) {
            if (p != null) {
                max = Math.max(max, p);
            }
        }
        if (average != null) {
            max = Math.max(max, average);
        }
        max = Math.ceil(max / 5.0) * 5.0;

        for (int v = 5; v < max; v += 5) {
            float y = y(v, max, top, bottom);
            canvas.drawLine(0, y, w, y, grid);
            canvas.drawText(String.valueOf(v), 0, y - 2 * density, gridText);
        }
        canvas.drawLine(0, bottom, w, bottom, grid);

        List<Double> points = this.points;
        int n = points.size();
        int slots = timeAxis ? FULL_SLOTS : Math.max(MIN_SLOTS, n);
        if (timeAxis) {
            // -30, -20, -10 minuti e "ora" sul bordo destro
            String[] labels = {"-30 min", "-20", "-10", "ora"};
            for (int i = 0; i < labels.length; i++) {
                float x = w * i / 3f;
                timeText.setTextAlign(i == 0 ? Paint.Align.LEFT : i == 3 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                canvas.drawText(labels[i], x, h - 2 * density, timeText);
                if (i > 0 && i < 3) {
                    canvas.drawLine(x, top, x, bottom, grid);
                }
            }
        }
        float step = w / (slots - 1);
        float x0 = w - (n - 1) * step; // i punti sono allineati a destra: il più recente sul bordo
        linePath.reset();
        areaPath.reset();
        boolean open = false;
        float lastX = 0, lastY = 0, segStartX = 0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Double p = points.get(i);
            float x = x0 + i * step;
            if (p == null) {
                if (open) {
                    areaPath.lineTo(lastX, bottom);
                    areaPath.lineTo(segStartX, bottom);
                    areaPath.close();
                }
                open = false;
                continue;
            }
            float y = y(p, max, top, bottom);
            if (!open) {
                linePath.moveTo(x, y);
                areaPath.moveTo(x, y);
                segStartX = x;
                open = true;
            } else {
                linePath.lineTo(x, y);
                areaPath.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
            any = true;
        }
        if (open) {
            areaPath.lineTo(lastX, bottom);
            areaPath.lineTo(segStartX, bottom);
            areaPath.close();
        }
        canvas.drawPath(areaPath, area);
        canvas.drawPath(linePath, line);
        if (any) {
            canvas.drawCircle(lastX, lastY, 3.5f * density, dot);
        }

        if (average != null) {
            float y = y(average, max, top, bottom);
            canvas.drawLine(0, y, w, y, avgLine);
            canvas.drawText(String.format(Locale.ITALIAN, "media %.1f", average), w, y - 3 * density, avgText);
        }
    }

    private static float y(double v, double max, float top, float bottom) {
        return (float) (bottom - v / max * (bottom - top));
    }
}
