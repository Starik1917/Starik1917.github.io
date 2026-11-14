package com.starik.modelviewer;

import java.util.Objects;

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

    public static String slice(String requestJson) {
        return nativeSlice(Objects.requireNonNull(requestJson, "requestJson"));
    }

    public static String engineName() {
        return nativeEngineName();
    }

    private static native String nativeSlice(String requestJson);
    private static native String nativeEngineName();
}
