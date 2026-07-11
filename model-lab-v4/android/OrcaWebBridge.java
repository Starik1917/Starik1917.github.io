package com.starik.modelviewer;

import android.app.Activity;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebView-to-JNI transport for the real OrcaSlicer engine.
 * Model bytes are streamed to disk in chunks to avoid a second full STL copy in RAM.
 */
public final class OrcaWebBridge {
    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object stageLock = new Object();

    private OutputStream stagedOutput;
    private File stagedModel;

    public OrcaWebBridge(Activity activity, WebView webView) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.webView = Objects.requireNonNull(webView, "webView");
    }

    @JavascriptInterface
    public String engineName() {
        return OrcaNative.engineName();
    }

    @JavascriptInterface
    public boolean beginModel(String filename) {
        synchronized (stageLock) {
            closeStagedOutput();
            deleteStagedModel();
            try {
                File directory = new File(activity.getCacheDir(), "orca-input");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IOException("Cannot create Orca input directory");
                }
                String safeName = sanitizeFilename(filename);
                stagedModel = new File(directory, UUID.randomUUID() + "-" + safeName);
                stagedOutput = new BufferedOutputStream(new FileOutputStream(stagedModel));
                return true;
            } catch (IOException exception) {
                closeStagedOutput();
                deleteStagedModel();
                return false;
            }
        }
    }

    @JavascriptInterface
    public boolean appendModelBase64(String chunk) {
        synchronized (stageLock) {
            if (stagedOutput == null || chunk == null) return false;
            try {
                stagedOutput.write(Base64.decode(chunk, Base64.NO_WRAP));
                return true;
            } catch (IllegalArgumentException | IOException exception) {
                return false;
            }
        }
    }

    @JavascriptInterface
    public String finishModel() {
        synchronized (stageLock) {
            if (stagedOutput == null || stagedModel == null) return "";
            try {
                stagedOutput.flush();
                stagedOutput.close();
                stagedOutput = null;
                return stagedModel.getAbsolutePath();
            } catch (IOException exception) {
                closeStagedOutput();
                deleteStagedModel();
                return "";
            }
        }
    }

    @JavascriptInterface
    public void slice(String requestJson, String callbackId) {
        final String safeCallbackId = callbackId == null ? "" : callbackId;
        executor.execute(() -> {
            String response;
            try {
                JSONObject request = new JSONObject(requestJson == null ? "{}" : requestJson);
                synchronized (stageLock) {
                    if (stagedModel == null || !stagedModel.isFile()) {
                        throw new IllegalStateException("STL is not staged for OrcaSlicer");
                    }
                    request.put("modelPath", stagedModel.getAbsolutePath());
                }
                File outputDirectory = new File(activity.getCacheDir(), "orca-output");
                if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                    throw new IOException("Cannot create Orca output directory");
                }
                File output = new File(outputDirectory, UUID.randomUUID() + ".gcode");
                request.put("outputGcodePath", output.getAbsolutePath());
                response = OrcaNative.slice(request.toString());
            } catch (Exception exception) {
                JSONObject error = new JSONObject();
                try {
                    error.put("ok", false);
                    error.put("error", exception.getMessage());
                } catch (JSONException ignored) {
                    // JSONObject with constant keys should not fail.
                }
                response = error.toString();
            }
            postResult(safeCallbackId, response);
        });
    }

    public void destroy() {
        executor.shutdownNow();
        synchronized (stageLock) {
            closeStagedOutput();
            deleteStagedModel();
        }
    }

    private void postResult(String callbackId, String responseJson) {
        String script = "window.ModelLabOrca&&window.ModelLabOrca._resolve(" +
                JSONObject.quote(callbackId) + "," + JSONObject.quote(responseJson) + ");";
        activity.runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private void closeStagedOutput() {
        if (stagedOutput == null) return;
        try {
            stagedOutput.close();
        } catch (IOException ignored) {
        }
        stagedOutput = null;
    }

    private void deleteStagedModel() {
        if (stagedModel != null && stagedModel.exists()) {
            // Best effort cleanup of user-provided temporary geometry.
            stagedModel.delete();
        }
        stagedModel = null;
    }

    private static String sanitizeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "model.stl" : filename;
        value = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return value.length() > 120 ? value.substring(value.length() - 120) : value;
    }
}
