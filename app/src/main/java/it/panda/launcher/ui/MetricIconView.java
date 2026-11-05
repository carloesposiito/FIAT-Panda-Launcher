package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/**
 * Icone dei dati dell'auto disegnate in codice, così possono cambiare col valore:
 * il termometro si riempie con la temperatura, la batteria mostra la carica (e un fulmine quando
 * l'alternatore ricarica), la pompa il livello del carburante, il carico la lancetta.
 * Il colore è quello dello stato (verde, ambra, rosso...). Disegnate su una griglia 24×24.
 */
public class MetricIconView extends View {

    public enum Type { NONE, WATER, OIL, BATTERY, FUEL, AIR, LOAD }

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private Type type = Type.NONE;
    private int color;
    /** 0..1, oppure negativo se il valore non c'è. */
    private float level = -1;
    private boolean charging;

    public MetricIconView(Context context) {
        this(context, null);
    }

    public MetricIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        color = context.getColor(R.color.muted);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.7f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setType(Type type) {
        this.type = type;
        invalidate();
    }

    /**
     * @param color    colore dello stato
     * @param level    riempimento 0..1 (negativo = nessun dato)
     * @param charging solo per la batteria: disegna il fulmine
     */
    public void setState(int color, float level, boolean charging) {
        this.color = color;
        this.level = Math.min(1f, level);
        this.charging = charging;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (type == Type.NONE) {
            return;
        }
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        canvas.save();
        canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
        canvas.scale(scale, scale);
        stroke.setColor(color);
        fill.setColor(color);
        float lv = Math.max(0, level);
        switch (type) {
            case WATER:
                drawThermometer(canvas, lv);
                break;
            case OIL:
                drawOilCan(canvas);
                break;
            case BATTERY:
                drawBattery(canvas, lv);
                break;
            case FUEL:
                drawPump(canvas, lv);
                break;
            case AIR:
                drawAir(canvas);
                break;
            case LOAD:
                drawLoad(canvas, lv);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void drawThermometer(Canvas c, float lv) {
        r.set(9.6f, 2.5f, 14.4f, 16f);
        c.drawRoundRect(r, 2.4f, 2.4f, stroke);
        c.drawCircle(12, 18.2f, 3.4f, stroke);
        c.drawCircle(12, 18.2f, 2f, fill);
        if (level >= 0) {
            r.set(11.1f, 15f - 10.5f * lv, 12.9f, 18.2f);
            c.drawRect(r, fill);
        }
        c.drawLine(16.5f, 6f, 19f, 6f, stroke);
        c.drawLine(16.5f, 9.5f, 19f, 9.5f, stroke);
        c.drawLine(16.5f, 13f, 19f, 13f, stroke);
    }

    private void drawOilCan(Canvas c) {
        path.reset();
        path.moveTo(3, 11);
        path.lineTo(7, 11);
        path.lineTo(9, 9);
        path.lineTo(13, 9);
        path.lineTo(15, 11);
        path.lineTo(21, 8.5f);
        path.lineTo(15, 17);
        path.lineTo(5, 17);
        path.lineTo(5, 13);
        path.lineTo(3, 12.5f);
        path.close();
        c.drawPath(path, stroke);
        c.drawLine(10, 9, 10, 7, stroke);
        c.drawLine(8.5f, 7, 11.5f, 7, stroke);
        // goccia
        path.reset();
        path.moveTo(20.5f, 13);
        path.quadTo(22, 15.5f, 20.5f, 16.5f);
        path.quadTo(19, 15.5f, 20.5f, 13);
        c.drawPath(path, fill);
    }

    private void drawBattery(Canvas c, float lv) {
        r.set(2.5f, 7, 19.5f, 17.5f);
        c.drawRoundRect(r, 2, 2, stroke);
        r.set(19.5f, 10, 21.5f, 14.5f);
        c.drawRect(r, fill);
        if (level >= 0) {
            int alpha = charging ? 90 : 255;
            fill.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            r.set(4.5f, 9, 4.5f + 13 * lv, 15.5f);
            c.drawRect(r, fill);
            fill.setColor(color);
        }
        if (charging) {
            path.reset();
            path.moveTo(12.5f, 7.5f);
            path.lineTo(8.5f, 12.8f);
            path.lineTo(11.5f, 12.8f);
            path.lineTo(10f, 17f);
            path.lineTo(14.5f, 11.3f);
            path.lineTo(11.5f, 11.3f);
            path.close();
            c.drawPath(path, fill);
        }
    }

    private void drawPump(Canvas c, float lv) {
        r.set(3.5f, 3.5f, 13.5f, 20.5f);
        c.drawRoundRect(r, 1.5f, 1.5f, stroke);
        r.set(5.8f, 6, 11.2f, 11.5f);
        c.drawRect(r, stroke);
        if (level >= 0) {
            r.set(5.8f, 11.5f - 5.5f * lv, 11.2f, 11.5f);
            c.drawRect(r, fill);
        }
        path.reset();
        path.moveTo(13.5f, 9);
        path.lineTo(16.5f, 9);
        path.lineTo(17.5f, 10.5f);
        path.lineTo(17.5f, 16.5f);
        path.quadTo(17.5f, 18, 19, 18);
        path.quadTo(20.5f, 18, 20.5f, 16.5f);
        path.lineTo(20.5f, 8);
        path.lineTo(18, 5.5f);
        c.drawPath(path, stroke);
    }

    private void drawAir(Canvas c) {
        path.reset();
        path.moveTo(3, 8);
        path.lineTo(14, 8);
        path.quadTo(17.5f, 8, 17.5f, 5.5f);
        path.quadTo(17.5f, 3.5f, 15.5f, 3.5f);
        path.moveTo(3, 12);
        path.lineTo(18, 12);
        path.quadTo(21, 12, 21, 14.8f);
        path.quadTo(21, 17, 19, 17);
        path.moveTo(3, 16);
        path.lineTo(10.5f, 16);
        path.quadTo(13.5f, 16, 13.5f, 18.5f);
        path.quadTo(13.5f, 20.5f, 11.5f, 20.5f);
        c.drawPath(path, stroke);
    }

    private void drawLoad(Canvas c, float lv) {
        r.set(3, 6, 21, 24);
        c.drawArc(r, 180, 180, false, stroke);
        double a = Math.PI + Math.PI * lv;
        float x = 12 + (float) Math.cos(a) * 6.5f;
        float y = 15 + (float) Math.sin(a) * 6.5f;
        c.drawLine(12, 15, x, y, stroke);
        c.drawCircle(12, 15, 1.6f, fill);
        c.drawLine(3, 15, 5, 15, stroke);
        c.drawLine(19, 15, 21, 15, stroke);
    }
}
