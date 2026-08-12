import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { FlowLevel, OvulationTestResult } from '@prisma/client';
import {
  IsArray,
  IsEnum,
  IsInt,
  IsNumber,
  IsOptional,
  IsString,
  IsUUID,
  ArrayMaxSize,
  Length,
  Matches,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export class CreateDailyLogDto {
  @ApiProperty({ format: 'uuid' })
  @IsUUID()
  id!: string;

  @ApiProperty({ format: 'date' })
  @Matches(LOCAL_DATE_PATTERN)
  localDate!: string;

  @ApiPropertyOptional({ enum: FlowLevel })
  @IsOptional()
  @IsEnum(FlowLevel)
  flowLevel?: FlowLevel;

  @ApiPropertyOptional({ minimum: 0, maximum: 10 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(10)
  painLevel?: number;

  @ApiPropertyOptional({ maxLength: 64 })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  mood?: string;

  @ApiPropertyOptional({ minimum: 0, maximum: 10 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(10)
  energy?: number;

  @ApiPropertyOptional({ minimum: 0, maximum: 10 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(10)
  sleepQuality?: number;

  @ApiPropertyOptional({ minimum: 30, maximum: 45 })
  @IsOptional()
  @IsNumber({ maxDecimalPlaces: 2 })
  @Min(30)
  @Max(45)
  bodyTemperature?: number;

  @ApiPropertyOptional({ enum: OvulationTestResult })
  @IsOptional()
  @IsEnum(OvulationTestResult)
  ovulationTestResult?: OvulationTestResult;

  @ApiPropertyOptional({ type: [String], maxItems: 30 })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(30)
  @IsString({ each: true })
  @MaxLength(80, { each: true })
  customSymptoms?: string[];

  @ApiPropertyOptional({ maxLength: 4000 })
  @IsOptional()
  @IsString()
  @Length(0, 4000)
  note?: string;
}

export class UpdateDailyLogDto extends CreateDailyLogDto {
  @ApiProperty({ minimum: 1 })
  @IsInt()
  @Min(1)
  version!: number;
}
