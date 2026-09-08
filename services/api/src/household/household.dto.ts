import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { PreferredLanguage } from '@prisma/client';
import {
  Equals,
  IsBoolean,
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
  @ApiPropertyOptional({
    minimum: 5,
    maximum: 1440,
    description: 'Cannot exceed the server policy',
  })
  @IsOptional()
  @IsInt()
  @Min(5)
  @Max(1_440)
  expiresInMinutes?: number;

  @ApiPropertyOptional({
    default: false,
    description:
      'Create a single-use, revocable invitation without an expiry date',
  })
  @IsOptional()
  @IsBoolean()
  neverExpires?: boolean;
}

/**
 * An authenticated account must opt in explicitly before its current
 * relationship membership can be moved to a partner invitation.
 */
export class AcceptInvitationDto {
  @ApiProperty({ minLength: 8, maxLength: 12 })
  @IsString()
  @Length(8, 12)
  @Matches(/^[A-HJ-NP-Z2-9]+$/i)
  invitationCode!: string;

  @ApiProperty({
    enum: [true],
    description:
      'Must be true to confirm that this account will leave its empty standalone relationship',
  })
  @IsBoolean()
  @Equals(true)
  confirm!: true;
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
  @Matches(/\S/u)
  displayName!: string;

  @ApiPropertyOptional({
    enum: PreferredLanguage,
    default: PreferredLanguage.DE,
  })
  @IsOptional()
  @IsEnum(PreferredLanguage)
  preferredLanguage?: PreferredLanguage;
}
