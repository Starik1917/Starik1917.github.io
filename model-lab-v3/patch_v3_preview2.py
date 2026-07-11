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
    "let slicePreviewPlaybackTimer = null;\nlet slicePreviewState = {",
    "let slicePreviewPlaybackTimer = null;\n"
    "let slicePreviewMode = false;\n"
    "let slicePreviewExpanded = true;\n"
    "let slicePreviewState = {",
    "preview mode state",
)

main = replace_once(
    main,
    "  dom.backButton.addEventListener('click', () => routeTo('home'));",
    "  dom.backButton.addEventListener('click', () => {\n"
    "    if (slicePreviewMode) exitSlicePreviewMode();\n"
    "    else routeTo('home');\n"
    "  });",
    "top back behavior",
)

main = replace_once(
    main,
    "  dom.previewFitButton.addEventListener('click', fitSlicePreview);\n  dom.previewPlayButton.addEventListener('click', toggleSlicePreviewPlayback);",
    "  dom.previewFitButton.addEventListener('click', fitSlicePreview);\n"
    "  dom.previewPlayButton.addEventListener('click', toggleSlicePreviewPlayback);\n"
    "  dom.previewCollapseButton.addEventListener('click', () => setSlicePreviewExpanded(false));\n"
    "  dom.previewDockPlayButton.addEventListener('click', toggleSlicePreviewPlayback);\n"
    "  dom.previewDockSlider.addEventListener('input', () => {\n"
    "    const layer = Number(dom.previewDockSlider.value) || 0;\n"
    "    dom.layerSlider.value = layer;\n"
    "    if (Number(dom.layerBottomSlider.value) > layer) dom.layerBottomSlider.value = layer;\n"
    "    updateSlicePreviewFromControls();\n"
    "  });\n"
    "  dom.previewDock.addEventListener('pointerdown', beginPreviewDockGesture, { passive: true });\n"
    "  dom.previewDock.addEventListener('pointerup', endPreviewDockGesture, { passive: true });\n"
    "  dom.previewDock.addEventListener('pointercancel', cancelPreviewDockGesture, { passive: true });\n"
    "  dom.previewDockLayer.addEventListener('click', () => setSlicePreviewExpanded(true));",
    "dock event bindings",
)

main = replace_once(
    main,
    "  dom.returnToModelButton.addEventListener('click', () => {\n    stopSlicePreviewPlayback();\n    currentSliceResult = null;",
    "  dom.returnToModelButton.addEventListener('click', () => {\n"
    "    exitSlicePreviewMode({ keepResult: false });\n"
    "    currentSliceResult = null;",
    "return button exits preview mode",
)

main = replace_once(
    main,
    "  window.appBack = () => {\n    if (currentSheet) {",
    "  window.appBack = () => {\n"
    "    if (slicePreviewMode) {\n"
    "      exitSlicePreviewMode();\n"
    "      return true;\n"
    "    }\n"
    "    if (currentSheet) {",
    "Android back exits preview",
)

main = replace_once(
    main,
    "  if (onHome) {\n    stopSlicePreviewPlayback();",
    "  if (onHome) {\n"
    "    if (slicePreviewMode) exitSlicePreviewMode({ keepResult: true, closeOverlays: true });\n"
    "    stopSlicePreviewPlayback();",
    "home route exits preview",
)

main = replace_once(
    main,
    "  dom.layerPreviewCard.hidden = true;\n  clearSlicePreview();\n  resizeRenderer();",
    "  if (!slicePreviewMode) dom.layerPreviewCard.hidden = true;\n"
    "  if (!slicePreviewMode) clearSlicePreview();\n"
    "  resizeRenderer();",
    "route preserves active preview",
)

main = replace_once(
    main,
    "  dom.layerPreviewCard.hidden = false;\n  prepareSlicerMaterialForPreview(false);",
    "  enterSlicePreviewMode();\n"
    "  dom.layerPreviewCard.hidden = false;\n"
    "  prepareSlicerMaterialForPreview(false);",
    "enter preview mode after slicing",
)

main = replace_once(
    main,
    "  dom.layerBottomSlider.value = bottom;\n  dom.layerSlider.value = top;\n\n  if (slicerModelGroup) {",
    "  dom.layerBottomSlider.value = bottom;\n"
    "  dom.layerSlider.value = top;\n"
    "  if (dom.previewDockSlider) {\n"
    "    dom.previewDockSlider.max = max;\n"
    "    dom.previewDockSlider.value = top;\n"
    "  }\n\n"
    "  if (slicerModelGroup) {",
    "sync dock slider",
)

main = replace_once(
    main,
    "  dom.layerPreviewValue.textContent = `Слой ${top + 1} / ${totalLayers} · Z ${topLayer.printZ.toFixed(2)} мм`;",
    "  dom.layerPreviewValue.textContent = `Слой ${top + 1} / ${totalLayers} · Z ${topLayer.printZ.toFixed(2)} мм`;\n"
    "  if (dom.previewDockLayer) dom.previewDockLayer.textContent = `Слой ${top + 1} / ${totalLayers}`;\n"
    "  if (dom.previewDockZ) dom.previewDockZ.textContent = `Z ${topLayer.printZ.toFixed(2)} мм`;",
    "sync dock labels",
)

main = replace_regex(
    main,
    r"function syncSlicePreviewButtons\(\) \{.*?\n\}",
    """function syncSlicePreviewButtons() {
  setPressed(dom.previewTravelButton, slicePreviewState.showTravel);
  setPressed(dom.previewModelButton, slicePreviewState.showModel);
  document.querySelectorAll('[data-preview-type]').forEach((button) => {
    setPressed(button, slicePreviewState.visibleTypes.has(button.dataset.previewType));
  });
  const playing = Boolean(slicePreviewPlaybackTimer);
  for (const button of [dom.previewPlayButton, dom.previewDockPlayButton]) {
    if (!button) continue;
    button.setAttribute('aria-pressed', String(playing));
    button.setAttribute('aria-label', playing ? 'Остановить просмотр' : 'Воспроизвести слои');
    const path = button.querySelector('path');
    if (path) path.setAttribute('d', playing ? 'M7 5h4v14H7V5Zm6 0h4v14h-4V5Z' : 'M8 5v14l11-7L8 5Z');
  }
}""",
    "sync both play buttons",
)

insert_anchor = "function toggleSlicePreviewPlayback() {"
preview_mode_functions = """let previewDockGesture = null;

function enterSlicePreviewMode() {
  slicePreviewMode = true;
  slicePreviewExpanded = true;
  dom.slicerToolbar.hidden = true;
  dom.previewDock.hidden = false;
  dom.layerPreviewCard.hidden = false;
  dom.workspaceScreen.classList.add('is-previewing');
  dom.backButton.setAttribute('aria-label', 'Назад к подготовке модели');
  syncSlicePreviewLayout();
}

function exitSlicePreviewMode({ keepResult = true, closeOverlays = true } = {}) {
  stopSlicePreviewPlayback();
  slicePreviewMode = false;
  slicePreviewExpanded = true;
  dom.previewDock.hidden = true;
  dom.layerPreviewCard.hidden = true;
  dom.workspaceScreen.classList.remove('is-previewing');
  dom.backButton.setAttribute('aria-label', 'Назад');
  if (appMode === 'slicer') dom.slicerToolbar.hidden = false;
  if (slicerModelGroup) {
    slicerModelGroup.visible = true;
    prepareSlicerMaterialForPreview(true);
  }
  clearSlicePreview();
  if (!keepResult) {
    currentSliceResult = null;
    currentGcode = '';
  }
  if (closeOverlays) closeSheets();
  resizeRenderer();
}

function setSlicePreviewExpanded(expanded) {
  if (!slicePreviewMode) return;
  slicePreviewExpanded = Boolean(expanded);
  syncSlicePreviewLayout();
}

function syncSlicePreviewLayout() {
  dom.layerPreviewCard.hidden = !slicePreviewMode || !slicePreviewExpanded;
  dom.previewDock.hidden = !slicePreviewMode;
  dom.previewDock.classList.toggle('is-expanded-companion', slicePreviewExpanded);
  resizeRenderer();
}

function beginPreviewDockGesture(event) {
  if (!slicePreviewMode || event.pointerType === 'mouse' && event.button !== 0) return;
  previewDockGesture = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, time: performance.now() };
}

function endPreviewDockGesture(event) {
  if (!previewDockGesture || previewDockGesture.pointerId !== event.pointerId) return;
  const gesture = previewDockGesture;
  previewDockGesture = null;
  const dy = event.clientY - gesture.y;
  const dx = Math.abs(event.clientX - gesture.x);
  if (dy < -28 && dx < 90 && performance.now() - gesture.time < 700) setSlicePreviewExpanded(true);
}

function cancelPreviewDockGesture() {
  previewDockGesture = null;
}

"""
main = replace_once(main, insert_anchor, preview_mode_functions + insert_anchor, "preview mode functions")

main = replace_once(
    main,
    "versionName '3.0.0-preview1'",
    "versionName '3.0.0-preview2'",
    "version name in generated source",
)

MAIN.write_text(main, encoding="utf-8")

html = HTML.read_text(encoding="utf-8")
html = replace_once(
    html,
    "<button class=\"preview-icon-button\" id=\"previewPlayButton\" aria-label=\"Воспроизвести слои\" aria-pressed=\"false\"><svg viewBox=\"0 0 24 24\"><path d=\"M8 5v14l11-7L8 5Z\"/></svg></button>",
    "<button class=\"preview-icon-button\" id=\"previewCollapseButton\" aria-label=\"Скрыть настройки предпросмотра\"><svg viewBox=\"0 0 24 24\"><path d=\"m7 9 5 5 5-5 1.4 1.4L12 16.8l-6.4-6.4L7 9Z\"/></svg></button>\n"
    "              <button class=\"preview-icon-button\" id=\"previewPlayButton\" aria-label=\"Воспроизвести слои\" aria-pressed=\"false\"><svg viewBox=\"0 0 24 24\"><path d=\"M8 5v14l11-7L8 5Z\"/></svg></button>",
    "collapse button",
)

slicer_toolbar_anchor = """      <nav class="bottom-toolbar slicer-toolbar" id="slicerToolbar" hidden>"""
if slicer_toolbar_anchor not in html:
    raise RuntimeError("Missing slicer toolbar anchor")

preview_dock_html = """      <nav class="preview-dock" id="previewDock" hidden aria-label="Управление предпросмотром слоёв">
        <button class="preview-dock-play" id="previewDockPlayButton" aria-label="Воспроизвести слои" aria-pressed="false">
          <svg viewBox="0 0 24 24"><path d="M8 5v14l11-7L8 5Z"/></svg>
        </button>
        <label class="preview-dock-range">
          <span class="preview-dock-copy"><strong id="previewDockLayer">Слой 1 / 1</strong><small id="previewDockZ">Z 0.00 мм</small></span>
          <input id="previewDockSlider" type="range" min="0" max="0" value="0" aria-label="Текущий слой" />
        </label>
      </nav>

"""
html = replace_once(html, slicer_toolbar_anchor, preview_dock_html + slicer_toolbar_anchor, "preview dock HTML")
HTML.write_text(html, encoding="utf-8")

css = CSS.read_text(encoding="utf-8")
css += r"""

/* Model Lab 3D v3 preview2 — dedicated compact preview controls */
.workspace-screen.is-previewing { --toolbar-height: 86px; }
.workspace-screen.is-previewing .slicer-status-card { opacity: .22; pointer-events: none; }
.preview-dock {
  z-index: 34;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-height: calc(78px + var(--safe-bottom));
  padding: 8px 14px calc(8px + var(--safe-bottom));
  background: color-mix(in srgb, var(--surface) 96%, transparent);
  border-top: 1px solid var(--outline-soft);
  box-shadow: 0 -16px 42px rgba(0,0,0,.22);
  backdrop-filter: blur(26px);
}
.preview-dock-play {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 20px;
  color: #10221b;
  background: var(--success);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--success) 28%, transparent);
}
.preview-dock-play svg { width: 28px; height: 28px; }
.preview-dock-play[aria-pressed="true"] { transform: scale(.96); }
.preview-dock-range {
  min-width: 0;
  display: grid;
  gap: 7px;
  padding: 5px 2px 2px;
}
.preview-dock-copy { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; min-width: 0; }
.preview-dock-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; letter-spacing: -.01em; }
.preview-dock-copy small { flex: 0 0 auto; color: var(--muted); font-size: 9px; }
.preview-dock-range input[type="range"] { width: 100%; height: 26px; margin: 0; accent-color: var(--primary); }
.preview-dock.is-expanded-companion { border-top-color: color-mix(in srgb, var(--primary) 18%, var(--outline-soft)); }
.workspace-screen.is-previewing .layer-preview-card {
  bottom: calc(78px + var(--safe-bottom) + 10px);
  max-height: min(38vh, 300px);
}
@media (max-width: 360px) {
  .preview-dock { grid-template-columns: 52px minmax(0,1fr); gap: 9px; padding-left: 9px; padding-right: 9px; }
  .preview-dock-play { width: 50px; height: 50px; border-radius: 18px; }
  .preview-dock-copy small { display: none; }
  .workspace-screen.is-previewing .layer-preview-card { bottom: calc(76px + var(--safe-bottom) + 7px); }
}
@media (max-height: 690px) {
  .workspace-screen.is-previewing .layer-preview-card { max-height: 34vh; }
}
"""
CSS.write_text(css, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 7", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.0.0-preview2'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

package = PACKAGE.read_text(encoding="utf-8")
package = re.sub(r'"version"\s*:\s*"[^"]+"', '"version": "3.0.0-preview2"', package, count=1)
PACKAGE.write_text(package, encoding="utf-8")

print("Model Lab v3 preview2 compact preview patch applied")
