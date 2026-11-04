package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Sfondo dietro l'auto 3D: alone scuro, pedana con anello ambra e ombra sotto l'auto.
 * La posizione della pedana arriva da CarRenderer, che la calcola dalla camera.
 */
public class StudioBackdropView extends View {

    private final Paint background = new Paint();
    private final Paint platform = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint platformEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix shadowMatrix = new Matrix();
    private final RadialGradient shadowGradient;

    private float cx = Float.NaN, cy, rx, ry;

    public StudioBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        platform.setColor(Color.rgb(0x14, 0x17, 0x1C));
        platformEdge.setStyle(Paint.Style.STROKE);
        platformEdge.setColor(Color.rgb(0x2A, 0x2F, 0x37));
        platformEdge.setStrokeWidth(2f);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.argb(140, 0xF2, 0xA3, 0x3A));
        ring.setStrokeWidth(2.5f);
        // gradiente di raggio 1, poi scalato sull'ellisse dell'ombra
        shadowGradient = new RadialGradient(0, 0, 1f,
                new int[]{Color.argb(215, 0, 0, 0), Color.argb(140, 0, 0, 0), Color.argb(0, 0, 0, 0)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        shadow.setShader(shadowGradient);
    }

    /** Chiamato da CarRenderer quando cambia la posizione della pedana sullo schermo. */
    public void setGround(float cx, float cy, float rx, float ry) {
        this.cx = cx;
        this.cy = cy;
        this.rx = rx;
        this.ry = ry;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            background.setShader(new RadialGradient(w / 2f, h * 0.6f, Math.max(w, h) * 0.7f,
                    new int[]{Color.rgb(0x1A, 0x1E, 0x25), Color.rgb(0x0F, 0x12, 0x16), Color.rgb(0x0A, 0x0C, 0x0F)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        if (Float.isNaN(cx) || rx <= 0 || ry <= 0) {
            return;
        }
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(oval, platform);
        canvas.drawOval(oval, platformEdge);

        float k = 0.96f;
        oval.set(cx - rx * k, cy - ry * k, cx + rx * k, cy + ry * k);
        canvas.drawOval(oval, ring);

        // ombra: ellisse più piccola al centro della pedana
        float srx = rx * 0.62f, sry = ry * 0.62f;
        shadowMatrix.setScale(srx, sry);
        shadowMatrix.postTranslate(cx, cy);
        shadowGradient.setLocalMatrix(shadowMatrix);
        oval.set(cx - srx, cy - sry, cx + srx, cy + sry);
        canvas.drawOval(oval, shadow);
    }
}
