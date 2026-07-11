from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

project = Path(sys.argv[1])
port_root = Path(sys.argv[2])

app = project / "app"
java_dir = app / "src/main/java/com/starik/modelviewer"
cpp_dir = app / "src/main/cpp"
java_dir.mkdir(parents=True, exist_ok=True)
cpp_dir.mkdir(parents=True, exist_ok=True)

for name in ["OrcaNative.java", "OrcaWebBridge.java"]:
    shutil.copy2(port_root / "android" / name, java_dir / name)
for name in ["CMakeLists.txt", "orca_core_bridge.hpp", "orca_core_bridge.cpp", "orca_jni.cpp"]:
    shutil.copy2(port_root / "native" / name, cpp_dir / name)

main_activity_path = java_dir / "MainActivity.java"
main = main_activity_path.read_text(encoding="utf-8")

main = main.replace(
    "    private WebView webView;\n",
    "    private WebView webView;\n    private OrcaWebBridge orcaWebBridge;\n",
    1,
)
main = main.replace(
    '        webView.addJavascriptInterface(new UiBridge(), "AndroidUi");\n',
    '        webView.addJavascriptInterface(new UiBridge(), "AndroidUi");\n'
    '        orcaWebBridge = new OrcaWebBridge(this, webView);\n'
    '        webView.addJavascriptInterface(orcaWebBridge, "AndroidOrca");\n',
    1,
)
main = main.replace(
    "        clearPendingSave();\n        if (webView != null) {\n",
    "        clearPendingSave();\n"
    "        if (orcaWebBridge != null) {\n"
    "            orcaWebBridge.destroy();\n"
    "            orcaWebBridge = null;\n"
    "        }\n"
    "        if (webView != null) {\n",
    1,
)
main = main.replace(
    '            webView.removeJavascriptInterface("AndroidUi");\n',
    '            webView.removeJavascriptInterface("AndroidUi");\n'
    '            webView.removeJavascriptInterface("AndroidOrca");\n',
    1,
)
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
        externalNativeBuild {
            cmake {
                arguments "-DORCA_SOURCE_DIR=${project.findProperty('ORCA_SOURCE_DIR') ?: ''}",
                          "-DORCA_DEP_PREFIX=${project.findProperty('ORCA_DEP_PREFIX') ?: ''}"
                cppFlags '-std=c++20 -fexceptions -frtti'
            }
        }
"""
if default_anchor not in gradle:
    raise RuntimeError("Android defaultConfig anchor not found")
gradle = gradle.replace(default_anchor, default_replacement, 1)

android_close = """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}"""
android_replacement = """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.31.1'
        }
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}"""
if android_close not in gradle:
    raise RuntimeError("Android block closing anchor not found")
gradle = gradle.replace(android_close, android_replacement, 1)
gradle_path.write_text(gradle, encoding="utf-8")

print("Integrated Model Lab Android app with the native Orca bridge")
