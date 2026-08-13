import { validate } from 'class-validator';
import { DeleteAccountDto } from '../data-management/data-management.dto.js';
import { ChangePasswordDto, LoginDto, RegisterDto } from './auth.dto.js';

describe('account password policy', () => {
  const registration = (password: string): RegisterDto =>
    Object.assign(new RegisterDto(), {
      username: 'tracker',
      password,
      deviceName: 'Password policy test',
      displayName: 'Tracker',
    });

  it('accepts eight-character new account passwords without composition rules', async () => {
    await expect(validate(registration('abcdefgh'))).resolves.toHaveLength(0);
    await expect(validate(registration('12345678'))).resolves.toHaveLength(0);
    await expect(validate(registration('😀'.repeat(8)))).resolves.toHaveLength(0);
    await expect(validate(registration('❤️'.repeat(4)))).resolves.toHaveLength(0);
  });

  it('rejects new account passwords outside 8 to 128 characters', async () => {
    expect(await validate(registration('1234567'))).not.toHaveLength(0);
    expect(await validate(registration('😀'.repeat(7)))).not.toHaveLength(0);
    expect(await validate(registration('❤️'.repeat(3)))).not.toHaveLength(0);
    expect(await validate(registration('x'.repeat(129)))).not.toHaveLength(0);
    expect(await validate(registration('❤️'.repeat(65)))).not.toHaveLength(0);
  });

  it('keeps login and current-password verification compatible with older credentials', async () => {
    const login = Object.assign(new LoginDto(), {
      username: 'tracker',
      password: 'x',
      deviceName: 'Compatibility test',
    });
    await expect(validate(login)).resolves.toHaveLength(0);

    const deletion = Object.assign(new DeleteAccountDto(), {
      password: 'x',
      deleteHousehold: true,
    });
    await expect(validate(deletion)).resolves.toHaveLength(0);
  });

  it('applies the new policy to password changes', async () => {
    const accepted = Object.assign(new ChangePasswordDto(), {
      currentPassword: 'x',
      newPassword: '12345678',
    });
    await expect(validate(accepted)).resolves.toHaveLength(0);

    const rejected = Object.assign(new ChangePasswordDto(), {
      currentPassword: 'x',
      newPassword: '1234567',
    });
    expect(await validate(rejected)).not.toHaveLength(0);
  });
});
