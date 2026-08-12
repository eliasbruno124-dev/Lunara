import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ConfigModule } from '@nestjs/config';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { appConfig, validateEnvironment } from './config/app.config.js';
import { AccessTokenGuard } from './common/guards/access-token.guard.js';
import { PrismaModule } from './prisma/prisma.module.js';
import { AuthModule } from './auth/auth.module.js';
import { HealthController } from './health/health.controller.js';
import { MetaController } from './meta/meta.controller.js';
import { HouseholdModule } from './household/household.module.js';
import { UsersModule } from './users/users.module.js';
import { PredictionModule } from './prediction/prediction.module.js';
import { PeriodsModule } from './periods/periods.module.js';
import { DailyLogsModule } from './daily-logs/daily-logs.module.js';
import { SharingModule } from './sharing/sharing.module.js';
import { CareModule } from './care/care.module.js';
import { PartnerModule } from './partner/partner.module.js';
import { SyncModule } from './sync/sync.module.js';
import { CheckInsModule } from './check-ins/check-ins.module.js';
import { TipsModule } from './tips/tips.module.js';
import { DataManagementModule } from './data-management/data-management.module.js';
import { RealtimeModule } from './realtime/realtime.module.js';
import { SymptomsModule } from './symptoms/symptoms.module.js';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      cache: true,
      load: [appConfig],
      validate: validateEnvironment,
    }),
    ThrottlerModule.forRoot([{ ttl: 60_000, limit: 120 }]),
    PrismaModule,
    AuthModule,
    HouseholdModule,
    UsersModule,
    PredictionModule,
    PeriodsModule,
    DailyLogsModule,
    SharingModule,
    CareModule,
    PartnerModule,
    SyncModule,
    CheckInsModule,
    TipsModule,
    DataManagementModule,
    RealtimeModule,
    SymptomsModule,
  ],
  controllers: [HealthController, MetaController],
  providers: [
    { provide: APP_GUARD, useClass: ThrottlerGuard },
    { provide: APP_GUARD, useClass: AccessTokenGuard },
  ],
})
export class AppModule {}
