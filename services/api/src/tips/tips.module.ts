import { Module } from '@nestjs/common';
import { PredictionModule } from '../prediction/prediction.module.js';
import { TipsController, TrackerTipsController } from './tips.controller.js';
import { TipsService } from './tips.service.js';

@Module({
  imports: [PredictionModule],
  controllers: [TipsController, TrackerTipsController],
  providers: [TipsService],
})
export class TipsModule {}
