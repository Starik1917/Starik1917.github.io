from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "model-lab-v3/source")
MAIN = ROOT / "web/src/main.js"
HTML = ROOT / "web/index.html"
CSS = ROOT / "web/src/style.css"
GRADLE = ROOT / "app/build.gradle"
PACKAGE = ROOT / "package.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Missing replacement anchor: {label}")
    return text.replace(old, new, 1)


def replace_regex(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"Expected one regex replacement for {label}, got {count}")
    return updated


main = MAIN.read_text(encoding="utf-8")

main = replace_once(
    main,
    "import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js';",
    "import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js';\n"
    "import { LineSegments2 } from 'three/examples/jsm/lines/LineSegments2.js';\n"
    "import { LineSegmentsGeometry } from 'three/examples/jsm/lines/LineSegmentsGeometry.js';\n"
    "import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js';",
    "wide-line imports",
)

main = replace_once(
    main,
    "const MAX_GCODE_CHARS = 80_000_000;",
    "const MAX_GCODE_CHARS = 80_000_000;\n"
    "const PREVIEW_SEGMENT_LIMIT = 650_000;\n"
    "const PREVIEW_FEATURES = {\n"
    "  outerWall: { label: 'Внешняя стенка', color: '#ff6f61' },\n"
    "  innerWall: { label: 'Внутренняя стенка', color: '#ffc857' },\n"
    "  sparseInfill: { label: 'Заполнение', color: '#b38cff' },\n"
    "  solidInfill: { label: 'Сплошное заполнение', color: '#58b8ff' },\n"
    "  support: { label: 'Поддержки', color: '#55d6a9' },\n"
    "  supportInterface: { label: 'Интерфейс поддержек', color: '#b8f1d6' },\n"
    "  brim: { label: 'Brim', color: '#ff9f68' },\n"
    "  travel: { label: 'Перемещения', color: '#9297a1' }\n"
    "};",
    "preview constants",
)

main = replace_once(
    main,
    "let currentGcode = '';\nlet loadingDepth = 0;",
    "let currentGcode = '';\n"
    "let slicePreviewMaterials = new Set();\n"
    "let slicePreviewPlaybackTimer = null;\n"
    "let slicePreviewState = {\n"
    "  bottom: 0,\n"
    "  top: 0,\n"
    "  colorMode: 'feature',\n"
    "  showTravel: false,\n"
    "  showModel: false,\n"
    "  visibleTypes: new Set(Object.keys(PREVIEW_FEATURES).filter((type) => type !== 'travel'))\n"
    "};\n"
    "let loadingDepth = 0;",
    "preview state",
)

main = replace_once(
    main,
    "  dom.layerSlider.addEventListener('input', () => showSliceLayer(Number(dom.layerSlider.value)));",
    "  dom.layerBottomSlider.addEventListener('input', () => {\n"
    "    if (Number(dom.layerBottomSlider.value) > Number(dom.layerSlider.value)) dom.layerSlider.value = dom.layerBottomSlider.value;\n"
    "    updateSlicePreviewFromControls();\n"
    "  });\n"
    "  dom.layerSlider.addEventListener('input', () => {\n"
    "    if (Number(dom.layerSlider.value) < Number(dom.layerBottomSlider.value)) dom.layerBottomSlider.value = dom.layerSlider.value;\n"
    "    updateSlicePreviewFromControls();\n"
    "  });\n"
    "  dom.previewColorMode.addEventListener('change', () => {\n"
    "    slicePreviewState.colorMode = dom.previewColorMode.value;\n"
    "    renderSlicePreview();\n"
    "  });\n"
    "  dom.previewTravelButton.addEventListener('click', () => {\n"
    "    slicePreviewState.showTravel = !slicePreviewState.showTravel;\n"
    "    syncSlicePreviewButtons();\n"
    "    renderSlicePreview();\n"
    "  });\n"
    "  dom.previewModelButton.addEventListener('click', () => {\n"
    "    slicePreviewState.showModel = !slicePreviewState.showModel;\n"
    "    syncSlicePreviewButtons();\n"
    "    renderSlicePreview();\n"
    "  });\n"
    "  dom.previewFitButton.addEventListener('click', fitSlicePreview);\n"
    "  dom.previewPlayButton.addEventListener('click', toggleSlicePreviewPlayback);\n"
    "  document.querySelectorAll('[data-preview-type]').forEach((button) => {\n"
    "    button.addEventListener('click', () => toggleSlicePreviewType(button.dataset.previewType));\n"
    "  });",
    "preview event bindings",
)

main = replace_once(
    main,
    "  dom.returnToModelButton.addEventListener('click', () => {\n    currentSliceResult = null;",
    "  dom.returnToModelButton.addEventListener('click', () => {\n    stopSlicePreviewPlayback();\n    currentSliceResult = null;",
    "stop playback on return",
)

main = replace_once(
    main,
    "    if (onHome) {\n    controls.enabled = false;",
    "    if (onHome) {\n    stopSlicePreviewPlayback();\n    controls.enabled = false;",
    "stop playback on home",
)

main = replace_once(
    main,
    "      addClosedPaths(layer.paths, wallPaths, 'wall');",
    "      addClosedPaths(layer.paths, wallPaths, wall === 0 ? 'outerWall' : 'innerWall');",
    "wall feature roles",
)
main = replace_once(
    main,
    "      addOpenPaths(layer.paths, infillPaths, 'infill');",
    "      addOpenPaths(layer.paths, infillPaths, isSolid ? 'solidInfill' : 'sparseInfill');",
    "infill feature roles",
)

main = replace_once(
    main,
    "  let totalExtrusionLength = 0;\n\n  for (const layer of result.layers) {",
    "  let totalExtrusionLength = 0;\n"
    "  result.previewSpeedRange = { min: Infinity, max: 0 };\n\n"
    "  for (const layer of result.layers) {",
    "preview speed range",
)
main = replace_once(
    main,
    "    const orderedPaths = orderPathsNearest(layer.paths, last, offsetX, offsetY);\n    for (const path of orderedPaths) {",
    "    const orderedPaths = orderPathsNearest(layer.paths, last, offsetX, offsetY);\n"
    "    layer.previewMoves = [];\n"
    "    for (const path of orderedPaths) {",
    "preview move list",
)
main = replace_once(
    main,
    "      const travelDistance = Math.hypot(start.x - last.x, start.y - last.y);\n      if (travelDistance > 2 && !retracted) {",
    "      const travelDistance = Math.hypot(start.x - last.x, start.y - last.y);\n"
    "      if (travelDistance > 0.001) {\n"
    "        layer.previewMoves.push({\n"
    "          type: 'travel',\n"
    "          speed: settings.travelSpeed || 150,\n"
    "          width: Math.max(0.08, lineWidth * 0.20),\n"
    "          points: [{ x: last.x - offsetX, z: last.y - offsetY }, { ...path.points[0] }]\n"
    "        });\n"
    "      }\n"
    "      if (travelDistance > 2 && !retracted) {",
    "travel preview moves",
)
main = replace_once(
    main,
    "      const speed = pathSpeed(path.type, layer.index, settings);\n      for (let pointIndex = 1; pointIndex < path.points.length; pointIndex += 1) {",
    "      const speed = pathSpeed(path.type, layer.index, settings);\n"
    "      result.previewSpeedRange.min = Math.min(result.previewSpeedRange.min, speed);\n"
    "      result.previewSpeedRange.max = Math.max(result.previewSpeedRange.max, speed);\n"
    "      layer.previewMoves.push({\n"
    "        type: path.type,\n"
    "        speed,\n"
    "        width: lineWidth,\n"
    "        points: path.points.map((point) => ({ x: point.x, z: point.z }))\n"
    "      });\n"
    "      for (let pointIndex = 1; pointIndex < path.points.length; pointIndex += 1) {",
    "extrusion preview moves",
)
main = replace_once(
    main,
    "  lines.push(';END', ...printerEndGcode(printer, settings));",
    "  if (!Number.isFinite(result.previewSpeedRange.min)) result.previewSpeedRange.min = 0;\n"
    "  lines.push(';END', ...printerEndGcode(printer, settings));",
    "normalize preview speed range",
)

main = replace_regex(
    main,
    r"function pathSpeed\(type, layerIndex, settings\) \{.*?\n\}",
    """function pathSpeed(type, layerIndex, settings) {
  if (layerIndex === 0) return Math.min(25, settings.speed * 0.45);
  if (type === 'outerWall') return Math.min(settings.speed * 0.58, 120);
  if (type === 'innerWall' || type === 'wall') return settings.speed * 0.72;
  if (type === 'solidInfill') return settings.speed * 0.82;
  if (type === 'supportInterface') return Math.min(settings.speed * 0.58, 45);
  if (type === 'support') return Math.min(settings.speed * 0.75, settings.supportType === 'tree' ? 70 : 60);
  if (type === 'brim') return Math.min(settings.speed * 0.55, 35);
  return settings.speed;
}""",
    "path speed roles",
)

preview_block = r"""function showSliceResult(result, gcode) {
  const filament = FILAMENT_PRESETS[slicerSettings.filament] || FILAMENT_PRESETS.pla;
  const lengthM = (result.filamentLengthMm || 0) / 1000;
  const filamentVolumeCm3 = (result.filamentLengthMm || 0) * Math.PI * Math.pow(1.75 / 2, 2) / 1000;
  const massG = filamentVolumeCm3 * filament.density;
  dom.resultTime.textContent = formatDuration(result.timeSeconds || 0);
  dom.resultMaterial.textContent = `${massG.toFixed(1)} г`;
  dom.resultLayers.textContent = formatNumber(result.layers.length);
  dom.resultFilamentLength.textContent = `${lengthM.toFixed(2)} м`;
  dom.resultGcodeSize.textContent = formatBytes(new Blob([gcode]).size);
  dom.resultWarnings.textContent = result.warningCount ? `${result.warningCount}` : 'Нет';

  const lastLayer = Math.max(0, result.layers.length - 1);
  slicePreviewState.bottom = 0;
  slicePreviewState.top = lastLayer;
  slicePreviewState.colorMode = 'feature';
  slicePreviewState.showTravel = false;
  slicePreviewState.showModel = false;
  slicePreviewState.visibleTypes = new Set(Object.keys(PREVIEW_FEATURES).filter((type) => type !== 'travel'));

  dom.layerBottomSlider.min = 0;
  dom.layerBottomSlider.max = lastLayer;
  dom.layerBottomSlider.value = 0;
  dom.layerSlider.min = 0;
  dom.layerSlider.max = lastLayer;
  dom.layerSlider.value = lastLayer;
  dom.previewColorMode.value = 'feature';
  dom.layerPreviewCard.hidden = false;
  prepareSlicerMaterialForPreview(false);
  if (slicerModelGroup) slicerModelGroup.visible = false;
  syncSlicePreviewButtons();
  renderSlicePreview();
  fitSlicePreview(false);
  openSheet(dom.sliceResultSheet);
}

function showSliceLayer(layerIndex) {
  if (!currentSliceResult) return;
  const index = Math.max(0, Math.min(currentSliceResult.layers.length - 1, Math.round(layerIndex)));
  dom.layerSlider.value = index;
  if (Number(dom.layerBottomSlider.value) > index) dom.layerBottomSlider.value = index;
  updateSlicePreviewFromControls();
}

function updateSlicePreviewFromControls() {
  if (!currentSliceResult) return;
  const max = Math.max(0, currentSliceResult.layers.length - 1);
  let bottom = Math.max(0, Math.min(max, Number(dom.layerBottomSlider.value) || 0));
  let top = Math.max(0, Math.min(max, Number(dom.layerSlider.value) || 0));
  if (bottom > top) bottom = top;
  slicePreviewState.bottom = bottom;
  slicePreviewState.top = top;
  dom.layerBottomSlider.value = bottom;
  dom.layerSlider.value = top;
  renderSlicePreview();
}

function toggleSlicePreviewType(type) {
  if (!PREVIEW_FEATURES[type] || type === 'travel') return;
  if (slicePreviewState.visibleTypes.has(type)) slicePreviewState.visibleTypes.delete(type);
  else slicePreviewState.visibleTypes.add(type);
  syncSlicePreviewButtons();
  renderSlicePreview();
}

function syncSlicePreviewButtons() {
  setPressed(dom.previewTravelButton, slicePreviewState.showTravel);
  setPressed(dom.previewModelButton, slicePreviewState.showModel);
  document.querySelectorAll('[data-preview-type]').forEach((button) => {
    setPressed(button, slicePreviewState.visibleTypes.has(button.dataset.previewType));
  });
  dom.previewPlayButton.setAttribute('aria-pressed', String(Boolean(slicePreviewPlaybackTimer)));
  dom.previewPlayButton.setAttribute('aria-label', slicePreviewPlaybackTimer ? 'Остановить просмотр' : 'Воспроизвести слои');
}

function toggleSlicePreviewPlayback() {
  if (!currentSliceResult) return;
  if (slicePreviewPlaybackTimer) {
    stopSlicePreviewPlayback();
    return;
  }
  const max = currentSliceResult.layers.length - 1;
  if (slicePreviewState.top >= max) {
    slicePreviewState.bottom = 0;
    slicePreviewState.top = 0;
    dom.layerBottomSlider.value = 0;
    dom.layerSlider.value = 0;
    renderSlicePreview();
  }
  slicePreviewPlaybackTimer = window.setInterval(() => {
    if (!currentSliceResult || slicePreviewState.top >= max) {
      stopSlicePreviewPlayback();
      return;
    }
    slicePreviewState.top += 1;
    dom.layerSlider.value = slicePreviewState.top;
    renderSlicePreview();
  }, 105);
  syncSlicePreviewButtons();
}

function stopSlicePreviewPlayback() {
  if (slicePreviewPlaybackTimer) window.clearInterval(slicePreviewPlaybackTimer);
  slicePreviewPlaybackTimer = null;
  if (dom.previewPlayButton) syncSlicePreviewButtons();
}

function fitSlicePreview(notify = true) {
  if (!currentSliceResult?.bounds) return;
  const bounds = currentSliceResult.bounds;
  const box = new THREE.Box3(
    new THREE.Vector3(bounds.minX, 0, bounds.minZ),
    new THREE.Vector3(bounds.maxX, Math.max(bounds.maxY, 1), bounds.maxZ)
  );
  fitCameraToBox(expandBoxForBed(box), false);
  if (notify) showSnackbar('Предпросмотр помещён в кадр');
}

function normalizePreviewType(type) {
  if (type === 'wall') return 'outerWall';
  if (type === 'infill') return 'sparseInfill';
  return type;
}

function previewColorToken(type, speed, layerIndex, totalLayers, speedRange) {
  if (slicePreviewState.colorMode === 'speed') {
    const min = Number.isFinite(speedRange?.min) ? speedRange.min : 0;
    const max = Math.max(min + 1, Number.isFinite(speedRange?.max) ? speedRange.max : min + 1);
    const normalized = Math.max(0, Math.min(1, (speed - min) / (max - min)));
    const bin = Math.round(normalized * 11);
    const color = new THREE.Color().setHSL((1 - bin / 11) * 0.66, 0.88, 0.58);
    return { key: `speed-${bin}`, color: `#${color.getHexString()}` };
  }
  if (slicePreviewState.colorMode === 'layer') {
    const normalized = totalLayers > 1 ? layerIndex / (totalLayers - 1) : 0;
    const bin = Math.round(normalized * 15);
    const color = new THREE.Color().setHSL(0.76 - (bin / 15) * 0.70, 0.78, 0.60);
    return { key: `layer-${bin}`, color: `#${color.getHexString()}` };
  }
  const meta = PREVIEW_FEATURES[type] || { color: '#ffffff' };
  return { key: `feature-${type}`, color: meta.color };
}

function renderSlicePreview() {
  clearSlicePreview();
  const result = currentSliceResult;
  if (!result?.layers?.length) return;

  const max = result.layers.length - 1;
  const bottom = Math.max(0, Math.min(max, Math.round(slicePreviewState.bottom)));
  const top = Math.max(bottom, Math.min(max, Math.round(slicePreviewState.top)));
  slicePreviewState.bottom = bottom;
  slicePreviewState.top = top;
  dom.layerBottomSlider.value = bottom;
  dom.layerSlider.value = top;

  if (slicerModelGroup) {
    slicerModelGroup.visible = slicePreviewState.showModel;
    if (slicePreviewState.showModel) prepareSlicerMaterialForPreview(false);
  }

  const buckets = new Map();
  let renderedSegments = 0;
  let availableSegments = 0;
  let estimatedSeconds = 0;
  let nozzlePoint = null;
  const totalLayers = result.layers.length;
  const speedRange = result.previewSpeedRange || { min: 0, max: slicerSettings.speed || 100 };

  for (let layerIndex = top; layerIndex >= bottom; layerIndex -= 1) {
    const layer = result.layers[layerIndex];
    const isTop = layerIndex === top;
    const moves = layer.previewMoves?.length
      ? layer.previewMoves
      : layer.paths.map((path) => ({ type: path.type, speed: pathSpeed(path.type, layerIndex, slicerSettings), width: result.lineWidth, points: path.points }));

    for (const move of moves) {
      const type = normalizePreviewType(move.type);
      if (type === 'travel' && !slicePreviewState.showTravel) continue;
      if (type !== 'travel' && !slicePreviewState.visibleTypes.has(type)) continue;
      const points = move.points || [];
      if (points.length < 2) continue;
      const speed = Math.max(1, Number(move.speed) || 1);
      estimatedSeconds += polylineLength(points) / speed;
      const token = previewColorToken(type, speed, layerIndex, totalLayers, speedRange);
      const width = type === 'travel'
        ? Math.max(0.07, result.lineWidth * 0.18)
        : Math.max(0.08, Number(move.width) || result.lineWidth);
      const opacity = type === 'travel' ? (isTop ? 0.72 : 0.26) : (isTop ? 1 : 0.40);
      const key = `${token.key}|${isTop ? 'top' : 'base'}|${type === 'travel' ? 'travel' : 'print'}`;
      if (!buckets.has(key)) {
        buckets.set(key, { vertices: [], color: token.color, width, opacity, dashed: type === 'travel', top: isTop });
      }
      const bucket = buckets.get(key);
      for (let pointIndex = 1; pointIndex < points.length; pointIndex += 1) {
        availableSegments += 1;
        if (renderedSegments >= PREVIEW_SEGMENT_LIMIT) continue;
        const a = points[pointIndex - 1];
        const b = points[pointIndex];
        const y = layer.printZ + (isTop ? 0.055 : 0.015);
        bucket.vertices.push(a.x, y, a.z, b.x, y, b.z);
        renderedSegments += 1;
      }
      if (isTop && type !== 'travel') nozzlePoint = { ...points.at(-1), y: layer.printZ };
    }
  }

  const width = Math.max(dom.canvasArea.clientWidth, 1);
  const height = Math.max(dom.canvasArea.clientHeight, 1);
  for (const bucket of buckets.values()) {
    if (!bucket.vertices.length) continue;
    const geometry = new LineSegmentsGeometry();
    geometry.setPositions(bucket.vertices);
    const material = new LineMaterial({
      color: bucket.color,
      linewidth: bucket.width,
      worldUnits: true,
      transparent: true,
      opacity: bucket.opacity,
      depthTest: !bucket.dashed,
      depthWrite: false,
      dashed: bucket.dashed,
      dashSize: 1.3,
      gapSize: 0.85,
      alphaToCoverage: true
    });
    material.worldUnits = true;
    material.resolution.set(width, height);
    const lines = new LineSegments2(geometry, material);
    lines.computeLineDistances();
    lines.frustumCulled = false;
    lines.renderOrder = bucket.top ? 960 : 930;
    slicePreviewMaterials.add(material);
    slicePreviewGroup.add(lines);
  }

  if (nozzlePoint) {
    const markerRadius = Math.max(0.45, result.lineWidth * 1.3);
    const marker = new THREE.Mesh(
      new THREE.SphereGeometry(markerRadius, 18, 12),
      new THREE.MeshBasicMaterial({ color: new THREE.Color(cssColor('--text', '#ffffff')), depthTest: false, depthWrite: false })
    );
    marker.position.set(nozzlePoint.x, nozzlePoint.y + markerRadius * 1.25, nozzlePoint.z);
    marker.renderOrder = 980;
    slicePreviewGroup.add(marker);
  }

  const topLayer = result.layers[top];
  const bottomLayer = result.layers[bottom];
  const lowPercent = max > 0 ? (bottom / max) * 100 : 0;
  const highPercent = max > 0 ? (top / max) * 100 : 100;
  dom.layerRange.style.setProperty('--range-low', `${lowPercent}%`);
  dom.layerRange.style.setProperty('--range-high', `${highPercent}%`);
  dom.layerPreviewValue.textContent = `Слой ${top + 1} / ${totalLayers} · Z ${topLayer.printZ.toFixed(2)} мм`;
  dom.layerBottomValue.textContent = `С ${bottom + 1} · ${bottomLayer.printZ.toFixed(2)} мм`;
  dom.layerTopValue.textContent = `По ${top + 1} · ${topLayer.printZ.toFixed(2)} мм`;
  dom.previewVisibleLayers.textContent = `${top - bottom + 1}`;
  dom.previewTimeValue.textContent = `≈ ${formatDuration(estimatedSeconds * 1.16)}`;
  dom.previewSegmentInfo.textContent = renderedSegments < availableSegments
    ? `${formatNumber(renderedSegments)} / ${formatNumber(availableSegments)} сегм.`
    : `${formatNumber(renderedSegments)} сегм.`;
  dom.previewSegmentInfo.classList.toggle('is-warning', renderedSegments < availableSegments);
}

function clearSlicePreview() {
  clearGroup(slicePreviewGroup);
  slicePreviewMaterials.clear();
}
"""

main = replace_regex(
    main,
    r"function showSliceResult\(result, gcode\) \{.*?\n\}\n\nfunction showSliceLayer\(layerIndex\) \{.*?\n\}\n\nfunction clearSlicePreview\(\) \{.*?\n\}",
    preview_block,
    "Orca-style preview implementation",
)

main = replace_once(
    main,
    "  if (currentSliceResult && !dom.layerPreviewCard.hidden) showSliceLayer(Number(dom.layerSlider.value));",
    "  if (currentSliceResult && !dom.layerPreviewCard.hidden) renderSlicePreview();",
    "theme preview refresh",
)
main = replace_once(
    main,
    "  renderer.setSize(width, height, false);\n  camera.aspect = width / height;",
    "  renderer.setSize(width, height, false);\n"
    "  for (const material of slicePreviewMaterials) material.resolution.set(width, height);\n"
    "  camera.aspect = width / height;",
    "preview line resolution",
)

MAIN.write_text(main, encoding="utf-8")

html = HTML.read_text(encoding="utf-8")
old_preview_html = """        <section class="layer-preview-card" id="layerPreviewCard" hidden>
          <div class="layer-preview-header"><span>Предпросмотр слоя</span><strong id="layerPreviewValue">1 / 1</strong></div>
          <input id="layerSlider" type="range" min="0" max="0" value="0" />
          <div class="layer-legend"><span><i class="legend-wall"></i>Стенки</span><span><i class="legend-infill"></i>Заполнение</span><span><i class="legend-support"></i>Поддержки</span></div>
        </section>"""
new_preview_html = """        <section class="layer-preview-card" id="layerPreviewCard" hidden aria-label="Предпросмотр траекторий печати">
          <header class="preview-head">
            <div class="preview-title"><small>Предпросмотр G-code</small><strong id="layerPreviewValue">Слой 1 / 1</strong></div>
            <div class="preview-icon-actions">
              <button class="preview-icon-button" id="previewPlayButton" aria-label="Воспроизвести слои" aria-pressed="false"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7L8 5Z"/></svg></button>
              <button class="preview-icon-button" id="previewFitButton" aria-label="Вписать в экран"><svg viewBox="0 0 24 24"><path d="M7 3H3v4h2V5h2V3Zm14 4V3h-4v2h2v2h2ZM5 17H3v4h4v-2H5v-2Zm16 0h-2v2h-2v2h4v-4Z"/></svg></button>
              <button class="preview-icon-button" id="previewModelButton" aria-label="Показать модель" aria-pressed="false"><svg viewBox="0 0 24 24"><path d="m12 2 9 5v10l-9 5-9-5V7l9-5Zm0 2.3L5.2 8.1 12 12l6.8-3.9L12 4.3ZM5 9.8v6l6 3.3v-6L5 9.8Zm14 0-6 3.3v6l6-3.3v-6Z"/></svg></button>
            </div>
          </header>

          <div class="preview-control-row">
            <label class="preview-select"><span>Цвет</span><select id="previewColorMode"><option value="feature">Тип линии</option><option value="speed">Скорость</option><option value="layer">Высота</option></select></label>
            <button class="preview-chip" id="previewTravelButton" aria-pressed="false"><i></i>Перемещения</button>
          </div>

          <div class="preview-layer-range">
            <div class="preview-range-labels"><span id="layerBottomValue">С 1</span><span id="layerTopValue">По 1</span></div>
            <div class="layer-range-track" id="layerRange">
              <input id="layerBottomSlider" type="range" min="0" max="0" value="0" aria-label="Нижний слой" />
              <input id="layerSlider" type="range" min="0" max="0" value="0" aria-label="Верхний слой" />
            </div>
          </div>

          <div class="preview-stat-row">
            <span><b id="previewVisibleLayers">1</b> сл.</span>
            <span id="previewTimeValue">≈ 0 сек.</span>
            <span id="previewSegmentInfo">0 сегм.</span>
          </div>

          <div class="layer-legend" id="layerLegend" aria-label="Видимые типы линий">
            <button data-preview-type="outerWall" aria-pressed="true" style="--legend-color:#ff6f61"><i></i>Внешние</button>
            <button data-preview-type="innerWall" aria-pressed="true" style="--legend-color:#ffc857"><i></i>Внутренние</button>
            <button data-preview-type="sparseInfill" aria-pressed="true" style="--legend-color:#b38cff"><i></i>Заполнение</button>
            <button data-preview-type="solidInfill" aria-pressed="true" style="--legend-color:#58b8ff"><i></i>Сплошное</button>
            <button data-preview-type="support" aria-pressed="true" style="--legend-color:#55d6a9"><i></i>Поддержки</button>
            <button data-preview-type="supportInterface" aria-pressed="true" style="--legend-color:#b8f1d6"><i></i>Интерфейс</button>
            <button data-preview-type="brim" aria-pressed="true" style="--legend-color:#ff9f68"><i></i>Brim</button>
          </div>
        </section>"""
html = replace_once(html, old_preview_html, new_preview_html, "layer preview HTML")
HTML.write_text(html, encoding="utf-8")

css = CSS.read_text(encoding="utf-8")
css += r"""

/* Model Lab 3D v3 — Orca-style mobile toolpath preview */
.layer-preview-card {
  position: absolute;
  z-index: 26;
  left: 12px;
  right: 12px;
  bottom: calc(var(--toolbar-height) + var(--safe-bottom) + 10px);
  display: grid;
  gap: 10px;
  max-height: min(43vh, 350px);
  padding: 12px;
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid var(--outline-soft);
  border-radius: 28px 28px 22px 22px;
  background: color-mix(in srgb, var(--surface) 94%, transparent);
  box-shadow: 0 18px 48px rgba(0,0,0,.34);
  backdrop-filter: blur(24px);
  overscroll-behavior: contain;
}
.layer-preview-card::-webkit-scrollbar { display: none; }
.preview-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-width: 0; }
.preview-title { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.preview-title small { color: var(--primary); font-size: 9px; font-weight: 850; letter-spacing: .08em; text-transform: uppercase; }
.preview-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; letter-spacing: -.015em; }
.preview-icon-actions { display: flex; align-items: center; gap: 4px; flex: 0 0 auto; }
.preview-icon-button { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 12px; color: var(--muted); background: var(--surface-2); border: 1px solid var(--outline-soft); }
.preview-icon-button svg { width: 18px; height: 18px; }
.preview-icon-button[aria-pressed="true"] { color: var(--on-primary); background: var(--primary); }
.preview-control-row { display: grid; grid-template-columns: minmax(0,1fr) auto; align-items: center; gap: 8px; }
.preview-select { min-width: 0; display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 42px; padding: 6px 8px 6px 12px; border-radius: 15px; background: var(--surface-2); border: 1px solid var(--outline-soft); }
.preview-select span { color: var(--muted); font-size: 10px; }
.preview-select select { min-width: 118px; max-width: 62%; height: 30px; padding: 0 8px; color: var(--text); background: var(--surface-3); border: 0; border-radius: 10px; outline: none; font-size: 11px; }
.preview-chip { display: flex; align-items: center; gap: 6px; min-height: 42px; padding: 0 11px; border-radius: 15px; color: var(--muted); background: var(--surface-2); border: 1px solid var(--outline-soft); font-size: 10px; font-weight: 700; white-space: nowrap; }
.preview-chip i { width: 7px; height: 7px; border-radius: 50%; background: #9297a1; }
.preview-chip[aria-pressed="true"] { color: var(--text); background: color-mix(in srgb, var(--primary) 14%, var(--surface-2)); border-color: color-mix(in srgb, var(--primary) 35%, transparent); }
.preview-layer-range { display: grid; gap: 4px; }
.preview-range-labels { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--muted); font-size: 9px; }
.layer-range-track { --range-low: 0%; --range-high: 100%; position: relative; height: 34px; }
.layer-range-track::before, .layer-range-track::after { content: ""; position: absolute; top: 15px; height: 4px; border-radius: 4px; pointer-events: none; }
.layer-range-track::before { left: 2px; right: 2px; background: var(--outline); }
.layer-range-track::after { left: var(--range-low); right: calc(100% - var(--range-high)); background: var(--primary); }
.layer-range-track input[type="range"] { position: absolute; inset: 0; width: 100%; height: 34px; margin: 0; appearance: none; -webkit-appearance: none; background: transparent; pointer-events: none; }
.layer-range-track input[type="range"]::-webkit-slider-runnable-track { height: 4px; background: transparent; }
.layer-range-track input[type="range"]::-webkit-slider-thumb { width: 20px; height: 20px; margin-top: -8px; border: 3px solid var(--surface); border-radius: 50%; background: var(--primary); box-shadow: 0 2px 8px rgba(0,0,0,.35); -webkit-appearance: none; pointer-events: auto; }
.layer-range-track input[type="range"]::-moz-range-track { height: 4px; background: transparent; }
.layer-range-track input[type="range"]::-moz-range-thumb { width: 16px; height: 16px; border: 3px solid var(--surface); border-radius: 50%; background: var(--primary); pointer-events: auto; }
.preview-stat-row { display: grid; grid-template-columns: auto auto minmax(0,1fr); align-items: center; gap: 6px; color: var(--muted); font-size: 9px; }
.preview-stat-row span { min-width: 0; padding: 6px 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; border-radius: 10px; background: color-mix(in srgb, var(--surface-2) 78%, transparent); }
.preview-stat-row span:last-child { text-align: right; }
.preview-stat-row .is-warning { color: var(--warning); }
.layer-legend { display: flex; gap: 6px; margin: 0 -2px; padding: 0 2px 1px; overflow-x: auto; overflow-y: hidden; scrollbar-width: none; }
.layer-legend::-webkit-scrollbar { display: none; }
.layer-legend button { display: flex; align-items: center; gap: 6px; flex: 0 0 auto; min-height: 30px; padding: 0 9px; border-radius: 11px; color: var(--muted); background: var(--surface-2); border: 1px solid var(--outline-soft); font-size: 9px; font-weight: 700; }
.layer-legend button i { width: 7px; height: 7px; border-radius: 50%; background: var(--legend-color); box-shadow: 0 0 0 2px color-mix(in srgb, var(--legend-color) 20%, transparent); }
.layer-legend button[aria-pressed="true"] { color: var(--text); border-color: color-mix(in srgb, var(--legend-color) 48%, var(--outline-soft)); background: color-mix(in srgb, var(--legend-color) 12%, var(--surface-2)); }
.layer-legend button[aria-pressed="false"] { opacity: .48; }
@media (max-width: 380px) {
  .layer-preview-card { left: 8px; right: 8px; padding: 10px; border-radius: 24px 24px 18px 18px; }
  .preview-control-row { grid-template-columns: 1fr; }
  .preview-chip { justify-content: center; min-height: 36px; }
  .preview-stat-row { grid-template-columns: 1fr 1fr; }
  .preview-stat-row span:last-child { grid-column: 1 / -1; text-align: left; }
}
@media (max-height: 700px) {
  .layer-preview-card { max-height: 48vh; gap: 7px; bottom: calc(var(--toolbar-height) + var(--safe-bottom) + 6px); }
  .layer-legend button { min-height: 27px; }
}
"""
CSS.write_text(css, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 6", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.0.0-preview1'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

package = PACKAGE.read_text(encoding="utf-8")
package = re.sub(r'"version"\s*:\s*"[^"]+"', '"version": "3.0.0-preview1"', package, count=1)
PACKAGE.write_text(package, encoding="utf-8")

print("Model Lab v3 preview patch applied")
