import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, Length } from 'class-validator';

export class CreateCheckInDto {
  @ApiPropertyOptional({
    default: 'Wie kann ich dich gerade unterstützen?',
    maxLength: 240,
  })
  @IsOptional()
  @IsString()
  @Length(1, 240)
  prompt?: string;
}

export class RespondCheckInDto {
  @ApiProperty({ maxLength: 500 })
  @IsString()
  @Length(1, 500)
  response!: string;
}
