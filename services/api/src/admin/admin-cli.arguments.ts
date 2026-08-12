const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const SUPPORTED_COMMANDS = new Set([
  'users list',
  'users disable',
  'sessions revoke-all',
  'bootstrap status',
  'invitations list',
  'invitations revoke',
]);

export interface ParsedOptions {
  command: string[];
  options: Map<string, string>;
}

export function parseArguments(argumentsList: string[]): ParsedOptions {
  const command: string[] = [];
  const options = new Map<string, string>();
  for (let index = 0; index < argumentsList.length; index += 1) {
    const argument = argumentsList[index];
    if (!argument) continue;
    if (!argument.startsWith('--')) {
      command.push(argument);
      continue;
    }
    const name = argument.slice(2);
    const value = argumentsList[index + 1];
    if (!name || !value || value.startsWith('--')) {
      throw new Error(`Option --${name || '?'} requires a value`);
    }
    if (options.has(name)) throw new Error(`Option --${name} was specified twice`);
    options.set(name, value);
    index += 1;
  }
  return { command, options };
}

export function isSupportedCommand(command: string[]): boolean {
  return SUPPORTED_COMMANDS.has(command.join(' '));
}

export function requiredUuid(options: Map<string, string>, name: string): string {
  const value = options.get(name);
  if (!value || !UUID_PATTERN.test(value)) {
    throw new Error(`--${name} must be an explicit UUID`);
  }
  return value.toLowerCase();
}

export function requireConfirmation(
  options: Map<string, string>,
  expected: string,
): void {
  if (options.get('confirm') !== expected) {
    throw new Error(`Refusing mutation. Repeat with --confirm ${expected}`);
  }
}
