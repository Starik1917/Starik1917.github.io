from __future__ import annotations

import re
import sys
from pathlib import Path

root = Path(sys.argv[1])


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Orca Android core patch anchor not found: {label}")
    return text.replace(old, new, 1)


# Build only libraries used by the native FDM engine. Desktop UI, USB/network
# discovery and Markdown widgets remain upstream but are not configured in the
# Android target.
deps_src = root / "deps_src/CMakeLists.txt"
deps_text = deps_src.read_text(encoding="utf-8")
for line, label in [
    ("add_subdirectory(Shiny)\n", "Shiny profiler"),
    ("add_subdirectory(hidapi)\n", "hidapi"),
    ("add_subdirectory(hints)        # Hints library with utility executable\n", "desktop hints"),
    ("add_subdirectory(imgui)\n", "imgui"),
    ("add_subdirectory(imguizmo)\n", "imguizmo"),
    ("add_subdirectory(md4c)\n", "Markdown renderer"),
    ("add_subdirectory(mdns)\n", "mDNS"),
    ("add_subdirectory(minilzo)\n", "minilzo"),
]:
    deps_text = replace_once(deps_text, line, f"# Android FDM core: omitted {label}\n", label)
deps_src.write_text(deps_text, encoding="utf-8")

# STEP import is a CAD convenience feature backed by OpenCASCADE. The Android
# application currently accepts mesh formats; removing STEP does not alter any
# FDM slicing algorithm. Keep the same public names as no-op stubs so Model.cpp
# can retain the upstream control flow without linking OCCT.
step_header = root / "src/libslic3r/Format/STEP.hpp"
step_header.write_text(r'''#ifndef slic3r_Format_STEP_hpp_
#define slic3r_Format_STEP_hpp_

#include <atomic>
#include <functional>
#include <string>

#include <boost/filesystem/path.hpp>

namespace Slic3r {

class Model;

constexpr int LOAD_STEP_STAGE_READ_FILE = 0;
constexpr int LOAD_STEP_STAGE_GET_SOLID = 1;
constexpr int LOAD_STEP_STAGE_GET_MESH = 2;
constexpr int LOAD_STEP_STAGE_NUM = 3;
constexpr int LOAD_STEP_STAGE_UNIT_NUM = 5;

using ImportStepProgressFn = std::function<void(int, int, int, bool&)>;
using StepIsUtf8Fn = std::function<void(bool)>;

inline bool load_step(const char*, Model*, bool&, double = 0.003, double = 0.5,
                      bool = false, ImportStepProgressFn = nullptr,
                      StepIsUtf8Fn = nullptr, long& mesh_face_num = *(new long(-1)))
{
    mesh_face_num = -1;
    return false;
}

class StepPreProcessor {
public:
    bool preprocess(const char*, std::string&) { return false; }
    static bool isUtf8File(const char*) { return false; }
    static bool isUtf8(const std::string) { return false; }
};

class Step {
public:
    enum class Step_Status { LOAD_SUCCESS, LOAD_ERROR, CANCEL, MESH_SUCCESS, MESH_ERROR };
    Step(const boost::filesystem::path&, ImportStepProgressFn = nullptr, StepIsUtf8Fn = nullptr) {}
    Step(const std::string&, ImportStepProgressFn = nullptr, StepIsUtf8Fn = nullptr) {}
    ~Step() = default;
    Step_Status load() { return Step_Status::LOAD_ERROR; }
    unsigned int get_triangle_num(double, double) { return 0; }
    unsigned int get_triangle_num_tbb(double, double) { return 0; }
    void clean_mesh_data() {}
    Step_Status mesh(Model*, bool&, bool, double = 0.003, double = 0.5)
    {
        return Step_Status::MESH_ERROR;
    }
    std::atomic<bool> m_stop_mesh { false };
    void update_process(int, int, int, bool&) {}
};

} // namespace Slic3r

#endif
''', encoding="utf-8")

# Orca's SVG importer is also backed by OpenCASCADE. Model.cpp references its
# public entry point, so keep a no-op mesh-import stub and omit only the CAD
# implementation file. STL/OBJ/3MF handling and all FDM algorithms remain.
svg_header = root / "src/libslic3r/Format/svg.hpp"
svg_header.write_text(r'''#pragma once
#include <string>
namespace Slic3r {
class Model;
inline bool load_svg(const char*, Model*, std::string& message)
{
    message = "SVG import is not available in the Android FDM build";
    return false;
}
} // namespace Slic3r
''', encoding="utf-8")

# Flush-volume calculation in libslic3r uses only RGB2HSV from a desktop GUI
# color helper. The upstream implementation file also pulls wxWidgets, which is
# not part of the slicing engine. Provide the same mathematical conversion as a
# header-only Android helper and leave all FDM flush-volume logic untouched.
color_header = root / "src/slic3r/Utils/ColorSpaceConvert.hpp"
color_header.parent.mkdir(parents=True, exist_ok=True)
color_header.write_text(r'''#ifndef slic3r_Utils_ColorSpaceConvert_hpp_
#define slic3r_Utils_ColorSpaceConvert_hpp_

#include <algorithm>
#include <cmath>

inline void RGB2HSV(float r, float g, float b, float* h, float* s, float* v)
{
    const float cmax = std::max(std::max(r, g), b);
    const float cmin = std::min(std::min(r, g), b);
    const float delta = cmax - cmin;

    if (std::abs(delta) < 0.001f)
        *h = 0.0f;
    else if (cmax == r)
        *h = 60.0f * std::fmod((g - b) / delta, 6.0f);
    else if (cmax == g)
        *h = 60.0f * ((b - r) / delta + 2.0f);
    else
        *h = 60.0f * ((r - g) / delta + 4.0f);

    *s = std::abs(cmax) < 0.001f ? 0.0f : delta / cmax;
    *v = cmax;
}

#endif
''', encoding="utf-8")

core_cmake = root / "src/libslic3r/CMakeLists.txt"
core = core_cmake.read_text(encoding="utf-8")

for line, label in [
    ("    Format/STEP.cpp\n", "STEP implementation"),
    ("    Format/svg.cpp\n", "OpenCASCADE SVG implementation"),
    ("    Shape/TextShape.cpp\n", "OpenCASCADE text-shape implementation"),
    ("    ObjColorUtils.cpp\n", "OpenCV object color implementation"),
    ("    ObjColorUtils.hpp\n", "OpenCV object color header"),
]:
    core = replace_once(core, line, "", label)

core = replace_once(core, "find_package(OpenCV REQUIRED core)\n", "", "OpenCV find_package")

occt_pattern = re.compile(
    r"# Find the OCCT and related libraries\n.*?set\(OCCT_LIBS\n.*?\n\)\n",
    re.DOTALL,
)
core, count = occt_pattern.subn(
    "# Android FDM core: OpenCASCADE/STEP/SVG/text import is intentionally omitted.\n",
    core,
    count=1,
)
if count != 1:
    raise RuntimeError(f"Expected one OCCT block, patched {count}")

core = replace_once(core, "        opencv_world\n", "", "OpenCV link target")
core = replace_once(core, "        ${OCCT_LIBS}\n", "", "OCCT link targets")

freetype_pattern = re.compile(
    r"if\(NOT WIN32\)\n\s*# Link freetype for OCCT dependency.*?\nendif\(\)\n",
    re.DOTALL,
)
core, count = freetype_pattern.subn(
    "# Android FDM core: no OCCT font-rendering dependency.\n",
    core,
    count=1,
)
if count != 1:
    raise RuntimeError(f"Expected one OCCT/freetype block, patched {count}")

core_cmake.write_text(core, encoding="utf-8")
print("Patched pinned OrcaSlicer sources for the Android FDM-only target")
