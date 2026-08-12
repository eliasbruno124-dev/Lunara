import { ApiPropertyOptional } from '@nestjs/swagger';
import { TipCategory } from '@prisma/client';
import { ArrayMaxSize, IsArray, IsBoolean, IsEnum, IsOptional, Matches } from 'class-validator';

export class UpdateSharingDto {
  @ApiPropertyOptional() @IsOptional() @IsBoolean() sharePrediction?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() sharePeriodDates?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() sharePhase?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareFlow?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() sharePain?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareMood?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareEnergy?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareSleep?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareSymptoms?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareNotes?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() shareFertileWindow?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() allowSymptomBasedTips?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() tipsEnabled?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() checkInsEnabled?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() sharingPaused?: boolean;

  @ApiPropertyOptional({ format: 'date', nullable: true })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  privateDate?: string | null;

  @ApiPropertyOptional({ enum: TipCategory, isArray: true })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(10)
  @IsEnum(TipCategory, { each: true })
  disabledTipCategories?: TipCategory[];
}
