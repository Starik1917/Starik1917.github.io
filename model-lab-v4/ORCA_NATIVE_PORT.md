# Model Lab 3D v4 — native OrcaSlicer port

This branch abandons the custom JavaScript slicing engine. No APK from this branch may expose slicing until the native OrcaSlicer core is compiled and passes parity tests.

## Upstream

- Repository: OrcaSlicer/OrcaSlicer
- Pinned commit: e1c0ea0cc48a05bdf7e57bc4f3a0fe0b54370c46
- License: AGPL-3.0
- Core target: src/libslic3r

## Architecture

1. Keep the existing Android/mobile Material interface.
2. Remove all custom contour, wall, infill, support and G-code generation code from JavaScript.
3. Build OrcaSlicer `libslic3r` and its required dependencies for Android arm64-v8a with the Android NDK.
4. Expose a small JNI bridge:
   - load model file;
   - load printer/process/filament profiles;
   - apply transforms;
   - slice;
   - export G-code;
   - export preview moves and statistics;
   - cancel and report progress.
5. Use official Orca profiles, including Flashforge Adventurer 5M, without reimplementing settings.
6. Render layers from Orca's real preview/G-code roles rather than reconstructed JS paths.

## Release gate

A build is not releasable until all of these pass:

- The vivo X200 Ultra case back panel is present in every expected layer.
- The same STL and profile produce matching layer count, feature roles and extrusion geometry in desktop OrcaSlicer and Android.
- G-code is generated only by the native Orca core.
- No JavaScript fallback slicer remains reachable.
- Full corresponding source and license notices are shipped under AGPL-3.0.

## Dependency work

The upstream core is a large C++ static library and links CGAL, OpenCV, OpenCASCADE, TBB, Boost, Eigen, Clipper/Clipper2, libigl, libnest2d, mcut, Qhull, zlib, OpenSSL and other libraries. Android build files must port or selectively disable desktop-only features while preserving FDM slicing behavior.
