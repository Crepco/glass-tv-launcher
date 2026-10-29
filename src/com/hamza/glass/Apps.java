package com.hamza.glass;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds launchable apps, renders their icons and persists the home-screen layout. */
final class Apps {
    static final String SETTINGS = "@settings";
    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final String[] DEF_DOCK = {
            "com.sony.dtv.tvx", "com.netflix.ninja", "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.android.vending", SETTINGS};
    private static final String[] DEF_FIRST = {"com.sony.dtv.tvx", "com.android.vending"};
    private static final String[] DEF_HIDDEN = {"com.spocky.projengmenu"};

    static final class Result {
        final List<AppEntry> dock = new ArrayList<>(), grid = new ArrayList<>();
        final Set<String> hidden = new HashSet<>();
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Map<String, AppEntry> cache = new HashMap<>();

    Apps(Context ctx) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences("home2", Context.MODE_PRIVATE);
    }

    synchronized void forget(String pkg) {
        cache.remove(pkg);
    }

    synchronized Result load(int iconPx) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, AppEntry> found = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                String pkg = ai.packageName;
                if (pkg.equals(ctx.getPackageName()) || pkg.equals(TV_SETTINGS) || found.containsKey(pkg)) continue;
                AppEntry e = entry(pkg);
                e.label = String.valueOf(ri.loadLabel(pm));
                e.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat).setClassName(pkg, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (e.icon == null) setIcon(e, loadIcon(pm, ri), iconPx);
                found.put(pkg, e);
            }
        }

        AppEntry st = entry(SETTINGS);
        st.label = "Settings";
        st.intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (st.icon == null) {
            Drawable d;
            try {
                d = pm.getApplicationIcon(TV_SETTINGS);
            } catch (PackageManager.NameNotFoundException x) {
                d = pm.getDefaultActivityIcon();
            }
            setIcon(st, d, iconPx);
        }
        found.put(SETTINGS, st);

        Result r = new Result();
        Set<String> used = new HashSet<>();
        for (String k : csv("dock", DEF_DOCK)) {
            AppEntry e = found.get(k);
            if (e != null && used.add(k) && r.dock.size() < HomeView.DOCK_MAX) r.dock.add(e);
        }
        for (String k : csv("order", DEF_FIRST)) {
            AppEntry e = found.get(k);
            if (e != null && used.add(k)) r.grid.add(e);
        }
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry e : found.values()) if (!used.contains(e.key)) rest.add(e);
        Collections.sort(rest, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        r.grid.addAll(rest);
        r.hidden.addAll(csv("hidden", DEF_HIDDEN));
        return r;
    }

    void save(List<AppEntry> dock, List<AppEntry> grid, Set<String> hidden) {
        prefs.edit()
                .putString("dock", join(dock))
                .putString("order", join(grid))
                .putString("hidden", TextUtils.join(",", hidden))
                .apply();
    }

    private AppEntry entry(String key) {
        AppEntry e = cache.get(key);
        if (e == null) {
            e = new AppEntry(key);
            cache.put(key, e);
        }
        return e;
    }

    private List<String> csv(String pref, String[] def) {
        String v = prefs.getString(pref, null);
        if (v == null) return Arrays.asList(def);
        if (v.isEmpty()) return Collections.emptyList();
        return Arrays.asList(v.split(","));
    }

    private static String join(List<AppEntry> list) {
        StringBuilder b = new StringBuilder();
        for (AppEntry e : list) {
            if (b.length() > 0) b.append(',');
            b.append(e.key);
        }
        return b.toString();
    }

    private static Drawable loadIcon(PackageManager pm, ResolveInfo ri) {
        try {
            int res = ri.getIconResource();
            if (res != 0) {
                Resources r = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                Drawable d = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d;
            }
        } catch (Exception ignored) {
            // fall through to the default lookup
        }
        return ri.loadIcon(pm);
    }

    /**
     * Adaptive icons and opaque square icons become full-bleed squircles like iOS;
     * icons with transparent shapes are kept as-is and later sit on a glass tile.
     */
    private static void setIcon(AppEntry e, Drawable d, int size) {
        Bitmap content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(content);
        boolean full;
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int pad = size / 4;
            Rect b = new Rect(-pad, -pad, size + pad, size + pad);
            if (a.getBackground() != null) { a.getBackground().setBounds(b); a.getBackground().draw(c); }
            if (a.getForeground() != null) {
                Bitmap fg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                a.getForeground().setBounds(b);
                a.getForeground().draw(new Canvas(fg));
                // The foreground layer is usually the logo by itself: use it directly when it is not full-bleed.
                if (coverage(fg) < 0.6f) e.glyph = glyph(fg, false, 0.55f);
                c.drawBitmap(fg, 0, 0, null);
                fg.recycle();
            }
            full = true;
        } else {
            d.setBounds(0, 0, size, size);
            d.draw(c);
            full = opaque(content, .5f, .05f) && opaque(content, .5f, .95f)
                    && opaque(content, .05f, .5f) && opaque(content, .95f, .5f)
                    && opaque(content, .2f, .2f) && opaque(content, .8f, .2f)
                    && opaque(content, .2f, .8f) && opaque(content, .8f, .8f);
            if (full) {
                // Zoom slightly so the squircle mask trims any rounded corners baked into the icon.
                content.eraseColor(0);
                int z = Math.round(size * .04f);
                d.setBounds(-z, -z, size + z, size + z);
                d.draw(c);
            }
        }
        if (e.glyph == null) e.glyph = glyph(content, full, 0.35f);
        e.accent = accent(content);
        if (full) {
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setShader(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            Path path = new Path();
            Gfx.squircle(path, new Matrix(), size / 2f, size / 2f, size / 2f, 0);
            new Canvas(out).drawPath(path, p);
            content.recycle();
            e.icon = out;
        } else {
            e.icon = content;
        }
        e.fullBleed = full;
    }

    private static float coverage(Bitmap b) {
        int n = b.getWidth() * b.getHeight();
        int[] px = new int[n];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        long sum = 0;
        for (int p : px) sum += p >>> 24;
        return sum / (255f * n);
    }

    /**
     * Builds a white, alpha-only version of an icon for the iOS 26 "Clear" look.
     * Full-bleed icons keep whatever stands out from their main background colour
     * (photo-like icons fall back to a soft luminance ghost); shaped icons keep their
     * silhouette faintly ({@code base}) with bright details in solid white.
     */
    private static Bitmap glyph(Bitmap src, boolean full, float base) {
        int w = src.getWidth(), h = src.getHeight(), n = w * h;
        int[] px = new int[n];
        src.getPixels(px, 0, w, 0, 0, w, h);
        float[] g = new float[n];

        int[] hist = new int[4096];
        int opaque = 0;
        for (int p : px) {
            if ((p >>> 24) > 200) {
                opaque++;
                hist[((p >> 20) & 0xF) << 8 | ((p >> 12) & 0xF) << 4 | ((p >> 4) & 0xF)]++;
            }
        }
        if (opaque == 0) {
            for (int i = 0; i < n; i++) g[i] = (px[i] >>> 24) / 255f;
            return white(g, w, h);
        }

        boolean done = false;
        if (full) {
            int bin = 0;
            for (int i = 1; i < 4096; i++) if (hist[i] > hist[bin]) bin = i;
            int br = (bin >> 8) * 16 + 8, bg = ((bin >> 4) & 0xF) * 16 + 8, bb = (bin & 0xF) * 16 + 8;
            double cov = 0;
            for (int i = 0; i < n; i++) {
                int p = px[i];
                double dr = ((p >> 16) & 255) - br, dg = ((p >> 8) & 255) - bg, db = (p & 255) - bb;
                float d = (float) (Math.sqrt(dr * dr + dg * dg + db * db) / 441.7);
                g[i] = smooth(0.10f, 0.30f, d) * (p >>> 24) / 255f;
                cov += g[i];
            }
            done = cov / n < 0.45;
        }
        if (!done) {
            float[] lum = new float[n], sample = new float[opaque];
            int k = 0;
            for (int i = 0; i < n; i++) {
                int p = px[i];
                lum[i] = (0.299f * ((p >> 16) & 255) + 0.587f * ((p >> 8) & 255) + 0.114f * (p & 255)) / 255f;
                if ((p >>> 24) > 200) sample[k++] = lum[i];
            }
            Arrays.sort(sample);
            float lo = sample[(int) (opaque * .05f)], hi = sample[Math.min(opaque - 1, (int) (opaque * .95f))];
            float span = Math.max(0.05f, hi - lo), b = full ? 0.12f : base;
            for (int i = 0; i < n; i++) {
                float t = Math.max(0f, Math.min(1f, (lum[i] - lo) / span));
                g[i] = (px[i] >>> 24) / 255f * (b + (1 - b) * t);
            }
        }
        return white(g, w, h);
    }

    /** Most prominent saturated hue in the icon, as a vivid opaque colour; 0 if there is none. */
    private static int accent(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        float[] weight = new float[36], sr = new float[36], sg = new float[36], sb = new float[36];
        float[] hsv = new float[3];
        float total = 0;
        for (int p : px) {
            if ((p >>> 24) < 200) continue;
            android.graphics.Color.colorToHSV(p, hsv);
            float wt = hsv[1] * hsv[1] * hsv[2];
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue;
            int bin = Math.min(35, (int) (hsv[0] / 10f));
            weight[bin] += wt;
            sr[bin] += wt * ((p >> 16) & 255);
            sg[bin] += wt * ((p >> 8) & 255);
            sb[bin] += wt * (p & 255);
            total += wt;
        }
        if (total < w * h * 0.02f) return 0;
        int best = 0;
        for (int i = 1; i < 36; i++) {
            // Neighbouring bins count too, so a gradient's hue isn't split in two.
            float a = weight[i] + 0.5f * (weight[(i + 35) % 36] + weight[(i + 1) % 36]);
            float b = weight[best] + 0.5f * (weight[(best + 35) % 36] + weight[(best + 1) % 36]);
            if (a > b) best = i;
        }
        int c = android.graphics.Color.rgb((int) (sr[best] / weight[best]), (int) (sg[best] / weight[best]), (int) (sb[best] / weight[best]));
        android.graphics.Color.colorToHSV(c, hsv);
        hsv[1] = Math.max(hsv[1], 0.65f);
        hsv[2] = Math.max(hsv[2], 0.80f);
        return android.graphics.Color.HSVToColor(hsv);
    }

    private static Bitmap white(float[] g, int w, int h) {
        int[] out = new int[g.length];
        for (int i = 0; i < g.length; i++) out[i] = Math.round(Math.max(0f, Math.min(1f, g[i])) * 255) << 24 | 0xFFFFFF;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(out, 0, w, 0, 0, w, h);
        return b;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static boolean opaque(Bitmap b, float fx, float fy) {
        int x = Math.min(b.getWidth() - 1, (int) (fx * b.getWidth()));
        int y = Math.min(b.getHeight() - 1, (int) (fy * b.getHeight()));
        return (b.getPixel(x, y) >>> 24) > 200;
    }
}
