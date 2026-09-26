import fs from 'node:fs';
import assert from 'node:assert/strict';

export function verifyInstrumentation(log) {
  const declared = Number(log.match(/INSTRUMENTATION_STATUS: numtests=(\d+)/)?.[1]);
  const completed = Number(log.match(/^OK \((\d+) tests?\)/m)?.[1]);
  const passed = [...log.matchAll(/^INSTRUMENTATION_STATUS_CODE: 0\r?$/gm)].length;
  assert(declared > 0 && completed === declared && passed === declared, 'Incomplete or failed Android test run');
  assert(!/FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -\d/.test(log), 'Android test failures');
  return completed;
}

if (process.argv.includes('--self-test')) {
  const good = 'INSTRUMENTATION_STATUS: numtests=2\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_STATUS_CODE: 0\nOK (2 tests)\n';
  assert.equal(verifyInstrumentation(good), 2);
  for (const bad of ['', good.replace('OK (2 tests)', 'FAILURES!!!'), good.replace('numtests=2', 'numtests=3'), good + 'INSTRUMENTATION_FAILED']) {
    assert.throws(() => verifyInstrumentation(bad));
  }
  console.log('Instrumentation oracle positive and negative controls passed');
} else if (process.argv[2]) {
  const count = verifyInstrumentation(fs.readFileSync(process.argv[2], 'utf8'));
  console.log(`All ${count} Android instrumentation tests passed.`);
  console.log('REDESIGN_REMOTE_INSTRUMENTATION_PASSED');
}
