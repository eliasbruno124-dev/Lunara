import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { PeriodSource } from '@prisma/client';
import { IsEnum, IsInt, IsOptional, IsUUID, Matches, Min } from 'class-validator';

const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export class CreatePeriodDto {
  @ApiProperty({ format: 'uuid', description: 'Client-generated idempotent identifier' })
  @IsUUID()
  id!: string;

  @ApiProperty({ format: 'date' })
  @Matches(LOCAL_DATE_PATTERN)
  startDate!: string;

  @ApiPropertyOptional({ format: 'date', nullable: true })
  @IsOptional()
  @Matches(LOCAL_DATE_PATTERN)
  endDate?: string | null;

  @ApiPropertyOptional({ enum: PeriodSource, default: PeriodSource.MANUAL })
  @IsOptional()
  @IsEnum(PeriodSource)
  source?: PeriodSource;
}

export class UpdatePeriodDto {
  @ApiProperty({ minimum: 1, description: 'Current version for optimistic conflict detection' })
  @IsInt()
  @Min(1)
  version!: number;

  @ApiPropertyOptional({ format: 'date' })
  @IsOptional()
  @Matches(LOCAL_DATE_PATTERN)
  startDate?: string;

  @ApiPropertyOptional({ format: 'date', nullable: true })
  @IsOptional()
  @Matches(LOCAL_DATE_PATTERN)
  endDate?: string | null;
}
