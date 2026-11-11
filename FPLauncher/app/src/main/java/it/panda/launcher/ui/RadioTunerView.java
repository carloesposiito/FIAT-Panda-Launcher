package it.panda.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;
import java.util.Random;

import it.panda.launcher.R;

/**
 * Sintonizzatore della Home con l'OBD spento, disegnato come uno strumento del quadro: frequenza grande con
 * banda e unità ai lati, sotto una scala a nastro che scorre sotto una lancetta rossa ferma al centro (le tacche
 * sfumano verso i bordi), e in basso una forma d'onda sottile e speculare. La forma d'onda è simulata (non legge
 * l'audio) e si muove solo quando la radio trasmette una frequenza.
 */
public class RadioTunerView extends View {

    private static final float BAND_MIN = 87.5f;
    private static final float BAND_MAX = 108f;
    /** MHz visibili nella larghezza della vista. */
    private static final float SPAN_MHZ = 9f;
    private static final float IDLE_CENTER = 98f;
    private static final int BARS = 56;
    private static final long FRAME_MS = 33;

    /** Chiamato quando si scorre sulla scala: +1 stazione successiva, -1 precedente. */
    public interface StepListener {
        void onStep(int direction);
    }

    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Maschera dell'alone: lo dissolve in alto, dove il cerchio uscirebbe dalla vista con un taglio netto. */
    private final Paint haloFade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scaleLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private final float[] barSpeed = new float[BARS];
    private final float[] barPhase = new float[BARS];
    private final float density;
    private final int text, muted, lineStrong, racing, accent;

    /** Testo grande, banda ("FM"/"AM") e unità; banda e unità vuote se la radio è spenta. */
    private String bigText, bandText = "", unitText = "";
    /** Frequenza FM su cui centrare la scala, null se non c'è. */
    private Float targetMhz;
    private float shownMhz = IDLE_CENTER;
    private boolean playing;
    /** 0 = forma d'onda ferma, 1 = in movimento: passa dall'una all'altra con dolcezza. */
    private float energy;
    private StepListener stepListener;
    /** Scorrimento attivo (disattivato: la vista non reagisce al dito e il tocco va alla pagina). */
    private boolean swipeEnabled;
    private float downX, downY, dragPx;
    private boolean dragging;

    public RadioTunerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        text = context.getColor(R.color.text);
        muted = context.getColor(R.color.muted);
        lineStrong = context.getColor(R.color.line_strong);
        racing = context.getColor(R.color.racing);
        accent = context.getColor(R.color.accent);

        number.setTypeface(Fonts.bold(context));
        number.setTextAlign(Paint.Align.CENTER);
        number.setFontFeatureSettings("tnum");
        side.setTypeface(Fonts.bold(context));
        side.setLetterSpacing(0.08f);
        scaleLabel.setTypeface(Fonts.bold(context));
        scaleLabel.setTextAlign(Paint.Align.CENTER);
        scaleLabel.setFontFeatureSettings("tnum");
        tick.setStrokeCap(Paint.Cap.ROUND);
        baseLine.setStrokeWidth(1.2f * density);
        needle.setColor(racing);
        needle.setStrokeCap(Paint.Cap.ROUND);
        needleGlow.setColor(racing);
        needleGlow.setStrokeCap(Paint.Cap.ROUND);
        wave.setStrokeCap(Paint.Cap.ROUND);
        wave.setStrokeWidth(3f * density);

        Random r = new Random(7); // sempre lo stesso disegno, non casuale a ogni avvio
        for (int i = 0; i < BARS; i++) {
            barSpeed[i] = 2.2f + r.nextFloat() * 3.2f;
            barPhase[i] = r.nextFloat() * 6.283f;
        }
        bigText = context.getString(R.string.radio_idle);
    }

    /** @param frequency testo letto dalla radio (es. "98.5 MHz" o "1008 kHz"), null se non c'è nulla da mostrare */
    public void setFrequency(String frequency) {
        Float mhz = null;
        String big = getContext().getString(R.string.radio_idle), band = "", unit = "";
        if (frequency != null) {
            boolean khz = frequency.toLowerCase(Locale.ROOT).contains("khz");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(frequency);
            if (m.find()) {
                float v = Float.parseFloat(m.group().replace(',', '.'));
                if (!khz && v >= BAND_MIN - 0.5f && v <= BAND_MAX + 0.5f) {
                    mhz = Math.max(BAND_MIN, Math.min(BAND_MAX, v));
                    boolean oneDecimal = Math.abs(v * 10 - Math.round(v * 10)) < 0.01f;
                    big = String.format(Locale.ITALIAN, oneDecimal ? "%.1f" : "%.2f", v);
                    band = "FM";
                    unit = "MHz";
                } else {
                    big = m.group();
                    band = khz ? "AM" : "";
                    unit = khz ? "kHz" : "";
                }
            } else {
                big = frequency;
            }
        }
        bigText = big;
        bandText = band;
        unitText = unit;
        targetMhz = mhz;
        playing = frequency != null;
        invalidate();
    }

    public void setStepListener(StepListener listener) {
        this.stepListener = listener;
    }

    /** Con false la scala non si scorre e il tocco passa alla pagina (che apre l'app radio). */
    public void setSwipeEnabled(boolean enabled) {
        swipeEnabled = enabled;
        if (!enabled) {
            dragging = false;
            dragPx = 0;
            invalidate();
        }
    }

    /** La zona sensibile allo scorrimento è la fascia della scala a nastro. */
    private boolean inScale(float y) {
        return y > getHeight() * 0.48f && y < getHeight() * 0.8f;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!swipeEnabled || stepListener == null) {
            return false;
        }
        float slop = 12 * density;
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                if (!inScale(e.getY())) {
                    return false; // il tocco fuori dalla scala va alla pagina
                }
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(e.getY() - downY)) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    dragPx = Math.max(-getWidth() / 4f, Math.min(getWidth() / 4f, dx));
                    invalidate();
                }
                return true;
            }
            case android.view.MotionEvent.ACTION_UP: {
                float dx = e.getX() - downX;
                boolean step = dragging && Math.abs(dx) > 56 * density;
                boolean tap = !dragging && Math.abs(dx) < slop && Math.abs(e.getY() - downY) < slop;
                dragging = false;
                invalidate(); // la scala torna al centro
                if (step) {
                    stepListener.onStep(dx < 0 ? 1 : -1); // da destra a sinistra: stazione successiva
                } else if (tap && getParent() instanceof View) {
                    ((View) getParent()).performClick();
                }
                return true;
            }
            case android.view.MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            invalidate(); // l'animazione vive in onDraw: da nascosta si ferma da sola, qui riparte
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int rgb = racing & 0x00FFFFFF;
        halo.setShader(new RadialGradient(w / 2f, h * 0.24f, Math.max(1f, w * 0.46f),
                new int[]{0x30000000 | rgb, 0x12000000 | rgb, rgb}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        haloFade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        haloFade.setShader(new LinearGradient(0, 0, 0, h * 0.34f, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        int a = accent & 0x00FFFFFF;
        wave.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{a, 0xFF000000 | a, 0xFF000000 | a, a}, new float[]{0f, 0.28f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        int l = lineStrong & 0x00FFFFFF;
        baseLine.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{l, 0xFF000000 | l, 0xFF000000 | l, l}, new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), cx = w / 2f;
        boolean tuned = targetMhz != null;
        boolean animating = false;

        // ---------------------------------------------------------------- frequenza
        // alone rosso, come dietro al tachimetro, che sfuma anche verso l'alto
        int layer = canvas.saveLayer(0, 0, w, h, null);
        canvas.drawCircle(cx, h * 0.24f, w * 0.46f, halo);
        canvas.drawRect(0, 0, w, h * 0.34f, haloFade);
        canvas.restoreToCount(layer);
        float numSize = Math.min(h * 0.34f, w * 0.30f);
        if (bandText.isEmpty() && unitText.isEmpty()) {
            numSize *= 0.62f; // "Radio": scritta più discreta
        }
        number.setTextSize(numSize);
        float maxNum = w * 0.62f;
        float numW = number.measureText(bigText);
        if (numW > maxNum) {
            number.setTextSize(numSize * maxNum / numW);
            numW = maxNum;
        }
        number.setColor(playing ? text : muted);
        float baseY = h * 0.36f;
        canvas.drawText(bigText, cx, baseY, number);
        side.setTextSize(numSize * 0.2f);
        float gap = numSize * 0.14f;
        side.setTextAlign(Paint.Align.RIGHT);
        side.setColor(racing);
        canvas.drawText(bandText, cx - numW / 2f - gap, baseY, side);
        side.setTextAlign(Paint.Align.LEFT);
        side.setColor(muted);
        canvas.drawText(unitText, cx + numW / 2f + gap, baseY, side);

        // ---------------------------------------------------------------- scala a nastro
        float goal = tuned ? targetMhz : IDLE_CENTER;
        shownMhz += (goal - shownMhz) * 0.18f;
        if (Math.abs(goal - shownMhz) > 0.005f) {
            animating = true;
        } else {
            shownMhz = goal;
        }
        if (!dragging && Math.abs(dragPx) > 0.5f) {
            dragPx *= 0.72f; // dopo il rilascio la scala torna al centro
            animating = true;
        } else if (!dragging) {
            dragPx = 0f;
        }
        float px = w / SPAN_MHZ;
        float lineY = h * 0.66f;
        float dim = tuned ? 1f : 0.45f;
        float left = Math.max(0, cx + dragPx + (BAND_MIN - shownMhz) * px);
        float right = Math.min(w, cx + dragPx + (BAND_MAX - shownMhz) * px);
        baseLine.setAlpha(Math.round(255 * dim));
        canvas.drawLine(left, lineY, right, lineY, baseLine);
        scaleLabel.setTextSize(h * 0.055f);
        for (int t = 876; t <= 1080; t += 2) { // decimi di MHz: una tacca ogni 0,2
            float x = cx + dragPx + (t / 10f - shownMhz) * px;
            if (x < 0 || x > w) {
                continue;
            }
            float edge = Math.abs(x - cx) / (w / 2f);
            float alpha = (1f - edge * edge) * dim;
            boolean whole = t % 10 == 0, numbered = t % 20 == 0;
            boolean near = tuned && Math.abs(x - cx) < px * 0.55f;
            tick.setColor(near ? text : (whole ? muted : lineStrong));
            tick.setAlpha(Math.round(255 * alpha));
            tick.setStrokeWidth((numbered ? 2.2f : whole ? 1.6f : 1.1f) * density);
            float len = h * (numbered ? 0.09f : whole ? 0.06f : 0.032f);
            canvas.drawLine(x, lineY, x, lineY - len, tick);
            if (numbered) {
                scaleLabel.setColor(near ? text : muted);
                scaleLabel.setAlpha(Math.round(255 * alpha));
                canvas.drawText(String.valueOf(t / 10), x, lineY + scaleLabel.getTextSize() * 1.35f, scaleLabel);
            }
        }
        // lancetta ferma al centro: rossa con alone se sintonizzata, spenta altrimenti
        float top = lineY - h * 0.14f, bottom = lineY + 3 * density;
        if (tuned) {
            needleGlow.setAlpha(50);
            needleGlow.setStrokeWidth(9 * density);
            canvas.drawLine(cx, top, cx, bottom, needleGlow);
            needleGlow.setAlpha(70);
            needleGlow.setStrokeWidth(5 * density);
            canvas.drawLine(cx, top, cx, bottom, needleGlow);
        }
        needle.setColor(tuned ? racing : lineStrong);
        needle.setStrokeWidth(2.4f * density);
        canvas.drawLine(cx, top, cx, bottom, needle);
        float tri = 6 * density;
        triangle.rewind();
        triangle.moveTo(cx - tri, top - tri * 1.5f);
        triangle.lineTo(cx + tri, top - tri * 1.5f);
        triangle.lineTo(cx, top);
        triangle.close();
        canvas.drawPath(triangle, needle);

        // ---------------------------------------------------------------- forma d'onda
        energy += ((playing ? 1f : 0f) - energy) * 0.08f;
        if (playing || energy > 0.01f) {
            animating = true;
        } else {
            energy = 0f;
        }
        float waveY = h * 0.885f, maxHalf = h * 0.095f, minHalf = 1.2f * density;
        float time = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f;
        for (int i = 0; i < BARS; i++) {
            float x = (i + 0.5f) * w / BARS;
            float envelope = (float) Math.pow(Math.sin(Math.PI * (i + 0.5f) / BARS), 0.8);
            float own = 0.5f + 0.5f * (float) Math.sin(time * barSpeed[i] + barPhase[i]);
            float travelling = 0.5f + 0.5f * (float) Math.sin(time * 1.3f - i * 0.28f);
            float v = 0.55f * own + 0.45f * travelling;
            float half = minHalf + energy * envelope * (0.12f + 0.88f * v * v) * maxHalf;
            canvas.drawLine(x, waveY - half, x, waveY + half, wave);
        }

        if (animating) {
            postInvalidateDelayed(FRAME_MS);
        }
    }
}
