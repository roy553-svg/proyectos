import { IsIn, IsObject, IsOptional, IsString, MaxLength } from 'class-validator';
import { VehicleCommandName } from '../providers/vehicle-provider.interface';

const COMMANDS: VehicleCommandName[] = [
  'set_climate_temp',
  'climate_on',
  'climate_off',
  'lock',
  'unlock',
  'honk',
  'flash_lights',
];

export class VehicleCommandDto {
  @IsOptional() @IsString() @MaxLength(120) driverId?: string;

  @IsIn(COMMANDS)
  command!: VehicleCommandName;

  @IsOptional()
  @IsObject()
  args?: Record<string, number | string | boolean>;
}
