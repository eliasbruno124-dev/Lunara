import { Module } from '@nestjs/common';
import { CareController } from './care.controller.js';

@Module({ controllers: [CareController] })
export class CareModule {}
