import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module.js';
import { HouseholdController } from './household.controller.js';
import { HouseholdService } from './household.service.js';

@Module({
  imports: [AuthModule],
  controllers: [HouseholdController],
  providers: [HouseholdService],
})
export class HouseholdModule {}
