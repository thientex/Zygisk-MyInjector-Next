package com.jiqiu.configapp;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bound memory and coalesce repeated requests while rows are recycled quickly. */
final class AppIconLoader {
    private static final LruCache<String, Drawable> CACHE = new LruCache<>(128);
    private static final Map<String, List<WeakReference<ImageView>>> PENDING = new HashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static void bind(ImageView view, String pkg) {
        view.setTag(pkg);
        Drawable cached = CACHE.get(pkg);
        if (cached != null) { view.setImageDrawable(cached); return; }
        Context context = view.getContext().getApplicationContext();
        view.setImageDrawable(context.getPackageManager().getDefaultActivityIcon());
        List<WeakReference<ImageView>> waiting = PENDING.get(pkg);
        if (waiting != null) { waiting.add(new WeakReference<>(view)); return; }
        waiting = new ArrayList<>();
        waiting.add(new WeakReference<>(view));
        PENDING.put(pkg, waiting);
        WORKERS.execute(() -> {
            Drawable loaded;
            try { loaded = context.getPackageManager().getApplicationIcon(pkg); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                loaded = context.getPackageManager().getDefaultActivityIcon();
            }
            Drawable icon = loaded;
            MAIN.post(() -> {
                CACHE.put(pkg, icon);
                List<WeakReference<ImageView>> targets = PENDING.remove(pkg);
                if (targets == null) return;
                for (WeakReference<ImageView> ref : targets) {
                    ImageView target = ref.get();
                    if (target != null && pkg.equals(target.getTag())) target.setImageDrawable(icon);
                }
            });
        });
    }
    static void clear() { CACHE.evictAll(); }
}
