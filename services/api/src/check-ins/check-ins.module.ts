import { Module } from '@nestjs/common';
import { CheckInsController } from './check-ins.controller.js';

@Module({ controllers: [CheckInsController] })
export class CheckInsModule {}
