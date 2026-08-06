import * as THREE from 'three';
import JSZip from 'jszip';
import ClipperLib from 'clipper-lib';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js';
import { KTX2Loader } from 'three/examples/jsm/loaders/KTX2Loader.js';
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
import { OBJLoader } from 'three/examples/jsm/loaders/OBJLoader.js';
import { MTLLoader } from 'three/examples/jsm/loaders/MTLLoader.js';
import { FBXLoader } from 'three/examples/jsm/loaders/FBXLoader.js';
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader.js';
import { ColladaLoader } from 'three/examples/jsm/loaders/ColladaLoader.js';
import { ThreeMFLoader } from 'three/examples/jsm/loaders/3MFLoader.js';
import { TDSLoader } from 'three/examples/jsm/loaders/TDSLoader.js';
import { AMFLoader } from 'three/examples/jsm/loaders/AMFLoader.js';
import { USDZLoader } from 'three/examples/jsm/loaders/USDZLoader.js';
import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js';

const MODEL_EXTENSIONS = new Set(['glb', 'gltf', 'stl', 'obj', 'fbx', 'ply', 'dae', '3mf', '3ds', 'amf', 'usdz']);
const MODEL_PRIORITY = { glb: 0, gltf: 1, fbx: 2, obj: 3, dae: 4, '3mf': 5, usdz: 6, '3ds': 7, amf: 8, stl: 9, ply: 10 };
const CLIPPER_SCALE = 1000;
const MAX_SLICE_TRIANGLES = 350000;
const MAX_LAYERS = 1800;
const MAX_GCODE_CHARS = 80_000_000;

const PRINTERS = {
  adventurer5m: {
    name: 'Flashforge Adventurer 5M', profile: 'adventurer5m', width: 220, depth: 220, height: 220,
    flavor: 'flashforge', nozzles: [0.25, 0.4, 0.6, 0.8], defaultNozzle: 0.4,
    maxSpeed: 600, maxTravelSpeed: 600, maxAcceleration: 20000, maxTemperature: 280, maxBedTemperature: 100,
    defaults: { nozzle: 0.4, layerHeight: 0.20, walls: 3, infill: 15, solidLayers: 4, speed: 180, travelSpeed: 300, acceleration: 10000, temperature: 220, bedTemperature: 55 },
    qualityPresets: {
      draft: { layerHeight: 0.28, walls: 2, infill: 12, solidLayers: 3, speed: 220, travelSpeed: 350, acceleration: 12000 },
      standard: { layerHeight: 0.20, walls: 3, infill: 15, solidLayers: 4, speed: 180, travelSpeed: 300, acceleration: 10000 },
      fine: { layerHeight: 0.12, walls: 3, infill: 18, solidLayers: 6, speed: 110, travelSpeed: 250, acceleration: 7000 }
    }
  },
  generic220: { name: 'Generic Cartesian 220', width: 220, depth: 220, height: 250, flavor: 'marlin', nozzles: [0.2, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 300, maxTravelSpeed: 300, maxAcceleration: 6000, maxTemperature: 300, maxBedTemperature: 120 },
  ender3: { name: 'Creality Ender-3', width: 220, depth: 220, height: 250, flavor: 'marlin', nozzles: [0.2, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 180, maxTravelSpeed: 250, maxAcceleration: 3000, maxTemperature: 300, maxBedTemperature: 110 },
  bambuA1: { name: 'Bambu Lab A1', width: 256, depth: 256, height: 256, flavor: 'marlin', nozzles: [0.2, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 500, maxTravelSpeed: 500, maxAcceleration: 10000, maxTemperature: 300, maxBedTemperature: 100 },
  bambuP1: { name: 'Bambu Lab P1 / X1', width: 256, depth: 256, height: 256, flavor: 'marlin', nozzles: [0.2, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 500, maxTravelSpeed: 500, maxAcceleration: 20000, maxTemperature: 300, maxBedTemperature: 120 },
  prusaMk4: { name: 'Prusa MK4', width: 250, depth: 210, height: 220, flavor: 'marlin', nozzles: [0.25, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 300, maxTravelSpeed: 300, maxAcceleration: 6000, maxTemperature: 290, maxBedTemperature: 120 },
  voron24: { name: 'Voron 2.4 300', width: 300, depth: 300, height: 300, flavor: 'klipper', nozzles: [0.25, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 500, maxTravelSpeed: 500, maxAcceleration: 20000, maxTemperature: 320, maxBedTemperature: 120 },
  custom: { name: 'Свой профиль', width: 220, depth: 220, height: 250, flavor: 'marlin', nozzles: [0.2, 0.25, 0.4, 0.6, 0.8], defaultNozzle: 0.4, maxSpeed: 600, maxTravelSpeed: 600, maxAcceleration: 20000, maxTemperature: 350, maxBedTemperature: 150 }
};

const QUALITY_PRESETS = {
  draft: { layerHeight: 0.28, walls: 2, infill: 12, solidLayers: 3, speed: 90, travelSpeed: 180, acceleration: 4000 },
  standard: { layerHeight: 0.20, walls: 3, infill: 15, solidLayers: 4, speed: 65, travelSpeed: 150, acceleration: 3000 },
  fine: { layerHeight: 0.12, walls: 3, infill: 18, solidLayers: 6, speed: 45, travelSpeed: 120, acceleration: 2200 }
};

const FILAMENT_PRESETS = {
  pla: { temperature: 210, bed: 60, density: 1.24, name: 'PLA' },
  petg: { temperature: 240, bed: 80, density: 1.27, name: 'PETG' },
  abs: { temperature: 255, bed: 100, density: 1.05, name: 'ABS / ASA' },
  tpu: { temperature: 225, bed: 50, density: 1.21, name: 'TPU' }
};

const UNIT_TO_MM = { mm: 1, cm: 10, m: 1000, in: 25.4, unit: 1 };
const UNIT_LABELS = { mm: 'мм', cm: 'см', m: 'м', in: 'дюйм', unit: 'ед.' };
const MEASURE_UNIT_OPTIONS = ['auto', 'mm', 'cm', 'm', 'in', 'unit'];

const dom = Object.fromEntries(
  Array.from(document.querySelectorAll('[id]')).map((node) => [node.id, node])
);

let appMode = 'home';
let openPurpose = 'viewer';
let scene;
let camera;
let renderer;
let controls;
let grid;
let buildPlateGroup;
let buildVolumeBox;
let modelRoot = null;
let modelBox = null;
let modelMeta = null;
let modelMixer = null;
let activeObjectUrls = [];
let previewGroup = null;
let measurementGroup = null;
let slicePreviewGroup = null;
let currentSliceResult = null;
let currentGcode = '';
let loadingDepth = 0;
let snackbarTimer = null;
let currentSheet = null;
let wireframeEnabled = false;
let gridVisible = true;
let autoRotateEnabled = false;
let clock = new THREE.Clock();

let measureMode = false;
let measurementPoints = [];
let measurePointerStart = null;
let measureUnitMode = 'auto';
let modelNativeUnit = 'unit';
const measureActivePointers = new Set();
const measureRaycaster = new THREE.Raycaster();
const measurePointer = new THREE.Vector2();

let slicerModelGroup = null;
let slicerNormalizedGroup = null;
let slicerSourceRoot = null;
let slicerSourceMeta = null;
let slicerUnitMode = 'auto';
let slicerTransform = { x: 0, z: 0, rx: 0, ry: 0, rz: 0, scale: 100 };
let slicerSettings = {
  printer: 'adventurer5m',
  quality: 'standard',
  filament: 'pla',
  nozzle: 0.4,
  layerHeight: 0.20,
  walls: 3,
  infill: 15,
  solidLayers: 4,
  speed: 180,
  travelSpeed: 300,
  acceleration: 10000,
  temperature: 220,
  bedTemperature: 55,
  infillPattern: 'grid',
  supports: false,
  supportType: 'none',
  supportPlacement: 'buildPlate',
  supportPattern: 'zigzag',
  supportDensity: 15,
  supportAngle: 50,
  supportInterfaceLayers: 2,
  supportZDistance: 0.20,
  supportXYDistance: 0.35,
  brim: true,
  brimWidth: 5
};

init();

function init() {
  createRubik(dom.bootRubik);
  populatePrinters();
  initScene();
  bindUi();
  applyInitialTheme();
  updateSlicerSettingsUi();
  routeTo('home', false);
  animate();
  setTimeout(() => dom.bootOverlay.classList.add('is-hidden'), 1150);
}

function createRubik(host) {
  if (!host) return;
  host.replaceChildren();
  for (let layerIndex = 0; layerIndex < 3; layerIndex += 1) {
    const layer = document.createElement('div');
    layer.className = 'rubik-layer';
    for (let z = -1; z <= 1; z += 1) {
      for (let x = -1; x <= 1; x += 1) {
        const cubie = document.createElement('div');
        cubie.className = 'rubik-cubie';
        cubie.style.setProperty('--cx', `${x * 24}px`);
        cubie.style.setProperty('--cz', `${z * 24}px`);
        for (let f = 0; f < 6; f += 1) {
          const face = document.createElement('i');
          face.className = 'rubik-face';
          cubie.appendChild(face);
        }
        layer.appendChild(cubie);
      }
    }
    host.appendChild(layer);
  }
}

function initScene() {
  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(44, 1, 0.01, 1000000);
  camera.position.set(3, 2.2, 4.5);

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2.2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  dom.canvasHost.appendChild(renderer.domElement);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.075;
  controls.rotateSpeed = 0.72;
  controls.zoomSpeed = 0.9;
  controls.panSpeed = 0.68;
  controls.screenSpacePanning = true;
  controls.minDistance = 0.001;
  controls.maxDistance = 1000000;

  const hemi = new THREE.HemisphereLight(0xffffff, 0x373044, 2.2);
  scene.add(hemi);
  const key = new THREE.DirectionalLight(0xffffff, 3.3);
  key.position.set(4, 8, 5);
  key.castShadow = true;
  key.shadow.mapSize.set(1024, 1024);
  scene.add(key);
  const fill = new THREE.DirectionalLight(0xbca9ff, 1.25);
  fill.position.set(-6, 3, -4);
  scene.add(fill);

  grid = new THREE.GridHelper(10, 20, 0x766e82, 0xaaa2b3);
  grid.material.transparent = true;
  grid.material.opacity = 0.32;
  grid.material.depthWrite = false;
  scene.add(grid);

  buildPlateGroup = new THREE.Group();
  buildPlateGroup.visible = false;
  scene.add(buildPlateGroup);

  measurementGroup = new THREE.Group();
  measurementGroup.renderOrder = 1000;
  scene.add(measurementGroup);

  slicePreviewGroup = new THREE.Group();
  slicePreviewGroup.renderOrder = 900;
  scene.add(slicePreviewGroup);

  bindMeasurementPointerEvents();
  updateSceneTheme();
  resizeRenderer();
  new ResizeObserver(resizeRenderer).observe(dom.canvasArea);
}

function bindUi() {
  dom.chooseViewerButton.addEventListener('click', () => enterWorkspace('viewer'));
  dom.chooseSlicerButton.addEventListener('click', () => enterWorkspace('slicer'));
  dom.backButton.addEventListener('click', () => routeTo('home'));
  dom.homeThemeButton.addEventListener('click', toggleTheme);
  dom.workspaceThemeButton.addEventListener('click', toggleTheme);
  dom.moreButton.addEventListener('click', () => {
    if (modelMeta) openSheet(dom.infoSheet);
    else openSheet(dom.formatsSheet);
  });
  dom.formatsButton.addEventListener('click', () => openSheet(dom.formatsSheet));
  dom.modelChip.addEventListener('click', () => modelMeta && openSheet(dom.infoSheet));
  dom.scrim.addEventListener('click', closeSheets);
  document.querySelectorAll('[data-close-sheet]').forEach((button) => button.addEventListener('click', closeSheets));

  const openPicker = () => {
    openPurpose = appMode === 'slicer' ? 'slicer' : 'viewer';
    dom.fileInput.value = '';
    dom.fileInput.click();
  };
  dom.openButton.addEventListener('click', openPicker);
  dom.emptyOpenButton.addEventListener('click', openPicker);
  dom.fileInput.addEventListener('change', async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length) await openFiles(files, openPurpose);
  });

  dom.gridButton.addEventListener('click', () => {
    gridVisible = !gridVisible;
    if (appMode === 'viewer') grid.visible = gridVisible;
    setPressed(dom.gridButton, gridVisible);
  });
  dom.wireframeButton.addEventListener('click', () => {
    if (!modelRoot) return showSnackbar('Сначала открой модель');
    wireframeEnabled = !wireframeEnabled;
    setWireframe(modelRoot, wireframeEnabled);
    setPressed(dom.wireframeButton, wireframeEnabled);
  });
  dom.rotateButton.addEventListener('click', () => {
    if (!modelRoot) return showSnackbar('Сначала открой модель');
    autoRotateEnabled = !autoRotateEnabled;
    controls.autoRotate = autoRotateEnabled;
    controls.autoRotateSpeed = 2.1;
    setPressed(dom.rotateButton, autoRotateEnabled);
  });
  dom.resetButton.addEventListener('click', () => {
    if (!modelBox) return showSnackbar('Сначала открой модель');
    fitCameraToBox(modelBox, false);
  });

  dom.measureButton.addEventListener('click', toggleMeasureMode);
  dom.measureUnitButton.addEventListener('click', cycleMeasurementUnit);
  dom.measureClearButton.addEventListener('click', () => clearMeasurement(true));

  dom.transformButton.addEventListener('click', () => {
    if (!slicerModelGroup) return showSnackbar('Сначала открой модель');
    openSheet(dom.transformSheet);
  });
  dom.printSettingsButton.addEventListener('click', () => openSheet(dom.settingsSheet));
  dom.quickSettingsButton.addEventListener('click', () => openSheet(dom.settingsSheet));
  dom.orientButton.addEventListener('click', autoOrientModel);
  dom.autoOrientSheetButton.addEventListener('click', autoOrientModel);
  dom.sliceButton.addEventListener('click', startSlicing);
  dom.exportModelButton.addEventListener('click', exportPreparedStl);
  dom.exportGcodeButton.addEventListener('click', exportGcode);
  dom.returnToModelButton.addEventListener('click', () => {
    currentSliceResult = null;
    currentGcode = '';
    dom.layerPreviewCard.hidden = true;
    clearSlicePreview();
    if (slicerModelGroup) slicerModelGroup.visible = true;
    closeSheets();
  });
  dom.layerSlider.addEventListener('input', () => showSliceLayer(Number(dom.layerSlider.value)));

  document.querySelectorAll('#transformTabs button').forEach((button) => {
    button.addEventListener('click', () => switchTransformTab(button.dataset.tab));
  });
  dom.positionXInput.addEventListener('input', updateTransformFromUi);
  dom.positionYInput.addEventListener('input', updateTransformFromUi);
  dom.rotationXInput.addEventListener('input', updateTransformFromUi);
  dom.rotationYInput.addEventListener('input', updateTransformFromUi);
  dom.rotationZInput.addEventListener('input', updateTransformFromUi);
  dom.scaleInput.addEventListener('input', updateTransformFromUi);
  dom.modelUnitSelect.addEventListener('change', () => {
    slicerUnitMode = dom.modelUnitSelect.value;
    reapplySlicerNormalization();
  });
  dom.centerModelButton.addEventListener('click', () => {
    slicerTransform.x = 0;
    slicerTransform.z = 0;
    syncTransformUi();
    applySlicerTransform();
  });
  dom.dropModelButton.addEventListener('click', () => {
    applySlicerTransform();
    showSnackbar('Модель поставлена на стол');
  });
  dom.resetRotationButton.addEventListener('click', () => {
    slicerTransform.rx = slicerTransform.ry = slicerTransform.rz = 0;
    syncTransformUi();
    applySlicerTransform();
  });
  dom.resetScaleButton.addEventListener('click', () => {
    slicerTransform.scale = 100;
    syncTransformUi();
    applySlicerTransform();
  });
  dom.fitBedButton.addEventListener('click', fitModelToBed);

  dom.printerSelect.addEventListener('change', () => {
    slicerSettings.printer = dom.printerSelect.value;
    applyPrinterPreset(true);
    updateBuildPlate();
    applySlicerTransform();
  });
  dom.qualitySelect.addEventListener('change', applyQualityPreset);
  dom.filamentSelect.addEventListener('change', applyFilamentPreset);
  [dom.nozzleSelect, dom.layerHeightInput, dom.wallsInput, dom.infillInput, dom.solidLayersInput,
    dom.speedInput, dom.travelSpeedInput, dom.accelerationInput, dom.temperatureInput, dom.bedTemperatureInput,
    dom.infillPatternSelect, dom.supportTypeSelect, dom.supportPlacementSelect, dom.supportPatternSelect,
    dom.supportDensityInput, dom.supportAngleInput, dom.supportInterfaceLayersInput,
    dom.supportZDistanceInput, dom.supportXYDistanceInput, dom.brimInput, dom.brimWidthInput]
    .forEach((input) => input.addEventListener('input', readSlicerSettingsFromUi));

  document.addEventListener('visibilitychange', () => document.hidden ? clock.stop() : clock.start());
  window.addEventListener('resize', resizeRenderer);
  window.addEventListener('popstate', () => {
    if (appMode !== 'home') routeTo('home', false);
  });
  window.addEventListener('error', (event) => {
    console.error(event.error || event.message);
    showSnackbar(`Ошибка интерфейса: ${event.message || 'неизвестная ошибка'}`, 5200);
  });
  window.addEventListener('unhandledrejection', (event) => console.error(event.reason));

  window.appBack = () => {
    if (currentSheet) {
      closeSheets();
      return true;
    }
    if (appMode !== 'home') {
      routeTo('home');
      return true;
    }
    return false;
  };
}

function enterWorkspace(mode) {
  routeTo(mode);
  if (mode === 'viewer') configureViewerScene();
  else configureSlicerScene();
}

function routeTo(mode, push = true) {
  appMode = mode;
  const onHome = mode === 'home';
  dom.homeScreen.classList.toggle('is-active', onHome);
  dom.workspaceScreen.classList.toggle('is-active', !onHome);
  if (push && !onHome) history.pushState({ mode }, '', `#${mode}`);
  if (onHome) {
    controls.enabled = false;
    autoRotateEnabled = false;
    controls.autoRotate = false;
    closeSheets();
    return;
  }
  controls.enabled = true;
  const isViewer = mode === 'viewer';
  dom.viewerToolbar.hidden = !isViewer;
  dom.slicerToolbar.hidden = isViewer;
  dom.measureButton.hidden = !isViewer || !modelRoot;
  dom.slicerStatusCard.hidden = isViewer;
  dom.workspaceEyebrow.textContent = isViewer ? 'Просмотр' : 'Подготовка к печати';
  dom.workspaceTitle.textContent = isViewer ? '3D-модель' : 'FDM Slicer';
  dom.emptyEyebrow.textContent = isViewer ? '3D-просмотр' : 'FDM-подготовка';
  dom.emptyTitle.textContent = isViewer ? 'Открой модель' : 'Добавь модель на стол';
  dom.emptyText.textContent = isViewer
    ? 'Выбери файл или ZIP с текстурами.'
    : 'Проверь ориентацию, масштаб и профиль, затем создай G-code.';
  dom.layerPreviewCard.hidden = true;
  clearSlicePreview();
  resizeRenderer();
}

function configureViewerScene() {
  buildPlateGroup.visible = false;
  grid.visible = gridVisible;
  if (slicerModelGroup) slicerModelGroup.visible = false;
  if (modelRoot) {
    modelRoot.visible = true;
    modelBox = new THREE.Box3().setFromObject(modelRoot);
    fitCameraToBox(modelBox, true);
  }
  updateEmptyState();
}

function configureSlicerScene() {
  grid.visible = false;
  buildPlateGroup.visible = true;
  updateBuildPlate();
  if (modelRoot) modelRoot.visible = false;
  if (slicerModelGroup) {
    slicerModelGroup.visible = true;
    modelBox = new THREE.Box3().setFromObject(slicerModelGroup);
    fitCameraToBox(expandBoxForBed(modelBox), true);
  } else {
    fitCameraToBuildPlate();
  }
  updateEmptyState();
  updateSlicerStatus();
}

function updateEmptyState() {
  const hasModel = appMode === 'viewer' ? Boolean(modelRoot) : Boolean(slicerModelGroup);
  dom.emptyState.hidden = hasModel;
  dom.modelChip.hidden = !hasModel;
}

async function openFiles(selectedFiles, purpose) {
  closeSheets();
  showOperation('Читаем модель', selectedFiles.length === 1 ? selectedFiles[0].name : `${selectedFiles.length} файлов`);
  try {
    const packageInfo = await createAssetRecords(selectedFiles);
    const mainRecord = chooseMainModel(packageInfo.records);
    if (!mainRecord) throw new Error('В выбранных файлах нет поддерживаемой 3D-модели');

    revokeActiveUrls();
    const assetContext = createAssetContext(packageInfo.records);
    activeObjectUrls = assetContext.urls;
    updateOperation('Загружаем геометрию', mainRecord.path);

    const loaded = await loadModel(mainRecord, packageInfo.records, assetContext);
    if (!loaded?.root) throw new Error('Загрузчик не вернул геометрию');

    const meta = {
      name: mainRecord.name,
      path: mainRecord.path,
      extension: extensionOf(mainRecord.name),
      fileSize: packageInfo.displaySize || mainRecord.size,
      packageName: packageInfo.packageName
    };

    if (purpose === 'slicer') installSlicerModel(loaded.root, meta);
    else installViewerModel(loaded.root, loaded.animations || [], meta);

    hideOperation();
  } catch (error) {
    console.error(error);
    hideOperation();
    showSnackbar(humanizeError(error), 5600);
    updateEmptyState();
  }
}

async function createAssetRecords(selectedFiles) {
  if (selectedFiles.length === 1 && extensionOf(selectedFiles[0].name) === 'zip') {
    updateOperation('Распаковываем ZIP', selectedFiles[0].name);
    const zip = await JSZip.loadAsync(selectedFiles[0]);
    const records = [];
    const entries = Object.values(zip.files).filter((entry) => {
      const path = normalizePath(entry.name);
      return !entry.dir && !path.startsWith('__MACOSX/') && !path.endsWith('/.DS_Store');
    });
    let completed = 0;
    for (const entry of entries) {
      const blob = await entry.async('blob');
      const normalized = normalizePath(entry.name);
      records.push({ path: normalized, name: basename(normalized), blob, size: blob.size, type: guessMime(normalized) });
      completed += 1;
      updateOperation('Распаковываем ZIP', `${completed} из ${entries.length}`);
    }
    return { records, packageName: selectedFiles[0].name, displaySize: selectedFiles[0].size };
  }

  const records = selectedFiles.map((file) => ({
    path: normalizePath(file.webkitRelativePath || file.name),
    name: file.name,
    blob: file,
    size: file.size,
    type: file.type || guessMime(file.name)
  }));
  return {
    records,
    packageName: selectedFiles.length > 1 ? `${selectedFiles.length} файлов` : selectedFiles[0].name,
    displaySize: selectedFiles.reduce((sum, file) => sum + file.size, 0)
  };
}

function chooseMainModel(records) {
  return records
    .filter((record) => MODEL_EXTENSIONS.has(extensionOf(record.name)))
    .sort((a, b) => {
      const depthDiff = a.path.split('/').length - b.path.split('/').length;
      return depthDiff || (MODEL_PRIORITY[extensionOf(a.name)] ?? 99) - (MODEL_PRIORITY[extensionOf(b.name)] ?? 99);
    })[0] || null;
}

function createAssetContext(records) {
  const exact = new Map();
  const byName = new Map();
  const urls = [];
  const ensureUrl = (record) => {
    if (!record.url) {
      record.url = URL.createObjectURL(record.blob);
      urls.push(record.url);
    }
    return record.url;
  };
  for (const record of records) {
    exact.set(normalizePath(record.path).toLowerCase(), record);
    if (!byName.has(record.name.toLowerCase())) byName.set(record.name.toLowerCase(), record);
  }
  const resolveRecord = (url) => {
    if (!url || /^(blob:|data:|file:\/\/\/android_asset\/)/i.test(url)) return null;
    let candidate = url;
    try { candidate = decodeURIComponent(candidate); } catch (_) { /* keep raw */ }
    candidate = normalizePath(candidate.split('?')[0].split('#')[0]).replace(/^(\.\/)+/, '').replace(/^\//, '');
    return exact.get(candidate.toLowerCase())
      || exact.get(normalizePath(candidate.replace(/^(\.\.\/)+/, '')).toLowerCase())
      || byName.get(basename(candidate).toLowerCase())
      || null;
  };
  const manager = new THREE.LoadingManager();
  manager.setURLModifier((url) => {
    const record = resolveRecord(url);
    return record ? ensureUrl(record) : url;
  });
  manager.onProgress = (_url, loaded, total) => updateOperation('Загружаем ресурсы', total > 0 ? `${loaded} из ${total}` : 'Чтение…');
  manager.onError = (url) => console.warn('Не удалось загрузить ресурс', url);
  return { manager, exact, byName, ensureUrl, urls, resolveRecord };
}

async function loadModel(mainRecord, records, context) {
  const ext = extensionOf(mainRecord.name);
  const mainUrl = context.ensureUrl(mainRecord);
  const manager = context.manager;
  const draco = new DRACOLoader(manager);
  draco.setDecoderPath('./draco/');
  const ktx2 = new KTX2Loader(manager);
  ktx2.setTranscoderPath('./basis/');
  ktx2.detectSupport(renderer);

  try {
    switch (ext) {
      case 'glb':
      case 'gltf': {
        const loader = new GLTFLoader(manager);
        loader.setDRACOLoader(draco);
        loader.setKTX2Loader(ktx2);
        loader.setMeshoptDecoder(MeshoptDecoder);
        const result = await loader.loadAsync(mainUrl);
        return { root: result.scene || result.scenes?.[0], animations: result.animations || [] };
      }
      case 'stl': {
        const geometry = await new STLLoader(manager).loadAsync(mainUrl);
        if (!geometry.attributes.normal) geometry.computeVertexNormals();
        return { root: new THREE.Mesh(geometry, printMaterial(geometry.getAttribute('color'))), animations: [] };
      }
      case 'ply': {
        const geometry = await new PLYLoader(manager).loadAsync(mainUrl);
        if (!geometry.attributes.normal) geometry.computeVertexNormals();
        return { root: new THREE.Mesh(geometry, printMaterial(geometry.getAttribute('color'))), animations: [] };
      }
      case 'obj': {
        const objLoader = new OBJLoader(manager);
        const sameBase = records.find((record) => extensionOf(record.name) === 'mtl' && stripExtension(record.name).toLowerCase() === stripExtension(mainRecord.name).toLowerCase());
        const mtl = sameBase || records.find((record) => extensionOf(record.name) === 'mtl');
        if (mtl) {
          const materials = await new MTLLoader(manager).loadAsync(context.ensureUrl(mtl));
          materials.preload();
          objLoader.setMaterials(materials);
        }
        return { root: await objLoader.loadAsync(mainUrl), animations: [] };
      }
      case 'fbx': {
        const root = await new FBXLoader(manager).loadAsync(mainUrl);
        return { root, animations: root.animations || [] };
      }
      case 'dae': {
        const collada = await new ColladaLoader(manager).loadAsync(mainUrl);
        return { root: collada.scene, animations: collada.animations || [] };
      }
      case '3mf': return { root: await new ThreeMFLoader(manager).loadAsync(mainUrl), animations: [] };
      case '3ds': return { root: await new TDSLoader(manager).loadAsync(mainUrl), animations: [] };
      case 'amf': return { root: await new AMFLoader(manager).loadAsync(mainUrl), animations: [] };
      case 'usdz': return { root: await new USDZLoader(manager).loadAsync(mainUrl), animations: [] };
      default: throw new Error(`Формат .${ext} пока не поддерживается`);
    }
  } finally {
    draco.dispose();
    ktx2.dispose();
  }
}

function printMaterial(colorAttribute) {
  return new THREE.MeshStandardMaterial({
    color: colorAttribute ? 0xffffff : 0x9b87d8,
    roughness: 0.7,
    metalness: 0.04,
    vertexColors: Boolean(colorAttribute)
  });
}

function installViewerModel(root, animations, meta) {
  disposeSlicerModel();
  disposeViewerModel();
  modelRoot = root;
  modelRoot.updateMatrixWorld(true);
  const initialBox = new THREE.Box3().setFromObject(modelRoot);
  if (initialBox.isEmpty()) throw new Error('В модели нет отображаемой геометрии');
  const center = initialBox.getCenter(new THREE.Vector3());
  modelRoot.position.sub(center);
  modelRoot.updateMatrixWorld(true);
  modelBox = new THREE.Box3().setFromObject(modelRoot);
  const size = modelBox.getSize(new THREE.Vector3());

  prepareRenderableRoot(modelRoot);
  scene.add(modelRoot);
  if (animations.length) {
    modelMixer = new THREE.AnimationMixer(modelRoot);
    for (const clip of animations) if (clip?.duration > 0) modelMixer.clipAction(clip).play();
  }
  setWireframe(modelRoot, wireframeEnabled);
  const stats = collectStats(modelRoot);
  modelMeta = { ...meta, dimensions: size, ...stats };
  modelNativeUnit = inferModelUnit(meta.extension);
  measureUnitMode = 'auto';
  clearMeasurement(false);
  updateModelUi();
  updateEmptyState();
  dom.measureButton.hidden = false;
  dom.gestureHint.hidden = false;
  grid.scale.setScalar(Math.max(Math.max(size.x, size.y, size.z) / 10, 0.0001));
  grid.position.y = modelBox.min.y;
  fitCameraToBox(modelBox, true);
  setTimeout(() => { dom.gestureHint.hidden = true; }, 4200);
}

function installSlicerModel(root, meta) {
  disposeViewerModel();
  disposeSlicerModel();
  slicerSourceRoot = root;
  slicerSourceMeta = meta;
  prepareRenderableRoot(slicerSourceRoot, true);

  slicerNormalizedGroup = new THREE.Group();
  slicerNormalizedGroup.add(slicerSourceRoot);
  slicerModelGroup = new THREE.Group();
  slicerModelGroup.add(slicerNormalizedGroup);
  scene.add(slicerModelGroup);

  slicerUnitMode = 'auto';
  slicerTransform = { x: 0, z: 0, rx: 0, ry: 0, rz: 0, scale: 100 };
  dom.modelUnitSelect.value = 'auto';
  normalizeSlicerSource();
  applySlicerTransform();

  const stats = collectStats(slicerSourceRoot);
  modelMeta = { ...meta, dimensions: new THREE.Box3().setFromObject(slicerModelGroup).getSize(new THREE.Vector3()), ...stats };
  updateModelUi();
  updateEmptyState();
  updateSlicerStatus();
  syncTransformUi();
  fitCameraToBox(expandBoxForBed(new THREE.Box3().setFromObject(slicerModelGroup)), true);
  showSnackbar('Модель добавлена на стол');
}

function prepareRenderableRoot(root, forcePrintMaterial = false) {
  root.traverse((object) => {
    if (!object.isMesh) return;
    object.castShadow = true;
    object.receiveShadow = true;
    if (object.geometry && !object.geometry.boundingSphere) object.geometry.computeBoundingSphere();
    if (forcePrintMaterial) {
      const original = Array.isArray(object.material) ? object.material[0] : object.material;
      object.userData.originalMaterial = object.material;
      object.material = new THREE.MeshStandardMaterial({
        color: 0x9d88e0,
        roughness: 0.68,
        metalness: 0.03,
        transparent: true,
        opacity: 0.98,
        side: THREE.DoubleSide,
        vertexColors: Boolean(original?.vertexColors)
      });
    }
  });
}

function normalizeSlicerSource() {
  if (!slicerNormalizedGroup || !slicerSourceRoot || !slicerSourceMeta) return;
  slicerSourceRoot.position.set(0, 0, 0);
  slicerSourceRoot.rotation.set(0, 0, 0);
  slicerSourceRoot.scale.setScalar(1);
  slicerNormalizedGroup.position.set(0, 0, 0);
  slicerNormalizedGroup.rotation.set(0, 0, 0);
  slicerNormalizedGroup.scale.setScalar(1);

  const unit = resolvedSlicerUnit();
  const factor = UNIT_TO_MM[unit] || 1;
  slicerNormalizedGroup.scale.setScalar(factor);

  if (usesZUpSource(slicerSourceMeta.extension)) {
    slicerNormalizedGroup.rotation.x = -Math.PI / 2;
  }
  slicerNormalizedGroup.updateMatrixWorld(true);

  const box = new THREE.Box3().setFromObject(slicerNormalizedGroup);
  if (box.isEmpty()) throw new Error('В модели нет геометрии для печати');
  const center = box.getCenter(new THREE.Vector3());
  slicerNormalizedGroup.position.set(-center.x, -box.min.y, -center.z);
  slicerNormalizedGroup.updateMatrixWorld(true);
}

function reapplySlicerNormalization() {
  if (!slicerSourceRoot) return;
  normalizeSlicerSource();
  applySlicerTransform();
  fitModelToBed(false);
}

function usesZUpSource(extension) {
  return ['stl', 'obj', 'ply', '3mf', '3ds', 'amf'].includes(String(extension).toLowerCase());
}

function resolvedSlicerUnit() {
  if (slicerUnitMode !== 'auto') return slicerUnitMode;
  return inferModelUnit(slicerSourceMeta?.extension || 'stl');
}

function inferModelUnit(extension) {
  switch (String(extension || '').toLowerCase()) {
    case 'stl':
    case '3mf':
    case 'amf':
    case 'obj':
    case 'ply':
    case '3ds':
      return 'mm';
    case 'fbx':
      return 'cm';
    case 'glb':
    case 'gltf':
    case 'dae':
    case 'usdz':
      return 'm';
    default:
      return 'unit';
  }
}

function prepareSlicerMaterialForPreview(visible = true) {
  if (!slicerSourceRoot) return;
  slicerSourceRoot.traverse((object) => {
    if (!object.isMesh || !object.material) return;
    object.material.transparent = true;
    object.material.opacity = visible ? 0.98 : 0.16;
    object.material.depthWrite = visible;
    object.material.needsUpdate = true;
  });
}

function collectStats(root) {
  let vertices = 0;
  let triangles = 0;
  let objects = 0;
  root.traverse((object) => {
    objects += 1;
    if (!object.isMesh || !object.geometry) return;
    const position = object.geometry.getAttribute('position');
    if (!position) return;
    vertices += position.count;
    triangles += object.geometry.index ? Math.floor(object.geometry.index.count / 3) : Math.floor(position.count / 3);
  });
  return { vertices, triangles, objects };
}

function updateModelUi() {
  if (!modelMeta) return;
  const size = appMode === 'slicer' && slicerModelGroup
    ? new THREE.Box3().setFromObject(slicerModelGroup).getSize(new THREE.Vector3())
    : modelMeta.dimensions;
  const ext = modelMeta.extension.toUpperCase();
  dom.modelName.textContent = modelMeta.name;
  dom.modelSummary.textContent = `${formatNumber(modelMeta.triangles)} треуг. · ${formatBytes(modelMeta.fileSize)}`;
  dom.formatBadge.textContent = ext;
  dom.infoName.textContent = modelMeta.name;
  dom.infoFormat.textContent = `${ext} · ${modelMeta.packageName || 'один файл'}`;
  dom.infoFileSize.textContent = formatBytes(modelMeta.fileSize);
  dom.infoDimensions.textContent = `${formatDimension(size.x)} × ${formatDimension(size.y)} × ${formatDimension(size.z)} ${appMode === 'slicer' ? 'мм' : 'ед.'}`;
  dom.infoVertices.textContent = formatNumber(modelMeta.vertices);
  dom.infoTriangles.textContent = formatNumber(modelMeta.triangles);
  dom.infoObjects.textContent = formatNumber(modelMeta.objects);
  dom.workspaceTitle.textContent = appMode === 'slicer' ? 'FDM Slicer' : modelMeta.name;
}

function disposeViewerModel() {
  clearMeasurement(false);
  if (!modelRoot) return;
  scene.remove(modelRoot);
  disposeObjectTree(modelRoot);
  if (modelMixer) {
    modelMixer.stopAllAction();
    modelMixer.uncacheRoot(modelRoot);
  }
  modelRoot = null;
  modelMixer = null;
  if (appMode === 'viewer') modelMeta = null;
}

function disposeSlicerModel() {
  clearSlicePreview();
  currentSliceResult = null;
  currentGcode = '';
  if (!slicerModelGroup) return;
  scene.remove(slicerModelGroup);
  disposeObjectTree(slicerModelGroup);
  slicerModelGroup = null;
  slicerNormalizedGroup = null;
  slicerSourceRoot = null;
  slicerSourceMeta = null;
  if (appMode === 'slicer') modelMeta = null;
}

function disposeObjectTree(root) {
  root.traverse((object) => {
    object.geometry?.dispose?.();
    const materials = Array.isArray(object.material) ? object.material : [object.material];
    for (const material of materials) {
      if (!material) continue;
      for (const value of Object.values(material)) if (value?.isTexture) value.dispose();
      material.dispose?.();
    }
  });
}

function revokeActiveUrls() {
  for (const url of activeObjectUrls) URL.revokeObjectURL(url);
  activeObjectUrls = [];
}

function updateBuildPlate() {
  if (!buildPlateGroup) return;
  while (buildPlateGroup.children.length) {
    const child = buildPlateGroup.children.pop();
    child.geometry?.dispose?.();
    const materials = Array.isArray(child.material) ? child.material : [child.material];
    materials.forEach((material) => material?.dispose?.());
  }

  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  const plate = new THREE.Mesh(
    new THREE.BoxGeometry(printer.width, 1.6, printer.depth),
    new THREE.MeshStandardMaterial({ color: isDarkTheme() ? 0x23232a : 0xdcd7e3, roughness: 0.88, metalness: 0.02 })
  );
  plate.position.y = -0.8;
  plate.receiveShadow = true;
  buildPlateGroup.add(plate);

  const gridLines = createRectGrid(printer.width, printer.depth, 10);
  gridLines.position.y = 0.03;
  buildPlateGroup.add(gridLines);

  const outline = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(printer.width, printer.height, printer.depth)),
    new THREE.LineBasicMaterial({ color: isDarkTheme() ? 0x6c6675 : 0x8a8191, transparent: true, opacity: 0.32 })
  );
  outline.position.y = printer.height / 2;
  buildVolumeBox = outline;
  buildPlateGroup.add(outline);
  buildPlateGroup.visible = appMode === 'slicer';
}

function createRectGrid(width, depth, step) {
  const vertices = [];
  const halfW = width / 2;
  const halfD = depth / 2;
  for (let x = -halfW; x <= halfW + 0.001; x += step) vertices.push(x, 0, -halfD, x, 0, halfD);
  for (let z = -halfD; z <= halfD + 0.001; z += step) vertices.push(-halfW, 0, z, halfW, 0, z);
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));
  const material = new THREE.LineBasicMaterial({ color: isDarkTheme() ? 0x6f6978 : 0x91889a, transparent: true, opacity: 0.23 });
  return new THREE.LineSegments(geometry, material);
}

function fitCameraToBuildPlate() {
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  const box = new THREE.Box3(
    new THREE.Vector3(-printer.width / 2, 0, -printer.depth / 2),
    new THREE.Vector3(printer.width / 2, Math.min(printer.height, 100), printer.depth / 2)
  );
  fitCameraToBox(box, true);
}

function expandBoxForBed(box) {
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  const bedBox = new THREE.Box3(
    new THREE.Vector3(-printer.width / 2, -1, -printer.depth / 2),
    new THREE.Vector3(printer.width / 2, Math.min(printer.height, Math.max(box.max.y, 40)), printer.depth / 2)
  );
  return box.clone().union(bedBox);
}

function fitCameraToBox(box, notify = false) {
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxSize = Math.max(size.x, size.y, size.z, 0.0001);
  const fov = THREE.MathUtils.degToRad(camera.fov);
  const distance = (maxSize / (2 * Math.tan(fov / 2))) * (appMode === 'slicer' ? 1.25 : 1.55);
  camera.near = Math.max(distance / 1500, 0.00001);
  camera.far = Math.max(distance * 1200, 2000);
  camera.updateProjectionMatrix();
  camera.position.set(center.x + distance * 0.82, center.y + distance * 0.62, center.z + distance * 0.95);
  controls.target.copy(center);
  controls.minDistance = Math.max(maxSize * 0.02, 0.001);
  controls.maxDistance = Math.max(maxSize * 100, 100);
  controls.update();
  if (notify) showSnackbar('Камера сброшена');
}

function setWireframe(root, enabled) {
  root.traverse((object) => {
    if (!object.isMesh) return;
    const materials = Array.isArray(object.material) ? object.material : [object.material];
    for (const material of materials) {
      if (material && 'wireframe' in material) {
        material.wireframe = enabled;
        material.needsUpdate = true;
      }
    }
  });
}

function switchTransformTab(tab) {
  document.querySelectorAll('#transformTabs button').forEach((button) => button.classList.toggle('is-selected', button.dataset.tab === tab));
  document.querySelectorAll('.transform-panel').forEach((panel) => panel.classList.toggle('is-active', panel.dataset.panel === tab));
}

function syncTransformUi() {
  dom.positionXInput.value = slicerTransform.x;
  dom.positionYInput.value = slicerTransform.z;
  dom.rotationXInput.value = slicerTransform.rx;
  dom.rotationYInput.value = slicerTransform.ry;
  dom.rotationZInput.value = slicerTransform.rz;
  dom.scaleInput.value = slicerTransform.scale;
  dom.positionXValue.textContent = `${roundSmart(slicerTransform.x)} мм`;
  dom.positionYValue.textContent = `${roundSmart(slicerTransform.z)} мм`;
  dom.rotationXValue.textContent = `${Math.round(slicerTransform.rx)}°`;
  dom.rotationYValue.textContent = `${Math.round(slicerTransform.ry)}°`;
  dom.rotationZValue.textContent = `${Math.round(slicerTransform.rz)}°`;
  dom.scaleValue.textContent = `${Math.round(slicerTransform.scale)}%`;
  updateTransformedDimensions();
}

function updateTransformFromUi() {
  slicerTransform.x = Number(dom.positionXInput.value) || 0;
  slicerTransform.z = Number(dom.positionYInput.value) || 0;
  slicerTransform.rx = Number(dom.rotationXInput.value) || 0;
  slicerTransform.ry = Number(dom.rotationYInput.value) || 0;
  slicerTransform.rz = Number(dom.rotationZInput.value) || 0;
  slicerTransform.scale = Math.max(1, Number(dom.scaleInput.value) || 100);
  syncTransformUi();
  applySlicerTransform();
}

function applySlicerTransform() {
  if (!slicerModelGroup || !slicerNormalizedGroup) return;
  clearSlicePreview();
  currentSliceResult = null;
  currentGcode = '';
  dom.layerPreviewCard.hidden = true;
  prepareSlicerMaterialForPreview(true);

  const scale = slicerTransform.scale / 100;
  slicerModelGroup.position.set(slicerTransform.x, 0, slicerTransform.z);
  slicerModelGroup.rotation.set(
    THREE.MathUtils.degToRad(slicerTransform.rx),
    THREE.MathUtils.degToRad(slicerTransform.ry),
    THREE.MathUtils.degToRad(slicerTransform.rz),
    'XYZ'
  );
  slicerModelGroup.scale.setScalar(scale);
  slicerModelGroup.updateMatrixWorld(true);

  const box = new THREE.Box3().setFromObject(slicerModelGroup);
  slicerModelGroup.position.y -= box.min.y;
  slicerModelGroup.updateMatrixWorld(true);
  modelBox = new THREE.Box3().setFromObject(slicerModelGroup);
  updateTransformedDimensions();
  updateSlicerStatus();
  updateModelUi();
}

function updateTransformedDimensions() {
  if (!slicerModelGroup) {
    dom.transformedDimensions.textContent = '—';
    return;
  }
  const size = new THREE.Box3().setFromObject(slicerModelGroup).getSize(new THREE.Vector3());
  dom.transformedDimensions.textContent = `${roundSmart(size.x)} × ${roundSmart(size.z)} × ${roundSmart(size.y)} мм`;
}

function fitModelToBed(notify = true) {
  if (!slicerModelGroup) return showSnackbar('Сначала открой модель');
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  const box = new THREE.Box3().setFromObject(slicerModelGroup);
  const size = box.getSize(new THREE.Vector3());
  const factor = Math.min(
    (printer.width - 4) / Math.max(size.x, 0.001),
    (printer.depth - 4) / Math.max(size.z, 0.001),
    (printer.height - 2) / Math.max(size.y, 0.001),
    1
  );
  if (factor < 1) slicerTransform.scale = Math.max(1, slicerTransform.scale * factor);
  slicerTransform.x = 0;
  slicerTransform.z = 0;
  syncTransformUi();
  applySlicerTransform();
  if (notify) showSnackbar(factor < 1 ? 'Модель уменьшена и помещена на стол' : 'Модель уже помещается');
}

function autoOrientModel() {
  if (!slicerModelGroup || !slicerNormalizedGroup) return showSnackbar('Сначала открой модель');
  showOperation('Ищем ориентацию', 'Проверяем шесть положений');
  const candidates = [
    [0, 0, 0], [90, 0, 0], [-90, 0, 0], [0, 0, 90], [0, 0, -90], [180, 0, 0]
  ];
  const original = { ...slicerTransform };
  let best = null;
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  for (const [rx, ry, rz] of candidates) {
    slicerTransform.rx = rx;
    slicerTransform.ry = ry;
    slicerTransform.rz = rz;
    applySlicerTransform();
    const box = new THREE.Box3().setFromObject(slicerModelGroup);
    const size = box.getSize(new THREE.Vector3());
    const fits = size.x <= printer.width && size.z <= printer.depth && size.y <= printer.height;
    const footprint = Math.max(size.x * size.z, 0.001);
    const score = (fits ? 1000000 : 0) + footprint * 3 - size.y * 18;
    if (!best || score > best.score) best = { rx, ry, rz, score };
  }
  slicerTransform = { ...original, rx: best.rx, ry: best.ry, rz: best.rz, x: 0, z: 0 };
  syncTransformUi();
  applySlicerTransform();
  hideOperation();
  showSnackbar('Выбрана ориентация с большой площадью основания');
}

function populatePrinters() {
  dom.printerSelect.replaceChildren();
  for (const [id, printer] of Object.entries(PRINTERS)) {
    const option = document.createElement('option');
    option.value = id;
    option.textContent = `${printer.name} · ${printer.width}×${printer.depth}×${printer.height}`;
    dom.printerSelect.appendChild(option);
  }
}

function populateNozzleOptions(printer) {
  const values = printer.nozzles || [0.2, 0.4, 0.6, 0.8];
  const current = Number(slicerSettings.nozzle);
  dom.nozzleSelect.replaceChildren();
  for (const value of values) {
    const option = document.createElement('option');
    option.value = String(value);
    option.textContent = `${value} мм`;
    dom.nozzleSelect.appendChild(option);
  }
  slicerSettings.nozzle = values.includes(current) ? current : (printer.defaultNozzle || values[0]);
}

function applyPrinterPreset(notify = false) {
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  if (printer.defaults) Object.assign(slicerSettings, printer.defaults);
  slicerSettings.quality = 'standard';
  populateNozzleOptions(printer);
  updateSlicerSettingsUi();
  if (notify) showSnackbar(`${printer.name}: профиль применён`);
}

function applyQualityPreset() {
  const quality = dom.qualitySelect.value;
  slicerSettings.quality = quality;
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  const preset = printer.qualityPresets?.[quality] || QUALITY_PRESETS[quality];
  if (preset) Object.assign(slicerSettings, preset);
  updateSlicerSettingsUi();
}

function applyFilamentPreset() {
  const filament = dom.filamentSelect.value;
  slicerSettings.filament = filament;
  const preset = FILAMENT_PRESETS[filament];
  if (preset) {
    slicerSettings.temperature = preset.temperature;
    slicerSettings.bedTemperature = preset.bed;
  }
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  if (printer.profile === 'adventurer5m' && filament === 'abs') {
    showSnackbar('Открытый Adventurer 5M не рассчитан на ABS/ASA без корпуса', 5200);
  }
  updateSlicerSettingsUi();
}

function readSlicerSettingsFromUi() {
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  slicerSettings.nozzle = clampNumber(dom.nozzleSelect.value, 0.1, 1.2, printer.defaultNozzle || 0.4);
  slicerSettings.layerHeight = clampNumber(dom.layerHeightInput.value, 0.04, slicerSettings.nozzle * 0.8, 0.2);
  slicerSettings.walls = Math.round(clampNumber(dom.wallsInput.value, 1, 8, 3));
  slicerSettings.infill = clampNumber(dom.infillInput.value, 0, 100, 15);
  slicerSettings.solidLayers = Math.round(clampNumber(dom.solidLayersInput.value, 0, 12, 4));
  slicerSettings.speed = clampNumber(dom.speedInput.value, 5, printer.maxSpeed || 600, 60);
  slicerSettings.travelSpeed = clampNumber(dom.travelSpeedInput.value, 20, printer.maxTravelSpeed || 600, 150);
  slicerSettings.acceleration = Math.round(clampNumber(dom.accelerationInput.value, 200, printer.maxAcceleration || 20000, 3000));
  slicerSettings.temperature = Math.round(clampNumber(dom.temperatureInput.value, 120, printer.maxTemperature || 350, 210));
  slicerSettings.bedTemperature = Math.round(clampNumber(dom.bedTemperatureInput.value, 0, printer.maxBedTemperature || 150, 60));
  slicerSettings.infillPattern = dom.infillPatternSelect.value;
  slicerSettings.supportType = dom.supportTypeSelect.value;
  slicerSettings.supports = slicerSettings.supportType !== 'none';
  slicerSettings.supportPlacement = dom.supportPlacementSelect.value;
  slicerSettings.supportPattern = dom.supportPatternSelect.value;
  slicerSettings.supportDensity = clampNumber(dom.supportDensityInput.value, 5, 80, 15);
  slicerSettings.supportAngle = clampNumber(dom.supportAngleInput.value, 30, 85, 50);
  slicerSettings.supportInterfaceLayers = Math.round(clampNumber(dom.supportInterfaceLayersInput.value, 0, 8, 2));
  slicerSettings.supportZDistance = clampNumber(dom.supportZDistanceInput.value, 0, 1.2, 0.2);
  slicerSettings.supportXYDistance = clampNumber(dom.supportXYDistanceInput.value, 0, 2, 0.35);
  slicerSettings.brim = dom.brimInput.checked;
  slicerSettings.brimWidth = clampNumber(dom.brimWidthInput.value, 0, 30, 5);
  slicerSettings.quality = 'custom';
  dom.qualitySelect.value = 'custom';
  dom.supportAngleValue.textContent = `${Math.round(slicerSettings.supportAngle)}°`;
  dom.supportDensityValue.textContent = `${Math.round(slicerSettings.supportDensity)}%`;
  updateSupportSettingsVisibility();
  updateSlicerStatus();
}

function updateSlicerSettingsUi() {
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  dom.printerSelect.value = slicerSettings.printer;
  populateNozzleOptions(printer);
  dom.qualitySelect.value = slicerSettings.quality;
  dom.filamentSelect.value = slicerSettings.filament;
  dom.nozzleSelect.value = String(slicerSettings.nozzle);
  dom.layerHeightInput.value = slicerSettings.layerHeight.toFixed(2);
  dom.wallsInput.value = slicerSettings.walls;
  dom.infillInput.value = slicerSettings.infill;
  dom.solidLayersInput.value = slicerSettings.solidLayers;
  dom.speedInput.value = slicerSettings.speed;
  dom.travelSpeedInput.value = slicerSettings.travelSpeed;
  dom.accelerationInput.value = slicerSettings.acceleration;
  dom.temperatureInput.value = slicerSettings.temperature;
  dom.bedTemperatureInput.value = slicerSettings.bedTemperature;
  dom.speedInput.max = printer.maxSpeed || 600;
  dom.travelSpeedInput.max = printer.maxTravelSpeed || 600;
  dom.accelerationInput.max = printer.maxAcceleration || 20000;
  dom.temperatureInput.max = printer.maxTemperature || 350;
  dom.bedTemperatureInput.max = printer.maxBedTemperature || 150;
  dom.infillPatternSelect.value = slicerSettings.infillPattern;
  dom.supportTypeSelect.value = slicerSettings.supportType;
  dom.supportPlacementSelect.value = slicerSettings.supportPlacement;
  dom.supportPatternSelect.value = slicerSettings.supportPattern;
  dom.supportDensityInput.value = slicerSettings.supportDensity;
  dom.supportDensityValue.textContent = `${Math.round(slicerSettings.supportDensity)}%`;
  dom.supportAngleInput.value = slicerSettings.supportAngle;
  dom.supportAngleValue.textContent = `${Math.round(slicerSettings.supportAngle)}°`;
  dom.supportInterfaceLayersInput.value = slicerSettings.supportInterfaceLayers;
  dom.supportZDistanceInput.value = slicerSettings.supportZDistance.toFixed(2);
  dom.supportXYDistanceInput.value = slicerSettings.supportXYDistance.toFixed(2);
  dom.brimInput.checked = slicerSettings.brim;
  dom.brimWidthInput.value = slicerSettings.brimWidth;
  dom.printerProfileSummary.innerHTML = `<strong>${printer.width} × ${printer.depth} × ${printer.height} мм</strong><span>Сопло ${slicerSettings.nozzle} мм · до ${printer.maxTemperature || 300}°C · профиль ${printer.profile === 'adventurer5m' ? 'AD5M' : printer.flavor}</span>`;
  updateSupportSettingsVisibility();
  updateSlicerStatus();
}

function updateSupportSettingsVisibility() {
  const enabled = slicerSettings.supportType !== 'none';
  dom.supportOptions.hidden = !enabled;
  const typeLabel = slicerSettings.supportType === 'tree' ? 'Древовидные' : slicerSettings.supportType === 'normal' ? 'Обычные' : 'Выключены';
  dom.supportSummary.textContent = enabled ? `${typeLabel} · ${Math.round(slicerSettings.supportDensity)}%` : typeLabel;
}

function updateSlicerStatus() {
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  dom.slicerStatusLabel.textContent = `${printer.name} · ${slicerSettings.layerHeight.toFixed(2)} мм`;
  if (!slicerModelGroup) {
    dom.slicerStatusValue.textContent = `${printer.width} × ${printer.depth} × ${printer.height} мм`;
    return;
  }
  const box = new THREE.Box3().setFromObject(slicerModelGroup);
  const size = box.getSize(new THREE.Vector3());
  const fits = modelFitsPrinter(box, printer);
  dom.slicerStatusValue.textContent = `${roundSmart(size.x)} × ${roundSmart(size.z)} × ${roundSmart(size.y)} мм · ${fits ? 'помещается' : 'вне стола'}`;
  dom.slicerStatusValue.style.color = fits ? '' : 'var(--danger)';
}

function modelFitsPrinter(box, printer) {
  return box.min.x >= -printer.width / 2 - 0.01
    && box.max.x <= printer.width / 2 + 0.01
    && box.min.z >= -printer.depth / 2 - 0.01
    && box.max.z <= printer.depth / 2 + 0.01
    && box.min.y >= -0.05
    && box.max.y <= printer.height + 0.01;
}

function bindMeasurementPointerEvents() {
  const canvas = renderer.domElement;
  canvas.addEventListener('pointerdown', (event) => {
    measureActivePointers.add(event.pointerId);
    if (appMode !== 'viewer' || !measureMode || !modelRoot || (event.pointerType === 'mouse' && event.button !== 0)) {
      measurePointerStart = null;
      return;
    }
    if (measureActivePointers.size === 1) {
      measurePointerStart = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, time: performance.now() };
    } else measurePointerStart = null;
  }, { passive: true });

  canvas.addEventListener('pointerup', (event) => {
    const wasSingle = measureActivePointers.size === 1;
    measureActivePointers.delete(event.pointerId);
    if (appMode !== 'viewer' || !measureMode || !modelRoot || !measurePointerStart || !wasSingle) {
      measurePointerStart = null;
      return;
    }
    const start = measurePointerStart;
    measurePointerStart = null;
    if (start.pointerId !== event.pointerId) return;
    const movement = Math.hypot(event.clientX - start.x, event.clientY - start.y);
    if (movement <= 11 && performance.now() - start.time <= 650) pickMeasurementPoint(event.clientX, event.clientY);
  }, { passive: true });

  canvas.addEventListener('pointercancel', (event) => {
    measureActivePointers.delete(event.pointerId);
    if (measurePointerStart?.pointerId === event.pointerId) measurePointerStart = null;
  }, { passive: true });
}

function toggleMeasureMode() {
  if (!modelRoot || appMode !== 'viewer') return showSnackbar('Сначала открой модель');
  measureMode = !measureMode;
  setPressed(dom.measureButton, measureMode);
  updateMeasurementUi();
  if (measureMode) showSnackbar(measurementPoints.length ? 'Линейка включена' : 'Коснись двух точек на модели');
}

function pickMeasurementPoint(clientX, clientY) {
  const rect = renderer.domElement.getBoundingClientRect();
  measurePointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
  measurePointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
  measureRaycaster.setFromCamera(measurePointer, camera);
  const hit = measureRaycaster.intersectObject(modelRoot, true)
    .find((intersection) => intersection.object?.visible && (intersection.object.isMesh || intersection.object.isPoints));
  if (!hit) return showSnackbar('Коснись поверхности модели');
  if (measurementPoints.length >= 2) clearMeasurement(false);
  measurementPoints.push(hit.point.clone());
  rebuildMeasurementGraphics();
  updateMeasurementUi();
  if (measurementPoints.length === 1) showSnackbar('Первая точка поставлена');
}

function clearMeasurement(updateUi = true) {
  measurementPoints = [];
  clearGroup(measurementGroup);
  if (updateUi) updateMeasurementUi();
}

function rebuildMeasurementGraphics() {
  clearGroup(measurementGroup);
  if (!measurementPoints.length || !modelBox) return;
  const radius = Math.max(modelBox.getSize(new THREE.Vector3()).length() * 0.008, 0.00001);
  const color = new THREE.Color(cssColor('--primary', '#8d72ff'));
  for (const point of measurementPoints) {
    const marker = new THREE.Mesh(
      new THREE.SphereGeometry(radius, 20, 12),
      new THREE.MeshBasicMaterial({ color, depthTest: false, depthWrite: false })
    );
    marker.position.copy(point);
    marker.renderOrder = 1002;
    measurementGroup.add(marker);
  }
  if (measurementPoints.length === 2) {
    const line = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(measurementPoints),
      new THREE.LineBasicMaterial({ color, depthTest: false, depthWrite: false })
    );
    line.renderOrder = 1001;
    measurementGroup.add(line);
  }
}

function cycleMeasurementUnit() {
  const index = MEASURE_UNIT_OPTIONS.indexOf(measureUnitMode);
  measureUnitMode = MEASURE_UNIT_OPTIONS[(index + 1) % MEASURE_UNIT_OPTIONS.length];
  updateMeasurementUi();
}

function updateMeasurementUi() {
  const unit = measureUnitMode === 'auto' ? modelNativeUnit : measureUnitMode;
  const label = UNIT_LABELS[unit] || 'ед.';
  dom.measureUnitButton.textContent = measureUnitMode === 'auto' ? `Авто · ${label}` : label;
  if (!measurementPoints.length) {
    dom.measureValue.textContent = 'Коснись первой точки';
    dom.measureDetail.textContent = measureMode ? `1 единица = 1 ${label}` : 'Включи линейку';
  } else if (measurementPoints.length === 1) {
    dom.measureValue.textContent = 'Коснись второй точки';
    dom.measureDetail.textContent = 'Первая точка поставлена';
  } else {
    const delta = measurementPoints[1].clone().sub(measurementPoints[0]);
    dom.measureValue.textContent = `${formatMeasurementNumber(delta.length())} ${label}`;
    dom.measureDetail.textContent = `ΔX ${formatMeasurementNumber(Math.abs(delta.x))} · ΔY ${formatMeasurementNumber(Math.abs(delta.y))} · ΔZ ${formatMeasurementNumber(Math.abs(delta.z))} ${label}`;
  }
  dom.measureCard.hidden = !measureMode && measurementPoints.length === 0;
}

function formatMeasurementNumber(value) {
  const number = Math.abs(Number(value) || 0);
  if (number >= 1000) return number.toLocaleString('ru-RU', { maximumFractionDigits: 2 });
  if (number >= 100) return number.toFixed(2);
  if (number >= 10) return number.toFixed(3);
  if (number >= 1) return number.toFixed(4);
  if (number >= 0.001) return number.toFixed(5);
  return number ? number.toExponential(3).replace('.', ',') : '0';
}

async function startSlicing() {
  if (!slicerModelGroup) return showSnackbar('Сначала открой модель');
  readSlicerSettingsFromUi();
  const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
  const box = new THREE.Box3().setFromObject(slicerModelGroup);
  if (!modelFitsPrinter(box, printer)) {
    showSnackbar('Модель выходит за пределы стола. Уменьши или перемести её.', 5200);
    return;
  }
  if (slicerSettings.layerHeight > slicerSettings.nozzle * 0.8) {
    showSnackbar('Высота слоя слишком большая для выбранного сопла');
    return;
  }

  try {
    showOperation('Готовим геометрию', 'Собираем треугольники');
    await nextFrame();
    const triangles = extractWorldTriangles(slicerModelGroup);
    if (!triangles.length) throw new Error('В модели не найдены треугольники');
    if (triangles.length > MAX_SLICE_TRIANGLES) {
      throw new Error(`Слишком сложная модель: ${formatNumber(triangles.length)} треугольников. Лимит мобильной нарезки — ${formatNumber(MAX_SLICE_TRIANGLES)}.`);
    }

    const result = await sliceTriangles(triangles, slicerSettings, printer);
    currentSliceResult = result;
    currentGcode = generateGcode(result, slicerSettings, printer, modelMeta?.name || 'model');
    if (currentGcode.length > MAX_GCODE_CHARS) throw new Error('G-code получился слишком большим для памяти телефона');

    hideOperation();
    showSliceResult(result, currentGcode);
  } catch (error) {
    console.error(error);
    hideOperation();
    showSnackbar(humanizeError(error), 6500);
  }
}

function extractWorldTriangles(root) {
  root.updateMatrixWorld(true);
  const triangles = [];
  const a = new THREE.Vector3();
  const b = new THREE.Vector3();
  const c = new THREE.Vector3();
  root.traverse((object) => {
    if (!object.isMesh || !object.geometry) return;
    const position = object.geometry.getAttribute('position');
    if (!position) return;
    const index = object.geometry.index;
    const count = index ? index.count : position.count;
    for (let i = 0; i + 2 < count; i += 3) {
      const ia = index ? index.getX(i) : i;
      const ib = index ? index.getX(i + 1) : i + 1;
      const ic = index ? index.getX(i + 2) : i + 2;
      a.fromBufferAttribute(position, ia).applyMatrix4(object.matrixWorld);
      b.fromBufferAttribute(position, ib).applyMatrix4(object.matrixWorld);
      c.fromBufferAttribute(position, ic).applyMatrix4(object.matrixWorld);
      if (triangleAreaSquared(a, b, c) < 1e-12) continue;
      triangles.push({
        ax: a.x, ay: a.y, az: a.z,
        bx: b.x, by: b.y, bz: b.z,
        cx: c.x, cy: c.y, cz: c.z,
        minY: Math.min(a.y, b.y, c.y),
        maxY: Math.max(a.y, b.y, c.y)
      });
    }
  });
  return triangles;
}

function triangleAreaSquared(a, b, c) {
  const ab = b.clone().sub(a);
  const ac = c.clone().sub(a);
  return ab.cross(ac).lengthSq();
}

async function sliceTriangles(triangles, settings, printer) {
  const lineWidth = Math.max(settings.nozzle * 1.05, settings.nozzle + 0.02);
  const firstLayerHeight = Math.min(Math.max(settings.layerHeight * 1.15, 0.12), settings.nozzle * 0.75);
  const maxY = triangles.reduce((max, triangle) => Math.max(max, triangle.maxY), 0);
  const layerCount = Math.max(1, Math.ceil(Math.max(0, maxY - firstLayerHeight) / settings.layerHeight) + 1);
  if (layerCount > MAX_LAYERS) throw new Error(`Слишком много слоёв: ${layerCount}. Увеличь высоту слоя.`);

  const layers = Array.from({ length: layerCount }, (_, index) => ({
    index,
    printZ: index === 0 ? firstLayerHeight : firstLayerHeight + index * settings.layerHeight,
    planeY: index === 0 ? firstLayerHeight * 0.5 : firstLayerHeight + (index - 0.5) * settings.layerHeight,
    regions: [],
    paths: []
  }));

  const starts = Array.from({ length: layerCount }, () => []);
  const ends = Array.from({ length: layerCount + 1 }, () => []);
  for (let triangleIndex = 0; triangleIndex < triangles.length; triangleIndex += 1) {
    const triangle = triangles[triangleIndex];
    let start = layerIndexAtOrAbove(triangle.minY, firstLayerHeight, settings.layerHeight);
    let end = layerIndexAtOrBelow(triangle.maxY, firstLayerHeight, settings.layerHeight);
    start = Math.max(0, Math.min(layerCount - 1, start));
    end = Math.max(-1, Math.min(layerCount - 1, end));
    if (end < start) continue;
    starts[start].push(triangleIndex);
    ends[end + 1].push(triangleIndex);
  }

  const active = new Set();
  const epsilon = Math.max(0.015, lineWidth * 0.1);
  for (let layerIndex = 0; layerIndex < layerCount; layerIndex += 1) {
    for (const index of ends[layerIndex]) active.delete(index);
    for (const index of starts[layerIndex]) active.add(index);
    const plane = layers[layerIndex].planeY;
    const segments = [];
    for (const triangleIndex of active) {
      const segment = intersectTriangleAtY(triangles[triangleIndex], plane, epsilon);
      if (segment) segments.push(segment);
    }
    const loops = stitchSegments(segments, epsilon * 2.2);
    const rawPaths = loops.map(loopToClipperPath).filter((path) => Math.abs(ClipperLib.Clipper.Area(path)) > lineWidth * lineWidth * CLIPPER_SCALE * CLIPPER_SCALE);
    layers[layerIndex].regions = unionPaths(rawPaths);
    updateOperation('Нарезаем модель', `Слой ${layerIndex + 1} из ${layerCount}`);
    if (layerIndex % 5 === 0) await nextFrame();
  }

  let supportRegions = Array.from({ length: layerCount }, () => []);
  if (settings.supports && layerCount > 1) {
    updateOperation('Строим поддержки', 'Ищем нависания');
    supportRegions = computeSupportRegions(layers.map((layer) => layer.regions), settings, lineWidth);
    await nextFrame();
  }

  let totalPathLength = 0;
  let warningCount = 0;
  for (let layerIndex = 0; layerIndex < layerCount; layerIndex += 1) {
    const layer = layers[layerIndex];
    const regions = layer.regions;
    if (!regions.length) {
      warningCount += 1;
      continue;
    }

    if (layerIndex === 0 && settings.brim && settings.brimWidth > 0) {
      const brimLoops = Math.max(1, Math.ceil(settings.brimWidth / lineWidth));
      for (let loop = brimLoops; loop >= 1; loop -= 1) {
        const paths = offsetPaths(regions, lineWidth * (loop + 0.5));
        addClosedPaths(layer.paths, paths, 'brim');
      }
    }

    for (let wall = 0; wall < settings.walls; wall += 1) {
      const wallPaths = offsetPaths(regions, -lineWidth * (wall + 0.5));
      addClosedPaths(layer.paths, wallPaths, 'wall');
    }

    const inner = offsetPaths(regions, -lineWidth * (settings.walls + 0.25));
    const isSolid = layerIndex < settings.solidLayers || layerIndex >= layerCount - settings.solidLayers;
    const density = isSolid ? 100 : settings.infill;
    if (density > 0 && inner.length) {
      const spacing = isSolid ? lineWidth * 0.92 : Math.max(lineWidth * 1.15, lineWidth / (density / 100));
      const angle = settings.infillPattern === 'grid'
        ? (layerIndex % 2 === 0 ? 45 : -45)
        : (layerIndex % 2 === 0 ? 0 : 90);
      const infillPaths = clippedHatch(inner, spacing, angle);
      addOpenPaths(layer.paths, infillPaths, 'infill');
    }

    if (settings.supports && supportRegions[layerIndex]?.length) {
      const treeMode = settings.supportType === 'tree';
      const erosion = treeMode ? lineWidth * 0.95 : lineWidth * 0.25;
      const supportInner = offsetPaths(supportRegions[layerIndex], -erosion);
      const interfaceLayer = isSupportInterfaceLayer(supportRegions, layerIndex, settings.supportInterfaceLayers);
      const density = interfaceLayer ? Math.max(65, settings.supportDensity) : settings.supportDensity;
      const effectiveDensity = treeMode && !interfaceLayer ? Math.max(5, density * 0.65) : density;
      const spacing = Math.max(lineWidth * 1.15, lineWidth / (effectiveDensity / 100));
      const angles = supportAngles(settings.supportPattern, layerIndex, treeMode);
      for (const angle of angles) {
        const supportPaths = clippedHatch(supportInner, spacing, angle);
        addOpenPaths(layer.paths, supportPaths, interfaceLayer ? 'supportInterface' : 'support');
      }
    }

    for (const path of layer.paths) totalPathLength += polylineLength(path.points);
    updateOperation('Строим траектории', `Слой ${layerIndex + 1} из ${layerCount}`);
    if (layerIndex % 6 === 0) await nextFrame();
  }

  const volumeMm3 = meshVolume(triangles);
  return {
    layers,
    lineWidth,
    firstLayerHeight,
    layerHeight: settings.layerHeight,
    triangleCount: triangles.length,
    totalPathLength,
    volumeMm3,
    warningCount,
    bounds: boundsFromTriangles(triangles),
    printer
  };
}

function layerIndexAtOrAbove(y, firstHeight, layerHeight) {
  const firstPlane = firstHeight * 0.5;
  if (y <= firstPlane) return 0;
  return Math.max(1, Math.ceil((y - firstHeight) / layerHeight + 0.5));
}

function layerIndexAtOrBelow(y, firstHeight, layerHeight) {
  const firstPlane = firstHeight * 0.5;
  if (y < firstPlane) return -1;
  if (y < firstHeight + 0.5 * layerHeight) return 0;
  return Math.floor((y - firstHeight) / layerHeight + 0.5);
}

function intersectTriangleAtY(triangle, plane, epsilon) {
  const vertices = [
    { x: triangle.ax, y: triangle.ay, z: triangle.az },
    { x: triangle.bx, y: triangle.by, z: triangle.bz },
    { x: triangle.cx, y: triangle.cy, z: triangle.cz }
  ];
  const points = [];
  for (let edge = 0; edge < 3; edge += 1) {
    const p1 = vertices[edge];
    const p2 = vertices[(edge + 1) % 3];
    const d1 = p1.y - plane;
    const d2 = p2.y - plane;
    if (Math.abs(d1) <= epsilon && Math.abs(d2) <= epsilon) continue;
    if ((d1 <= epsilon && d2 >= -epsilon) || (d2 <= epsilon && d1 >= -epsilon)) {
      const denominator = p2.y - p1.y;
      if (Math.abs(denominator) < epsilon) continue;
      const t = (plane - p1.y) / denominator;
      if (t < -epsilon || t > 1 + epsilon) continue;
      const point = { x: p1.x + (p2.x - p1.x) * t, z: p1.z + (p2.z - p1.z) * t };
      if (!points.some((other) => Math.hypot(other.x - point.x, other.z - point.z) <= epsilon)) points.push(point);
    }
  }
  if (points.length !== 2) return null;
  if (Math.hypot(points[0].x - points[1].x, points[0].z - points[1].z) <= epsilon) return null;
  return [points[0], points[1]];
}

function stitchSegments(segments, epsilon) {
  const keyOf = (point) => `${Math.round(point.x / epsilon)},${Math.round(point.z / epsilon)}`;
  const adjacency = new Map();
  const used = new Uint8Array(segments.length);
  segments.forEach((segment, index) => {
    segment.forEach((point, endpoint) => {
      const key = keyOf(point);
      if (!adjacency.has(key)) adjacency.set(key, []);
      adjacency.get(key).push({ index, endpoint });
    });
  });

  const loops = [];
  for (let startIndex = 0; startIndex < segments.length; startIndex += 1) {
    if (used[startIndex]) continue;
    used[startIndex] = 1;
    const startSegment = segments[startIndex];
    const path = [startSegment[0], startSegment[1]];
    let current = startSegment[1];
    let guard = 0;
    while (guard++ < segments.length + 4) {
      if (Math.hypot(current.x - path[0].x, current.z - path[0].z) <= epsilon && path.length >= 4) break;
      const options = adjacency.get(keyOf(current)) || [];
      let next = null;
      for (const option of options) {
        if (!used[option.index]) {
          next = option;
          break;
        }
      }
      if (!next) break;
      used[next.index] = 1;
      const segment = segments[next.index];
      const point = segment[next.endpoint === 0 ? 1 : 0];
      path.push(point);
      current = point;
    }
    if (path.length >= 4 && Math.hypot(path.at(-1).x - path[0].x, path.at(-1).z - path[0].z) <= epsilon * 1.5) {
      path[path.length - 1] = { ...path[0] };
      loops.push(simplifyLoop(path, epsilon * 0.35));
    }
  }
  return loops.filter((loop) => loop.length >= 4);
}

function simplifyLoop(points, epsilon) {
  const output = [];
  for (const point of points) {
    const previous = output.at(-1);
    if (!previous || Math.hypot(point.x - previous.x, point.z - previous.z) > epsilon) output.push(point);
  }
  if (output.length > 2 && Math.hypot(output[0].x - output.at(-1).x, output[0].z - output.at(-1).z) > epsilon) output.push({ ...output[0] });
  return output;
}

function loopToClipperPath(loop) {
  const source = loop.length > 1 && samePoint2(loop[0], loop.at(-1), 1e-6) ? loop.slice(0, -1) : loop;
  return source.map((point) => ({ X: Math.round(point.x * CLIPPER_SCALE), Y: Math.round(point.z * CLIPPER_SCALE) }));
}

function samePoint2(a, b, epsilon) {
  return Math.abs(a.x - b.x) <= epsilon && Math.abs(a.z - b.z) <= epsilon;
}

function unionPaths(paths) {
  if (!paths?.length) return [];
  const clipper = new ClipperLib.Clipper();
  clipper.StrictlySimple = true;
  clipper.AddPaths(paths, ClipperLib.PolyType.ptSubject, true);
  const solution = new ClipperLib.Paths();
  clipper.Execute(
    ClipperLib.ClipType.ctUnion,
    solution,
    ClipperLib.PolyFillType.pftEvenOdd,
    ClipperLib.PolyFillType.pftEvenOdd
  );
  return cleanClipperPaths(solution);
}

function offsetPaths(paths, deltaMm) {
  if (!paths?.length) return [];
  if (Math.abs(deltaMm) < 1e-6) return paths.map((path) => path.map((point) => ({ ...point })));
  const offset = new ClipperLib.ClipperOffset(2, 0.15 * CLIPPER_SCALE);
  offset.AddPaths(paths, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon);
  const solution = new ClipperLib.Paths();
  offset.Execute(solution, deltaMm * CLIPPER_SCALE);
  return cleanClipperPaths(solution);
}

function differencePaths(subject, clip) {
  if (!subject?.length) return [];
  if (!clip?.length) return subject.map((path) => path.map((point) => ({ ...point })));
  const clipper = new ClipperLib.Clipper();
  clipper.AddPaths(subject, ClipperLib.PolyType.ptSubject, true);
  clipper.AddPaths(clip, ClipperLib.PolyType.ptClip, true);
  const solution = new ClipperLib.Paths();
  clipper.Execute(
    ClipperLib.ClipType.ctDifference,
    solution,
    ClipperLib.PolyFillType.pftEvenOdd,
    ClipperLib.PolyFillType.pftEvenOdd
  );
  return cleanClipperPaths(solution);
}

function intersectionPaths(subject, clip) {
  if (!subject?.length || !clip?.length) return [];
  const clipper = new ClipperLib.Clipper();
  clipper.AddPaths(subject, ClipperLib.PolyType.ptSubject, true);
  clipper.AddPaths(clip, ClipperLib.PolyType.ptClip, true);
  const solution = new ClipperLib.Paths();
  clipper.Execute(
    ClipperLib.ClipType.ctIntersection,
    solution,
    ClipperLib.PolyFillType.pftEvenOdd,
    ClipperLib.PolyFillType.pftEvenOdd
  );
  return cleanClipperPaths(solution);
}

function cleanClipperPaths(paths) {
  const cleaned = ClipperLib.Clipper.CleanPolygons(paths, 0.02 * CLIPPER_SCALE);
  return cleaned.filter((path) => path.length >= 3 && Math.abs(ClipperLib.Clipper.Area(path)) > 0.02 * CLIPPER_SCALE * CLIPPER_SCALE);
}

function clippedHatch(polygons, spacingMm, angleDegrees) {
  if (!polygons?.length || spacingMm <= 0) return [];
  const bounds = clipperBounds(polygons);
  if (!bounds) return [];
  const centerX = (bounds.left + bounds.right) / 2;
  const centerY = (bounds.top + bounds.bottom) / 2;
  const diagonal = Math.hypot(bounds.right - bounds.left, bounds.bottom - bounds.top) + spacingMm * 4;
  const angle = THREE.MathUtils.degToRad(angleDegrees);
  const dir = { x: Math.cos(angle), y: Math.sin(angle) };
  const normal = { x: -dir.y, y: dir.x };
  const lines = [];
  for (let offset = -diagonal; offset <= diagonal; offset += spacingMm) {
    const cx = centerX + normal.x * offset;
    const cy = centerY + normal.y * offset;
    const p1 = {
      X: Math.round((cx - dir.x * diagonal) * CLIPPER_SCALE),
      Y: Math.round((cy - dir.y * diagonal) * CLIPPER_SCALE)
    };
    const p2 = {
      X: Math.round((cx + dir.x * diagonal) * CLIPPER_SCALE),
      Y: Math.round((cy + dir.y * diagonal) * CLIPPER_SCALE)
    };
    lines.push([p1, p2]);
  }

  const clipper = new ClipperLib.Clipper();
  clipper.AddPaths(lines, ClipperLib.PolyType.ptSubject, false);
  clipper.AddPaths(polygons, ClipperLib.PolyType.ptClip, true);
  const tree = new ClipperLib.PolyTree();
  clipper.Execute(
    ClipperLib.ClipType.ctIntersection,
    tree,
    ClipperLib.PolyFillType.pftNonZero,
    ClipperLib.PolyFillType.pftEvenOdd
  );
  return ClipperLib.Clipper.OpenPathsFromPolyTree(tree)
    .filter((path) => path.length >= 2)
    .map((path) => path.map((point) => ({ x: point.X / CLIPPER_SCALE, z: point.Y / CLIPPER_SCALE })));
}

function clipperBounds(paths) {
  if (!paths?.length) return null;
  let left = Infinity;
  let right = -Infinity;
  let top = Infinity;
  let bottom = -Infinity;
  for (const path of paths) for (const point of path) {
    const x = point.X / CLIPPER_SCALE;
    const y = point.Y / CLIPPER_SCALE;
    left = Math.min(left, x);
    right = Math.max(right, x);
    top = Math.min(top, y);
    bottom = Math.max(bottom, y);
  }
  return Number.isFinite(left) ? { left, right, top, bottom } : null;
}

function addClosedPaths(destination, paths, type) {
  for (const path of paths || []) {
    if (path.length < 3) continue;
    const points = path.map((point) => ({ x: point.X / CLIPPER_SCALE, z: point.Y / CLIPPER_SCALE }));
    points.push({ ...points[0] });
    destination.push({ type, points });
  }
}

function addOpenPaths(destination, paths, type) {
  for (const points of paths || []) if (points.length >= 2) destination.push({ type, points });
}

function computeSupportRegions(regions, settings, lineWidth) {
  const count = regions.length;
  const unsupported = Array.from({ length: count }, () => []);
  const fullResult = Array.from({ length: count }, () => []);
  const angleFromVertical = Math.max(5, Math.min(80, settings.supportAngle));
  const allowance = Math.max(lineWidth * 0.45, Math.tan(THREE.MathUtils.degToRad(90 - angleFromVertical)) * settings.layerHeight);

  for (let layer = 1; layer < count; layer += 1) {
    if (!regions[layer]?.length) continue;
    const supported = offsetPaths(regions[layer - 1], allowance);
    unsupported[layer] = differencePaths(regions[layer], supported);
  }

  let carry = [];
  for (let layer = count - 1; layer >= 1; layer -= 1) {
    carry = unionPaths([...(carry || []), ...(unsupported[layer] || [])]);
    carry = differencePaths(carry, regions[layer - 1] || []);
    carry = offsetPaths(carry, settings.supportType === 'tree' ? lineWidth * 0.20 : lineWidth * 0.10);
    fullResult[layer - 1] = carry;
  }

  let result = fullResult;
  if (settings.supportPlacement === 'buildPlate' && fullResult[0]?.length) {
    result = Array.from({ length: count }, () => []);
    let connected = offsetPaths(fullResult[0], lineWidth * 0.4);
    result[0] = intersectionPaths(fullResult[0], connected);
    for (let layer = 1; layer < count; layer += 1) {
      const expansion = settings.supportType === 'tree' ? lineWidth * 0.48 : lineWidth * 0.18;
      connected = offsetPaths(connected, expansion);
      result[layer] = intersectionPaths(fullResult[layer], connected);
    }
  }

  const xyGap = Math.max(0, settings.supportXYDistance || 0);
  const gapLayers = Math.max(0, Math.ceil((settings.supportZDistance || 0) / settings.layerHeight));
  for (let layer = 0; layer < count; layer += 1) {
    if (!result[layer]?.length) continue;
    if (xyGap > 0 && regions[layer]?.length) result[layer] = differencePaths(result[layer], offsetPaths(regions[layer], xyGap));
    if (gapLayers > 0) {
      const nearModel = [];
      for (let offset = 1; offset <= gapLayers && layer + offset < count; offset += 1) nearModel.push(...(regions[layer + offset] || []));
      if (nearModel.length) result[layer] = differencePaths(result[layer], offsetPaths(unionPaths(nearModel), Math.max(0, xyGap * 0.35)));
    }
  }
  return result;
}

function isSupportInterfaceLayer(supportRegions, layerIndex, interfaceLayers) {
  if (!interfaceLayers || !supportRegions[layerIndex]?.length) return false;
  const lookAhead = Math.max(1, interfaceLayers);
  for (let offset = 1; offset <= lookAhead; offset += 1) {
    const above = supportRegions[layerIndex + offset];
    if (!above?.length) return true;
  }
  return false;
}

function supportAngles(pattern, layerIndex, treeMode) {
  if (treeMode) return [layerIndex % 2 ? 60 : -60];
  if (pattern === 'grid') return layerIndex % 2 ? [0, 90] : [45, -45];
  if (pattern === 'lines') return [layerIndex % 2 ? 0 : 90];
  return [layerIndex % 2 ? 45 : -45];
}

function polylineLength(points) {
  let length = 0;
  for (let i = 1; i < points.length; i += 1) length += Math.hypot(points[i].x - points[i - 1].x, points[i].z - points[i - 1].z);
  return length;
}

function meshVolume(triangles) {
  let volume = 0;
  for (const triangle of triangles) {
    volume += (
      triangle.ax * (triangle.by * triangle.cz - triangle.bz * triangle.cy)
      - triangle.ay * (triangle.bx * triangle.cz - triangle.bz * triangle.cx)
      + triangle.az * (triangle.bx * triangle.cy - triangle.by * triangle.cx)
    ) / 6;
  }
  return Math.abs(volume);
}

function boundsFromTriangles(triangles) {
  const bounds = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity, minZ: Infinity, maxZ: -Infinity };
  for (const triangle of triangles) {
    for (const [x, y, z] of [
      [triangle.ax, triangle.ay, triangle.az],
      [triangle.bx, triangle.by, triangle.bz],
      [triangle.cx, triangle.cy, triangle.cz]
    ]) {
      bounds.minX = Math.min(bounds.minX, x);
      bounds.maxX = Math.max(bounds.maxX, x);
      bounds.minY = Math.min(bounds.minY, y);
      bounds.maxY = Math.max(bounds.maxY, y);
      bounds.minZ = Math.min(bounds.minZ, z);
      bounds.maxZ = Math.max(bounds.maxZ, z);
    }
  }
  return bounds;
}

function printerStartGcode(printer, settings, firstLayerHeight) {
  const accel = Math.round(Math.min(settings.acceleration || 3000, printer.maxAcceleration || 20000));
  const common = [
    'G90 ; absolute positioning',
    'M82 ; absolute extrusion',
    `M204 S${accel}`,
    `M140 S${settings.bedTemperature}`,
    `M104 S${settings.temperature}`,
    'G28',
    `M190 S${settings.bedTemperature}`,
    `M109 S${settings.temperature}`,
    'G92 E0'
  ];
  if (printer.profile === 'adventurer5m') {
    return [
      '; Flashforge Adventurer 5M preset',
      ...common,
      'G1 Z5 F3000',
      'G1 X10 Y10 F12000',
      `G1 Z${Math.max(firstLayerHeight, 0.22).toFixed(3)} F1200`,
      'G1 X10 Y200 E15 F1200',
      'G1 X10.5 Y200 F9000',
      'G1 X10.5 Y10 E30 F1200',
      'G92 E0'
    ];
  }
  return [...common, 'G1 Z2.000 F3000', 'G1 X5 Y5 F6000', `G1 Z${Math.max(firstLayerHeight, 0.20).toFixed(3)} F1200`];
}

function printerEndGcode(printer, settings) {
  const travelFeed = Math.round((settings.travelSpeed || 150) * 60);
  if (printer.profile === 'adventurer5m') {
    return ['M400', 'G92 E0', 'G1 E-1.5 F1800', 'G91', 'G1 Z10 F1200', 'G90', 'M104 S0', 'M140 S0', 'M107', `G1 X110 Y210 F${travelFeed}`, 'M84'];
  }
  return ['G92 E0', 'G1 E-1.5 F1800', 'G91', 'G1 Z10 F1200', 'G90', 'M104 S0', 'M140 S0', 'M107', `G1 X5 Y5 F${travelFeed}`, 'M84'];
}

function generateGcode(result, settings, printer, modelName) {
  const lineWidth = result.lineWidth;
  const filamentDiameter = 1.75;
  const filamentArea = Math.PI * Math.pow(filamentDiameter / 2, 2);
  const flowMultiplier = 0.96;
  const offsetX = printer.width / 2;
  const offsetY = printer.depth / 2;
  const safeName = String(modelName).replace(/[\r\n;]/g, '_');
  const lines = [
    '; Model Lab 3D mobile slicer',
    `; Model: ${safeName}`,
    `; Printer: ${printer.name}`,
    `; Layer height: ${settings.layerHeight.toFixed(3)} mm`,
    `; Nozzle: ${settings.nozzle.toFixed(2)} mm`,
    `; Infill: ${settings.infill}%`,
    `; Supports: ${settings.supportType || 'none'} / ${settings.supportPlacement || 'buildPlate'}`,
    '; IMPORTANT: verify the first layer and printer-specific macros',
    ...printerStartGcode(printer, settings, result.firstLayerHeight)
  ];

  let e = 0;
  let last = { x: 5, y: 5 };
  let retracted = false;
  let totalPrintSeconds = 0;
  let totalTravelSeconds = 0;
  let totalExtrusionLength = 0;

  for (const layer of result.layers) {
    lines.push(`;LAYER:${layer.index}`, `;Z:${layer.printZ.toFixed(3)}`);
    const layerHeight = layer.index === 0 ? result.firstLayerHeight : result.layerHeight;
    const orderedPaths = orderPathsNearest(layer.paths, last, offsetX, offsetY);
    for (const path of orderedPaths) {
      if (path.points.length < 2) continue;
      const start = toGcodePoint(path.points[0], offsetX, offsetY);
      const travelDistance = Math.hypot(start.x - last.x, start.y - last.y);
      if (travelDistance > 2 && !retracted) {
        e -= 0.8;
        lines.push(`G1 E${e.toFixed(5)} F1800`);
        retracted = true;
      }
      lines.push(`G0 X${start.x.toFixed(3)} Y${start.y.toFixed(3)} Z${layer.printZ.toFixed(3)} F${Math.round((settings.travelSpeed || 150) * 60)}`);
      totalTravelSeconds += travelDistance / Math.max(20, settings.travelSpeed || 150);
      if (retracted) {
        e += 0.8;
        lines.push(`G1 E${e.toFixed(5)} F1800`);
        retracted = false;
      }

      const speed = pathSpeed(path.type, layer.index, settings);
      for (let pointIndex = 1; pointIndex < path.points.length; pointIndex += 1) {
        const previous = toGcodePoint(path.points[pointIndex - 1], offsetX, offsetY);
        const point = toGcodePoint(path.points[pointIndex], offsetX, offsetY);
        const length = Math.hypot(point.x - previous.x, point.y - previous.y);
        if (length < 0.001) continue;
        const extrusion = length * layerHeight * lineWidth / filamentArea * flowMultiplier;
        e += extrusion;
        totalExtrusionLength += extrusion;
        totalPrintSeconds += length / speed;
        lines.push(`G1 X${point.x.toFixed(3)} Y${point.y.toFixed(3)} E${e.toFixed(5)} F${Math.round(speed * 60)}`);
        last = point;
      }
    }
  }

  lines.push(';END', ...printerEndGcode(printer, settings));

  result.filamentLengthMm = totalExtrusionLength;
  result.timeSeconds = (totalPrintSeconds + totalTravelSeconds) * 1.16 + result.layers.length * 1.2;
  result.gcodeLineCount = lines.length;
  return `${lines.join('\n')}\n`;
}

function orderPathsNearest(paths, lastGcodePoint, offsetX, offsetY) {
  const remaining = [...paths];
  const ordered = [];
  let current = { ...lastGcodePoint };
  while (remaining.length) {
    let bestIndex = 0;
    let bestReverse = false;
    let bestDistance = Infinity;
    for (let index = 0; index < remaining.length; index += 1) {
      const path = remaining[index];
      if (!path.points.length) continue;
      const start = toGcodePoint(path.points[0], offsetX, offsetY);
      const end = toGcodePoint(path.points.at(-1), offsetX, offsetY);
      const startDistance = Math.hypot(start.x - current.x, start.y - current.y);
      const endDistance = Math.hypot(end.x - current.x, end.y - current.y);
      if (startDistance < bestDistance) {
        bestDistance = startDistance;
        bestIndex = index;
        bestReverse = false;
      }
      if (path.type !== 'wall' && path.type !== 'brim' && endDistance < bestDistance) {
        bestDistance = endDistance;
        bestIndex = index;
        bestReverse = true;
      }
    }
    const selected = remaining.splice(bestIndex, 1)[0];
    if (bestReverse) selected.points = [...selected.points].reverse();
    ordered.push(selected);
    const end = toGcodePoint(selected.points.at(-1), offsetX, offsetY);
    current = end;
  }
  return ordered;
}

function toGcodePoint(point, offsetX, offsetY) {
  return { x: point.x + offsetX, y: point.z + offsetY };
}

function pathSpeed(type, layerIndex, settings) {
  if (layerIndex === 0) return Math.min(25, settings.speed * 0.45);
  if (type === 'wall') return settings.speed * 0.72;
  if (type === 'supportInterface') return Math.min(settings.speed * 0.58, 45);
  if (type === 'support') return Math.min(settings.speed * 0.75, settings.supportType === 'tree' ? 70 : 60);
  if (type === 'brim') return Math.min(settings.speed * 0.55, 35);
  return settings.speed;
}

function showSliceResult(result, gcode) {
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
  dom.layerSlider.min = 0;
  dom.layerSlider.max = Math.max(0, result.layers.length - 1);
  dom.layerSlider.value = Math.max(0, result.layers.length - 1);
  dom.layerPreviewCard.hidden = false;
  prepareSlicerMaterialForPreview(false);
  showSliceLayer(Number(dom.layerSlider.value));
  openSheet(dom.sliceResultSheet);
}

function showSliceLayer(layerIndex) {
  clearSlicePreview();
  const layer = currentSliceResult?.layers?.[layerIndex];
  if (!layer) return;
  const colorMap = {
    wall: cssColor('--primary', '#8d72ff'),
    infill: cssColor('--secondary', '#94d5ff'),
    support: cssColor('--success', '#77dbb2'),
    supportInterface: cssColor('--support-interface', '#b8f1d6'),
    brim: cssColor('--warning', '#ffca75')
  };
  const grouped = new Map();
  for (const path of layer.paths) {
    if (!grouped.has(path.type)) grouped.set(path.type, []);
    const vertices = grouped.get(path.type);
    for (let i = 1; i < path.points.length; i += 1) {
      const a = path.points[i - 1];
      const b = path.points[i];
      vertices.push(a.x, layer.printZ + 0.08, a.z, b.x, layer.printZ + 0.08, b.z);
    }
  }
  for (const [type, vertices] of grouped) {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));
    const material = new THREE.LineBasicMaterial({ color: new THREE.Color(colorMap[type]), transparent: true, opacity: 0.95, depthTest: false });
    const lines = new THREE.LineSegments(geometry, material);
    lines.renderOrder = 950;
    slicePreviewGroup.add(lines);
  }
  dom.layerPreviewValue.textContent = `${layerIndex + 1} / ${currentSliceResult.layers.length} · ${layer.printZ.toFixed(2)} мм`;
}

function clearSlicePreview() {
  clearGroup(slicePreviewGroup);
}

async function exportGcode() {
  if (!currentGcode) return showSnackbar('Сначала нарежь модель');
  const filename = `${stripExtension(modelMeta?.name || 'model')}.gcode`;
  await saveTextFile(filename, 'text/x-gcode', currentGcode);
}

async function exportPreparedStl() {
  if (!slicerModelGroup) return showSnackbar('Сначала открой модель');
  try {
    showOperation('Готовим STL', 'Преобразуем геометрию');
    await nextFrame();
    const triangles = extractWorldTriangles(slicerModelGroup);
    const printer = PRINTERS[slicerSettings.printer] || PRINTERS.generic220;
    const offsetX = printer.width / 2;
    const offsetY = printer.depth / 2;
    const chunks = [`solid ${stripExtension(modelMeta?.name || 'model')}\n`];
    for (let index = 0; index < triangles.length; index += 1) {
      const triangle = triangles[index];
      const a = new THREE.Vector3(triangle.ax, triangle.ay, triangle.az);
      const b = new THREE.Vector3(triangle.bx, triangle.by, triangle.bz);
      const c = new THREE.Vector3(triangle.cx, triangle.cy, triangle.cz);
      const normal = b.clone().sub(a).cross(c.clone().sub(a)).normalize();
      chunks.push(
        `facet normal ${normal.x.toFixed(7)} ${normal.z.toFixed(7)} ${normal.y.toFixed(7)}\n`,
        ' outer loop\n',
        `  vertex ${(a.x + offsetX).toFixed(6)} ${(a.z + offsetY).toFixed(6)} ${a.y.toFixed(6)}\n`,
        `  vertex ${(b.x + offsetX).toFixed(6)} ${(b.z + offsetY).toFixed(6)} ${b.y.toFixed(6)}\n`,
        `  vertex ${(c.x + offsetX).toFixed(6)} ${(c.z + offsetY).toFixed(6)} ${c.y.toFixed(6)}\n`,
        ' endloop\nendfacet\n'
      );
      if (index % 12000 === 0) {
        updateOperation('Готовим STL', `${formatNumber(index)} из ${formatNumber(triangles.length)}`);
        await nextFrame();
      }
    }
    chunks.push(`endsolid ${stripExtension(modelMeta?.name || 'model')}\n`);
    const stl = chunks.join('');
    hideOperation();
    await saveTextFile(`${stripExtension(modelMeta?.name || 'model')}_prepared.stl`, 'model/stl', stl);
  } catch (error) {
    hideOperation();
    showSnackbar(humanizeError(error), 5200);
  }
}

async function saveTextFile(filename, mimeType, text) {
  try {
    if (window.AndroidFiles?.beginSave && window.AndroidFiles?.appendSave && window.AndroidFiles?.finishSave) {
      window.AndroidFiles.beginSave(filename, mimeType);
      const chunkSize = 180000;
      for (let offset = 0; offset < text.length; offset += chunkSize) {
        window.AndroidFiles.appendSave(text.slice(offset, offset + chunkSize));
        if (offset % (chunkSize * 8) === 0) await nextFrame();
      }
      window.AndroidFiles.finishSave();
      showSnackbar('Выбери папку для сохранения');
      return;
    }
    const blob = new Blob([text], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  } catch (error) {
    console.error(error);
    showSnackbar('Не удалось сохранить файл');
  }
}

function showOperation(title, detail = '') {
  loadingDepth += 1;
  dom.bootTitle.textContent = title;
  dom.bootDetail.textContent = detail;
  dom.bootOverlay.classList.remove('is-hidden');
}

function updateOperation(title, detail = '') {
  dom.bootTitle.textContent = title;
  dom.bootDetail.textContent = detail;
}

function hideOperation() {
  loadingDepth = Math.max(0, loadingDepth - 1);
  if (!loadingDepth) dom.bootOverlay.classList.add('is-hidden');
}

function showSnackbar(message, duration = 3400) {
  clearTimeout(snackbarTimer);
  dom.snackbar.textContent = message;
  dom.snackbar.hidden = false;
  snackbarTimer = setTimeout(() => { dom.snackbar.hidden = true; }, duration);
}

function openSheet(sheet) {
  closeSheets();
  currentSheet = sheet;
  dom.scrim.hidden = false;
  sheet.hidden = false;
}

function closeSheets() {
  if (currentSheet) currentSheet.hidden = true;
  currentSheet = null;
  dom.scrim.hidden = true;
}

function setPressed(button, pressed) {
  button.setAttribute('aria-pressed', String(Boolean(pressed)));
}

function applyInitialTheme() {
  const stored = localStorage.getItem('model-lab-theme');
  const dark = stored ? stored === 'dark' : true;
  document.body.dataset.theme = dark ? 'dark' : 'light';
  updateSceneTheme();
  syncSystemBars();
}

function toggleTheme() {
  document.body.dataset.theme = isDarkTheme() ? 'light' : 'dark';
  localStorage.setItem('model-lab-theme', document.body.dataset.theme);
  updateSceneTheme();
  syncSystemBars();
}

function isDarkTheme() {
  return document.body.dataset.theme !== 'light';
}

function syncSystemBars() {
  try { window.AndroidUi?.setDarkMode?.(isDarkTheme()); } catch (_) { /* optional bridge */ }
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', isDarkTheme() ? '#0f0f12' : '#f7f5fb');
}

function updateSceneTheme() {
  if (!renderer) return;
  renderer.setClearColor(new THREE.Color(cssColor('--canvas', isDarkTheme() ? '#111116' : '#f2eff7')), 1);
  if (grid) {
    grid.material.color?.set?.(isDarkTheme() ? 0x77717f : 0x8e8695);
    grid.material.opacity = isDarkTheme() ? 0.30 : 0.24;
  }
  if (buildPlateGroup) updateBuildPlate();
  if (measurementPoints.length) rebuildMeasurementGraphics();
  if (currentSliceResult && !dom.layerPreviewCard.hidden) showSliceLayer(Number(dom.layerSlider.value));
}

function resizeRenderer() {
  if (!renderer || !camera) return;
  const width = Math.max(dom.canvasArea.clientWidth, 1);
  const height = Math.max(dom.canvasArea.clientHeight, 1);
  renderer.setSize(width, height, false);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
}

function animate() {
  requestAnimationFrame(animate);
  const delta = Math.min(clock.getDelta(), 0.05);
  modelMixer?.update(delta);
  controls.update();
  renderer.render(scene, camera);
}

function clearGroup(group) {
  if (!group) return;
  while (group.children.length) {
    const object = group.children.pop();
    object.geometry?.dispose?.();
    const materials = Array.isArray(object.material) ? object.material : [object.material];
    materials.forEach((material) => material?.dispose?.());
  }
}

function nextFrame() {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

function cssColor(variable, fallback) {
  return getComputedStyle(document.documentElement).getPropertyValue(variable).trim() || fallback;
}

function normalizePath(value) {
  return String(value || '').replace(/\\/g, '/').replace(/^\.\//, '').replace(/\/+/g, '/');
}

function basename(value) {
  const parts = normalizePath(value).split('/');
  return parts.at(-1) || '';
}

function extensionOf(name) {
  const base = basename(name);
  const dot = base.lastIndexOf('.');
  return dot >= 0 ? base.slice(dot + 1).toLowerCase() : '';
}

function stripExtension(name) {
  const base = basename(name);
  const dot = base.lastIndexOf('.');
  return dot > 0 ? base.slice(0, dot) : base;
}

function formatBytes(value) {
  const bytes = Number(value) || 0;
  if (bytes < 1024) return `${bytes} Б`;
  const units = ['КБ', 'МБ', 'ГБ'];
  let amount = bytes;
  let index = -1;
  do {
    amount /= 1024;
    index += 1;
  } while (amount >= 1024 && index < units.length - 1);
  return `${amount.toFixed(amount >= 100 ? 0 : amount >= 10 ? 1 : 2)} ${units[index]}`;
}

function formatNumber(value) {
  return Math.round(Number(value) || 0).toLocaleString('ru-RU');
}

function formatDimension(value) {
  const number = Math.abs(Number(value) || 0);
  if (number >= 1000) return number.toFixed(0);
  if (number >= 100) return number.toFixed(1);
  if (number >= 10) return number.toFixed(2);
  return number.toFixed(3);
}

function roundSmart(value) {
  const number = Number(value) || 0;
  return Math.abs(number) >= 100 ? number.toFixed(1) : Math.abs(number) >= 10 ? number.toFixed(2) : number.toFixed(3);
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.round(Number(seconds) || 0));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (hours) return `${hours} ч ${minutes} мин`;
  return `${Math.max(1, minutes)} мин`;
}

function clampNumber(value, min, max, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.min(max, Math.max(min, number)) : fallback;
}

function guessMime(name) {
  switch (extensionOf(name)) {
    case 'png': return 'image/png';
    case 'jpg':
    case 'jpeg': return 'image/jpeg';
    case 'webp': return 'image/webp';
    case 'json':
    case 'gltf': return 'application/json';
    case 'bin': return 'application/octet-stream';
    case 'stl': return 'model/stl';
    case 'obj': return 'model/obj';
    case 'mtl': return 'text/plain';
    default: return 'application/octet-stream';
  }
}

function humanizeError(error) {
  const message = String(error?.message || error || 'Неизвестная ошибка');
  if (/out of memory|allocation/i.test(message)) return 'Модель слишком тяжёлая для памяти телефона';
  if (/unsupported|not supported/i.test(message)) return `Формат или функция не поддерживается: ${message}`;
  if (/unexpected token|parse|invalid/i.test(message)) return `Файл повреждён или имеет нестандартную структуру: ${message}`;
  return message;
}
