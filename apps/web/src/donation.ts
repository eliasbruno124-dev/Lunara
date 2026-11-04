import { translate } from './i18n';
import type { Language } from './types';

/** Same voluntary PayPal address as the Android app (res/values/brand.xml donation_url). */
export const DONATION_URL = 'https://paypal.me/eliasbruno123';

/**
 * Optional support link for the settings, matching the Android app: a plain external link that
 * asks for confirmation first. Lunara sends no data to PayPal and never starts a payment itself.
 */
export function createDonationCard(language: Language, confirmOpen: (message: string) => boolean): HTMLElement {
  const card = document.createElement('section');
  card.className = 'info-card setting-card donation';
  const title = document.createElement('h2');
  title.textContent = translate(language, 'donationTitle');
  const link = document.createElement('a');
  link.className = 'secondary-button donation-link';
  link.href = DONATION_URL;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.textContent = translate(language, 'donationButton');
  // Ask on left and middle click alike; a plain link keeps working where pop-ups are blocked.
  const askFirst = (event: Event) => {
    if (!confirmOpen(translate(language, 'donationConfirm', { url: DONATION_URL }))) event.preventDefault();
  };
  link.addEventListener('click', askFirst);
  link.addEventListener('auxclick', askFirst);
  const hint = document.createElement('p');
  hint.className = 'subtle';
  hint.textContent = translate(language, 'donationOptional');
  card.append(title, link, hint);
  return card;
}
