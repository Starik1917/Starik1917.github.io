cmake_minimum_required(VERSION 3.22.1)
project(model_lab_orca LANGUAGES C CXX)

include(GNUInstallDirs)

set(CMAKE_CXX_STANDARD 17)
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
if(NOT EXISTS "${ORCA_DEP_PREFIX}/include")
    message(FATAL_ERROR "ORCA_DEP_PREFIX does not contain Android Orca dependencies")
endif()

list(PREPEND CMAKE_PREFIX_PATH "${ORCA_DEP_PREFIX}")
list(APPEND CMAKE_MODULE_PATH "${ORCA_SOURCE_DIR}/cmake/modules")

set(SLIC3R_GUI OFF CACHE BOOL "" FORCE)
set(SLIC3R_PCH OFF CACHE BOOL "" FORCE)
set(SLIC3R_STATIC ON CACHE BOOL "" FORCE)
set(SLIC3R_PROFILE OFF CACHE BOOL "" FORCE)
set(SLIC3R_SYNTAXONLY OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ORCA_TOOLS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(TBB_STATIC 1)
set(Boost_USE_STATIC_LIBS ON)
set(Boost_NO_SYSTEM_PATHS TRUE)

set(SLIC3R_APP_NAME "Model Lab 3D")
set(SLIC3R_APP_KEY "ModelLab3D")
set(SLIC3R_VERSION "4.0.0-alpha1")
set(SoftFever_VERSION "2.3.1-model-lab")
set(SLIC3R_BUILD_ID "android-arm64")
set(BBL_INTERNAL_TESTING 0)
set(ORCA_CHECK_GCODE_PLACEHOLDERS 0)
set(SLIC3R_RESOURCES_DIR "${ORCA_SOURCE_DIR}/resources")
set(LIBDIR "${ORCA_SOURCE_DIR}/src")
set(LIBDIR_BIN "${CMAKE_BINARY_DIR}/orca-generated/src")

add_compile_definitions(
    ORCA_ANDROID_PORT=1
    BBL_RELEASE_TO_PUBLIC=1
    GIT_COMMIT_HASH="e1c0ea0"
    NDEBUG
)
add_compile_options(
    -fsigned-char
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-unused-but-set-variable
    -Wno-unused-label
    -Wno-unused-local-typedefs
    -Wno-sign-compare
    -Wno-misleading-indentation
    -Wno-switch
    -Wno-ignored-attributes
    -Wno-deprecated-declarations
)

# Orca's normal top-level build defines this check for desktop builds. The
# Android cross-build cannot execute target binaries during configuration.
function(encoding_check target_name)
    message(STATUS "Skipping host encoding check for Android target ${target_name}")
endfunction()

find_package(Boost 1.83.0 REQUIRED COMPONENTS
    system filesystem thread log log_setup locale regex chrono atomic
    date_time iostreams program_options nowide)
find_package(Eigen3 5.0.1 REQUIRED)
find_package(TBB REQUIRED)
find_package(OpenSSL REQUIRED)
find_package(ZLIB REQUIRED)
find_package(EXPAT REQUIRED)
find_package(PNG REQUIRED)
find_package(cereal REQUIRED)
find_package(NLopt 1.4 REQUIRED)
find_package(libnoise REQUIRED)

add_library(boost_headeronly INTERFACE)
target_include_directories(boost_headeronly SYSTEM INTERFACE ${Boost_INCLUDE_DIRS})
add_library(boost_libs INTERFACE)
target_link_libraries(boost_libs INTERFACE boost_headeronly ${Boost_LIBRARIES})

# The preparation script removes only desktop/CAD integration code. Arachne,
# perimeters, bridges, infill, supports, tree supports and G-code stay upstream.
add_subdirectory("${ORCA_SOURCE_DIR}/deps_src" "${CMAKE_BINARY_DIR}/orca-deps-src" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_SOURCE_DIR}/src/libslic3r" "${CMAKE_BINARY_DIR}/orca-libslic3r" EXCLUDE_FROM_ALL)

add_library(model_lab_orca SHARED
    orca_core_bridge.cpp
    orca_jni.cpp
)

target_include_directories(model_lab_orca PRIVATE
    "${ORCA_SOURCE_DIR}/src"
    "${CMAKE_BINARY_DIR}/orca-libslic3r"
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
