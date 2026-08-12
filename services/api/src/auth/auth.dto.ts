import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { PreferredLanguage } from '@prisma/client';
import {
  IsEnum,
  IsOptional,
  IsString,
  Length,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class CredentialsDto {
  @ApiProperty({ example: 'partner-a' })
  @IsString()
  @Length(3, 64)
  @Matches(/^[\p{L}\p{N}][\p{L}\p{N}._-]*$/u)
  username!: string;

  @ApiProperty({ minLength: 12, maxLength: 128, writeOnly: true })
  @IsString()
  @Length(12, 128)
  password!: string;

  @ApiProperty({ example: 'Pixel 10' })
  @IsString()
  @Length(1, 120)
  deviceName!: string;
}

export class SetupDto extends CredentialsDto {
  @ApiProperty({ writeOnly: true })
  @IsString()
  @MinLength(20)
  @MaxLength(256)
  bootstrapToken!: string;

  @ApiProperty({ example: 'Unser Haushalt' })
  @IsString()
  @Length(1, 120)
  householdName!: string;

  @ApiProperty({ example: 'Partner A' })
  @IsString()
  @Length(1, 120)
  displayName!: string;

  @ApiPropertyOptional({ enum: PreferredLanguage, default: PreferredLanguage.DE })
  @IsOptional()
  @IsEnum(PreferredLanguage)
  preferredLanguage?: PreferredLanguage;
}

export class LoginDto extends CredentialsDto {}

export class RefreshDto {
  @ApiProperty({ writeOnly: true })
  @IsString()
  @MinLength(32)
  @MaxLength(256)
  refreshToken!: string;
}

export class LogoutDto {
  @ApiPropertyOptional({ writeOnly: true })
  @IsOptional()
  @IsString()
  @MinLength(32)
  @MaxLength(256)
  refreshToken?: string;
}
