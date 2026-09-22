import { IsBoolean, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class AssistantMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(600)
  message!: string;

  @IsOptional() @IsString() @MaxLength(120) driverId?: string;

  /** true cuando la pregunta llego por el orbe de voz (aplica rate limit). */
  @IsOptional() @IsBoolean() voice?: boolean;
}
