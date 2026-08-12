import { Transform } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional, IsString, IsUUID, Length, Matches } from 'class-validator';

const SAFE_LABEL = /^\P{Cc}+$/u;
const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

export class CreateCustomSymptomDto {
  @ApiProperty({ format: 'uuid', description: 'Client-generated idempotent identifier' })
  @IsUUID()
  id!: string;

  @ApiProperty({ minLength: 1, maxLength: 80 })
  @Transform(trim)
  @IsString()
  @Length(1, 80)
  @Matches(SAFE_LABEL)
  label!: string;
}

export class UpdateCustomSymptomDto {
  @ApiPropertyOptional({ minLength: 1, maxLength: 80 })
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 80)
  @Matches(SAFE_LABEL)
  label?: string;

  @ApiPropertyOptional({ description: 'Set false to restore an archived custom symptom.' })
  @IsOptional()
  @IsBoolean()
  archived?: boolean;
}

export class PredefinedSymptomResponseDto {
  @ApiProperty() key!: string;
  @ApiProperty() labelDe!: string;
  @ApiProperty() labelEn!: string;
}

export class CustomSymptomResponseDto {
  @ApiProperty({ format: 'uuid' }) id!: string;
  @ApiProperty() label!: string;
  @ApiProperty({ format: 'date-time', nullable: true }) archivedAt!: Date | null;
  @ApiProperty({ format: 'date-time' }) createdAt!: Date;
  @ApiProperty({ format: 'date-time' }) updatedAt!: Date;
}

export class SymptomCatalogResponseDto {
  @ApiProperty({ type: [PredefinedSymptomResponseDto] })
  predefined!: PredefinedSymptomResponseDto[];

  @ApiProperty({ type: [CustomSymptomResponseDto] })
  custom!: CustomSymptomResponseDto[];
}
