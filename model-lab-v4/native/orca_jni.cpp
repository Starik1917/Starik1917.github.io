#include <jni.h>

#include <string>

#include <nlohmann/json.hpp>

#include "orca_core_bridge.hpp"

using nlohmann::json;

namespace {

std::string from_jstring(JNIEnv* env, jstring value)
{
    if (value == nullptr)
        return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr)
        return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_jstring(JNIEnv* env, const std::string& value)
{
    return env->NewStringUTF(value.c_str());
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_starik_modelviewer_OrcaNative_nativeSlice(
    JNIEnv* env,
    jclass,
    jstring request_json)
{
    json response;
    try {
        const json request = json::parse(from_jstring(env, request_json));
        model_lab::orca::SliceRequest native_request;
        native_request.model_path = request.value("modelPath", "");
        native_request.output_gcode_path = request.value("outputGcodePath", "");
        native_request.resources_dir = request.value("resourcesDir", "");
        native_request.data_dir = request.value("dataDir", "");
        native_request.machine_profile_path = request.value("machineProfilePath", "");
        native_request.process_profile_path = request.value("processProfilePath", "");
        native_request.filament_profile_path = request.value("filamentProfilePath", "");
        native_request.profile_root = request.value("profileRoot", "");
        native_request.config_overrides_json = request.value("configOverrides", json::object()).dump();

        const auto result = model_lab::orca::slice_with_orca(native_request, {});
        response = {
            {"ok", result.ok},
            {"error", result.error},
            {"gcodePath", result.gcode_path},
            {"report", result.report_json.empty() ? json::object() : json::parse(result.report_json)}
        };
    } catch (const std::exception& exception) {
        response = {{"ok", false}, {"error", exception.what()}};
    }
    return to_jstring(env, response.dump());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_starik_modelviewer_OrcaNative_nativeEngineName(JNIEnv* env, jclass)
{
    return to_jstring(env, "OrcaSlicer/libslic3r");
}
