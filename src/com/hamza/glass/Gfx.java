package com.hamza.glass;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** Shapes, wallpaper and the frosted "glass" source bitmap. */
final class Gfx {
    private Gfx() {}

    /** Unit superellipse (n = 5), close to the iOS continuous-corner icon shape. */
    private static final Path UNIT = new Path();

    static {
        final int n = 160;
        final double e = 2.0 / 5.0;
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n, c = Math.cos(t), s = Math.sin(t);
            float x = (float) (Math.signum(c) * Math.pow(Math.abs(c), e));
            float y = (float) (Math.signum(s) * Math.pow(Math.abs(s), e));
            if (i == 0) UNIT.moveTo(x, y); else UNIT.lineTo(x, y);
        }
        UNIT.close();
    }

    static void squircle(Path out, Matrix m, float cx, float cy, float half, float rotDeg) {
        m.setScale(half, half);
        if (rotDeg != 0) m.postRotate(rotDeg);
        m.postTranslate(cx, cy);
        UNIT.transform(m, out);
    }

    /**
     * Dusk mountain landscape. Returns {background, foreground}: the nearest ridge is a
     * separate layer so it can be drawn over the clock (the iOS "depth effect").
     */
    static Bitmap[] mountains(int w, int h) {
        Random rnd = new Random(11);
        Bitmap back = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(back);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        p.setShader(new LinearGradient(0, 0, 0, h * .66f,
                new int[]{0xFF071A45, 0xFF2A2B7E, 0xFF7A3192, 0xFFE0467A, 0xFFFF9A52},
                new float[]{0f, .32f, .58f, .82f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // Setting sun glowing just behind the far ridges.
        p.setShader(new RadialGradient(w * .38f, h * .60f, w * .42f,
                new int[]{0xCCFFE2A8, 0x66FFA060, 0x00FF7A50}, new float[]{0f, .3f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * .38f, h * .60f, w * .045f,
                new int[]{0xFFFFF4DC, 0xE6FFE0A8, 0x00FFD08A}, new float[]{0f, .6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(w * .38f, h * .60f, w * .045f, p);
        p.setShader(null);

        float sc = w / 1920f;
        for (int i = 0; i < 150; i++) {
            float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h * .45f;
            float fade = 1f - y / (h * .45f);
            p.setColor(Color.argb((int) (30 + rnd.nextFloat() * 140 * fade), 255, 255, 255));
            c.drawCircle(x, y, (0.6f + rnd.nextFloat() * 1.3f) * sc, p);
        }

        ridge(c, p, rnd, w, h, .60f, .17f, .42f, 0xFFE58A9A, 0xFFC0638F, 0x90FFE0B0, -1);
        ridge(c, p, rnd, w, h, .67f, .18f, .44f, 0xFFB05A93, 0xFF8A4588, 0x70FFC8A0, -1);
        ridge(c, p, rnd, w, h, .74f, .18f, .46f, 0xFF733C85, 0xFF552E72, 0x50FFB890, -1);
        ridge(c, p, rnd, w, h, .82f, .18f, .47f, 0xFF40275E, 0xFF2E1C4C, 0x40FFA880, -1);

        Bitmap front = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ridge(new Canvas(front), p, rnd, w, h, .93f, .56f, .48f, 0xFF1F1438, 0xFF0A0616, 0x50FFA070, .63f);
        return new Bitmap[]{back, front};
    }

    private static void ridge(Canvas c, Paint p, Random rnd, int w, int h, float base, float amp, float rough,
                              int top, int bottom, int rimColor, float peakX) {
        float[] a = displace(rnd, 256, rough);
        int n = a.length;
        if (peakX >= 0) {
            float max = 0;
            for (int i = 0; i < n; i++) {
                double x = i / (double) (n - 1);
                double g = .75 * Math.exp(-Math.pow((x - peakX) / 0.085, 2))
                        + .38 * Math.exp(-Math.pow((x - peakX + .13) / 0.09, 2))
                        + .30 * Math.exp(-Math.pow((x - peakX - .14) / 0.10, 2));
                a[i] = a[i] * .62f + (float) g;
                max = Math.max(max, a[i]);
            }
            for (int i = 0; i < n; i++) a[i] /= max;
        }
        Path fillPath = new Path(), edge = new Path();
        fillPath.moveTo(0, h);
        for (int i = 0; i < n; i++) {
            float x = i * w / (float) (n - 1), y = (base - amp * a[i]) * h;
            fillPath.lineTo(x, y);
            if (i == 0) edge.moveTo(x, y); else edge.lineTo(x, y);
        }
        fillPath.lineTo(w, h);
        fillPath.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        p.setShader(new LinearGradient(0, (base - amp) * h, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(fillPath, p);
        p.setShader(null);
        // Faint sunset light catching the ridge line.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * w / 1920f);
        p.setColor(rimColor);
        c.drawPath(edge, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** 1-D midpoint displacement, normalised to 0..1. */
    private static float[] displace(Random rnd, int size, float rough) {
        float[] a = new float[size + 1];
        a[0] = rnd.nextFloat();
        a[size] = rnd.nextFloat();
        float d = 1f;
        for (int step = size; step > 1; step /= 2) {
            for (int i = step / 2; i < size; i += step) {
                a[i] = (a[i - step / 2] + a[i + step / 2]) / 2 + (rnd.nextFloat() * 2 - 1) * d;
            }
            d *= rough;
        }
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : a) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        for (int i = 0; i < a.length; i++) a[i] = (a[i] - lo) / (hi - lo);
        return a;
    }

    /** Heavily blurred, vibrancy-boosted copy of the wallpaper; glass panels sample it. */
    static Bitmap frosted(Bitmap src, int factor) {
        int w = Math.max(1, src.getWidth() / factor), h = Math.max(1, src.getHeight() / factor);
        Bitmap b = Bitmap.createScaledBitmap(src, w, h, true);
        int[] a = new int[w * h], t = new int[w * h];
        b.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < 3; i++) {
            blur(a, t, w, h, 5, 1, w);
            blur(t, a, h, w, 5, w, 1);
        }
        b.setPixels(a, 0, w, 0, 0, w, h);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.6f);
        cm.postConcat(new ColorMatrix(new float[]{
                1.1f, 0, 0, 0, 18,
                0, 1.1f, 0, 0, 18,
                0, 0, 1.1f, 0, 18,
                0, 0, 0, 1, 0}));
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(b, 0, 0, p);
        b.recycle();
        return out;
    }

    /** One box-blur pass along a line direction (step) for each of `lines` lines (stride). */
    private static void blur(int[] in, int[] out, int len, int lines, int r, int step, int stride) {
        int div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * stride, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[base + clamp(i, len) * step];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int i = 0; i < len; i++) {
                out[base + i * step] = 0xFF000000 | (sr / div) << 16 | (sg / div) << 8 | (sb / div);
                int pa = in[base + clamp(i + r + 1, len) * step], pr = in[base + clamp(i - r, len) * step];
                sr += ((pa >> 16) & 255) - ((pr >> 16) & 255);
                sg += ((pa >> 8) & 255) - ((pr >> 8) & 255);
                sb += (pa & 255) - (pr & 255);
            }
        }
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }

    /** Soft squircle shadow; drawn at 1.5x the tile size. */
    static Bitmap shadow() {
        int size = 192, half = 64;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);
        p.setMaskFilter(new BlurMaskFilter(16, BlurMaskFilter.Blur.NORMAL));
        Path path = new Path();
        squircle(path, new Matrix(), size / 2f, size / 2f, half * .92f, 0);
        new Canvas(b).drawPath(path, p);
        return b;
    }
}
