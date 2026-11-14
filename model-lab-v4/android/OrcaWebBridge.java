package com.starik.modelviewer;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebView-to-JNI transport for the real OrcaSlicer engine.
 * Model and G-code bytes are streamed in chunks so large files do not need a
 * second complete copy in the Java heap.
 */
public final class OrcaWebBridge {
    private static final String ORCA_ASSET_ROOT = "orca";
    private static final String ORCA_ASSET_VERSION = "e1c0ea0-model-lab-v4";
    private static final int MAX_READ_CHUNK = 256 * 1024;

    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object stageLock = new Object();

    private OutputStream stagedOutput;
    private File stagedModel;
    private File extractedRoot;

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
                ensureDirectory(directory);
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

                File root = ensureBundledResources();
                File resources = new File(root, "resources");
                File profiles = new File(resources, "profiles");
                File data = new File(root, "data");
                ensureDirectory(data);
                request.put("resourcesDir", resources.getAbsolutePath());
                request.put("profileRoot", profiles.getAbsolutePath());
                request.put("dataDir", data.getAbsolutePath());
                request.put("machineProfilePath", resolveProfile(profiles, request,
                        "machineProfile", "Flashforge/machine/Flashforge Adventurer 5M 0.4 Nozzle.json"));
                request.put("processProfilePath", resolveProfile(profiles, request,
                        "processProfile", "Flashforge/process/0.20mm Standard @Flashforge AD5M 0.4 Nozzle.json"));
                request.put("filamentProfilePath", resolveProfile(profiles, request,
                        "filamentProfile", "Flashforge/filament/Flashforge Generic PLA.json"));

                File outputDirectory = new File(activity.getCacheDir(), "orca-output");
                ensureDirectory(outputDirectory);
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

    @JavascriptInterface
    public long outputSize(String path) {
        try {
            File output = checkedOutput(path);
            return output.length();
        } catch (IOException exception) {
            return -1;
        }
    }

    @JavascriptInterface
    public String readOutputBase64(String path, long offset, int requestedBytes) {
        try {
            File output = checkedOutput(path);
            if (offset < 0 || offset >= output.length()) return "";
            int count = Math.max(1, Math.min(requestedBytes, MAX_READ_CHUNK));
            count = (int) Math.min(count, output.length() - offset);
            byte[] buffer = new byte[count];
            try (RandomAccessFile input = new RandomAccessFile(output, "r")) {
                input.seek(offset);
                input.readFully(buffer);
            }
            return Base64.encodeToString(buffer, Base64.NO_WRAP);
        } catch (IOException | IllegalArgumentException exception) {
            return "";
        }
    }

    @JavascriptInterface
    public boolean deleteOutput(String path) {
        try {
            return checkedOutput(path).delete();
        } catch (IOException exception) {
            return false;
        }
    }

    public void destroy() {
        executor.shutdownNow();
        synchronized (stageLock) {
            closeStagedOutput();
            deleteStagedModel();
        }
    }

    private File ensureBundledResources() throws IOException {
        synchronized (stageLock) {
            if (extractedRoot != null && extractedRoot.isDirectory()) return extractedRoot;
            File root = new File(activity.getFilesDir(), "orca-" + ORCA_ASSET_VERSION);
            File marker = new File(root, ".complete");
            if (!marker.isFile()) {
                deleteRecursively(root);
                ensureDirectory(root);
                copyAssetTree(activity.getAssets(), ORCA_ASSET_ROOT, root);
                Files.writeString(marker.toPath(), ORCA_ASSET_VERSION, StandardCharsets.UTF_8);
            }
            extractedRoot = root;
            return root;
        }
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File destination)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            ensureDirectory(destination);
            for (String child : children) {
                copyAssetTree(assets, assetPath + "/" + child, new File(destination, child));
            }
            return;
        }

        File parent = destination.getParentFile();
        if (parent != null) ensureDirectory(parent);
        try (InputStream input = new BufferedInputStream(assets.open(assetPath));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
    }

    private static String resolveProfile(File profileRoot, JSONObject request,
                                         String key, String defaultRelative) throws IOException {
        String relative = request.optString(key, defaultRelative);
        File candidate = new File(profileRoot, relative).getCanonicalFile();
        File canonicalRoot = profileRoot.getCanonicalFile();
        if (!candidate.getPath().startsWith(canonicalRoot.getPath() + File.separator)) {
            throw new IOException("Profile path leaves the bundled Orca profile directory");
        }
        if (!candidate.isFile()) throw new IOException("Bundled Orca profile not found: " + relative);
        return candidate.getAbsolutePath();
    }

    private File checkedOutput(String path) throws IOException {
        if (path == null || path.isBlank()) throw new IOException("Missing G-code path");
        File root = new File(activity.getCacheDir(), "orca-output").getCanonicalFile();
        File output = new File(path).getCanonicalFile();
        if (!output.getPath().startsWith(root.getPath() + File.separator) || !output.isFile()) {
            throw new IOException("Invalid Orca G-code path");
        }
        return output;
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
        if (stagedModel != null && stagedModel.exists()) stagedModel.delete();
        stagedModel = null;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create directory: " + directory);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (!file.delete()) throw new IOException("Cannot delete stale Orca asset: " + file);
    }

    private static String sanitizeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "model.stl" : filename;
        value = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return value.length() > 120 ? value.substring(value.length() - 120) : value;
    }
}
