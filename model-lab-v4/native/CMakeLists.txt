cmake_minimum_required(VERSION 3.22.1)
project(model_lab_orca LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT ANDROID)
    message(FATAL_ERROR "The Model Lab Orca bridge is an Android NDK target")
endif()

set(ORCA_SOURCE_DIR "" CACHE PATH "Pinned OrcaSlicer source tree")
set(ORCA_DEP_PREFIX "" CACHE PATH "Android-built Orca dependency prefix")

if(NOT EXISTS "${ORCA_SOURCE_DIR}/src/libslic3r/CMakeLists.txt")
    message(FATAL_ERROR "ORCA_SOURCE_DIR does not contain the pinned OrcaSlicer source")
endif()
if(NOT EXISTS "${ORCA_DEP_PREFIX}")
    message(FATAL_ERROR "ORCA_DEP_PREFIX does not contain Android Orca dependencies")
endif()

list(PREPEND CMAKE_PREFIX_PATH "${ORCA_DEP_PREFIX}")
set(SLIC3R_GUI OFF CACHE BOOL "" FORCE)
set(SLIC3R_PCH OFF CACHE BOOL "" FORCE)
set(SLIC3R_STATIC ON CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ORCA_TOOLS OFF CACHE BOOL "" FORCE)

# The Android preparation script patches only platform/build glue. The slicing
# algorithms remain the pinned upstream OrcaSlicer sources.
add_subdirectory("${ORCA_SOURCE_DIR}/deps_src" "${CMAKE_BINARY_DIR}/orca-deps-src" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_SOURCE_DIR}/src/libslic3r" "${CMAKE_BINARY_DIR}/orca-libslic3r" EXCLUDE_FROM_ALL)

add_library(model_lab_orca SHARED
    orca_core_bridge.cpp
    orca_jni.cpp
)

target_include_directories(model_lab_orca PRIVATE
    "${ORCA_SOURCE_DIR}/src"
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

target_compile_definitions(model_lab_orca PRIVATE
    MODEL_LAB_ORCA_NATIVE=1
    ORCA_ANDROID_PORT=1
)

target_compile_options(model_lab_orca PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
    -fexceptions
    -frtti
)

find_library(ANDROID_LOG_LIBRARY log REQUIRED)
find_library(ANDROID_LIBRARY android REQUIRED)

target_link_libraries(model_lab_orca PRIVATE
    libslic3r
    ${ANDROID_LOG_LIBRARY}
    ${ANDROID_LIBRARY}
)
