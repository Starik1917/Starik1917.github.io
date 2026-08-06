import { build } from 'esbuild';
import { cp, mkdir, rm, copyFile } from 'node:fs/promises';
import path from 'node:path';

const root = process.cwd();
const outDir = path.join(root, 'app/src/main/assets');

await rm(outDir, { recursive: true, force: true });
await mkdir(outDir, { recursive: true });

await build({
  entryPoints: [path.join(root, 'web/src/main.js')],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: ['chrome100'],
  minify: true,
  sourcemap: false,
  outfile: path.join(outDir, 'app.js'),
  define: {
    'process.env.NODE_ENV': '"production"'
  }
});

await copyFile(path.join(root, 'web/index.html'), path.join(outDir, 'index.html'));
await copyFile(path.join(root, 'web/src/style.css'), path.join(outDir, 'style.css'));

await cp(
  path.join(root, 'node_modules/three/examples/jsm/libs/draco/gltf'),
  path.join(outDir, 'draco'),
  { recursive: true }
);
await cp(
  path.join(root, 'node_modules/three/examples/jsm/libs/basis'),
  path.join(outDir, 'basis'),
  { recursive: true }
);

console.log('Web assets built into app/src/main/assets');
