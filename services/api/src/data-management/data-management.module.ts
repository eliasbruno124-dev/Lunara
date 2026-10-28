import { Module } from '@nestjs/common';
import { EncryptionService } from '../common/security/encryption.service.js';
import { DailyLogsModule } from '../daily-logs/daily-logs.module.js';
import { PredictionModule } from '../prediction/prediction.module.js';
import { DataManagementController } from './data-management.controller.js';
import { DataManagementService } from './data-management.service.js';
import { OidcTokenVerifier } from '../auth/oidc-token-verifier.js';
import { AccountLifecycleController, AccountLifecycleService } from './account-lifecycle.controller.js';

@Module({
  imports: [DailyLogsModule, PredictionModule],
  controllers: [DataManagementController, AccountLifecycleController],
  providers: [DataManagementService, EncryptionService, OidcTokenVerifier, AccountLifecycleService],
})
export class DataManagementModule {}
