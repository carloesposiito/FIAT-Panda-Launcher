package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import it.panda.launcher.R;

/**
 * Icone dei dati dell'auto, a contorno sottile e colorate secondo lo stato. Con la batteria in ricarica
 * si usa la versione col fulmine.
 */
public class MetricIconView extends View {

    public enum Type { NONE, WATER, OIL, BATTERY, FUEL }

    private Drawable normal, charging;
    private Type type = Type.NONE;
    private int color;
    /** 0..1, oppure negativo se il valore non c'è. */
    private float level = -1;
    private boolean isCharging;

    public MetricIconView(Context context) {
        this(context, null);
    }

    public MetricIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        color = context.getColor(R.color.muted);
    }

    public void setType(Type type) {
        this.type = type;
        normal = load(type, false);
        charging = type == Type.BATTERY ? load(type, true) : null;
        invalidate();
    }

    private Drawable load(Type t, boolean withBolt) {
        int res;
        switch (t) {
            case WATER:
                res = R.drawable.ic_line_thermo;
                break;
            case OIL:
                res = R.drawable.ic_line_oil;
                break;
            case BATTERY:
                res = withBolt ? R.drawable.ic_line_battery_charging : R.drawable.ic_line_battery;
                break;
            case FUEL:
                res = R.drawable.ic_line_fuel;
                break;
            default:
                return null;
        }
        return getContext().getDrawable(res).mutate();
    }

    /**
     * @param color    colore dello stato
     * @param level    riempimento 0..1 (negativo = nessun dato)
     * @param charging solo per la batteria: mostra il fulmine
     */
    public void setState(int color, float level, boolean charging) {
        this.color = color;
        this.level = Math.min(1f, level);
        this.isCharging = charging;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable icon = isCharging && charging != null ? charging : normal;
        if (icon == null) {
            return;
        }
        int w = getWidth(), h = getHeight();
        icon.setBounds(0, 0, w, h);
        if (level < 0) { // nessun dato: icona vuota e spenta
            icon.setTint(withAlpha(color, 150));
            icon.draw(canvas);
            return;
        }
        icon.setTint(color);
        icon.draw(canvas);
    }

    private static int withAlpha(int c, int alpha) {
        return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c));
    }
}
