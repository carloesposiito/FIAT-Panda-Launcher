package it.panda.launcher.car;

import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Illuminazione "da showroom" generata in codice: una cubemap con cielo grigio scuro e alcuni
 * pannelli luminosi (softbox). Sulla vernice nera sono i riflessi di questi pannelli a far
 * capire la forma dell'auto, quindi è la parte che conta di più per l'aspetto finale.
 * Per cambiare l'atmosfera basta modificare {@link #radiance}.
 */
final class StudioEnvironment {

    private static final int SIZE = 128;
    /** Luminosità complessiva dell'ambiente; va tarata insieme all'esposizione della camera. */
    private static final float INTENSITY = 28000f;

    private final Engine engine;
    private final Texture cubemap;
    private final IndirectLight light;

    StudioEnvironment(Engine engine) {
        this.engine = engine;
        int levels = 1 + (int) Math.floor(Math.log(SIZE) / Math.log(2));
        cubemap = new Texture.Builder()
                .width(SIZE)
                .height(SIZE)
                .levels(levels)
                .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
                .format(Texture.InternalFormat.R11F_G11F_B10F)
                .build(engine);

        int faceBytes = SIZE * SIZE * 3 * 4;
        ByteBuffer buffer = ByteBuffer.allocateDirect(faceBytes * 6).order(ByteOrder.nativeOrder());
        FloatBuffer floats = buffer.asFloatBuffer();
        float[] dir = new float[3];
        float[] rgb = new float[3];
        for (int face = 0; face < 6; face++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float u = 2f * (x + 0.5f) / SIZE - 1f;
                    float v = 2f * (y + 0.5f) / SIZE - 1f;
                    faceDirection(face, u, v, dir);
                    radiance(dir, rgb);
                    floats.put(rgb);
                }
            }
        }
        int[] offsets = new int[6];
        for (int i = 0; i < 6; i++) {
            offsets[i] = i * faceBytes;
        }
        Texture.PixelBufferDescriptor pixels =
                new Texture.PixelBufferDescriptor(buffer, Texture.Format.RGB, Texture.Type.FLOAT);
        // Filament calcola da solo i livelli sfocati usati dai materiali più opachi.
        cubemap.generatePrefilterMipmap(engine, pixels, offsets, new Texture.PrefilterOptions());

        light = new IndirectLight.Builder()
                .reflections(cubemap)
                .irradiance(1, new float[]{0.16f, 0.16f, 0.18f})
                .intensity(INTENSITY)
                .build(engine);
    }

    IndirectLight light() {
        return light;
    }

    void destroy() {
        engine.destroyIndirectLight(light);
        engine.destroyTexture(cubemap);
    }

    /** Direzione (normalizzata) del texel (u, v) della faccia indicata, convenzione cubemap OpenGL. */
    private static void faceDirection(int face, float u, float v, float[] out) {
        float x, y, z;
        switch (face) {
            case 0: x = 1; y = -v; z = -u; break;   // +X
            case 1: x = -1; y = -v; z = u; break;   // -X
            case 2: x = u; y = 1; z = v; break;     // +Y
            case 3: x = u; y = -1; z = -v; break;   // -Y
            case 4: x = u; y = -v; z = 1; break;    // +Z
            default: x = -u; y = -v; z = -1; break; // -Z
        }
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        out[0] = x / len;
        out[1] = y / len;
        out[2] = z / len;
    }

    /** Colore della luce che arriva dalla direzione d: cielo, pavimento e pannelli luminosi. */
    private static void radiance(float[] d, float[] out) {
        float x = d[0], y = d[1], z = d[2];
        float c;
        if (y >= 0) {
            c = 0.06f + 0.26f * (float) Math.pow(y, 0.7);   // dall'orizzonte al soffitto
        } else {
            c = 0.04f - 0.03f * Math.min(1f, -y);            // pavimento scuro
        }
        float r = c, g = c, b = c * 1.08f;

        if (y > 0.92f) {                                      // grande softbox sopra
            r = g = b = 3.0f;
        }
        if (z < -0.85f && Math.abs(y - 0.25f) < 0.07f) {      // striscia luminosa dietro
            r = g = b = 4.0f;
        }
        if (z > 0.85f && Math.abs(y - 0.18f) < 0.05f) {       // striscia luminosa davanti
            r = g = b = 2.5f;
        }
        if (x < -0.92f && y > -0.1f && y < 0.6f && Math.abs(z) < 0.12f) { // colonna a sinistra
            r = g = b = 3.5f;
        }
        float warm = (0.75f * x + 0.2f * y + 0.55f * z) / 0.9513f; // luce calda di lato
        if (warm > 0.96f) {
            r = 2.2f;
            g = 1.6f;
            b = 1.1f;
        }
        out[0] = r;
        out[1] = g;
        out[2] = b;
    }
}
