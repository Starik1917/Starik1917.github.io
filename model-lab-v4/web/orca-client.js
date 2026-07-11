const callbacks = new Map();

function requireNativeBridge() {
  if (!window.AndroidOrca) {
    throw new Error('Нативное ядро OrcaSlicer недоступно. Упрощённого слайсера в Model Lab v4 нет.');
  }
  return window.AndroidOrca;
}

function bytesToBase64(bytes) {
  let binary = '';
  const block = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += block) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + block));
  }
  return btoa(binary);
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
  const callbackId = crypto.randomUUID();
  return new Promise((resolve, reject) => {
    callbacks.set(callbackId, { resolve, reject });
    try {
      bridge.slice(JSON.stringify(request), callbackId);
    } catch (error) {
      callbacks.delete(callbackId);
      reject(error);
    }
  });
}

export const ModelLabOrca = {
  engineName() {
    return requireNativeBridge().engineName();
  },

  async slice(file, request, onStageProgress) {
    await stageModel(file, onStageProgress);
    const result = await invokeNativeSlice(request);
    if (!result.ok) throw new Error(result.error || 'OrcaSlicer не смог выполнить нарезку');
    return result;
  },

  _resolve(callbackId, responseJson) {
    const callback = callbacks.get(callbackId);
    if (!callback) return;
    callbacks.delete(callbackId);
    try {
      callback.resolve(JSON.parse(responseJson));
    } catch (error) {
      callback.reject(error);
    }
  }
};

window.ModelLabOrca = ModelLabOrca;
