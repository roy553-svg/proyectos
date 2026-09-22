import {
  IsIn,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

export class DriverScopeDto {
  @IsString()
  @MaxLength(120)
  driverId!: string;
}

export class UpsertPreferenceDto extends DriverScopeDto {
  @IsString() @MaxLength(32) category!: string;
  @IsString() @MaxLength(120) subject!: string;
  @IsIn(['like', 'dislike', 'neutral']) sentiment: 'like' | 'dislike' | 'neutral' = 'like';
  @IsIn(['declared', 'inferred']) origin: 'declared' | 'inferred' = 'declared';
  @IsOptional() @IsNumber() @Min(0) @Max(1) confidence?: number;
  @IsOptional() @IsString() @MaxLength(300) evidence?: string;
}

export class UpsertPlaceDto extends DriverScopeDto {
  @IsString() @MaxLength(160) name!: string;
  @IsOptional() @IsString() @MaxLength(48) kind?: string;
  @IsOptional() @IsString() @MaxLength(240) address?: string;
  @IsOptional() @IsNumber() lat?: number;
  @IsOptional() @IsNumber() lon?: number;
  @IsOptional() @IsNumber() @Min(0) @Max(5) rating?: number;
  @IsOptional() @IsString() @MaxLength(300) notes?: string;
}

export class UpsertFactDto extends DriverScopeDto {
  @IsString() @MaxLength(64) factKey!: string;
  @IsString() @MaxLength(500) factValue!: string;
  @IsOptional() @IsString() @MaxLength(64) scheduleCron?: string;
  @IsOptional() @IsNumber() @Min(0) @Max(1) confidence?: number;
  @IsOptional() @IsString() @MaxLength(300) evidence?: string;
}

export class PrivacyFlagsDto extends DriverScopeDto {
  @IsOptional() micEnabled?: boolean;
  @IsOptional() locationEnabled?: boolean;
  @IsOptional() memoryEnabled?: boolean;
}
