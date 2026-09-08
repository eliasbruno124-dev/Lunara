import { createHash } from 'node:crypto';
import type { Express } from 'express';

// This public page has no API access. It captures the fragment locally before
// the protected application redirects to the identity provider.
export const invitationCaptureScript = `(() => {
  const english = navigator.language.toLowerCase().startsWith('en');
  const fail = () => {
    document.getElementById('status').textContent = english
      ? 'This invitation could not be opened. Open Lunara and enter the invitation code under Settings.'
      : 'Diese Einladung konnte nicht geöffnet werden. Öffne Lunara und gib den Einladungscode unter Einstellungen ein.';
  };
  try {
    const params = new URLSearchParams(location.hash.slice(1));
    const values = [...params.entries()];
    const code = (params.get('invite') || '').trim().toUpperCase();
    if (values.length !== 1 || values[0][0] !== 'invite' || !/^[A-HJ-NP-Z2-9]{8,12}$/.test(code)) {
      history.replaceState(null, '', '/invite');
      fail();
      return;
    }
    sessionStorage.setItem('lunara-invitation.v1', JSON.stringify({ invitationCode: code, capturedAt: Date.now() }));
    history.replaceState(null, '', '/invite');
    location.replace('/');
  } catch {
    fail();
  }
})();`;

export function installInvitationLanding(server: Express): void {
  const hash = createHash('sha256').update(invitationCaptureScript).digest('base64');
  server.get('/invite', (_request, response) => {
    response.set({
      'Cache-Control': 'no-store, max-age=0',
      Pragma: 'no-cache',
      'Referrer-Policy': 'no-referrer',
      'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': `default-src 'none'; script-src 'sha256-${hash}'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`,
    });
    response.type('html').send(`<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Lunara · Einladung</title></head><body><main><h1>Lunara</h1><p id="status">Einladung wird geöffnet … / Opening invitation …</p><p><a href="/" rel="noreferrer">Lunara öffnen / Open Lunara</a></p><noscript>Bitte JavaScript aktivieren oder den Einladungscode in Lunara unter Einstellungen eingeben. / Enable JavaScript or enter your invitation code in Lunara Settings.</noscript></main><script>${invitationCaptureScript}</script></body></html>`);
  });
}
