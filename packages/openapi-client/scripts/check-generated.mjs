import { readFile } from 'node:fs/promises';
import {
  configPath,
  generatedTreeHash,
  metadataPath,
  sha256File,
  specPath,
} from './generation-metadata.mjs';

const metadata = JSON.parse(await readFile(metadataPath, 'utf8'));
const expected = {
  specificationSha256: await sha256File(specPath),
  configurationSha256: await sha256File(configPath),
  generatedTreeSha256: await generatedTreeHash(),
};
const mismatches = Object.entries(expected).filter(
  ([key, value]) => metadata[key] !== value,
);
if (metadata.generatorVersion !== '7.24.0') {
  mismatches.push(['generatorVersion', '7.24.0']);
}
if (mismatches.length > 0) {
  process.stderr.write(
    `Generated Kotlin client is stale or modified: ${mismatches.map(([key]) => key).join(', ')}\n`,
  );
  process.exitCode = 1;
} else {
  process.stdout.write('OpenAPI snapshot and generated Kotlin client hashes are current.\n');
}
