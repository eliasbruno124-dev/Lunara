import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = new URL('../public/icons/', import.meta.url);
await mkdir(root, { recursive: true });

const outputs = [
  ['lunara.svg', 'lunara-180.png', 180],
  ['lunara.svg', 'lunara-192.png', 192],
  ['lunara-maskable.svg', 'lunara-512.png', 512],
];

for (const [source, target, size] of outputs) {
  await sharp(fileURLToPath(new URL(source, root)))
    .resize(size, size)
    .png({ compressionLevel: 9, adaptiveFiltering: true })
    .toFile(fileURLToPath(new URL(target, root)));
}
