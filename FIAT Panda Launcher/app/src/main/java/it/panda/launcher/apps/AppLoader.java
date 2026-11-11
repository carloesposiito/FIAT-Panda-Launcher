package it.panda.launcher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Legge in background l'elenco delle app installate, in ordine alfabetico. */
public final class AppLoader {

    private final Context app;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AppLoader(Context context) {
        this.app = context.getApplicationContext();
    }

    public void load(Consumer<List<AppEntry>> onLoaded) {
        executor.execute(() -> {
            PackageManager pm = app.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
            List<AppEntry> apps = new ArrayList<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(app.getPackageName())) {
                    continue; // non mostriamo il launcher stesso
                }
                apps.add(new AppEntry(
                        String.valueOf(ri.loadLabel(pm)),
                        new ComponentName(pkg, ri.activityInfo.name),
                        ri.loadIcon(pm)));
            }
            Collator collator = Collator.getInstance(Locale.ITALIAN);
            apps.sort((a, b) -> collator.compare(a.label, b.label));
            main.post(() -> onLoaded.accept(apps));
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
