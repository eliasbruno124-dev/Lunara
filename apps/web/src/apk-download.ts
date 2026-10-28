import { translate } from './i18n';
import type { Language } from './types';

/** Browser navigation preserves gateway authentication without buffering the APK in JS. */
export function createApkDownloadCard(language: Language): HTMLElement {
  const card = document.createElement('section');
  card.className = 'info-card setting-card apk-download';
  const title = document.createElement('h2');
  title.textContent = translate(language, 'androidApp');
  const description = document.createElement('p');
  description.className = 'subtle';
  description.textContent = translate(language, 'androidAppDescription');
  const download = document.createElement('a');
  download.className = 'primary-button download-link';
  download.href = '/api/v1/downloads/lunara.apk';
  download.download = 'lunara.apk';
  download.textContent = translate(language, 'downloadApk');
  const hint = document.createElement('p');
  hint.className = 'subtle download-hint';
  hint.textContent = translate(language, 'downloadApkHint');
  card.append(title, description, download, hint);
  return card;
}
