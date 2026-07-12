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
list(PREPEND CMAKE_FIND_ROOT_PATH "${ORCA_DEP_PREFIX}")
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
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
set(Boost_DIR "${ORCA_DEP_PREFIX}/lib/cmake/Boost-1.84.0")
foreach(_boost_component
    headers system filesystem thread log log_setup locale regex chrono atomic
    date_time iostreams program_options nowide)
    set("boost_${_boost_component}_DIR"
        "${ORCA_DEP_PREFIX}/lib/cmake/boost_${_boost_component}-1.84.0")
endforeach()

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

function(encoding_check target_name)
    message(STATUS "Skipping host encoding check for Android target ${target_name}")
endfunction()
function(slic3r_remap_configs)
endfunction()

find_package(Boost 1.84.0 EXACT CONFIG REQUIRED COMPONENTS
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
find_package(draco CONFIG REQUIRED)

# Google Draco 1.5.7 exports its static library as draco::draco_static, while
# this pinned Orca revision links the generic draco::draco target. Preserve the
# exact Draco implementation and provide only the target-name compatibility
# alias expected by libslic3r.
if(NOT TARGET draco::draco)
    if(NOT TARGET draco::draco_static)
        message(FATAL_ERROR "The Android Draco package exports neither draco::draco nor draco::draco_static")
    endif()
    add_library(model_lab_draco_compat INTERFACE)
    target_link_libraries(model_lab_draco_compat INTERFACE draco::draco_static)
    add_library(draco::draco ALIAS model_lab_draco_compat)
endif()

# Orca links the modern target name, while the bundled cereal package used by
# the pinned revision may export only include paths. Supply the canonical
# header-only target without substituting any implementation.
if(NOT TARGET cereal::cereal)
    add_library(cereal::cereal INTERFACE IMPORTED)
    set_target_properties(cereal::cereal PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${ORCA_DEP_PREFIX}/include")
endif()

add_library(boost_headeronly INTERFACE)
target_link_libraries(boost_headeronly INTERFACE Boost::headers)
add_library(boost_libs INTERFACE)
target_link_libraries(boost_libs INTERFACE
    boost_headeronly
    Boost::system
    Boost::filesystem
    Boost::thread
    Boost::log
    Boost::log_setup
    Boost::locale
    Boost::regex
    Boost::chrono
    Boost::atomic
    Boost::date_time
    Boost::iostreams
    Boost::program_options
    Boost::nowide
)

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
