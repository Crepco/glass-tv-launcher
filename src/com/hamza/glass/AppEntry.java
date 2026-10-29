package com.hamza.glass;

import android.content.Intent;
import android.graphics.Bitmap;

final class AppEntry {
    final String key;
    String label = "", shortLabel = "";
    Intent intent;
    Bitmap icon;
    /** White "clear mode" version of the icon, drawn on a glass tile. */
    Bitmap glyph;
    /** The icon's main brand colour (opaque), or 0 when the icon is colourless. */
    int accent;
    /** True when the icon is full-bleed artwork already masked to a squircle. */
    boolean fullBleed;
    final Spring focus = new Spring(420f, 0.62f);
    final float phase = (float) (Math.random() * Math.PI * 2);

    AppEntry(String key) {
        this.key = key;
    }
}
