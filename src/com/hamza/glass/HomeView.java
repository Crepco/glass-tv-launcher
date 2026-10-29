package com.hamza.glass;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal lock-screen style home: mountain wallpaper, a glass clock tucked behind the
 * nearest peak, one row of favourite apps, and an App Library sheet with everything else.
 */
final class HomeView extends View {
    static final int DOCK_MAX = 7;
    private static final int LIB_COLS = 8;
    private static final String LIBRARY = "@library";

    private static final int LABEL_NONE = 0, LABEL_ALWAYS = 1, LABEL_FOCUS = 2;

    final Apps apps;
    private List<AppEntry> pinned = new ArrayList<>(), others = new ArrayList<>(), lib = new ArrayList<>();
    private final AppEntry libraryTile = new AppEntry(LIBRARY);
    private Set<String> hidden = new HashSet<>();
    private boolean loaded, showHidden;

    private int W = 1920, H = 1080;
    private float s;
    private Bitmap back, front, shadow, frostBmp;

    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glyphShadow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint date = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint menuText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clockGlass = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clockTint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clockRim = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient sheenUnit = new LinearGradient(0, 0, 0, 1,
            new int[]{0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF}, new float[]{0f, .42f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient rimUnit = new LinearGradient(0, 0, 1, 1,
            new int[]{0xC8FFFFFF, 0x1AFFFFFF, 0x60FFFFFF}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient shadeUnit = new LinearGradient(0, 0, 0, 1,
            0x00000000, 0x5C000000, Shader.TileMode.CLAMP);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix(), sm = new Matrix();
    private final Path path = new Path();
    private final RectF r = new RectF(), r2 = new RectF();

    // Focus: index into the home row, or into the App Library when it is open.
    private int ri, li;
    private boolean inLib;
    private final Spring libAnim = new Spring(300f, 0.86f), libScroll = new Spring(240f, 0.92f);
    private final Spring bumpX = new Spring(520f, 0.35f), bumpY = new Spring(520f, 0.35f);

    private static final class Menu {
        final AppEntry target;
        final List<String> items = new ArrayList<>();
        int sel;
        float ax, ay, ah;

        Menu(AppEntry t) {
            target = t;
        }
    }

    private Menu menu, shownMenu;
    private final Spring menuAnim = new Spring(380f, 0.74f);
    private AppEntry held;

    private boolean animating, selDown, longFired;
    private long lastFrame;
    private int lastMinute = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            int mnt = Calendar.getInstance().get(Calendar.MINUTE);
            if (mnt != lastMinute) {
                lastMinute = mnt;
                invalidate();
            }
            postDelayed(this, 1000);
        }
    };

    HomeView(Context c) {
        super(c);
        apps = new Apps(c);
        s = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        setFocusable(true);
        sheen.setShader(sheenUnit);
        rim.setStyle(Paint.Style.STROKE);
        rim.setShader(rimUnit);
        glyphShadow.setColorFilter(new PorterDuffColorFilter(0x40000000, PorterDuff.Mode.SRC_IN));
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        for (TextPaint p : new TextPaint[]{label, date, title}) {
            p.setTypeface(medium);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.WHITE);
        }
        date.setColor(0xE6FFFFFF);
        menuText.setTypeface(medium);
        for (TextPaint p : new TextPaint[]{clockGlass, clockTint, clockRim}) {
            p.setTypeface(bold);
            p.setTextAlign(Paint.Align.CENTER);
            p.setLetterSpacing(-0.03f);
        }
        clockRim.setStyle(Paint.Style.STROKE);
        libraryTile.label = libraryTile.shortLabel = "App Library";
        libraryTile.accent = 0xFF8A5CFF;
        shade.setShader(shadeUnit);
        applyScale();
    }

    private float clockBase() {
        return 452 * s;
    }

    private void applyScale() {
        label.setTextSize(23 * s);
        label.setShadowLayer(6 * s, 0, 2 * s, 0x99000000);
        date.setTextSize(38 * s);
        date.setShadowLayer(10 * s, 0, 2 * s, 0x55000000);
        title.setTextSize(44 * s);
        menuText.setTextSize(30 * s);

        float size = 290 * s, top = clockBase() - size * 0.74f;
        for (TextPaint p : new TextPaint[]{clockGlass, clockTint, clockRim}) p.setTextSize(size);
        clockTint.setShader(new LinearGradient(0, top, 0, clockBase(), 0xD9FFFFFF, 0x66FFFFFF, Shader.TileMode.CLAMP));
        clockTint.setShadowLayer(40 * s, 0, 10 * s, 0x40000000);
        clockRim.setStrokeWidth(2.6f * s);
        clockRim.setShader(new LinearGradient(0, top, 0, clockBase(), 0xFFFFFFFF, 0x80FFFFFF, Shader.TileMode.CLAMP));
        updateClockGlass();
    }

    /** The clock samples a slightly magnified frosted wallpaper, like light bending through glass. */
    private void updateClockGlass() {
        if (frostBmp == null) return;
        BitmapShader bs = new BitmapShader(frostBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix lm = new Matrix();
        lm.setScale(W / (float) frostBmp.getWidth(), H / (float) frostBmp.getHeight());
        lm.postScale(1.12f, 1.12f, W / 2f, clockBase() - 100 * s);
        bs.setLocalMatrix(lm);
        clockGlass.setShader(bs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        s = w / 1920f;
        applyScale();
        final int w0 = w, h0 = h;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap[] layers = Gfx.mountains(w0, h0);
                Bitmap flat = layers[0].copy(Bitmap.Config.ARGB_8888, true);
                new Canvas(flat).drawBitmap(layers[1], 0, 0, null);
                final Bitmap fr = Gfx.frosted(flat, 8);
                flat.recycle();
                final Bitmap sh = Gfx.shadow();
                post(new Runnable() {
                    @Override
                    public void run() {
                        back = layers[0];
                        front = layers[1];
                        shadow = sh;
                        frostBmp = fr;
                        BitmapShader bs = new BitmapShader(fr, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        Matrix lm = new Matrix();
                        lm.setScale(w0 / (float) fr.getWidth(), h0 / (float) fr.getHeight());
                        bs.setLocalMatrix(lm);
                        frost.setShader(bs);
                        updateClockGlass();
                        invalidate();
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- data

    void reload() {
        final int px = Math.round(176 * s);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Apps.Result res = apps.load(px);
                post(new Runnable() {
                    @Override
                    public void run() {
                        apply(res);
                    }
                });
            }
        }).start();
    }

    private void apply(Apps.Result res) {
        if (held != null || menu != null) return;
        AppEntry f = loaded ? focused() : null;
        pinned = res.dock;
        others = res.grid;
        hidden = res.hidden;
        for (AppEntry e : pinned) ellipsize(e);
        for (AppEntry e : others) ellipsize(e);
        refresh();
        if (f != null) focusOn(f);
        loaded = true;
        invalidate();
    }

    private void ellipsize(AppEntry e) {
        e.shortLabel = TextUtils.ellipsize(e.label, label, 180 * s, TextUtils.TruncateAt.END).toString();
    }

    private void refresh() {
        lib = new ArrayList<>();
        for (AppEntry e : pinned) if (showHidden || !hidden.contains(e.key)) lib.add(e);
        for (AppEntry e : others) if (showHidden || !hidden.contains(e.key)) lib.add(e);
        Collections.sort(lib, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        ri = clamp(ri, 0, row().size() - 1);
        li = clamp(li, 0, lib.size() - 1);
        if (lib.isEmpty()) inLib = false;
        scrollToFocus();
    }

    private void save() {
        apps.save(pinned, others, hidden);
    }

    private List<AppEntry> row() {
        List<AppEntry> r = new ArrayList<>(pinned);
        r.add(libraryTile);
        return r;
    }

    private AppEntry focused() {
        if (inLib) return lib.isEmpty() ? null : lib.get(li);
        List<AppEntry> row = row();
        return row.get(clamp(ri, 0, row.size() - 1));
    }

    private void focusOn(AppEntry e) {
        if (inLib) {
            int i = lib.indexOf(e);
            if (i >= 0) li = i;
        } else {
            int i = row().indexOf(e);
            if (i >= 0) ri = i;
        }
        scrollToFocus();
    }

    private void pin(AppEntry e) {
        if (pinned.size() >= DOCK_MAX) { toast("Your home row is full"); return; }
        others.remove(e);
        pinned.add(e);
    }

    private void unpin(AppEntry e) {
        if (pinned.remove(e)) others.add(e);
    }

    // ---------------------------------------------------------------- layout

    private float rowX(int i, int n) {
        return W / 2f + (i - (n - 1) / 2f) * 172 * s;
    }

    private float rowY() {
        return 876 * s;
    }

    private float rowIcon() {
        return 118 * s;
    }

    private float libX(int col) {
        return W / 2f + (col - (LIB_COLS - 1) / 2f) * 200 * s;
    }

    private float libY(int row) {
        return 300 * s + row * 236 * s - libScroll.x + (1 - Math.min(1f, libAnim.x)) * 90 * s;
    }

    private float libIcon() {
        return 118 * s;
    }

    private void scrollToFocus() {
        float pitch = 236 * s, rowTop = (li / LIB_COLS) * pitch;
        if (rowTop - libScroll.target > 2 * pitch) libScroll.target = rowTop - 2 * pitch;
        if (rowTop < libScroll.target) libScroll.target = rowTop;
    }

    // ---------------------------------------------------------------- input

    boolean onKey(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down) {
                    if (ev.getRepeatCount() == 0) {
                        selDown = true;
                        longFired = false;
                    } else if (selDown && !longFired && (ev.isLongPress() || ev.getEventTime() - ev.getDownTime() > 550)) {
                        longFired = true;
                        if (menu == null && held == null) openMenu();
                    }
                } else if (ev.getAction() == KeyEvent.ACTION_UP) {
                    if (selDown && !longFired) select();
                    selDown = false;
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) arrow(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) arrow(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) arrow(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) arrow(0, 1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (down && ev.getRepeatCount() == 0) {
                    if (menu != null) closeMenu();
                    else if (held == null) openMenu();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (ev.getAction() == KeyEvent.ACTION_UP && !ev.isCanceled()) back();
                return true;
            default:
                return false;
        }
    }

    private void select() {
        AppEntry f = focused();
        if (menu != null) runMenu(menu.items.get(menu.sel));
        else if (held != null) { held = null; save(); }
        else if (f == libraryTile) openLibrary();
        else launch(f);
        invalidate();
    }

    private void back() {
        if (menu != null) closeMenu();
        else if (held != null) { held = null; save(); }
        else if (inLib) closeLibrary();
        else if (showHidden) { showHidden = false; refresh(); }
        invalidate();
    }

    void goHome() {
        closeMenu();
        if (held != null) save();
        held = null;
        closeLibrary();
        invalidate();
    }

    private void openLibrary() {
        if (lib.isEmpty()) return;
        inLib = true;
        li = 0;
        libScroll.x = libScroll.target = 0;
        libAnim.target = 1;
    }

    private void closeLibrary() {
        inLib = false;
        libAnim.target = 0;
    }

    private void arrow(int dx, int dy) {
        if (!loaded) return;
        if (menu != null) {
            if (dy != 0) {
                int n = menu.sel + dy;
                if (n >= 0 && n < menu.items.size()) menu.sel = n;
            }
        } else if (held != null) {
            int i = pinned.indexOf(held), t = i + dx;
            if (dx != 0 && t >= 0 && t < pinned.size()) {
                Collections.swap(pinned, i, t);
                ri = t;
            } else {
                kick(dx, dy);
            }
        } else if (inLib) {
            navLibrary(dx, dy);
        } else {
            int t = ri + dx;
            if (dx != 0 && t >= 0 && t < row().size()) ri = t; else kick(dx, dy);
        }
        invalidate();
    }

    private void navLibrary(int dx, int dy) {
        int n = lib.size(), col = li % LIB_COLS;
        int t = li;
        if (dx < 0 && col > 0) t = li - 1;
        else if (dx > 0 && col < LIB_COLS - 1 && li + 1 < n) t = li + 1;
        else if (dy < 0 && li - LIB_COLS >= 0) t = li - LIB_COLS;
        else if (dy > 0 && li + LIB_COLS < n) t = li + LIB_COLS;
        else if (dy > 0 && li / LIB_COLS < (n - 1) / LIB_COLS) t = n - 1;
        if (t == li) kick(dx, dy);
        li = t;
        scrollToFocus();
    }

    private void kick(int dx, int dy) {
        bumpX.v += dx * 9f;
        bumpY.v += dy * 9f;
    }

    private void launch(AppEntry e) {
        if (e == null || e.intent == null) return;
        e.focus.v -= 6f;
        try {
            getContext().startActivity(e.intent);
        } catch (Exception x) {
            toast("Can't open " + e.label);
        }
    }

    private void toast(String t) {
        Toast.makeText(getContext(), t, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- menu

    private void openMenu() {
        AppEntry e = focused();
        if (e == null) return;
        Menu mn = new Menu(e);
        if (e == libraryTile) {
            if (hidden.isEmpty()) return;
            mn.items.add(showHidden ? "Hide Hidden Apps" : "Show Hidden Apps");
        } else {
            boolean isPinned = pinned.contains(e), isHidden = hidden.contains(e.key);
            mn.items.add("Open");
            if (!inLib && isPinned) mn.items.add("Move");
            if (isPinned) mn.items.add("Remove from Home");
            else if (!isHidden) mn.items.add("Add to Home");
            if (!e.key.startsWith("@")) mn.items.add("App Info");
            if (inLib && !hidden.isEmpty()) mn.items.add(showHidden ? "Hide Hidden Apps" : "Show Hidden Apps");
            mn.items.add(isHidden ? "Unhide App" : "Hide App");
        }
        if (inLib) {
            mn.ax = libX(li % LIB_COLS);
            mn.ay = libY(li / LIB_COLS);
            mn.ah = libIcon() * .58f;
        } else {
            mn.ax = rowX(ri, row().size());
            mn.ay = rowY();
            mn.ah = rowIcon() * .58f;
        }
        menu = shownMenu = mn;
        menuAnim.target = 1;
        invalidate();
    }

    private void closeMenu() {
        menu = null;
        menuAnim.target = 0;
    }

    private void runMenu(String item) {
        AppEntry e = menu.target;
        closeMenu();
        switch (item) {
            case "Open":
                launch(e);
                break;
            case "Move":
                held = e;
                break;
            case "Add to Home":
                pin(e);
                break;
            case "Remove from Home":
                unpin(e);
                break;
            case "App Info":
                try {
                    getContext().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", e.key, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception x) {
                    toast("App info isn't available");
                }
                break;
            case "Show Hidden Apps":
                showHidden = true;
                break;
            case "Hide Hidden Apps":
                showHidden = false;
                break;
            case "Hide App":
                hidden.add(e.key);
                unpin(e);
                break;
            case "Unhide App":
                hidden.remove(e.key);
                break;
            default:
                break;
        }
        save();
        refresh();
        if (e != libraryTile) focusOn(e);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = animating ? Math.min((now - lastFrame) / 1000f, 0.05f) : 1 / 60f;
        lastFrame = now;
        animating = step(dt);

        if (back == null || front == null || shadow == null || frost.getShader() == null) {
            c.drawColor(0xFF060B1E);
            return;
        }
        c.drawBitmap(back, 0, 0, null);
        drawClock(c);
        c.drawBitmap(front, 0, 0, null);

        float la = Math.max(0f, Math.min(1f, libAnim.x));
        if (loaded && la < 0.999f) drawRow(c, 1 - la);
        if (la > 0.001f) drawLibrary(c, la);
        if (shownMenu != null) drawMenu(c);
        if (animating || held != null) postInvalidateOnAnimation();
    }

    private boolean step(float dt) {
        AppEntry f = focused();
        boolean a = false;
        for (AppEntry e : pinned) a |= stepFocus(e, f, dt);
        for (AppEntry e : others) a |= stepFocus(e, f, dt);
        a |= stepFocus(libraryTile, f, dt);
        a |= libAnim.step(dt);
        a |= libScroll.step(dt);
        a |= bumpX.step(dt);
        a |= bumpY.step(dt);
        a |= menuAnim.step(dt);
        if (menu == null && menuAnim.x < 0.01f) shownMenu = null;
        return a;
    }

    private boolean stepFocus(AppEntry e, AppEntry f, float dt) {
        e.focus.target = e == held ? 1.4f : (e == f ? 1f : 0f);
        return e.focus.step(dt);
    }

    /** Date, then the time in frosted glass with a bright rim, iOS 26 lock-screen style. */
    private void drawClock(Canvas c) {
        Calendar cal = Calendar.getInstance();
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        c.drawText(DateFormat.format(pattern, cal).toString(), W / 2f, 162 * s, date);

        String time = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm", cal).toString();
        float x = W / 2f, y = clockBase();
        c.drawText(time, x, y, clockTint);
        if (clockGlass.getShader() != null) {
            clockGlass.setAlpha(140);
            c.drawText(time, x, y, clockGlass);
        }
        c.drawText(time, x, y, clockRim);
    }

    private void drawRow(Canvas c, float alpha) {
        List<AppEntry> row = row();
        int n = row.size();
        for (int i = 0; i < n; i++) {
            AppEntry e = row.get(i);
            if (e != held) drawTile(c, e, rowX(i, n), rowY(), rowIcon(), LABEL_FOCUS, alpha);
        }
        if (held != null) {
            int i = row.indexOf(held);
            drawTile(c, held, rowX(i, n), rowY(), rowIcon(), LABEL_FOCUS, alpha);
        }
    }

    private void drawLibrary(Canvas c, float a) {
        frost.setAlpha((int) (255 * a));
        c.drawRect(0, 0, W, H, frost);
        fill.setColor(Color.argb((int) (90 * a), 0, 0, 0));
        c.drawRect(0, 0, W, H, fill);

        float fadeTop = 190 * s;
        for (int i = 0; i < lib.size(); i++) {
            float y = libY(i / LIB_COLS);
            if (y < fadeTop - libIcon() || y > H + libIcon()) continue;
            float fade = Math.max(0f, Math.min(1f, (y - fadeTop) / (90 * s)));
            AppEntry e = lib.get(i);
            float hiddenDim = hidden.contains(e.key) ? 0.4f : 1f;
            drawTile(c, e, libX(i % LIB_COLS), y, libIcon(), LABEL_ALWAYS, a * fade * hiddenDim);
        }

        title.setAlpha((int) (255 * a));
        c.drawText("App Library", W / 2f, 120 * s - (1 - a) * 30 * s, title);
    }

    private void drawTile(Canvas c, AppEntry e, float cx, float cy, float size, int labelMode, float alpha) {
        if (alpha <= 0.003f) return;
        float f = e.focus.x, fc = Math.max(0f, Math.min(1f, f));
        if (e == focused()) {
            cx += bumpX.x * 24 * s;
            cy += bumpY.x * 24 * s;
        }
        float half = size * (1 + 0.18f * f) / 2;
        float cyLift = cy - 8 * s * f;
        float rot = e == held
                ? (float) Math.sin(SystemClock.uptimeMillis() / 1000.0 * Math.PI * 2 * 2.3 + e.phase) * 2.6f
                : 0f;

        float sh = half * 1.5f, drop = (10 + 12 * f) * s;
        bmp.setAlpha((int) (alpha * (95 + 70 * fc)));
        r.set(cx - sh, cyLift - sh + drop, cx + sh, cyLift + sh + drop);
        c.drawBitmap(shadow, null, r, bmp);

        Gfx.squircle(path, m, cx, cyLift, half, rot);
        r.set(cx - half, cyLift - half, cx + half, cyLift + half);
        tintedGlass(c, path, r, e.accent, fc, alpha);
        c.save();
        c.rotate(rot, cx, cyLift);
        if (e.glyph != null) {
            float ih = half * (e.fullBleed ? 0.80f : 0.64f);
            r2.set(cx - ih, cyLift - ih + 3 * s, cx + ih, cyLift + ih + 3 * s);
            glyphShadow.setAlpha((int) (255 * alpha));
            c.drawBitmap(e.glyph, null, r2, glyphShadow);
            r2.offset(0, -3 * s);
            bmp.setAlpha((int) ((215 + 40 * fc) * alpha));
            c.drawBitmap(e.glyph, null, r2, bmp);
        } else {
            drawLibraryGlyph(c, cx, cyLift, half, alpha);
        }
        c.restore();
        highlight(c, path, r, alpha, fc);

        float la = labelMode == LABEL_ALWAYS ? 1f : (labelMode == LABEL_FOCUS ? fc : 0f);
        if (la > 0.01f) {
            label.setAlpha((int) (alpha * la * 245));
            c.drawText(e.shortLabel, cx, cy + half + 40 * s, label);
        }
    }

    /** Four small rounded squares, like the App Library icon. */
    private void drawLibraryGlyph(Canvas c, float cx, float cy, float half, float alpha) {
        float g = half * 0.19f, d = g + half * 0.06f, rad = g * 0.8f;
        fill.setColor(Color.argb((int) (235 * alpha), 255, 255, 255));
        for (int i = 0; i < 4; i++) {
            float ox = (i % 2 == 0 ? -d : d), oy = (i < 2 ? -d : d);
            r2.set(cx + ox - g, cy + oy - g, cx + ox + g, cy + oy + g);
            c.drawRoundRect(r2, rad, rad, fill);
        }
    }

    /** Glass tinted with the app's brand colour, shaded darker towards the bottom. */
    private void tintedGlass(Canvas c, Path p, RectF b, int accent, float f, float alpha) {
        if (accent == 0) {
            glass(c, p, 0.10f + 0.12f * f, alpha);
            return;
        }
        frost.setAlpha((int) (255 * alpha));
        c.drawPath(p, frost);
        fill.setColor(accent);
        fill.setAlpha((int) ((150 + 55 * f) * alpha));
        c.drawPath(p, fill);
        sm.setScale(1, b.height());
        sm.postTranslate(0, b.top);
        shadeUnit.setLocalMatrix(sm);
        shade.setAlpha((int) (255 * alpha));
        c.drawPath(p, shade);
    }

    /** Frosted wallpaper behind the shape plus a white tint, like iOS materials. */
    private void glass(Canvas c, Path p, float tint, float alpha) {
        frost.setAlpha((int) (255 * alpha));
        c.drawPath(p, frost);
        fill.setColor(Color.argb((int) (255 * tint * alpha), 255, 255, 255));
        c.drawPath(p, fill);
    }

    /** Liquid Glass edge: top sheen and a light-catching rim, brighter when focused. */
    private void highlight(Canvas c, Path p, RectF b, float alpha, float f) {
        sm.setScale(1, b.height());
        sm.postTranslate(0, b.top);
        sheenUnit.setLocalMatrix(sm);
        sheen.setAlpha((int) (255 * alpha * (0.75f + 0.25f * f)));
        c.drawPath(p, sheen);

        sm.setScale(b.width(), b.height());
        sm.postTranslate(b.left, b.top);
        rimUnit.setLocalMatrix(sm);
        rim.setStrokeWidth((1.6f + 1.6f * f) * s);
        rim.setAlpha((int) (255 * alpha * (0.55f + 0.45f * f)));
        c.drawPath(p, rim);
    }

    private void drawMenu(Canvas c) {
        float a = Math.max(0f, Math.min(1f, menuAnim.x));
        fill.setColor(Color.argb((int) (120 * a), 0, 0, 0));
        c.drawRect(0, 0, W, H, fill);

        Menu mn = shownMenu;
        float iw = 460 * s, ih = 76 * s, pad = 12 * s, h = mn.items.size() * ih + pad * 2;
        boolean right = mn.ax + mn.ah + 36 * s + iw < W - 48 * s;
        float left = right ? mn.ax + mn.ah + 36 * s : mn.ax - mn.ah - 36 * s - iw;
        float top = Math.max(48 * s, Math.min(H - 48 * s - h, mn.ay - h / 2));
        float pvx = right ? left : left + iw, pvy = mn.ay;
        float sc = 0.7f + 0.3f * menuAnim.x;
        r.set(pvx + (left - pvx) * sc, pvy + (top - pvy) * sc,
                pvx + (left + iw - pvx) * sc, pvy + (top + h - pvy) * sc);

        path.reset();
        path.addRoundRect(r, 38 * s * sc, 38 * s * sc, Path.Direction.CW);
        glass(c, path, 0.08f, a);
        fill.setColor(Color.argb((int) (70 * a), 0, 0, 0));
        c.drawPath(path, fill);
        highlight(c, path, r, a, 0f);

        menuText.setTextSize(30 * s * sc);
        for (int i = 0; i < mn.items.size(); i++) {
            float it = r.top + (pad + i * ih) * sc;
            if (i == mn.sel && menu != null) {
                r2.set(r.left + 10 * s * sc, it, r.right - 10 * s * sc, it + ih * sc);
                fill.setColor(Color.argb((int) (64 * a), 255, 255, 255));
                c.drawRoundRect(r2, 28 * s * sc, 28 * s * sc, fill);
            }
            String t = mn.items.get(i);
            boolean red = t.equals("Hide App") || t.equals("Remove from Home");
            menuText.setColor(red ? 0xFFFF6961 : Color.WHITE);
            menuText.setAlpha((int) (255 * a));
            c.drawText(t, r.left + 34 * s * sc, it + ih * sc / 2 + menuText.getTextSize() * 0.36f, menuText);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
