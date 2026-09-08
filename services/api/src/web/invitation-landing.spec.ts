import { createHash } from 'node:crypto';
import { runInNewContext } from 'node:vm';
import express from 'express';
import request from 'supertest';
import { installInvitationLanding, invitationCaptureScript } from './invitation-landing.js';

function capture(hash: string, storageFailure = false) {
  const status = { textContent: '' };
  const stored = new Map<string, string>();
  const replace = jest.fn();
  const replaceState = jest.fn();
  runInNewContext(invitationCaptureScript, {
    URLSearchParams,
    navigator: { language: 'de' },
    location: { hash, replace },
    history: { replaceState },
    sessionStorage: { setItem(key: string, value: string) {
      if (storageFailure) throw new Error('storage disabled');
      stored.set(key, value);
    } },
    document: { getElementById: () => status },
  });
  return { stored, replace, replaceState, status };
}

describe('public invitation capture before authentication', () => {
  it('stores only the invitation locally and redirects to the credential-free root', () => {
    const result = capture('#invite=abcdefgh23');
    const value = JSON.parse(result.stored.get('lunara-invitation.v1')!) as Record<string, unknown>;
    expect(value.invitationCode).toBe('ABCDEFGH23');
    expect(typeof value.capturedAt).toBe('number');
    expect(result.replaceState).toHaveBeenCalledWith(null, '', '/invite');
    expect(result.replace).toHaveBeenCalledWith('/');
  });

  it.each(['', '#invite=SHORT', '#invite=ABCDEFGH23&invite=ABCDEFGH24', '#invite=ABCDEFGH23&next=https://example.test', '#invite=%3Cscript%3E'])('rejects malformed or ambiguous fragments %s', (fragment) => {
    const result = capture(fragment);
    expect(result.stored.size).toBe(0);
    expect(result.replace).not.toHaveBeenCalled();
    expect(result.status.textContent).toContain('Einladungscode');
  });

  it('does not lose the invitation by redirecting when browser storage is blocked', () => {
    const result = capture('#invite=ABCDEFGH23', true);
    expect(result.replace).not.toHaveBeenCalled();
    expect(result.replaceState).not.toHaveBeenCalled();
    expect(result.status.textContent).toContain('Einstellungen');
  });

  it('is public, cache-free and restricts execution to the capture script', async () => {
    const app = express();
    installInvitationLanding(app);
    const result = await request(app).get('/invite').expect(200);
    const hash = createHash('sha256').update(invitationCaptureScript).digest('base64');
    expect(result.headers['content-security-policy']).toContain(`script-src 'sha256-${hash}'`);
    expect(result.headers['cache-control']).toBe('no-store, max-age=0');
    expect(result.headers['referrer-policy']).toBe('no-referrer');
    expect(result.text).not.toContain('fetch(');
    await request(app).post('/invite').expect(404);
    await request(app).get('/invite/private').expect(404);
  });
});
