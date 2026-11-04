package it.panda.launcher.car;

import android.content.Context;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.VelocityTracker;

import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.Filament;
import com.google.android.filament.LightManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.TransformManager;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.AssetLoader;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.gltfio.Gltfio;
import com.google.android.filament.gltfio.ResourceLoader;
import com.google.android.filament.gltfio.UbershaderProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Disegna l'auto 3D con Filament dentro una TextureView con sfondo trasparente.
 *
 * - l'auto gira da sola finché la rotazione automatica è attiva; se la si trascina a mano si ferma
 *   (lo comunica {@link Callback#onFreeRotation()}) e resta ferma finché non la si riattiva;
 * - trascinando in orizzontale la si ruota, in verticale si cambia l'altezza della camera;
 * - doppio tocco: torna alla vista 3/4;
 * - si disegna a circa 30 fotogrammi al secondo e ci si ferma del tutto con {@link #pause()}.
 *
 * Il modello deve essere un .glb con asse Y verso l'alto. Se il muso non guarda verso +X,
 * correggere {@link #MODEL_YAW_OFFSET}.
 */
public final class CarRenderer implements Choreographer.FrameCallback, UiHelper.RendererCallback {

    static {
        Filament.init();
        Gltfio.init();
    }

    /** Vista 3/4 di partenza (rotazione dell'auto in radianti). */
    public static final float VIEW_3_4 = -0.6f;

    /** Correzione se il modello scaricato è orientato diversamente (muso verso +X = 0). */
    private static final float MODEL_YAW_OFFSET = (float) (Math.PI / 2); // il muso del modello Panda 169 guarda verso +Z

    private static final float FOV_DEGREES = 26f;
    private static final float AUTO_SPEED = 0.25f;            // rad/s
    private static final long FRAME_INTERVAL_NS = 31_000_000L; // ~30 fps
    private static final float MIN_ELEVATION = 0.05f;
    private static final float MAX_ELEVATION = 0.60f;

    /** Avvisi verso l'Activity. */
    public interface Callback {
        /** L'utente ha ruotato l'auto a mano: la rotazione automatica va spenta. */
        void onFreeRotation();

        /** Posizione sullo schermo della pedana sotto l'auto (ellisse), per disegnarla dietro. */
        void onGroundProjected(float cx, float cy, float rx, float ry);

        void onModelError(String message);
    }

    private final TextureView textureView;
    private final Callback callback;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final GestureDetector gestures;
    private final float radiansPerPixel;

    private final Engine engine;
    private final Renderer renderer;
    private final Scene scene;
    private final View view;
    private final Camera camera;
    private final int cameraEntity;
    private final int sun;
    private final int rimLight;
    private final StudioEnvironment environment;
    private final UiHelper uiHelper;
    private final DisplayHelper displayHelper;
    private SwapChain swapChain;

    private UbershaderProvider materials;
    private AssetLoader assetLoader;
    private ResourceLoader resourceLoader;
    private FilamentAsset asset;

    // Misure del modello (dal bounding box)
    private float centerX, centerZ, bottomY, halfLength = 1.8f, halfHeight = 0.8f;

    // Stato del movimento
    private float yaw = VIEW_3_4;
    private float elevation = 0.2f;
    private float velocity;
    private float spin;
    private Float targetYaw;
    private boolean autoRotate = true;
    private boolean dragging;
    private boolean resumed;
    private long lastFrameNs;
    private float lastTouchX, lastTouchY;
    private VelocityTracker tracker;

    private int width = 1, height = 1;
    private float lastGroundCx = Float.NaN, lastGroundCy, lastGroundRx, lastGroundRy;

    public CarRenderer(Context context, TextureView textureView, String assetPath, Callback callback) {
        this.textureView = textureView;
        this.callback = callback;
        this.radiansPerPixel = 0.009f / context.getResources().getDisplayMetrics().density;

        engine = Engine.create();
        renderer = engine.createRenderer();
        scene = engine.createScene();
        view = engine.createView();
        cameraEntity = EntityManager.get().create();
        camera = engine.createCamera(cameraEntity);
        view.setScene(scene);
        view.setCamera(camera);

        // Sfondo trasparente: dietro si vede la pedana disegnata da StudioBackdropView.
        view.setBlendMode(View.BlendMode.TRANSLUCENT);
        view.setAntiAliasing(View.AntiAliasing.FXAA);
        Renderer.ClearOptions clear = new Renderer.ClearOptions();
        clear.clear = true;
        clear.clearColor = new float[]{0f, 0f, 0f, 0f};
        renderer.setClearOptions(clear);
        camera.setExposure(16f, 1f / 125f, 100f);

        environment = new StudioEnvironment(engine);
        scene.setIndirectLight(environment.light());

        sun = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.97f, 0.92f)
                .intensity(60000f)
                .direction(-0.4f, -1.0f, -0.6f)
                .castShadows(false)
                .build(engine, sun);
        scene.addEntity(sun);

        rimLight = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.74f, 0.82f, 1.0f)
                .intensity(35000f)
                .direction(0.6f, -0.4f, 0.8f)
                .castShadows(false)
                .build(engine, rimLight);
        scene.addEntity(rimLight);

        displayHelper = new DisplayHelper(context);
        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        uiHelper.setRenderCallback(this);
        uiHelper.setOpaque(false);
        uiHelper.attachTo(textureView);

        loadModel(context, assetPath);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                showView(VIEW_3_4);
                return true;
            }
        });
        textureView.setOnTouchListener((v, e) -> onTouch(e));
    }

    // ---------------------------------------------------------------- comandi pubblici

    public void showView(float viewYaw) {
        targetYaw = viewYaw;
        velocity = 0;
        spin = 0;
    }

    public void setAutoRotate(boolean enabled) {
        autoRotate = enabled;
        spin = 0;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public void resume() {
        if (resumed) {
            return;
        }
        resumed = true;
        lastFrameNs = 0;
        choreographer.postFrameCallback(this);
    }

    public void pause() {
        resumed = false;
        choreographer.removeFrameCallback(this);
    }

    public void destroy() {
        pause();
        uiHelper.detach();
        if (asset != null) {
            scene.removeEntities(asset.getEntities());
            assetLoader.destroyAsset(asset);
            asset = null;
        }
        if (resourceLoader != null) {
            resourceLoader.destroy();
        }
        if (assetLoader != null) {
            assetLoader.destroy();
        }
        if (materials != null) {
            materials.destroyMaterials();
            materials.destroy();
        }
        environment.destroy();
        engine.destroyEntity(sun);
        engine.destroyEntity(rimLight);
        EntityManager.get().destroy(sun);
        EntityManager.get().destroy(rimLight);
        engine.destroyRenderer(renderer);
        engine.destroyView(view);
        engine.destroyScene(scene);
        engine.destroyCameraComponent(cameraEntity);
        EntityManager.get().destroy(cameraEntity);
        engine.destroy();
    }

    // ---------------------------------------------------------------- caricamento modello

    private void loadModel(Context context, String path) {
        materials = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materials, EntityManager.get());
        resourceLoader = new ResourceLoader(engine);
        try (InputStream in = context.getAssets().open(path)) {
            byte[] bytes = readAll(in);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes);
            buffer.flip();

            asset = assetLoader.createAsset(buffer);
            if (asset == null) {
                callback.onModelError("File .glb non valido: " + path);
                return;
            }
            resourceLoader.loadResources(asset);
            asset.releaseSourceData();
            scene.addEntities(asset.getEntities());

            Box box = asset.getBoundingBox();
            float[] c = box.getCenter();
            float[] h = box.getHalfExtent();
            centerX = c[0];
            centerZ = c[2];
            bottomY = c[1] - h[1];
            halfLength = Math.max(h[0], h[2]);
            halfHeight = h[1];
        } catch (IOException e) {
            callback.onModelError("Modello non trovato: " + path);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        int n;
        while ((n = in.read(chunk)) > 0) {
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- ciclo di disegno

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!resumed) {
            return;
        }
        choreographer.postFrameCallback(this);
        if (lastFrameNs != 0 && frameTimeNanos - lastFrameNs < FRAME_INTERVAL_NS) {
            return; // limitiamo a ~30 fps per non scaldare l'autoradio
        }
        float dt = lastFrameNs == 0 ? 0.033f : Math.min(0.1f, (frameTimeNanos - lastFrameNs) / 1e9f);
        lastFrameNs = frameTimeNanos;

        updateMotion(dt);
        applyModelTransform();
        updateCamera();

        if (uiHelper.isReadyToRender() && swapChain != null && renderer.beginFrame(swapChain, frameTimeNanos)) {
            renderer.render(view);
            renderer.endFrame();
        }
    }

    private void updateMotion(float dt) {
        if (dragging) {
            return;
        }
        if (targetYaw != null) {
            float diff = wrap(targetYaw - yaw);
            yaw += diff * Math.min(1f, dt * 4f);
            if (Math.abs(diff) < 0.002f) {
                yaw = targetYaw;
                targetYaw = null;
            }
        } else if (Math.abs(velocity) > 0.02f) {
            // inerzia dopo un trascinamento veloce
            yaw += velocity * dt;
            velocity *= (float) Math.pow(0.05, dt);
        } else if (autoRotate) {
            spin = Math.min(1f, spin + dt * 0.5f); // parte dolcemente
            yaw += AUTO_SPEED * dt * spin;
        }
        yaw = wrap(yaw);
    }

    /** Rotazione attorno all'asse verticale + spostamento per avere l'auto centrata e appoggiata a terra. */
    private void applyModelTransform() {
        if (asset == null) {
            return;
        }
        TransformManager tm = engine.getTransformManager();
        int instance = tm.getInstance(asset.getRoot());
        float a = yaw + MODEL_YAW_OFFSET;
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float tx = -centerX, ty = -bottomY, tz = -centerZ;
        // matrice 4x4 per colonne: rotazione Y moltiplicata per la traslazione
        float[] m = {
                c, 0, -s, 0,
                0, 1, 0, 0,
                s, 0, c, 0,
                c * tx + s * tz, ty, -s * tx + c * tz, 1
        };
        tm.setTransform(instance, m);
    }

    private double cameraDistance() {
        double t = Math.tan(Math.toRadians(FOV_DEGREES / 2));
        double aspect = (double) width / height;
        return Math.max(halfLength * 1.4 / (t * aspect), halfHeight * 2.0 / t);
    }

    private float targetY() {
        return halfHeight * 0.78f;
    }

    private void updateCamera() {
        double d = cameraDistance();
        double ty = targetY();
        double ey = ty + d * Math.sin(elevation);
        double ez = d * Math.cos(elevation);
        camera.lookAt(0, ey, ez, 0, ty, 0, 0, 1, 0);
        projectGround(ey, ez, ty);
    }

    /** Calcola dove cade sullo schermo il cerchio a terra sotto l'auto e lo passa alla vista di sfondo. */
    private void projectGround(double ey, double ez, double ty) {
        double r = halfLength * 1.45;
        float[] center = project(0, 0, 0, ey, ez, ty);
        float[] right = project(r, 0, 0, ey, ez, ty);
        float[] near = project(0, 0, r, ey, ez, ty);
        float[] far = project(0, 0, -r, ey, ez, ty);
        float rx = Math.abs(right[0] - center[0]);
        float cy = (near[1] + far[1]) / 2f;
        float ry = Math.abs(near[1] - far[1]) / 2f;
        float cx = center[0];
        if (Float.isNaN(lastGroundCx) || Math.abs(cx - lastGroundCx) > 0.5f || Math.abs(cy - lastGroundCy) > 0.5f
                || Math.abs(rx - lastGroundRx) > 0.5f || Math.abs(ry - lastGroundRy) > 0.5f) {
            lastGroundCx = cx;
            lastGroundCy = cy;
            lastGroundRx = rx;
            lastGroundRy = ry;
            callback.onGroundProjected(cx, cy, rx, ry);
        }
    }

    /** Proiezione prospettica semplice per la camera che guarda lungo -Z dall'alto (nessun rollio). */
    private float[] project(double x, double y, double z, double ey, double ez, double ty) {
        // assi della camera
        double fy = ty - ey, fz = -ez;
        double fl = Math.sqrt(fy * fy + fz * fz);
        fy /= fl;
        fz /= fl;
        // destra = (1,0,0); su = destra × avanti
        double uy = -fz, uz = fy;
        double px = x, py = y - ey, pz = z - ez;
        double camX = px;
        double camY = py * uy + pz * uz;
        double camZ = py * fy + pz * fz; // profondità (positiva davanti)
        double t = Math.tan(Math.toRadians(FOV_DEGREES / 2));
        double aspect = (double) width / height;
        float sx = (float) (width / 2.0 + camX / (camZ * t * aspect) * width / 2.0);
        float sy = (float) (height / 2.0 - camY / (camZ * t) * height / 2.0);
        return new float[]{sx, sy};
    }

    private static float wrap(float a) {
        double twoPi = Math.PI * 2;
        double r = ((a + Math.PI) % twoPi + twoPi) % twoPi - Math.PI;
        return (float) r;
    }

    // ---------------------------------------------------------------- tocco

    private boolean onTouch(MotionEvent e) {
        gestures.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                lastTouchX = e.getX();
                lastTouchY = e.getY();
                velocity = 0;
                spin = 0;
                targetYaw = null;
                if (tracker != null) {
                    tracker.recycle();
                }
                tracker = VelocityTracker.obtain();
                tracker.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastTouchX;
                float dy = e.getY() - lastTouchY;
                lastTouchX = e.getX();
                lastTouchY = e.getY();
                if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                    callback.onFreeRotation();
                }
                yaw += dx * radiansPerPixel;
                elevation = Math.max(MIN_ELEVATION, Math.min(MAX_ELEVATION, elevation + dy * radiansPerPixel * 0.45f));
                if (tracker != null) {
                    tracker.addMovement(e);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracker != null) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    velocity = Math.max(-6f, Math.min(6f, tracker.getXVelocity() * radiansPerPixel));
                    tracker.recycle();
                    tracker = null;
                }
                dragging = false;
                return true;
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- superficie di disegno

    @Override
    public void onNativeWindowChanged(Surface surface) {
        if (swapChain != null) {
            engine.destroySwapChain(swapChain);
        }
        swapChain = engine.createSwapChain(surface, uiHelper.getSwapChainFlags());
        displayHelper.attach(renderer, textureView.getDisplay());
    }

    @Override
    public void onDetachedFromSurface() {
        displayHelper.detach();
        if (swapChain != null) {
            engine.destroySwapChain(swapChain);
            engine.flushAndWait();
            swapChain = null;
        }
    }

    @Override
    public void onResized(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        view.setViewport(new Viewport(0, 0, width, height));
        camera.setProjection(FOV_DEGREES, (double) width / height, 0.1, 100.0, Camera.Fov.VERTICAL);
        lastGroundCx = Float.NaN;
    }
}
