import { ApiPropertyOptional } from '@nestjs/swagger';
import { ArrayMaxSize, IsArray, IsBoolean, IsOptional, IsString, MaxLength } from 'class-validator';

export class UpdateCarePreferencesDto {
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesWarmth?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesCuddling?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesSpace?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesSnacks?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesConversation?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesHouseholdHelp?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() likesReminders?: boolean;
  @ApiPropertyOptional() @IsOptional() @IsBoolean() dislikesUnsolicitedAdvice?: boolean;

  @ApiPropertyOptional({ type: [String], maxItems: 20 })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(20)
  @IsString({ each: true })
  @MaxLength(160, { each: true })
  customHelpfulActions?: string[];

  @ApiPropertyOptional({ type: [String], maxItems: 20 })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(20)
  @IsString({ each: true })
  @MaxLength(160, { each: true })
  customUnhelpfulActions?: string[];
}
