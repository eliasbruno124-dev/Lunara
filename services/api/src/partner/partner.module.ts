import { Module } from '@nestjs/common';
import { EncryptionService } from '../common/security/encryption.service.js';
import { PredictionModule } from '../prediction/prediction.module.js';
import { PartnerController } from './partner.controller.js';
import { PartnerService } from './partner.service.js';

@Module({
  imports: [PredictionModule],
  controllers: [PartnerController],
  providers: [PartnerService, EncryptionService],
  exports: [PartnerService],
})
export class PartnerModule {}
