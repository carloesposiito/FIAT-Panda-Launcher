package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import it.panda.launcher.R;

/**
 * Strato trasparente sopra la foto dell'auto: una linea sottile da ogni casella dati fino a un punto
 * della carrozzeria (con un pallino). I punti si danno come frazione della foto (0..1), così seguono
 * la foto qualunque sia la dimensione dello schermo.
 */
public class CarLinesView extends View {

    private static final class Link {
        final View cell;
        final float ax, ay;

        Link(View cell, float ax, float ay) {
            this.cell = cell;
            this.ax = ax;
            this.ay = ay;
        }
    }

    private final List<Link> links = new ArrayList<>();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int[] me = new int[2], other = new int[2];
    private final float[] drawn = new float[9];
    private final RectF imageRect = new RectF();
    private ImageView image;
    private final ViewTreeObserver.OnGlobalLayoutListener relayout = this::invalidate;

    public CarLinesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * density);
        line.setColor(context.getColor(R.color.muted));
        line.setAlpha(150);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(context.getColor(R.color.accent));
    }

    /** La foto sulla quale cadono i punti. */
    public void setImage(ImageView image) {
        this.image = image;
    }

    /** Collega una casella a un punto della foto, dato come frazione della sua larghezza e altezza. */
    public void link(View cell, float fractionX, float fractionY) {
        links.add(new Link(cell, fractionX, fractionY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(relayout);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(relayout);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (image == null || image.getDrawable() == null || image.getWidth() == 0) {
            return;
        }
        // area occupata davvero dall'immagine (fitCenter lascia bordi vuoti)
        imageRect.set(0, 0, image.getDrawable().getIntrinsicWidth(), image.getDrawable().getIntrinsicHeight());
        image.getImageMatrix().mapRect(imageRect);
        getLocationInWindow(me);
        image.getLocationInWindow(other);
        float imgLeft = other[0] - me[0] + imageRect.left, imgTop = other[1] - me[1] + imageRect.top;

        for (Link l : links) {
            if (l.cell.getVisibility() != VISIBLE) {
                continue;
            }
            l.cell.getLocationInWindow(other);
            float cx = other[0] - me[0] + l.cell.getWidth() / 2f;
            float top = other[1] - me[1], bottom = top + l.cell.getHeight();
            float ax = imgLeft + l.ax * imageRect.width();
            float ay = imgTop + l.ay * imageRect.height();
            float sy = ay > (top + bottom) / 2f ? bottom : top; // si parte dal lato verso l'auto
            canvas.drawLine(cx, sy, ax, ay, line);
            canvas.drawCircle(ax, ay, 3.5f * density, dot);
        }
    }
}
