package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import it.panda.launcher.R;

/**
 * Linee che collegano i dati alla zona dell'auto a cui si riferiscono (serbatoio, motore, batteria...).
 * Ogni linea parte dal bordo della casella del dato rivolto verso l'auto (alto, basso, sinistra o destra),
 * fa un breve tratto dritto e va fino al punto sulla foto, segnato da un cerchietto. I punti sono frazioni (0..1) dell'immagine dell'auto,
 * quindi restano al loro posto a qualunque dimensione. La vista non intercetta i tocchi.
 */
public class CalloutLinesView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int[] mine = new int[2];
    private final int[] other = new int[2];
    private final RectF rect = new RectF();

    private ImageView image;
    private View[] labels = new View[0];
    private float[][] anchors = new float[0][];
    private boolean[] sideStart = new boolean[0];

    public CalloutLinesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        int light = context.getColor(R.color.text);
        line.setColor(light);
        line.setAlpha(150);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.4f * density);
        line.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(light);
        ring.setAlpha(190);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.4f * density);
        core.setColor(context.getColor(R.color.accent));
        setClickable(false);
    }

    /**
     * @param image   foto dell'auto (stessa area di questa vista)
     * @param labels  caselle dei dati, una per linea
     * @param anchors per ogni casella {x, y}: punto sulla foto, frazioni 0..1
     * @param sideStart per ogni casella: true = la linea parte sempre dal lato (destro o sinistro),
     *                  false = dal lato piu' vicino al punto (alto, basso o laterale)
     */
    public void setup(ImageView image, View[] labels, float[][] anchors, boolean[] sideStart) {
        this.image = image;
        this.labels = labels;
        this.anchors = anchors;
        this.sideStart = sideStart;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable d = image != null ? image.getDrawable() : null;
        if (d == null) {
            return;
        }
        getLocationInWindow(mine);
        // area occupata dalla foto (fitCenter), in coordinate di questa vista
        rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        image.getImageMatrix().mapRect(rect);
        image.getLocationInWindow(other);
        rect.offset(other[0] - mine[0], other[1] - mine[1]);

        for (int i = 0; i < labels.length; i++) {
            View v = labels[i];
            if (!v.isShown() || v.getWidth() == 0) {
                continue;
            }
            v.getLocationInWindow(other);
            float left = other[0] - mine[0], top = other[1] - mine[1];
            float right = left + v.getWidth(), bottom = top + v.getHeight();
            float cx = (left + right) / 2f, cy = (top + bottom) / 2f;
            float ax = rect.left + rect.width() * anchors[i][0];
            float ay = rect.top + rect.height() * anchors[i][1];
            float dx = ax - cx, dy = ay - cy;
            float gap = 6 * density, stub = 16 * density;
            float sx, sy, ex, ey; // partenza sul bordo del dato e fine del tratto dritto
            if (!sideStart[i] && Math.abs(dy) > Math.abs(dx) * 0.6f) { // il punto sta sopra o sotto: si parte dal bordo alto/basso
                sx = cx;
                sy = dy > 0 ? bottom + gap : top - gap;
                ex = sx;
                ey = sy + (dy > 0 ? stub : -stub);
            } else { // il punto sta di lato: si parte dal bordo sinistro/destro, vicino all'icona o al testo
                sx = dx > 0 ? right + gap : left - gap;
                sy = cy;
                ex = sx + (dx > 0 ? stub : -stub);
                ey = sy;
            }
            canvas.drawLine(sx, sy, ex, ey, line);
            canvas.drawLine(ex, ey, ax, ay, line);
            canvas.drawCircle(ax, ay, 5 * density, ring);
            canvas.drawCircle(ax, ay, 2.2f * density, core);
        }
    }
}
