import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsEnum,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  ValidateNested,
} from 'class-validator';

export enum SyncEntityTypeDto {
  PERIOD = 'PERIOD',
  DAILY_LOG = 'DAILY_LOG',
}

export enum SyncMutationOperationDto {
  UPSERT = 'UPSERT',
  DELETE = 'DELETE',
}

export class SyncMutationDto {
  @ApiProperty({ format: 'uuid' })
  @IsUUID()
  clientMutationId!: string;

  @ApiProperty({ enum: SyncEntityTypeDto })
  @IsEnum(SyncEntityTypeDto)
  entityType!: SyncEntityTypeDto;

  @ApiProperty({ enum: SyncMutationOperationDto })
  @IsEnum(SyncMutationOperationDto)
  operation!: SyncMutationOperationDto;

  @ApiProperty({ type: 'object', additionalProperties: true })
  @IsObject()
  payload!: Record<string, unknown>;
}

export class SyncRequestDto {
  @ApiPropertyOptional({ pattern: '^\\d+$', default: '0' })
  @IsOptional()
  @IsString()
  @Matches(/^\d+$/)
  cursor?: string;

  @ApiProperty({ type: [SyncMutationDto], maxItems: 100 })
  @IsArray()
  @ArrayMaxSize(100)
  @ValidateNested({ each: true })
  @Type(() => SyncMutationDto)
  mutations!: SyncMutationDto[];
}
