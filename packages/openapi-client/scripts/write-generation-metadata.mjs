import { writeFile } from 'node:fs/promises';
import {
  configPath,
  generatedTreeHash,
  metadataPath,
  sha256File,
  specPath,
} from './generation-metadata.mjs';

const metadata = {
  generator: 'OpenAPITools/openapi-generator',
  generatorVersion: '7.24.0',
  specificationSha256: await sha256File(specPath),
  configurationSha256: await sha256File(configPath),
  generatedTreeSha256: await generatedTreeHash(),
};
await writeFile(metadataPath, `${JSON.stringify(metadata, null, 2)}\n`, 'utf8');
