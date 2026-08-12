import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsEnum, IsObject, IsOptional, IsString, Length } from 'class-validator';

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
  @ApiProperty({ writeOnly: true })
  @IsString()
  @Length(12, 128)
  password!: string;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  deleteHousehold?: boolean;
}
