package it.panda.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le due immagini di un'auto: "Top" (vista dall'alto, nella Home) e "Side" (vista laterale, nella pagina dei dati).
 * Si importano da Pictures\MARCA_MODELLO\ della memoria interna o di una chiavetta USB e si copiano nella memoria
 * dell'app, così poi la chiavetta si può togliere.
 */
final class CarImages {

    enum Kind {
        /** Colonna centrale della Home, sopra il livello del serbatoio: verticale. */
        TOP("Top", "top.png", 700, 1200),
        /** Pagina dei dati OBD, con i valori ai lati: orizzontale. */
        SIDE("Side", "side.png", 1500, 960);

        final String importName;
        final String storedName;
        final int recommendedWidth;
        final int recommendedHeight;

        Kind(String importName, String storedName, int w, int h) {
            this.importName = importName;
            this.storedName = storedName;
            this.recommendedWidth = w;
            this.recommendedHeight = h;
        }
    }

    /** Lato lungo massimo dopo l'importazione: tiene bassa la memoria usata. */
    private static final int MAX_SIDE = 1600;
    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "webp"};

    private CarImages() {
    }

    /** Esito dell'importazione. */
    static final class Result {
        boolean folderFound;
        final List<Kind> imported = new ArrayList<>();
        final List<Kind> missing = new ArrayList<>();
    }

    static File file(Context c, String carId, Kind kind) {
        return new File(CarStore.imagesDir(c, carId), kind.storedName);
    }

    static boolean has(Context c, String carId, Kind kind) {
        return file(c, carId, kind).isFile();
    }

    /** Immagine importata, oppure null (si usa quella di serie). */
    static Bitmap load(Context c, String carId, Kind kind) {
        File f = file(c, carId, kind);
        return f.isFile() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** Cambia quando cambiano le immagini: chi le mostra le ricarica solo allora. */
    static String stamp(Context c, String carId) {
        return file(c, carId, Kind.TOP).lastModified() + "/" + file(c, carId, Kind.SIDE).lastModified();
    }

    static void delete(Context c, String carId, Kind kind) {
        file(c, carId, kind).delete();
    }

    static void deleteAll(Context c, String carId) {
        File dir = CarStore.imagesDir(c, carId);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    // ---------------------------------------------------------------- importazione

    /** Cerca Pictures\MARCA_MODELLO\ in ogni memoria e copia le immagini trovate. Va chiamata fuori dal thread principale. */
    static Result importFor(Context c, CarProfile car) {
        Result result = new Result();
        File folder = null;
        for (File root : storageRoots(c)) {
            folder = findFolder(new File(root, "Pictures"), car.folderName());
            if (folder == null) {
                folder = findFolder(root, car.folderName());
            }
            if (folder != null) {
                break;
            }
        }
        result.folderFound = folder != null;
        for (Kind kind : Kind.values()) {
            File source = findImage(folder, kind.importName);
            if (source != null && copyScaled(source, file(c, car.id, kind))) {
                result.imported.add(kind);
            } else {
                result.missing.add(kind);
            }
        }
        return result;
    }

    /** Memoria interna e volumi montati (chiavette USB, schede). */
    private static List<File> storageRoots(Context c) {
        List<File> roots = new ArrayList<>();
        addRoot(roots, Environment.getExternalStorageDirectory());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                for (StorageVolume v : sm.getStorageVolumes()) {
                    addRoot(roots, v.getDirectory());
                }
            }
        }
        File[] mounted = new File("/storage").listFiles();
        if (mounted != null) {
            for (File f : mounted) {
                String name = f.getName();
                if (!name.equals("self") && !name.equals("emulated")) {
                    addRoot(roots, f);
                }
            }
        }
        return roots;
    }

    private static void addRoot(List<File> roots, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        for (File r : roots) {
            if (r.getAbsolutePath().equals(dir.getAbsolutePath())) {
                return;
            }
        }
        roots.add(dir);
    }

    /** Sottocartella con quel nome, senza distinguere maiuscole e minuscole. */
    static File findFolder(File parent, String name) {
        File[] children = parent == null ? null : parent.listFiles();
        if (children == null) {
            return null;
        }
        for (File f : children) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    /** File con quel nome e un'estensione supportata, nell'ordine di EXTENSIONS (prima PNG). */
    static File findImage(File folder, String baseName) {
        File[] children = folder == null ? null : folder.listFiles();
        if (children == null) {
            return null;
        }
        for (String ext : EXTENSIONS) {
            for (File f : children) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).equals(baseName.toLowerCase(Locale.ROOT) + "." + ext)) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Fattore (potenza di 2) per portare il lato lungo entro il limite. */
    static int sampleSize(int width, int height, int maxSide) {
        int size = 1;
        int longest = Math.max(width, height);
        while (longest / size > maxSide) {
            size *= 2;
        }
        return size;
    }

    private static boolean copyScaled(File source, File target) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) {
            return false;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), opts);
        if (bitmap == null) {
            return false;
        }
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            return false;
        } finally {
            bitmap.recycle();
        }
    }
}
