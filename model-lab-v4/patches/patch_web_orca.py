from pathlib import Path
import re, sys
root=Path(sys.argv[1])
mainp=root/'web/src/main.js'
main=mainp.read_text()
main=main.replace("import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js';", "import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js';\nimport { ModelLabOrca } from './orca-client.js';",1)
start=main.index('async function startSlicing() {')
end=main.index('\nfunction extractWorldTriangles(', start)
new=r'''async function startSlicing() {
  if (!slicerModelGroup) return showSnackbar('Сначала открой модель');
  readSlicerSettingsFromUi();
  if (slicerSettings.printer !== 'adventurer5m') {
    showSnackbar('В нативной сборке пока подключён официальный профиль Adventurer 5M', 5600);
    return;
  }
  if (Math.abs(slicerSettings.nozzle - 0.4) > 0.001) {
    showSnackbar('Первая нативная сборка Orca поддерживает сопло 0,4 мм', 5600);
    return;
  }

  const printer = PRINTERS.adventurer5m;
  const box = new THREE.Box3().setFromObject(slicerModelGroup);
  if (!modelFitsPrinter(box, printer)) {
    showSnackbar('Модель выходит за пределы стола. Уменьши или перемести её.', 5200);
    return;
  }

  let nativeOutputPath = '';
  try {
    showOperation('Готовим модель для OrcaSlicer', 'Создаём бинарный STL');
    await nextFrame();
    const prepared = buildPreparedBinaryStl(slicerModelGroup, modelMeta?.name || 'model');
    const profile = orcaProfileRequest(slicerSettings);
    const native = await ModelLabOrca.slice(
      prepared,
      profile,
      (progress) => updateOperation('Передаём модель OrcaSlicer', `${Math.round(progress * 100)}%`),
      (progress) => updateOperation('Читаем G-code OrcaSlicer', `${Math.round(progress * 100)}%`)
    );
    nativeOutputPath = native.gcodePath || '';
    if (!native.gcode || native.gcode.length > MAX_GCODE_CHARS) {
      throw new Error('G-code OrcaSlicer слишком большой для памяти телефона');
    }

    updateOperation('Строим предпросмотр OrcaSlicer', 'Разбираем настоящие траектории G-code');
    await nextFrame();
    const result = await parseOrcaGcode(native.gcode, slicerSettings, printer);
    currentSliceResult = result;
    currentGcode = native.gcode;
    hideOperation();
    showSliceResult(result, currentGcode);
  } catch (error) {
    console.error(error);
    hideOperation();
    showSnackbar(humanizeError(error), 6800);
  } finally {
    if (nativeOutputPath) ModelLabOrca.deleteOutput(nativeOutputPath);
  }
}

function orcaProfileRequest(settings) {
  const quality = {
    draft: 'Flashforge/process/0.24mm Draft @Flashforge AD5M 0.4 Nozzle.json',
    standard: 'Flashforge/process/0.20mm Standard @Flashforge AD5M 0.4 Nozzle.json',
    fine: 'Flashforge/process/0.12mm Fine @Flashforge AD5M 0.4 Nozzle.json'
  }[settings.quality] || 'Flashforge/process/0.20mm Standard @Flashforge AD5M 0.4 Nozzle.json';
  const filament = {
    pla: 'Flashforge/filament/Flashforge Generic PLA.json',
    petg: 'Flashforge/filament/Flashforge Generic PETG.json',
    abs: 'Flashforge/filament/Flashforge Generic ABS.json',
    tpu: 'Flashforge/filament/Flashforge Generic TPU.json'
  }[settings.filament] || 'Flashforge/filament/Flashforge Generic PLA.json';

  return {
    machineProfile: 'Flashforge/machine/Flashforge Adventurer 5M 0.4 Nozzle.json',
    processProfile: quality,
    filamentProfile: filament,
    configOverrides: {
      layer_height: String(settings.layerHeight),
      wall_loops: String(settings.walls),
      sparse_infill_density: `${settings.infill}%`,
      top_shell_layers: String(settings.solidLayers),
      bottom_shell_layers: String(settings.solidLayers),
      enable_support: settings.supports ? '1' : '0',
      support_type: settings.supportType === 'tree' ? 'tree(auto)' : 'normal(auto)',
      support_on_build_plate_only: settings.supportPlacement === 'buildPlate' ? '1' : '0',
      support_threshold_angle: String(settings.supportAngle),
      support_base_pattern: settings.supportPattern === 'grid' ? 'grid' : settings.supportPattern === 'lines' ? 'rectilinear' : 'default',
      support_base_pattern_spacing: String(Math.max(0.4, 100 / Math.max(1, settings.supportDensity))),
      support_interface_top_layers: String(settings.supportInterfaceLayers),
      support_top_z_distance: String(settings.supportZDistance),
      support_object_xy_distance: String(settings.supportXYDistance),
      brim_type: settings.brim ? 'outer_only' : 'no_brim',
      brim_width: String(settings.brim ? settings.brimWidth : 0)
    }
  };
}

function buildPreparedBinaryStl(root, filename) {
  const triangles = extractWorldTriangles(root);
  if (!triangles.length) throw new Error('В модели не найдены треугольники');
  const byteLength = 84 + triangles.length * 50;
  const buffer = new ArrayBuffer(byteLength);
  const view = new DataView(buffer);
  const header = new TextEncoder().encode('Model Lab 3D / OrcaSlicer prepared mesh');
  new Uint8Array(buffer, 0, Math.min(80, header.length)).set(header.subarray(0, 80));
  view.setUint32(80, triangles.length, true);

  const writeVertex = (offset, x, y, z) => {
    view.setFloat32(offset, x, true);
    view.setFloat32(offset + 4, y, true);
    view.setFloat32(offset + 8, z, true);
  };
  let offset = 84;
  for (const triangle of triangles) {
    const a = new THREE.Vector3(triangle.ax, triangle.ay, triangle.az);
    const b = new THREE.Vector3(triangle.bx, triangle.by, triangle.bz);
    const c = new THREE.Vector3(triangle.cx, triangle.cy, triangle.cz);
    const normal = b.clone().sub(a).cross(c.clone().sub(a)).normalize();
    // Three.js uses Y-up; STL/Orca uses Z-up. Bed axes are X and Y.
    writeVertex(offset, normal.x, normal.z, normal.y); offset += 12;
    writeVertex(offset, a.x, a.z, a.y); offset += 12;
    writeVertex(offset, b.x, b.z, b.y); offset += 12;
    writeVertex(offset, c.x, c.z, c.y); offset += 12;
    view.setUint16(offset, 0, true); offset += 2;
  }
  return new File([buffer], `${stripExtension(filename)}_prepared.stl`, { type: 'model/stl' });
}

function orcaFeatureRole(raw) {
  const value = String(raw || '').trim().toLowerCase();
  if (value.includes('outer wall') || value.includes('external perimeter')) return 'outerWall';
  if (value.includes('inner wall') || value.includes('perimeter')) return 'innerWall';
  if (value.includes('internal solid') || value.includes('top surface') || value.includes('bottom surface') || value.includes('solid infill')) return 'solidInfill';
  if (value.includes('sparse infill') || value === 'infill' || value.includes('internal infill')) return 'sparseInfill';
  if (value.includes('support interface')) return 'supportInterface';
  if (value.includes('support')) return 'support';
  if (value.includes('brim') || value.includes('skirt')) return 'brim';
  return 'innerWall';
}

async function parseOrcaGcode(gcode, settings, printer) {
  const layers = [];
  let layer = null;
  let layerIndex = -1;
  let feature = 'innerWall';
  let lineWidth = Math.max(0.2, settings.nozzle * 1.05);
  let layerHeight = settings.layerHeight;
  let absoluteXYZ = true;
  let absoluteE = true;
  let x = 0;
  let y = 0;
  let z = 0;
  let e = 0;
  let feed = 0;
  let extrusionMm = 0;
  let parsedTimeSeconds = 0;
  let estimatedTimeSeconds = 0;
  let minX = Infinity, minY = Infinity, minZ = Infinity;
  let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;
  const speedRange = { min: Infinity, max: 0 };

  const ensureLayer = (printZ = z) => {
    if (layer && Math.abs(layer.printZ - printZ) < 0.0005) return layer;
    layerIndex += 1;
    layer = { index: layerIndex, printZ: Number.isFinite(printZ) ? printZ : 0, previewMoves: [], paths: [] };
    layers.push(layer);
    return layer;
  };
  const addMove = (type, fromX, fromY, toX, toY, moveZ, speed, width) => {
    const target = ensureLayer(moveZ);
    target.previewMoves.push({
      type,
      speed: Math.max(0.01, speed),
      width: Math.max(0.05, width),
      points: [{ x: fromX, z: fromY }, { x: toX, z: toY }]
    });
    minX = Math.min(minX, fromX, toX); maxX = Math.max(maxX, fromX, toX);
    minY = Math.min(minY, moveZ); maxY = Math.max(maxY, moveZ);
    minZ = Math.min(minZ, fromY, toY); maxZ = Math.max(maxZ, fromY, toY);
  };

  const lines = gcode.split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index];
    const trimmed = rawLine.trim();
    if (!trimmed) continue;
    if (trimmed.startsWith(';')) {
      const comment = trimmed.slice(1).trim();
      if (/^(CHANGE_LAYER|LAYER_CHANGE)/i.test(comment)) {
        layer = null;
      } else if (/^(FEATURE:|TYPE:)/i.test(comment)) {
        feature = orcaFeatureRole(comment.slice(comment.indexOf(':') + 1));
      } else if (/^(LINE_WIDTH:|WIDTH:)/i.test(comment)) {
        const value = Number(comment.slice(comment.indexOf(':') + 1));
        if (Number.isFinite(value) && value > 0) lineWidth = value;
      } else if (/^(LAYER_HEIGHT:|HEIGHT:)/i.test(comment)) {
        const value = Number(comment.slice(comment.indexOf(':') + 1));
        if (Number.isFinite(value) && value > 0) layerHeight = value;
      } else {
        const timeMatch = comment.match(/(?:estimated printing time|total estimated time).*?([0-9]+)h\s*([0-9]+)m\s*([0-9]+)s/i);
        if (timeMatch) parsedTimeSeconds = Number(timeMatch[1]) * 3600 + Number(timeMatch[2]) * 60 + Number(timeMatch[3]);
      }
      continue;
    }

    const code = trimmed.split(';', 1)[0].trim();
    if (!code) continue;
    if (/^G90\b/i.test(code)) { absoluteXYZ = true; continue; }
    if (/^G91\b/i.test(code)) { absoluteXYZ = false; continue; }
    if (/^M82\b/i.test(code)) { absoluteE = true; continue; }
    if (/^M83\b/i.test(code)) { absoluteE = false; continue; }
    if (/^G92\b/i.test(code)) {
      for (const match of code.matchAll(/([XYZE])(-?\d+(?:\.\d+)?)/gi)) {
        const value = Number(match[2]);
        if (match[1].toUpperCase() === 'X') x = value;
        if (match[1].toUpperCase() === 'Y') y = value;
        if (match[1].toUpperCase() === 'Z') z = value;
        if (match[1].toUpperCase() === 'E') e = value;
      }
      continue;
    }
    if (!/^G0?1\b/i.test(code)) continue;

    let nextX = x, nextY = y, nextZ = z, nextE = e, nextFeed = feed;
    let hasX = false, hasY = false, hasZ = false, hasE = false;
    for (const match of code.matchAll(/([XYZEF])(-?\d+(?:\.\d+)?)/gi)) {
      const axis = match[1].toUpperCase();
      const value = Number(match[2]);
      if (!Number.isFinite(value)) continue;
      if (axis === 'X') { nextX = absoluteXYZ ? value : x + value; hasX = true; }
      if (axis === 'Y') { nextY = absoluteXYZ ? value : y + value; hasY = true; }
      if (axis === 'Z') { nextZ = absoluteXYZ ? value : z + value; hasZ = true; }
      if (axis === 'E') { nextE = absoluteE ? value : e + value; hasE = true; }
      if (axis === 'F') nextFeed = value;
    }
    const deltaE = hasE ? nextE - e : 0;
    const distance = Math.hypot(nextX - x, nextY - y, nextZ - z);
    const speed = Math.max(0.01, nextFeed / 60);
    if (distance > 0.0001 && (hasX || hasY)) {
      const type = deltaE > 0.000001 ? feature : 'travel';
      addMove(type, x, y, nextX, nextY, nextZ, speed, type === 'travel' ? lineWidth * 0.18 : lineWidth);
      if (deltaE > 0) extrusionMm += deltaE;
      speedRange.min = Math.min(speedRange.min, speed);
      speedRange.max = Math.max(speedRange.max, speed);
      estimatedTimeSeconds += distance / speed;
    }
    x = nextX; y = nextY; z = nextZ; e = nextE; feed = nextFeed;
    if (hasZ && (!layer || Math.abs(layer.printZ - z) > Math.max(0.001, layerHeight * 0.25))) layer = null;
    if (index % 22000 === 0) {
      updateOperation('Строим предпросмотр OrcaSlicer', `${Math.round(index / Math.max(1, lines.length) * 100)}%`);
      await nextFrame();
    }
  }

  const nonEmpty = layers.filter((item) => item.previewMoves.some((move) => move.type !== 'travel'));
  if (!nonEmpty.length) throw new Error('В G-code OrcaSlicer не найдены печатные траектории');
  nonEmpty.forEach((item, index) => { item.index = index; });
  if (!Number.isFinite(speedRange.min)) speedRange.min = 0;
  const filamentDiameter = 1.75;
  return {
    layers: nonEmpty,
    lineWidth,
    firstLayerHeight: nonEmpty[0].printZ,
    layerHeight,
    totalPathLength: nonEmpty.reduce((sum, item) => sum + item.previewMoves.reduce((part, move) => part + polylineLength(move.points), 0), 0),
    filamentLengthMm: extrusionMm,
    timeSeconds: parsedTimeSeconds || estimatedTimeSeconds,
    warningCount: 0,
    previewSpeedRange: speedRange,
    bounds: {
      minX: Number.isFinite(minX) ? minX : 0,
      maxX: Number.isFinite(maxX) ? maxX : printer.width,
      minY: Number.isFinite(minY) ? minY : 0,
      maxY: Number.isFinite(maxY) ? maxY : 0,
      minZ: Number.isFinite(minZ) ? minZ : 0,
      maxZ: Number.isFinite(maxZ) ? maxZ : printer.depth
    },
    printer,
    engine: 'OrcaSlicer/libslic3r',
    filamentVolumeMm3: extrusionMm * Math.PI * Math.pow(filamentDiameter / 2, 2)
  };
}
'''
main=main[:start]+new+main[end:]
mainp.write_text(main)
