import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, mkdirSync, lstatSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import ts from 'typescript';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const mode = process.argv[2];
if (!['--write', '--check', '--self-test'].includes(mode)) throw new Error('Use --write, --check or --self-test');
const generated = new Set(['docs/CODE_MAP.md', 'docs/code-map.json']);
const digest = data => createHash('sha256').update(data).digest('hex');
const escape = text => String(text).replaceAll('|', '\\|').replaceAll('\n', ' ').replaceAll('<', '&lt;');
const link = path => '../' + path.split('/').map(encodeURIComponent).join('/');

function sourcePaths() {
  return [...new Set(execFileSync('git', ['ls-files', '-co', '--exclude-standard', '-z'], { cwd: root, encoding: 'utf8' }).split('\0'))]
    .filter(path => path && !generated.has(path) && !path.startsWith('artifacts/'))
    .filter(path => existsSync(resolve(root, path)) && lstatSync(resolve(root, path)).isFile())
    .sort();
}

function role(path) {
  if (path.includes('/generated/')) return 'Generated API client';
  if (path.includes('/androidTest/')) return 'Android device regression';
  if (path.includes('/test/') || /\.(spec|test)\./.test(path) || path.includes('/__tests__/')) return 'Automated regression';
  if (path.includes('/ui/')) return 'Android presentation';
  if (path.includes('/security/') || path.includes('/auth/')) return 'Identity and security';
  if (path.includes('/repository/')) return 'Android data and session coordination';
  if (path.includes('/sync/')) return 'Synchronization';
  if (path.includes('/notifications/')) return 'Notifications';
  if (path.includes('/local/') || path.includes('/prisma/')) return 'Persistence and schema';
  if (path.includes('/res/')) return 'Android resource';
  if (path.startsWith('services/api/src/')) return 'API: ' + path.split('/')[3];
  if (path.startsWith('apps/web/')) return 'Web application';
  if (path.startsWith('apps/android/')) return 'Android application and build';
  if (path.startsWith('scripts/') || path.startsWith('tools/')) return 'Build and maintenance';
  if (path.startsWith('.github/')) return 'CI';
  if (path.startsWith('infra/')) return 'Deployment';
  return 'Documentation or project configuration';
}

function typescriptSymbols(path, text) {
  const file = ts.createSourceFile(path, text, ts.ScriptTarget.Latest, true,
    path.endsWith('.tsx') ? ts.ScriptKind.TSX : path.endsWith('.jsx') ? ts.ScriptKind.JSX
      : /\.[cm]?js$/.test(path) ? ts.ScriptKind.JS : ts.ScriptKind.TS);
  if (file.parseDiagnostics.length) throw new Error('TypeScript/JavaScript parse failure: ' + path);
  const symbols = [];
  const walk = (node, owners = []) => {
    const declaration = ts.isClassDeclaration(node) || ts.isInterfaceDeclaration(node) || ts.isFunctionDeclaration(node)
      || ts.isMethodDeclaration(node) || ts.isPropertyDeclaration(node) || ts.isVariableDeclaration(node)
      || ts.isTypeAliasDeclaration(node) || ts.isEnumDeclaration(node) || ts.isModuleDeclaration(node)
      || ts.isParameter(node) || ts.isGetAccessorDeclaration(node) || ts.isSetAccessorDeclaration(node);
    const name = declaration && node.name ? node.name.getText(file) : null;
    if (name) symbols.push({ name, kind: ts.SyntaxKind[node.kind], line: file.getLineAndCharacterOfPosition(node.name.getStart(file)).line + 1, owner: owners.join('.') });
    ts.forEachChild(node, child => walk(child, name ? [...owners, name] : owners));
  };
  walk(file);
  return symbols;
}

function kotlinSymbols(paths) {
  const build = resolve(root, 'tools/code-map/build');
  mkdirSync(build, { recursive: true });
  const list = resolve(build, 'inputs.txt');
  const output = resolve(build, 'kotlin-map.json');
  writeFileSync(list, paths.join('\n') + '\n');
  const gradle = resolve(root, 'apps/android/gradlew');
  // Arguments are a Gradle command-line string; paths in this repository must not
  // contain quotes/newlines. No shell receives it.
  const args = [root, list, output];
  if (args.some(value => /["\r\n]/.test(value))) throw new Error('Unsupported quote in workspace path');
  execFileSync(gradle, ['--no-daemon', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx768m', '-q', '-p', resolve(root, 'tools/code-map'), 'run', '--args=' + args.map(value => '"' + value + '"').join(' ')], { cwd: root, stdio: ['ignore', 'pipe', 'pipe'], timeout: 240000 });
  return JSON.parse(readFileSync(output, 'utf8'));
}

function buildMap() {
  const paths = sourcePaths();
  const kotlin = kotlinSymbols(paths.filter(path => path.endsWith('.kt') && !path.includes('/generated/')));
  const entries = paths.map(path => {
    const bytes = readFileSync(resolve(root, path));
    const code = /\.(kt|kts|ts|tsx|js|jsx|mjs|cjs|java|xml|json|md|yml|yaml|sh|prisma|properties)$/.test(path);
    const text = code ? bytes.toString('utf8') : '';
    const symbols = kotlin[path] ?? (/\.[cm]?[jt]sx?$/.test(path) && !path.includes('/generated/') ? typescriptSymbols(path, text) : []);
    return { path, role: role(path), bytes: bytes.length, sha256: digest(bytes), ...(code ? { lines: text.split('\n').length } : {}), symbols };
  });
  const flowPath = resolve(root, 'docs/code-map-flows.json');
  const flows = JSON.parse(readFileSync(flowPath, 'utf8')).map(flow => ({ ...flow, anchors: flow.anchors.map(anchor => {
    const file = entries.find(entry => entry.path === anchor.path);
    if (!file) throw new Error('Unknown flow path: ' + anchor.path);
    const matches = file.symbols.filter(symbol => symbol.name === anchor.symbol && symbol.owner === (anchor.owner ?? ''));
    if (matches.length !== 1) throw new Error(`Flow symbol is absent or ambiguous: ${anchor.path} ${anchor.symbol}`);
    return { ...anchor, line: matches[0].line };
  }) }));
  return { format: 1, scope: 'All non-ignored repository files, excluding generated maps and APK artifacts. Kotlin PSI and TypeScript syntax inventory; not a resolved call graph. Generated API client files are hashed but declarations are omitted.', files: entries, flows };
}

function render(map) {
  let md = '# Lunara code map\n\nGenerated from source. Run `npm run code-map:check` before relying on line links. Read [the change guide](CODE_GUIDE.md) for behavior and boundaries.\n\n';
  md += map.scope + '\n\n';
  md += `Files: ${map.files.length}. Parsed declarations: ${map.files.reduce((n, file) => n + file.symbols.length, 0)}. Full hashes and symbols: [code-map.json](code-map.json).\n\n`;
  for (const flow of map.flows) {
    md += '## ' + flow.title + '\n\n' + flow.description + '\n\n';
    for (const anchor of flow.anchors) md += `- [${escape(anchor.symbol)}](${link(anchor.path)}#L${anchor.line}) — ${anchor.purpose}\n`;
    md += '\n';
  }
  md += '## File inventory\n\n| File | Responsibility | Declarations |\n|---|---|---:|\n';
  for (const file of map.files) md += `| [${escape(file.path)}](${link(file.path)}) | ${file.role} | ${file.symbols.length} |\n`;
  md += '\n## Declaration index\n\n';
  for (const file of map.files.filter(file => file.symbols.length)) {
    md += `<details>\n<summary>${escape(file.path)}</summary>\n\n| Kind | Owner / name | Source |\n|---|---|---|\n`;
    for (const symbol of file.symbols) md += `| ${symbol.kind} | ${escape((symbol.owner ? symbol.owner + '.' : '') + symbol.name)} | [${symbol.line}](${link(file.path)}#L${symbol.line}) |\n`;
    md += '\n</details>\n\n';
  }
  return md.trimEnd() + '\n';
}

function assertCurrent(files, read = path => existsSync(resolve(root, path)) ? readFileSync(resolve(root, path), 'utf8') : null) {
  const stale = Object.entries(files).filter(([path, text]) => read(path) !== text);
  if (stale.length) throw new Error('Stale code map: ' + stale.map(([path]) => path).join(', ') + '. Run npm run code-map:write.');
}

if (mode === '--self-test') {
  const rendered = render({ scope: 'Test inventory', files: [], flows: [] });
  assert(rendered.endsWith('\n') && !rendered.endsWith('\n\n'));
  const symbols = typescriptSymbols('fixture.ts', '// function fake() {}\nconst text = "class Wrong {}";\nclass Real { work() { const local = 1; return local; } }');
  assert(symbols.some(symbol => symbol.name === 'work' && symbol.owner === 'Real'));
  assert(!symbols.some(symbol => ['fake', 'Wrong'].includes(symbol.name)));
  const current = Buffer.from('source');
  assert.notEqual(digest(current), digest(Buffer.from('source changed')));
  assert.throws(() => typescriptSymbols('invalid.ts', 'class {'));
  assert(typescriptSymbols('fixture.tsx', 'const View = () => <div />;').some(symbol => symbol.name === 'View'));
  const expected = { 'map.json': '{"source":"current"}' };
  assert.doesNotThrow(() => assertCurrent(expected, path => expected[path]));
  assert.throws(() => assertCurrent(expected, () => '{"source":"stale"}'), /Stale code map/);
  assert.throws(() => assertCurrent(expected, () => null), /Stale code map/);
  const fixture = 'tools/code-map/build/controls/Fixture.kt';
  mkdirSync(dirname(resolve(root, fixture)), { recursive: true });
  writeFileSync(resolve(root, fixture), '// fun fake() {}\nval text = "class Wrong {}"\nclass Real { fun work() = 1 }\n');
  const kotlin = kotlinSymbols([fixture])[fixture];
  assert(kotlin.some(symbol => symbol.name === 'work' && symbol.owner === 'Real'));
  assert(!kotlin.some(symbol => ['fake', 'Wrong'].includes(symbol.name)));
  writeFileSync(resolve(root, fixture), 'class {');
  assert.throws(() => kotlinSymbols([fixture]));
  console.log('CODE MAP CONTROLS PASSED');
} else {
  const map = buildMap();
  const files = { 'docs/code-map.json': JSON.stringify(map, null, 2) + '\n', 'docs/CODE_MAP.md': render(map) };
  if (mode === '--write') {
    for (const [path, text] of Object.entries(files)) writeFileSync(resolve(root, path), text);
    console.log('CODE MAP WRITTEN');
  } else {
    assertCurrent(files);
    console.log('CODE MAP CURRENT');
  }
}
