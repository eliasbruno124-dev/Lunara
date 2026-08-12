import {
  isSupportedCommand,
  parseArguments,
  requiredUuid,
  requireConfirmation,
} from './admin-cli.arguments.js';

describe('administrative CLI argument safety', () => {
  it('rejects unknown commands before database construction', () => {
    expect(isSupportedCommand(parseArguments(['unknown']).command)).toBe(false);
    expect(isSupportedCommand(parseArguments(['users', 'list']).command)).toBe(true);
  });

  it('rejects malformed target identifiers without querying', () => {
    const parsed = parseArguments([
      'users',
      'disable',
      '--user-id',
      '../all',
      '--confirm',
      'DISABLE:../all',
    ]);
    expect(() => requiredUuid(parsed.options, 'user-id')).toThrow(
      '--user-id must be an explicit UUID',
    );
  });

  it('requires an exact, target-bound confirmation value', () => {
    const id = '22222222-2222-4222-8222-222222222222';
    expect(() => requireConfirmation(new Map([['confirm', 'DISABLE:other']]), `DISABLE:${id}`))
      .toThrow(`Refusing mutation. Repeat with --confirm DISABLE:${id}`);
    expect(() => requireConfirmation(new Map([['confirm', `DISABLE:${id}`]]), `DISABLE:${id}`))
      .not.toThrow();
  });

  it('rejects duplicate options', () => {
    expect(() => parseArguments(['users', 'disable', '--user-id', 'a', '--user-id', 'b']))
      .toThrow('Option --user-id was specified twice');
  });
});
