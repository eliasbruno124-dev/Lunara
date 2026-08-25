import { Controller, Get, Header, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiExcludeController } from '@nestjs/swagger';
import { Public } from '../common/decorators/public.decorator.js';

interface AndroidAssetLinksStatement {
  relation: ['delegate_permission/common.handle_all_urls'];
  target: {
    namespace: 'android_app';
    package_name: string;
    sha256_cert_fingerprints: string[];
  };
}

@ApiExcludeController()
@Controller('.well-known/assetlinks.json')
export class AssetLinksController {
  constructor(private readonly config: ConfigService) {}

  @Public()
  @Get()
  @Header('Cache-Control', 'no-store, max-age=0')
  @Header('Pragma', 'no-cache')
  getAssetLinks(): AndroidAssetLinksStatement[] {
    if (!this.config.get<boolean>('lunara.androidAppLink.enabled', false)) {
      throw new NotFoundException();
    }
    return [
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: this.config.get<string>(
            'lunara.androidAppLink.packageName',
            'de.lunara.app',
          ),
          sha256_cert_fingerprints: this.config.get<string[]>(
            'lunara.androidAppLink.certificateFingerprints',
            [],
          ),
        },
      },
    ];
  }
}
