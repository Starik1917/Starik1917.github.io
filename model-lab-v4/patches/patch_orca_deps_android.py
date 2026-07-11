from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1])
cmake = root / "deps/CMakeLists.txt"
text = cmake.read_text(encoding="utf-8")


def remove_once(block: str, label: str) -> None:
    global text
    if block not in text:
        raise RuntimeError(f"Orca dependency patch anchor not found: {label}")
    text = text.replace(block, f"# Android port: omitted desktop-only dependency ({label})\n", 1)


# These libraries serve the wx/OpenGL desktop application and are not part of
# the FDM toolpath engine used by the Android JNI bridge.
remove_once("include(GLEW/GLEW.cmake)\n", "GLEW")
remove_once("include(GLFW/GLFW.cmake)\n", "GLFW")
remove_once("include(OpenCSG/OpenCSG.cmake)\n", "OpenCSG")
remove_once("include(Blosc/Blosc.cmake)\n", "Blosc")
remove_once("include(OpenEXR/OpenEXR.cmake)\n", "OpenEXR")
remove_once("include(OpenVDB/OpenVDB.cmake)\n", "OpenVDB")

wx_block = '''# flatpak builds wxwidgets separately, so it is not included in the deps target
set(WXWIDGETS_PKG "")
include(wxWidgets/wxWidgets.cmake)
if (NOT FLATPAK)
    set(WXWIDGETS_PKG "dep_wxWidgets")
endif()
'''
if wx_block not in text:
    raise RuntimeError("Orca dependency patch anchor not found: wxWidgets")
text = text.replace(
    wx_block,
    '# Android port: wxWidgets is a desktop UI dependency and is intentionally not built.\nset(WXWIDGETS_PKG "")\n',
    1,
)

for entry in [
    "    ${WXWIDGETS_PKG}\n",
    "    dep_OpenVDB\n",
    "    dep_OpenCSG\n",
    "    dep_GLFW\n",
]:
    if entry not in text:
        raise RuntimeError(f"Orca dependency list entry not found: {entry.strip()}")
    text = text.replace(entry, "", 1)

cmake.write_text(text, encoding="utf-8")
print("Patched Orca dependency graph for Android FDM core")
