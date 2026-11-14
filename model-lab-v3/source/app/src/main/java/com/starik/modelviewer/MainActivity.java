package com.starik.modelviewer;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 501;
    private static final int SAVE_DOCUMENT_REQUEST = 502;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final Object saveLock = new Object();
    private StringBuilder pendingSaveText;
    private String pendingSaveName;
    private String pendingSaveMime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars(true);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(15, 15, 18));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.addJavascriptInterface(new FileBridge(), "AndroidFiles");
        webView.addJavascriptInterface(new UiBridge(), "AndroidUi");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                return !("file".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams
            ) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (RuntimeException exception) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return false;
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void applySystemBars(boolean darkTheme) {
        int color = darkTheme ? Color.rgb(15, 15, 18) : Color.rgb(247, 245, 251);
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        int flags = 0;
        if (!darkTheme) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private final class UiBridge {
        @JavascriptInterface
        public void setDarkMode(boolean darkTheme) {
            runOnUiThread(() -> applySystemBars(darkTheme));
        }
    }

    private final class FileBridge {
        @JavascriptInterface
        public void beginSave(String filename, String mimeType) {
            synchronized (saveLock) {
                pendingSaveName = sanitizeFilename(filename);
                pendingSaveMime = mimeType == null || mimeType.isBlank()
                        ? "application/octet-stream"
                        : mimeType;
                pendingSaveText = new StringBuilder();
            }
        }

        @JavascriptInterface
        public void appendSave(String chunk) {
            synchronized (saveLock) {
                if (pendingSaveText != null && chunk != null) {
                    pendingSaveText.append(chunk);
                }
            }
        }

        @JavascriptInterface
        public void finishSave() {
            runOnUiThread(() -> {
                synchronized (saveLock) {
                    if (pendingSaveText == null || pendingSaveName == null) {
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(pendingSaveMime);
                    intent.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
                    try {
                        startActivityForResult(intent, SAVE_DOCUMENT_REQUEST);
                    } catch (RuntimeException exception) {
                        clearPendingSave();
                        Toast.makeText(MainActivity.this, "Не удалось открыть выбор папки", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private static String sanitizeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "model.txt" : filename;
        value = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return value.length() > 160 ? value.substring(value.length() - 160) : value;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            handleFileChooserResult(resultCode, data);
            return;
        }

        if (requestCode == SAVE_DOCUMENT_REQUEST) {
            handleSaveResult(resultCode, data);
        }
    }

    private void handleFileChooserResult(int resultCode, Intent data) {
        if (filePathCallback == null) {
            return;
        }

        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) result = uris.toArray(new Uri[0]);
        }

        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
    }

    private void handleSaveResult(int resultCode, Intent data) {
        String text;
        synchronized (saveLock) {
            text = pendingSaveText == null ? null : pendingSaveText.toString();
        }

        if (resultCode == RESULT_OK && data != null && data.getData() != null && text != null) {
            Uri uri = data.getData();
            try (OutputStream stream = getContentResolver().openOutputStream(uri, "w")) {
                if (stream == null) throw new IllegalStateException("Output stream is null");
                stream.write(text.getBytes(StandardCharsets.UTF_8));
                stream.flush();
                Toast.makeText(this, "Файл сохранён", Toast.LENGTH_SHORT).show();
            } catch (Exception exception) {
                Toast.makeText(this, "Ошибка сохранения: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        clearPendingSave();
    }

    private void clearPendingSave() {
        synchronized (saveLock) {
            pendingSaveText = null;
            pendingSaveName = null;
            pendingSaveMime = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        clearPendingSave();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidFiles");
            webView.removeJavascriptInterface("AndroidUi");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
