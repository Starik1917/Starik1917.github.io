from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "model-lab-v3/source")
MAIN = ROOT / "web/src/main.js"
GRADLE = ROOT / "app/build.gradle"
PACKAGE = ROOT / "package.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Missing replacement anchor: {label}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

main = replace_once(
    main,
    """  let totalPathLength = 0;
  let warningCount = 0;
  for (let layerIndex = 0; layerIndex < layerCount; layerIndex += 1) {""",
    """  updateOperation('Ищем верхние и нижние поверхности', 'Строим локальные сплошные слои');
  const localSolidRegions = computeLocalSolidRegions(
    layers.map((layer) => layer.regions),
    settings.solidLayers,
    lineWidth
  );
  await nextFrame();

  let totalPathLength = 0;
  let warningCount = 0;
  for (let layerIndex = 0; layerIndex < layerCount; layerIndex += 1) {""",
    "local solid region calculation",
)

main = replace_once(
    main,
    """    const inner = offsetPaths(regions, -lineWidth * (settings.walls + 0.25));
    const isSolid = layerIndex < settings.solidLayers || layerIndex >= layerCount - settings.solidLayers;
    const density = isSolid ? 100 : settings.infill;
    if (density > 0 && inner.length) {
      const spacing = isSolid ? lineWidth * 0.92 : Math.max(lineWidth * 1.15, lineWidth / (density / 100));
      const angle = settings.infillPattern === 'grid'
        ? (layerIndex % 2 === 0 ? 45 : -45)
        : (layerIndex % 2 === 0 ? 0 : 90);
      const infillPaths = clippedHatch(inner, spacing, angle);
      addOpenPaths(layer.paths, infillPaths, isSolid ? 'solidInfill' : 'sparseInfill');
    }""",
    """    const inner = offsetPaths(regions, -lineWidth * (settings.walls + 0.25));
    if (inner.length) {
      const solidMask = intersectionPaths(
        inner,
        offsetPaths(localSolidRegions[layerIndex] || [], lineWidth * 0.42)
      );
      const sparseMask = solidMask.length
        ? differencePaths(inner, offsetPaths(solidMask, lineWidth * 0.18))
        : inner;

      if (settings.infill > 0 && sparseMask.length) {
        const sparseSpacing = Math.max(lineWidth * 1.15, lineWidth / (settings.infill / 100));
        const sparseAngle = settings.infillPattern === 'grid'
          ? (layerIndex % 2 === 0 ? 45 : -45)
          : (layerIndex % 2 === 0 ? 0 : 90);
        addOpenPaths(layer.paths, clippedHatch(sparseMask, sparseSpacing, sparseAngle), 'sparseInfill');
      }

      if (solidMask.length) {
        const solidAngle = layerIndex % 2 === 0 ? 45 : -45;
        addOpenPaths(layer.paths, clippedHatch(solidMask, lineWidth * 0.90, solidAngle), 'solidInfill');
      }
    }""",
    "local solid and sparse infill paths",
)

solid_helpers = r"""
function computeLocalSolidRegions(layerRegions, requestedLayers, lineWidth) {
  const count = layerRegions.length;
  const skinLayers = Math.max(1, Math.round(Number(requestedLayers) || 1));
  const resultParts = Array.from({ length: count }, () => []);
  const comparisonExpansion = Math.max(0.02, lineWidth * 0.22);
  const skinExpansion = Math.max(0.04, lineWidth * 0.72);

  const addSkin = (source, startLayer, direction) => {
    if (!source?.length) return;
    const expanded = offsetPaths(source, skinExpansion);
    for (let depth = 0; depth < skinLayers; depth += 1) {
      const target = startLayer + direction * depth;
      if (target < 0 || target >= count) break;
      const clipped = intersectionPaths(layerRegions[target], expanded);
      if (clipped.length) resultParts[target].push(...clipped);
    }
  };

  for (let layerIndex = 0; layerIndex < count; layerIndex += 1) {
    const current = layerRegions[layerIndex];
    if (!current?.length) continue;

    const below = layerIndex > 0 && layerRegions[layerIndex - 1]?.length
      ? offsetPaths(layerRegions[layerIndex - 1], comparisonExpansion)
      : [];
    const above = layerIndex + 1 < count && layerRegions[layerIndex + 1]?.length
      ? offsetPaths(layerRegions[layerIndex + 1], comparisonExpansion)
      : [];

    const bottomSurface = below.length ? differencePaths(current, below) : current;
    const topSurface = above.length ? differencePaths(current, above) : current;

    // A new horizontal shelf is a bottom skin and grows upward.
    addSkin(bottomSurface, layerIndex, 1);
    // A disappearing horizontal shelf is a top skin and grows downward.
    addSkin(topSurface, layerIndex, -1);
  }

  return resultParts.map((parts, layerIndex) => {
    if (!parts.length) return [];
    return intersectionPaths(layerRegions[layerIndex], unionPaths(parts));
  });
}

"""

main = replace_once(
    main,
    "function layerIndexAtOrAbove(y, firstHeight, layerHeight) {",
    solid_helpers + "function layerIndexAtOrAbove(y, firstHeight, layerHeight) {",
    "solid region helper insertion",
)

main = replace_once(
    main,
    "    warningCount,\n    bounds: boundsFromTriangles(triangles),",
    "    warningCount,\n    localSolidLayerCount: localSolidRegions.filter((regions) => regions.length).length,\n    bounds: boundsFromTriangles(triangles),",
    "solid layer statistics",
)

MAIN.write_text(main, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 8", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.0.0-preview3'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

package = PACKAGE.read_text(encoding="utf-8")
package = re.sub(r'"version"\s*:\s*"[^"]+"', '"version": "3.0.0-preview3"', package, count=1)
PACKAGE.write_text(package, encoding="utf-8")

print("Model Lab v3 preview3 local top/bottom skin patch applied")
