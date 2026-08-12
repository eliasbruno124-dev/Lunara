import { Module } from '@nestjs/common';
import { SymptomsController } from './symptoms.controller.js';
import { SymptomsService } from './symptoms.service.js';

@Module({
  controllers: [SymptomsController],
  providers: [SymptomsService],
})
export class SymptomsModule {}
