import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsEnum, IsObject, IsOptional, IsString, Length } from 'class-validator';
import {
  ACCOUNT_PASSWORD_MAX_LENGTH,
  EXISTING_PASSWORD_MIN_LENGTH,
  IsPasswordCodePointLength,
} from '../auth/password-policy.js';

export enum ExportFormatDto {
  JSON = 'JSON',
  PERIODS_CSV = 'PERIODS_CSV',
  DAILY_LOGS_CSV = 'DAILY_LOGS_CSV',
  ENCRYPTED_JSON = 'ENCRYPTED_JSON',
}

export class ExportDto {
  @ApiPropertyOptional({ enum: ExportFormatDto, default: ExportFormatDto.JSON })
  @IsOptional()
  @IsEnum(ExportFormatDto)
  format?: ExportFormatDto;

  @ApiPropertyOptional({ minLength: 12, maxLength: 128, writeOnly: true })
  @IsOptional()
  @IsString()
  @Length(12, 128)
  password?: string;
}

export class ImportDto {
  @ApiProperty({
    type: 'object',
    additionalProperties: true,
    description: 'Object previously returned by the JSON export',
  })
  @IsObject()
  data!: Record<string, unknown>;

  @ApiPropertyOptional({ default: true })
  @IsOptional()
  @IsBoolean()
  preview?: boolean;
}

export class DeleteAccountDto {
  @ApiPropertyOptional({
    minLength: EXISTING_PASSWORD_MIN_LENGTH,
    maxLength: ACCOUNT_PASSWORD_MAX_LENGTH,
    writeOnly: true,
  })
  @IsOptional()
  @IsString()
  @IsPasswordCodePointLength(EXISTING_PASSWORD_MIN_LENGTH, ACCOUNT_PASSWORD_MAX_LENGTH)
  password?: string;

  @ApiPropertyOptional({
    writeOnly: true,
    description: 'OIDC-only accounts: exact linked subject, signed auth_time within five minutes; obtain with prompt=login and max_age=0. A refreshed token is insufficient.',
  })
  @IsOptional()
  @IsString()
  @Length(64, 16_384)
  oidcAccessToken?: string;

  @ApiPropertyOptional({ description: 'Required for OIDC deletion: DELETE:<immutable Lunara user UUID>' })
  @IsOptional()
  @IsString()
  @Length(1, 80)
  confirm?: string;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  deleteHousehold?: boolean;
}
