import { Module } from '@nestjs/common';
import { DailyLogsModule } from '../daily-logs/daily-logs.module.js';
import { PartnerModule } from '../partner/partner.module.js';
import { PeriodsModule } from '../periods/periods.module.js';
import { SyncController } from './sync.controller.js';
import { SyncService } from './sync.service.js';

@Module({
  imports: [PeriodsModule, DailyLogsModule, PartnerModule],
  controllers: [SyncController],
  providers: [SyncService],
})
export class SyncModule {}
