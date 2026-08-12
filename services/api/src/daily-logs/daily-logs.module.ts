import { Module } from '@nestjs/common';
import { EncryptionService } from '../common/security/encryption.service.js';
import { DailyLogsController } from './daily-logs.controller.js';
import { DailyLogsService } from './daily-logs.service.js';

@Module({
  controllers: [DailyLogsController],
  providers: [DailyLogsService, EncryptionService],
  exports: [DailyLogsService, EncryptionService],
})
export class DailyLogsModule {}
