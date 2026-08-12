import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import { relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const packageDir = resolve(fileURLToPath(new URL('..', import.meta.url)));
export const specPath = resolve(packageDir, 'openapi/lunara-v1.openapi.json');
export const configPath = resolve(packageDir, 'generator-config.json');
export const generatedDir = resolve(packageDir, 'generated/kotlin');
export const metadataPath = resolve(generatedDir, '.lunara-generation.json');

export async function sha256File(path) {
  return createHash('sha256').update(await readFile(path)).digest('hex');
}

export async function generatedTreeHash() {
  const files = await walk(generatedDir);
  const hash = createHash('sha256');
  for (const path of files.sort()) {
    const name = relative(generatedDir, path).replaceAll('\\', '/');
    if (name === '.lunara-generation.json') continue;
    hash.update(name).update('\0').update(await readFile(path)).update('\0');
  }
  return hash.digest('hex');
}

async function walk(directory) {
  const output = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (
      entry.isDirectory() &&
      ['.gradle', '.gradle-ci', '.kotlin', 'build'].includes(entry.name)
    ) {
      continue;
    }
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) output.push(...(await walk(path)));
    else if (entry.isFile()) output.push(path);
  }
  return output;
}
