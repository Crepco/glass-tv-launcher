package com.hamza.glass;

/** Damped spring used for every animation (focus lift, paging, menus). */
final class Spring {
    float x, v, target;
    private final float k, c;

    Spring(float stiffness, float dampingRatio) {
        k = stiffness;
        c = 2f * (float) Math.sqrt(stiffness) * dampingRatio;
    }

    /** Advances the spring; returns true while it is still moving. */
    boolean step(float dt) {
        if (Math.abs(x - target) < 1e-3f && Math.abs(v) < 1e-2f) {
            x = target;
            v = 0;
            return false;
        }
        int n = (int) Math.ceil(dt / 0.004f);
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float a = -k * (x - target) - c * v;
            v += a * h;
            x += v * h;
        }
        return true;
    }
}
