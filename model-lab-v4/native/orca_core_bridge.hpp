#pragma once

#include <functional>
#include <string>

namespace model_lab::orca {

struct SliceRequest {
    std::string model_path;
    std::string output_gcode_path;
    std::string resources_dir;
    std::string data_dir;
    std::string machine_profile_path;
    std::string process_profile_path;
    std::string filament_profile_path;
    std::string profile_root;
    std::string config_overrides_json;
};

struct SliceResult {
    bool ok { false };
    std::string error;
    std::string gcode_path;
    std::string report_json;
};

using ProgressCallback = std::function<void(int percent, const std::string& stage)>;

// Runs the real OrcaSlicer FDM pipeline through libslic3r.
// There is deliberately no custom or fallback slicing implementation here.
SliceResult slice_with_orca(const SliceRequest& request, const ProgressCallback& progress);

} // namespace model_lab::orca
