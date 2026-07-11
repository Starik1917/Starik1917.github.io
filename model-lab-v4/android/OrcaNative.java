package com.starik.modelviewer;

import androidx.annotation.NonNull;

/**
 * Thin Java entry point for the native OrcaSlicer engine.
 * No fallback slicer is provided: a missing native library is a hard error.
 */
public final class OrcaNative {
    static {
        System.loadLibrary("model_lab_orca");
    }

    private OrcaNative() {
    }

    @NonNull
    public static String slice(@NonNull String requestJson) {
        return nativeSlice(requestJson);
    }

    @NonNull
    public static String engineName() {
        return nativeEngineName();
    }

    private static native String nativeSlice(String requestJson);
    private static native String nativeEngineName();
}
