import { NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AssetLinksController } from './asset-links.controller.js';

describe('AssetLinksController', () => {
  it('fails closed when the verified app link is disabled', () => {
    const controller = new AssetLinksController(
      new ConfigService({ lunara: { androidAppLink: { enabled: false } } }),
    );
    expect(() => controller.getAssetLinks()).toThrow(NotFoundException);
  });

  it('serves only the configured package and signing fingerprints', () => {
    const fingerprints = [
      Array.from({ length: 32 }, () => 'AB').join(':'),
      Array.from({ length: 32 }, () => 'CD').join(':'),
    ];
    const controller = new AssetLinksController(
      new ConfigService({
        lunara: {
          androidAppLink: {
            enabled: true,
            packageName: 'de.lunara.app',
            certificateFingerprints: fingerprints,
          },
        },
      }),
    );

    expect(controller.getAssetLinks()).toEqual([
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: 'de.lunara.app',
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ]);
  });
});
