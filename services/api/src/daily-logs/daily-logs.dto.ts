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
  ValidateIf,
} from 'class-validator';

const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export class CreateDailyLogDto {
  @ApiProperty({ format: 'uuid' })
  @IsUUID()
  id!: string;

  @ApiProperty({ format: 'date' })
  @Matches(LOCAL_DATE_PATTERN)
  localDate!: string;

  @ApiPropertyOptional({ enum: FlowLevel, nullable: true })
  @IsOptional()
  @IsEnum(FlowLevel)
  flowLevel?: FlowLevel | null;

  @ApiPropertyOptional({ type: Number, minimum: 0, maximum: 10, nullable: true })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(10)
  painLevel?: number | null;

  @ApiPropertyOptional({ type: String, maxLength: 64, nullable: true })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  mood?: string | null;

  @ApiPropertyOptional({ type: Number, minimum: 0, maximum: 10, nullable: true })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(10)
  energy?: number | null;

  @ApiPropertyOptional({ type: Number, minimum: 0, maximum: 10, nullable: true })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(10)
  sleepQuality?: number | null;

  @ApiPropertyOptional({ type: Number, minimum: 30, maximum: 45, nullable: true })
  @IsOptional()
  @IsNumber({ maxDecimalPlaces: 2 })
  @Min(30)
  @Max(45)
  bodyTemperature?: number | null;

  @ApiPropertyOptional({ enum: OvulationTestResult, nullable: true })
  @IsOptional()
  @IsEnum(OvulationTestResult)
  ovulationTestResult?: OvulationTestResult | null;

  @ApiPropertyOptional({ type: [String], maxItems: 30 })
  // Unlike the nullable scalar fields, null is not meaningful here: omission
  // preserves an existing list on PATCH, while [] explicitly clears it.
  @ValidateIf((_object, value) => value !== undefined)
  @IsArray()
  @ArrayMaxSize(30)
  @IsString({ each: true })
  @MaxLength(80, { each: true })
  customSymptoms?: string[];

  @ApiPropertyOptional({ type: String, maxLength: 4000, nullable: true })
  @IsOptional()
  @IsString()
  @Length(0, 4000)
  note?: string | null;
}

export class UpdateDailyLogDto extends CreateDailyLogDto {
  @ApiProperty({ minimum: 1 })
  @IsInt()
  @Min(1)
  version!: number;
}
