import { Transform, Type } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { CyclePhase, TipCategory } from '@prisma/client';
import {
  ArrayMaxSize,
  ArrayUnique,
  IsArray,
  IsBoolean,
  IsEnum,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  Matches,
  Max,
  Min,
} from 'class-validator';

const SAFE_TEXT = /^\P{Cc}+$/u;

export class TodayTipQueryDto {
  @ApiPropertyOptional({ default: 0, minimum: 0, maximum: 100 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  @Max(100)
  variant: number = 0;
}
const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;
const trimArray = ({ value }: { value: unknown }) =>
  Array.isArray(value)
    ? (value as unknown[]).map((entry): unknown =>
        typeof entry === 'string' ? entry.trim() : entry,
      )
    : value;

export enum TipSharedField {
  SHARE_PREDICTION = 'sharePrediction',
  SHARE_PERIOD_DATES = 'sharePeriodDates',
  SHARE_PHASE = 'sharePhase',
  SHARE_FLOW = 'shareFlow',
  SHARE_PAIN = 'sharePain',
  SHARE_MOOD = 'shareMood',
  SHARE_ENERGY = 'shareEnergy',
  SHARE_SLEEP = 'shareSleep',
  SHARE_SYMPTOMS = 'shareSymptoms',
  SHARE_NOTES = 'shareNotes',
  SHARE_FERTILE_WINDOW = 'shareFertileWindow',
}

export class TipFeedbackDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  helpful?: boolean;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  dismissed?: boolean;
}

export class CreateUserDefinedTipDto {
  @ApiProperty({ format: 'uuid', description: 'Client-generated idempotent identifier' })
  @IsUUID()
  id!: string;

  @ApiProperty({ minLength: 1, maxLength: 160 })
  @Transform(trim)
  @IsString()
  @Length(1, 160)
  @Matches(SAFE_TEXT)
  titleDe!: string;

  @ApiProperty({ minLength: 1, maxLength: 600 })
  @Transform(trim)
  @IsString()
  @Length(1, 600)
  @Matches(SAFE_TEXT)
  bodyDe!: string;

  @ApiProperty({ minLength: 1, maxLength: 240 })
  @Transform(trim)
  @IsString()
  @Length(1, 240)
  @Matches(SAFE_TEXT)
  callToActionDe!: string;

  @ApiProperty({ minLength: 1, maxLength: 160 })
  @Transform(trim)
  @IsString()
  @Length(1, 160)
  @Matches(SAFE_TEXT)
  titleEn!: string;

  @ApiProperty({ minLength: 1, maxLength: 600 })
  @Transform(trim)
  @IsString()
  @Length(1, 600)
  @Matches(SAFE_TEXT)
  bodyEn!: string;

  @ApiProperty({ minLength: 1, maxLength: 240 })
  @Transform(trim)
  @IsString()
  @Length(1, 240)
  @Matches(SAFE_TEXT)
  callToActionEn!: string;

  @ApiPropertyOptional({ enum: TipSharedField, isArray: true, maxItems: 11 })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(11)
  @ArrayUnique()
  @IsEnum(TipSharedField, { each: true })
  requiredSharedFields?: TipSharedField[];

  @ApiPropertyOptional({ enum: CyclePhase, nullable: true })
  @IsOptional()
  @IsEnum(CyclePhase)
  applicablePhase?: CyclePhase | null;

  @ApiPropertyOptional({ type: [String], maxItems: 20 })
  @IsOptional()
  @Transform(trimArray)
  @IsArray()
  @ArrayMaxSize(20)
  @ArrayUnique()
  @IsString({ each: true })
  @Length(1, 80, { each: true })
  @Matches(SAFE_TEXT, { each: true })
  applicableSymptoms?: string[];

  @ApiPropertyOptional({ minimum: 0, maximum: 100, default: 50 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(100)
  priority?: number;
}

export class UpdateUserDefinedTipDto {
  @ApiPropertyOptional({ minLength: 1, maxLength: 160 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 160)
  @Matches(SAFE_TEXT)
  titleDe?: string;

  @ApiPropertyOptional({ minLength: 1, maxLength: 600 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 600)
  @Matches(SAFE_TEXT)
  bodyDe?: string;

  @ApiPropertyOptional({ minLength: 1, maxLength: 240 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 240)
  @Matches(SAFE_TEXT)
  callToActionDe?: string;

  @ApiPropertyOptional({ minLength: 1, maxLength: 160 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 160)
  @Matches(SAFE_TEXT)
  titleEn?: string;

  @ApiPropertyOptional({ minLength: 1, maxLength: 600 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 600)
  @Matches(SAFE_TEXT)
  bodyEn?: string;

  @ApiPropertyOptional({ minLength: 1, maxLength: 240 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 240)
  @Matches(SAFE_TEXT)
  callToActionEn?: string;

  @ApiPropertyOptional({ enum: TipSharedField, isArray: true, maxItems: 11 })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(11)
  @ArrayUnique()
  @IsEnum(TipSharedField, { each: true })
  requiredSharedFields?: TipSharedField[];

  @ApiPropertyOptional({ enum: CyclePhase, nullable: true })
  @IsOptional()
  @IsEnum(CyclePhase)
  applicablePhase?: CyclePhase | null;

  @ApiPropertyOptional({ type: [String], maxItems: 20 })
  @IsOptional()
  @Transform(trimArray)
  @IsArray()
  @ArrayMaxSize(20)
  @ArrayUnique()
  @IsString({ each: true })
  @Length(1, 80, { each: true })
  @Matches(SAFE_TEXT, { each: true })
  applicableSymptoms?: string[];

  @ApiPropertyOptional({ minimum: 0, maximum: 100 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(100)
  priority?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  enabled?: boolean;
}

export class UserDefinedTipResponseDto {
  @ApiProperty({ format: 'uuid' }) id!: string;
  @ApiProperty({ enum: TipCategory, default: TipCategory.USER_DEFINED })
  category!: TipCategory;
  @ApiProperty() titleDe!: string;
  @ApiProperty() bodyDe!: string;
  @ApiProperty() callToActionDe!: string;
  @ApiProperty() titleEn!: string;
  @ApiProperty() bodyEn!: string;
  @ApiProperty() callToActionEn!: string;
  @ApiProperty({ enum: TipSharedField, isArray: true })
  requiredSharedFields!: string[];
  @ApiProperty({ enum: CyclePhase, nullable: true })
  applicablePhase!: CyclePhase | null;
  @ApiProperty({ type: [String] }) applicableSymptoms!: string[];
  @ApiProperty() priority!: number;
  @ApiProperty() enabled!: boolean;
  @ApiProperty({ format: 'date-time' }) createdAt!: Date;
  @ApiProperty({ format: 'date-time' }) updatedAt!: Date;
}
