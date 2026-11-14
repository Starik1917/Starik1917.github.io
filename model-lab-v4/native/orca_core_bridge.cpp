#include "orca_core_bridge.hpp"

#include <algorithm>
#include <fstream>
#include <initializer_list>
#include <limits>
#include <map>
#include <set>
#include <stdexcept>
#include <unordered_map>
#include <utility>
#include <vector>

#include <boost/filesystem.hpp>
#include <nlohmann/json.hpp>

#include "libslic3r/Config.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Utils.hpp"

namespace fs = boost::filesystem;
using nlohmann::json;

namespace model_lab::orca {
namespace {

json read_json(const fs::path& path)
{
    std::ifstream input(path.string());
    if (!input)
        throw std::runtime_error("Cannot open Orca profile: " + path.string());
    json value;
    input >> value;
    return value;
}

std::unordered_map<std::string, fs::path> index_profiles(const fs::path& profile_root)
{
    std::unordered_map<std::string, fs::path> result;
    if (!fs::exists(profile_root))
        throw std::runtime_error("Profile root does not exist: " + profile_root.string());

    for (fs::recursive_directory_iterator iterator(profile_root), end; iterator != end; ++iterator) {
        if (!fs::is_regular_file(iterator->path()) || iterator->path().extension() != ".json")
            continue;
        try {
            const json value = read_json(iterator->path());
            if (value.contains("name") && value["name"].is_string())
                result.emplace(value["name"].get<std::string>(), iterator->path());
        } catch (...) {
            // Vendor indexes and unrelated metadata are allowed in the profile tree.
        }
    }
    return result;
}

void load_profile_recursive(
    Slic3r::DynamicPrintConfig& config,
    const fs::path& profile,
    const std::unordered_map<std::string, fs::path>& profile_index,
    std::set<std::string>& loading,
    std::set<std::string>& loaded)
{
    const std::string canonical = fs::canonical(profile).string();
    if (loaded.find(canonical) != loaded.end())
        return;
    if (!loading.insert(canonical).second)
        throw std::runtime_error("Cyclic Orca profile inheritance at " + canonical);

    const json descriptor = read_json(profile);
    if (descriptor.contains("inherits") && descriptor["inherits"].is_string()) {
        const std::string parent_name = descriptor["inherits"].get<std::string>();
        if (!parent_name.empty()) {
            const auto parent = profile_index.find(parent_name);
            if (parent == profile_index.end())
                throw std::runtime_error("Orca parent profile not found: " + parent_name);
            load_profile_recursive(config, parent->second, profile_index, loading, loaded);
        }
    }

    std::map<std::string, std::string> raw_values;
    std::string reason;
    config.load_from_json(profile.string(), Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent, raw_values, reason);
    if (!reason.empty())
        throw std::runtime_error("Cannot load Orca profile " + profile.string() + ": " + reason);

    loading.erase(canonical);
    loaded.insert(canonical);
}

std::string override_value(const json& value)
{
    if (value.is_string())
        return value.get<std::string>();
    if (value.is_boolean())
        return value.get<bool>() ? "1" : "0";
    if (value.is_number_integer())
        return std::to_string(value.get<long long>());
    if (value.is_number_unsigned())
        return std::to_string(value.get<unsigned long long>());
    if (value.is_number_float())
        return value.dump();
    throw std::runtime_error("Orca setting override must be a scalar value");
}

void apply_config_overrides(Slic3r::DynamicPrintConfig& config, const std::string& overrides_json)
{
    if (overrides_json.empty())
        return;
    const json overrides = json::parse(overrides_json);
    if (!overrides.is_object())
        throw std::runtime_error("Orca configOverrides must be a JSON object");

    Slic3r::ConfigSubstitutionContext substitutions(
        Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent);
    for (json::const_iterator item = overrides.begin(); item != overrides.end(); ++item)
        config.set_deserialize(item.key(), override_value(item.value()), substitutions, false);
}

void load_profile_stack(Slic3r::DynamicPrintConfig& config, const SliceRequest& request)
{
    const auto profile_index = index_profiles(fs::path(request.profile_root));
    std::set<std::string> loading;
    std::set<std::string> loaded;

    for (const std::string* profile : {
            &request.machine_profile_path,
            &request.process_profile_path,
            &request.filament_profile_path }) {
        if (profile->empty())
            throw std::runtime_error("An Orca machine, process or filament profile was not supplied");
        load_profile_recursive(config, fs::path(*profile), profile_index, loading, loaded);
    }

    apply_config_overrides(config, request.config_overrides_json);
    Slic3r::Preset::normalize(config);
}

Slic3r::Vec2d printable_area_center(const Slic3r::DynamicPrintConfig& config)
{
    const Slic3r::ConfigOptionPoints* area = config.opt<Slic3r::ConfigOptionPoints>("printable_area");
    if (area == nullptr || area->values.empty())
        return Slic3r::Vec2d(110.0, 110.0);

    double min_x = std::numeric_limits<double>::max();
    double min_y = std::numeric_limits<double>::max();
    double max_x = std::numeric_limits<double>::lowest();
    double max_y = std::numeric_limits<double>::lowest();
    for (const Slic3r::Vec2d& point : area->values) {
        min_x = std::min(min_x, point.x());
        min_y = std::min(min_y, point.y());
        max_x = std::max(max_x, point.x());
        max_y = std::max(max_y, point.y());
    }
    return Slic3r::Vec2d((min_x + max_x) * 0.5, (min_y + max_y) * 0.5);
}

void validate_request(const SliceRequest& request)
{
    for (const auto& pair : std::initializer_list<std::pair<const char*, const std::string*> >{
            {"model", &request.model_path},
            {"output G-code", &request.output_gcode_path},
            {"resources", &request.resources_dir},
            {"profile root", &request.profile_root}}) {
        if (pair.second->empty())
            throw std::runtime_error(std::string("Missing ") + pair.first + " path");
    }
    if (!fs::is_regular_file(request.model_path))
        throw std::runtime_error("Model file does not exist: " + request.model_path);
}

} // namespace

SliceResult slice_with_orca(const SliceRequest& request, const ProgressCallback& progress)
{
    SliceResult result;
    try {
        validate_request(request);
        if (progress) progress(1, "Инициализация OrcaSlicer");

        Slic3r::set_resources_dir(request.resources_dir);
        if (!request.data_dir.empty())
            Slic3r::set_data_dir(request.data_dir);
        Slic3r::set_temporary_dir(fs::temp_directory_path().string());

        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        load_profile_stack(config, request);
        if (progress) progress(8, "Загрузка официальных профилей Orca");

        Slic3r::Model model;
        if (!Slic3r::load_stl(request.model_path.c_str(), &model))
            throw std::runtime_error("OrcaSlicer could not load the STL model");
        if (model.objects.empty())
            throw std::runtime_error("The STL contains no printable objects");

        model.add_default_instances();
        model.center_instances_around_point(printable_area_center(config));
        model.adjust_min_z();

        Slic3r::Print print;
        for (Slic3r::ModelObject* object : model.objects) {
            object->ensure_on_bed();
            print.auto_assign_extruders(object);
        }

        print.set_status_callback([&progress](const Slic3r::PrintBase::SlicingStatus& status) {
            if (progress && status.percent >= 0)
                progress(std::max(0, std::min(status.percent, 100)), status.text);
        });

        print.apply(model, config);
        std::vector<Slic3r::StringObjectException> warnings;
        const Slic3r::StringObjectException validation = print.validate(&warnings);
        if (!validation.string.empty() && !validation.is_warning)
            throw std::runtime_error(validation.string);

        print.process();
        if (progress) progress(94, "Генерация G-code OrcaSlicer");
        print.export_gcode(request.output_gcode_path, nullptr, nullptr);

        if (!fs::is_regular_file(request.output_gcode_path) || fs::file_size(request.output_gcode_path) == 0)
            throw std::runtime_error("OrcaSlicer produced an empty G-code file");

        json report = {
            {"engine", "OrcaSlicer/libslic3r"},
            {"gcodeBytes", fs::file_size(request.output_gcode_path)},
            {"warnings", json::array()}
        };
        for (const auto& warning : warnings)
            report["warnings"].push_back(warning.string);

        result.ok = true;
        result.gcode_path = request.output_gcode_path;
        result.report_json = report.dump();
        if (progress) progress(100, "Готово");
    } catch (const std::exception& exception) {
        result.error = exception.what();
    } catch (...) {
        result.error = "Unknown OrcaSlicer error";
    }
    return result;
}

} // namespace model_lab::orca
