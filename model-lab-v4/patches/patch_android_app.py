from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit("usage: patch_android_app.py <android-project> <model-lab-v4> <libmodel_lab_orca.so>")

project = Path(sys.argv[1])
port_root = Path(sys.argv[2])
native_library = Path(sys.argv[3])
if not native_library.is_file():
    raise RuntimeError(f"Native Orca library does not exist: {native_library}")

app = project / "app"
java_dir = app / "src/main/java/com/starik/modelviewer"
jni_dir = app / "src/main/jniLibs/arm64-v8a"
java_dir.mkdir(parents=True, exist_ok=True)
jni_dir.mkdir(parents=True, exist_ok=True)

for name in ["OrcaNative.java", "OrcaWebBridge.java"]:
    shutil.copy2(port_root / "android" / name, java_dir / name)
shutil.copy2(native_library, jni_dir / "libmodel_lab_orca.so")

main_activity_path = java_dir / "MainActivity.java"
main = main_activity_path.read_text(encoding="utf-8")

replacements = [
    (
        "    private WebView webView;\n",
        "    private WebView webView;\n    private OrcaWebBridge orcaWebBridge;\n",
        "OrcaWebBridge field",
    ),
    (
        '        webView.addJavascriptInterface(new UiBridge(), "AndroidUi");\n',
        '        webView.addJavascriptInterface(new UiBridge(), "AndroidUi");\n'
        '        orcaWebBridge = new OrcaWebBridge(this, webView);\n'
        '        webView.addJavascriptInterface(orcaWebBridge, "AndroidOrca");\n',
        "WebView bridge registration",
    ),
    (
        "        clearPendingSave();\n        if (webView != null) {\n",
        "        clearPendingSave();\n"
        "        if (orcaWebBridge != null) {\n"
        "            orcaWebBridge.destroy();\n"
        "            orcaWebBridge = null;\n"
        "        }\n"
        "        if (webView != null) {\n",
        "bridge cleanup",
    ),
    (
        '            webView.removeJavascriptInterface("AndroidUi");\n',
        '            webView.removeJavascriptInterface("AndroidUi");\n'
        '            webView.removeJavascriptInterface("AndroidOrca");\n',
        "JavaScript interface cleanup",
    ),
]
for old, new, label in replacements:
    if old not in main:
        raise RuntimeError(f"MainActivity anchor not found: {label}")
    main = main.replace(old, new, 1)
main_activity_path.write_text(main, encoding="utf-8")

gradle_path = app / "build.gradle"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 10", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '4.0.0-alpha1'", gradle, count=1)

default_anchor = """        targetSdk 34
        versionCode 10
        versionName '4.0.0-alpha1'
"""
default_replacement = default_anchor + """
        ndk {
            abiFilters 'arm64-v8a'
        }
"""
if default_anchor not in gradle:
    raise RuntimeError("Android defaultConfig anchor not found")
gradle = gradle.replace(default_anchor, default_replacement, 1)

gradle_path.write_text(gradle, encoding="utf-8")
print("Integrated the prebuilt arm64 OrcaSlicer core into the Android application")
