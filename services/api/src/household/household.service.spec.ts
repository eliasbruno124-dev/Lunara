import { NotFoundException } from '@nestjs/common';
import { HouseholdService } from './household.service.js';

describe('HouseholdService invitation preflight', () => {
  it('rejects an unknown invitation before hashing the supplied password', async () => {
    const prisma = {
      invitation: { findUnique: jest.fn().mockResolvedValue(null) },
    };
    const auth = {
      normalizeUsername: jest.fn().mockReturnValue('partner'),
      hashToken: jest.fn().mockReturnValue('invitation-hash'),
      hashPassword: jest.fn(),
    };
    const config = { get: jest.fn() };
    const service = new HouseholdService(prisma as never, auth as never, config as never);

    await expect(
      service.join({
        invitationCode: 'AAAAAAAAAA',
        username: 'partner',
        password: 'A secure partner password!',
        displayName: 'Partner',
        deviceName: 'Phone',
      }),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(auth.hashPassword).not.toHaveBeenCalled();
  });
});
