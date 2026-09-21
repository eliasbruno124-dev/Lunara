import { createRequire } from 'node:module';
import { readFile, writeFile } from 'node:fs/promises';

// Run after the API build. This is a documentation snapshot, never a database import.
const require = createRequire(import.meta.url);
const { TIP_CATALOG } = require('../services/api/dist/tips/tip-catalog.js');
const output = new URL('../docs/partner-tips.seed.json', import.meta.url);
const snapshot = {
  schemaVersion: 1,
  locales: ['de', 'en'],
  tips: TIP_CATALOG.map(tip => ({
    key: tip.id,
    category: tip.category,
    priority: tip.priority,
    requiredSharedFields: tip.requiredSharedFields,
    applicablePhase: tip.applicablePhase ? [tip.applicablePhase] : ['ANY'],
    applicableSymptoms: tip.applicableSymptoms,
    enabled: true,
    translations: {
      de: { title: tip.titleDe, body: tip.bodyDe, callToAction: tip.callToActionDe },
      en: { title: tip.titleEn, body: tip.bodyEn, callToAction: tip.callToActionEn },
    },
  })),
};
const content = `${JSON.stringify(snapshot, null, 2)}\n`;
if (process.argv.includes('--check')) {
  if (await readFile(output, 'utf8') !== content) throw new Error('Tip documentation is out of date');
} else {
  await writeFile(output, content);
}
console.log(`Tip documentation matches ${snapshot.tips.length} runtime entries`);
