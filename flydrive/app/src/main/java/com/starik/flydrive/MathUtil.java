package com.starik.flydrive;

final class MathUtil {
    private MathUtil() {}
    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float smooth(float t) { t = clamp(t, 0f, 1f); return t*t*(3f - 2f*t); }
    static float tanh(float x) { return (float)Math.tanh(x); }
    static float sigmoid(float x) { return 1f / (1f + (float)Math.exp(-x)); }
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
    static float hash01(long x) {
        long v = mix64(x);
        return ((v >>> 40) & 0xffffffL) / 16777215f;
    }
}
