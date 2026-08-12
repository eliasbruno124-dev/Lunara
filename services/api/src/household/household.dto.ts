import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { PreferredLanguage } from '@prisma/client';
import {
  IsEnum,
  IsInt,
  IsOptional,
  IsString,
  Length,
  Matches,
  Max,
  Min,
} from 'class-validator';
import { CredentialsDto } from '../auth/auth.dto.js';

export class CreateInvitationDto {
  @ApiPropertyOptional({ minimum: 5, maximum: 1440, description: 'Cannot exceed the server policy' })
  @IsOptional()
  @IsInt()
  @Min(5)
  @Max(1_440)
  expiresInMinutes?: number;
}

export class JoinHouseholdDto extends CredentialsDto {
  @ApiProperty({ minLength: 8, maxLength: 12 })
  @IsString()
  @Length(8, 12)
  @Matches(/^[A-HJ-NP-Z2-9]+$/i)
  invitationCode!: string;

  @ApiProperty({ example: 'Partner B' })
  @IsString()
  @Length(1, 120)
  displayName!: string;

  @ApiPropertyOptional({ enum: PreferredLanguage, default: PreferredLanguage.DE })
  @IsOptional()
  @IsEnum(PreferredLanguage)
  preferredLanguage?: PreferredLanguage;
}
