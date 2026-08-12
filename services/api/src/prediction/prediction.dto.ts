import { ApiPropertyOptional, PartialType } from '@nestjs/swagger';
import { SpecialMode } from '@prisma/client';
import { IsBoolean, IsEnum, IsInt, IsOptional, IsString, IsTimeZone, Max, Min, MaxLength } from 'class-validator';

export class CycleProfileDto {
  @ApiPropertyOptional({ minimum: 15, maximum: 90, nullable: true })
  @IsOptional()
  @IsInt()
  @Min(15)
  @Max(90)
  typicalCycleLengthOptional?: number;

  @ApiPropertyOptional({ minimum: 1, maximum: 14, nullable: true })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(14)
  typicalPeriodLengthOptional?: number;

  @ApiPropertyOptional({ minimum: 8, maximum: 20, nullable: true })
  @IsOptional()
  @IsInt()
  @Min(8)
  @Max(20)
  lutealLengthOptional?: number;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  fertilityPredictionEnabled?: boolean;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  predictionPaused?: boolean;

  @ApiPropertyOptional({ enum: SpecialMode })
  @IsOptional()
  @IsEnum(SpecialMode)
  specialMode?: SpecialMode;

  @ApiPropertyOptional({ example: 'Europe/Berlin' })
  @IsOptional()
  @IsString()
  @IsTimeZone()
  @MaxLength(64)
  timezone?: string;
}

export class UpdateCycleProfileDto extends PartialType(CycleProfileDto) {}
