const callbacks = new Map();

function requireNativeBridge() {
  if (!window.AndroidOrca) {
    throw new Error('Нативное ядро OrcaSlicer недоступно. Упрощённого слайсера в Model Lab v4 нет.');
  }
  return window.AndroidOrca;
}

function callbackId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `orca-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function bytesToBase64(bytes) {
  let binary = '';
  const block = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += block) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + block));
  }
  return btoa(binary);
}

function base64ToBytes(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

async function stageModel(file, onProgress = () => {}) {
  const bridge = requireNativeBridge();
  if (!bridge.beginModel(file.name)) {
    throw new Error('Не удалось подготовить временный файл для OrcaSlicer');
  }

  const chunkSize = 192 * 1024;
  for (let offset = 0; offset < file.size; offset += chunkSize) {
    const buffer = await file.slice(offset, Math.min(file.size, offset + chunkSize)).arrayBuffer();
    if (!bridge.appendModelBase64(bytesToBase64(new Uint8Array(buffer)))) {
      throw new Error('Ошибка передачи STL в нативное ядро OrcaSlicer');
    }
    onProgress(Math.min(1, (offset + buffer.byteLength) / Math.max(file.size, 1)));
    await new Promise((resolve) => requestAnimationFrame(resolve));
  }

  const stagedPath = bridge.finishModel();
  if (!stagedPath) throw new Error('OrcaSlicer не получил STL целиком');
  return stagedPath;
}

function invokeNativeSlice(request) {
  const bridge = requireNativeBridge();
  const id = callbackId();
  return new Promise((resolve, reject) => {
    callbacks.set(id, { resolve, reject });
    try {
      bridge.slice(JSON.stringify(request), id);
    } catch (error) {
      callbacks.delete(id);
      reject(error);
    }
  });
}

async function readGcode(path, onProgress = () => {}) {
  const bridge = requireNativeBridge();
  const size = Number(bridge.outputSize(path));
  if (!Number.isFinite(size) || size <= 0) throw new Error('OrcaSlicer вернул пустой G-code');

  const decoder = new TextDecoder('utf-8');
  const chunks = [];
  const chunkSize = 192 * 1024;
  for (let offset = 0; offset < size; offset += chunkSize) {
    const encoded = bridge.readOutputBase64(path, offset, Math.min(chunkSize, size - offset));
    if (!encoded) throw new Error('Не удалось прочитать G-code из нативного ядра');
    chunks.push(decoder.decode(base64ToBytes(encoded), { stream: offset + chunkSize < size }));
    onProgress(Math.min(1, (offset + chunkSize) / size));
    await new Promise((resolve) => requestAnimationFrame(resolve));
  }
  chunks.push(decoder.decode());
  return chunks.join('');
}

export const ModelLabOrca = {
  engineName() {
    return requireNativeBridge().engineName();
  },

  async slice(file, request, onStageProgress, onReadProgress) {
    await stageModel(file, onStageProgress);
    const result = await invokeNativeSlice(request);
    if (!result.ok) throw new Error(result.error || 'OrcaSlicer не смог выполнить нарезку');
    result.gcode = await readGcode(result.gcodePath, onReadProgress);
    return result;
  },

  deleteOutput(path) {
    return Boolean(requireNativeBridge().deleteOutput(path));
  },

  _resolve(id, responseJson) {
    const callback = callbacks.get(id);
    if (!callback) return;
    callbacks.delete(id);
    try {
      callback.resolve(JSON.parse(responseJson));
    } catch (error) {
      callback.reject(error);
    }
  }
};

window.ModelLabOrca = ModelLabOrca;
