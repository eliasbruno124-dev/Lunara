import { Module } from '@nestjs/common';
import { TipsController, TrackerTipsController } from './tips.controller.js';
import { TipsService } from './tips.service.js';

@Module({ controllers: [TipsController, TrackerTipsController], providers: [TipsService] })
export class TipsModule {}
