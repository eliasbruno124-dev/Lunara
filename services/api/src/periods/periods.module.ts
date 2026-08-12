import { Module } from '@nestjs/common';
import { PredictionModule } from '../prediction/prediction.module.js';
import { PeriodsController } from './periods.controller.js';
import { PeriodsService } from './periods.service.js';

@Module({
  imports: [PredictionModule],
  controllers: [PeriodsController],
  providers: [PeriodsService],
  exports: [PeriodsService],
})
export class PeriodsModule {}
