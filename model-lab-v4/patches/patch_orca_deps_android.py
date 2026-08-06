from __future__ import annotations

import re
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


# Orca's dependency helper forwards the Android toolchain file to nested CMake
# projects, but not the selected ABI/API/STL. The NDK then falls back to its
# default 32-bit ARM ABI, which produced 32-bit Boost packages inside an
# arm64-v8a build. Forward the complete Android target tuple to every nested
# dependency project.
android_toolchain_anchor = """            -DCMAKE_TOOLCHAIN_FILE:STRING=${CMAKE_TOOLCHAIN_FILE}
            -DCMAKE_EXE_LINKER_FLAGS:STRING=${CMAKE_EXE_LINKER_FLAGS}
"""
android_toolchain_replacement = """            -DCMAKE_TOOLCHAIN_FILE:STRING=${CMAKE_TOOLCHAIN_FILE}
            -DCMAKE_SYSTEM_NAME:STRING=Android
            -DCMAKE_ANDROID_NDK:PATH=${CMAKE_ANDROID_NDK}
            -DCMAKE_ANDROID_ARCH_ABI:STRING=${CMAKE_ANDROID_ARCH_ABI}
            -DCMAKE_SYSTEM_VERSION:STRING=${CMAKE_SYSTEM_VERSION}
            -DANDROID_ABI:STRING=${ANDROID_ABI}
            -DANDROID_PLATFORM:STRING=${ANDROID_PLATFORM}
            -DANDROID_STL:STRING=${ANDROID_STL}
            -DCMAKE_EXE_LINKER_FLAGS:STRING=${CMAKE_EXE_LINKER_FLAGS}
"""
if android_toolchain_anchor not in text:
    raise RuntimeError("Orca dependency helper Android forwarding anchor not found")
text = text.replace(android_toolchain_anchor, android_toolchain_replacement, 1)

# These libraries serve the wx/OpenGL desktop application or optional CAD/
# networking features and are not part of the local FDM toolpath engine.
remove_once("include(GLEW/GLEW.cmake)\n", "GLEW")
remove_once("include(GLFW/GLFW.cmake)\n", "GLFW")
remove_once("include(OpenCSG/OpenCSG.cmake)\n", "OpenCSG")
remove_once("include(Blosc/Blosc.cmake)\n", "Blosc")
remove_once("include(OpenEXR/OpenEXR.cmake)\n", "OpenEXR")
remove_once("include(OpenVDB/OpenVDB.cmake)\n", "OpenVDB")
remove_once("include(OCCT/OCCT.cmake)\n", "OpenCASCADE STEP import")
remove_once("include(OpenCV/OpenCV.cmake)\n", "OpenCV color utility")

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

curl_block = '''set(CURL_PKG "")
if (NOT OPENSSL_FOUND OR NOT CURL_FOUND)
    include(CURL/CURL.cmake)
    set(CURL_PKG dep_CURL)
endif ()
'''
if curl_block not in text:
    raise RuntimeError("Orca dependency patch anchor not found: CURL")
text = text.replace(
    curl_block,
    '# Android FDM core performs no printer-host networking.\nset(CURL_PKG "")\n',
    1,
)

freetype_block = '''set(FREETYPE_PKG "")
if(NOT FREETYPE_FOUND)
    include(FREETYPE/FREETYPE.cmake)
    set(FREETYPE_PKG "dep_FREETYPE")
endif()
'''
if freetype_block not in text:
    raise RuntimeError("Orca dependency patch anchor not found: Freetype")
text = text.replace(
    freetype_block,
    '# Android FDM core omits OCCT text/STEP rendering.\nset(FREETYPE_PKG "")\n',
    1,
)

for entry in [
    "    ${CURL_PKG}\n",
    "    ${WXWIDGETS_PKG}\n",
    "    ${FREETYPE_PKG}\n",
    "    dep_OpenVDB\n",
    "    dep_OpenCSG\n",
    "    dep_OpenCV\n",
    "    dep_GLFW\n",
    "    dep_OCCT\n",
]:
    if entry not in text:
        raise RuntimeError(f"Orca dependency list entry not found: {entry.strip()}")
    text = text.replace(entry, "", 1)

cmake.write_text(text, encoding="utf-8")

# Explicitly select the 64-bit ARM Boost.Context assembly. With the forwarded
# Android ABI this should also be detected automatically, but setting it here
# prevents Boost from silently selecting its 32-bit ARM context implementation.
boost_cmake = root / "deps/Boost/Boost.cmake"
boost_text = boost_cmake.read_text(encoding="utf-8")
boost_anchor = """if (APPLE AND CMAKE_OSX_ARCHITECTURES)
    if (CMAKE_OSX_ARCHITECTURES MATCHES "x86")
        set(_context_abi_line "-DBOOST_CONTEXT_ABI:STRING=sysv")
    elseif (CMAKE_OSX_ARCHITECTURES MATCHES "arm")
        set (_context_abi_line "-DBOOST_CONTEXT_ABI:STRING=aapcs")
    endif ()
    set(_context_arch_line "-DBOOST_CONTEXT_ARCHITECTURE:STRING=${CMAKE_OSX_ARCHITECTURES}")
endif ()
"""
boost_replacement = boost_anchor + """
if (ANDROID)
    set(_context_abi_line "-DBOOST_CONTEXT_ABI:STRING=aapcs")
    set(_context_arch_line "-DBOOST_CONTEXT_ARCHITECTURE:STRING=arm64")
endif ()
"""
if boost_anchor not in boost_text:
    raise RuntimeError("Boost Android architecture anchor not found")
boost_text = boost_text.replace(boost_anchor, boost_replacement, 1)
boost_cmake.write_text(boost_text, encoding="utf-8")

# OpenSSL's upstream dependency recipe treats every non-Apple cross build as
# Linux and invokes `./config linux-aarch64`. OpenSSL then detects the x86_64
# CI host and aborts. Android must use its explicit Configure target and keep
# the NDK compiler wrappers on PATH for both configure and make.
openssl_cmake = root / "deps/OpenSSL/OpenSSL.cmake"
openssl_text = openssl_cmake.read_text(encoding="utf-8")
openssl_setup_pattern = re.compile(
    r"if\(DEFINED OPENSSL_ARCH\).*?\nendif\(\)\n\nExternalProject_Add\(dep_OpenSSL",
    re.DOTALL,
)
openssl_setup = '''set(_android_api_line "")

if(ANDROID)
    if(NOT CMAKE_ANDROID_NDK)
        message(FATAL_ERROR "CMAKE_ANDROID_NDK is required to build OpenSSL for Android")
    endif()
    if(NOT ANDROID_API_LEVEL)
        set(ANDROID_API_LEVEL 26)
    endif()
    set(_openssl_toolchain_bin "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin")
    set(_openssl_env
        ${CMAKE_COMMAND} -E env
        "ANDROID_NDK_ROOT=${CMAKE_ANDROID_NDK}"
        "ANDROID_NDK_HOME=${CMAKE_ANDROID_NDK}"
        "PATH=${_openssl_toolchain_bin}:$ENV{PATH}")
    set(_conf_cmd ${_openssl_env} perl Configure)
    set(_cross_arch "android-arm64")
    set(_cross_comp_prefix_line "")
    set(_android_api_line "-D__ANDROID_API__=${ANDROID_API_LEVEL}")
    set(_make_cmd ${_openssl_env} make -j${NPROC})
    set(_install_cmd ${_openssl_env} make -j${NPROC} install_sw)
elseif(DEFINED OPENSSL_ARCH)
    set(_cross_arch ${OPENSSL_ARCH})
endif()

if(NOT ANDROID)
    if(WIN32)
        if("${CMAKE_GENERATOR_PLATFORM}" STREQUAL "ARM64")
            set(_cross_arch "VC-WIN64-ARM")
        else()
            set(_cross_arch "VC-WIN64A")
        endif()
    elseif(APPLE)
        set(_cross_arch "darwin64-${CMAKE_OSX_ARCHITECTURES}-cc")
    endif()

    if(WIN32)
        set(_conf_cmd perl Configure)
        set(_cross_comp_prefix_line "")
        set(_make_cmd nmake)
        set(_install_cmd nmake install_sw)
    else()
        if(APPLE)
            set(_conf_cmd export MACOSX_DEPLOYMENT_TARGET=${CMAKE_OSX_DEPLOYMENT_TARGET} && ./Configure -mmacosx-version-min=${CMAKE_OSX_DEPLOYMENT_TARGET})
        else()
            set(_conf_cmd env "CC=${CMAKE_C_COMPILER}" "LDFLAGS=${CMAKE_EXE_LINKER_FLAGS}" "./config")
        endif()
        set(_cross_comp_prefix_line "")
        set(_make_cmd make -j${NPROC})
        set(_install_cmd make -j${NPROC} install_sw)
        if(CMAKE_CROSSCOMPILING)
            set(_cross_comp_prefix_line "--cross-compile-prefix=${TOOLCHAIN_PREFIX}-")
            if(${CMAKE_SYSTEM_PROCESSOR} STREQUAL "aarch64" OR ${CMAKE_SYSTEM_PROCESSOR} STREQUAL "arm64")
                set(_cross_arch "linux-aarch64")
            elseif(${CMAKE_SYSTEM_PROCESSOR} STREQUAL "armhf")
                set(_cross_arch "linux-armv4")
            endif()
        endif()
    endif()
endif()

ExternalProject_Add(dep_OpenSSL'''
openssl_text, count = openssl_setup_pattern.subn(openssl_setup, openssl_text, count=1)
if count != 1:
    raise RuntimeError(f"Expected one OpenSSL setup block, patched {count}")

configure_anchor = '''\tCONFIGURE_COMMAND ${_conf_cmd} ${_cross_arch}
        "--openssldir=${DESTDIR}"'''
configure_replacement = '''\tCONFIGURE_COMMAND ${_conf_cmd} ${_cross_arch}
        ${_android_api_line}
        "--openssldir=${DESTDIR}"'''
if configure_anchor not in openssl_text:
    raise RuntimeError("OpenSSL Configure command anchor not found")
openssl_text = openssl_text.replace(configure_anchor, configure_replacement, 1)

if "        no-tests\n" not in openssl_text:
    openssl_text = openssl_text.replace(
        "        no-dynamic-engine\n",
        "        no-dynamic-engine\n        no-tests\n",
        1,
    )

openssl_cmake.write_text(openssl_text, encoding="utf-8")
print("Patched Orca dependencies for Android arm64-v8a FDM core")
