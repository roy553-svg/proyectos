import { Body, Controller, Get, Headers, Post } from '@nestjs/common';
import { AssistantService } from './assistant.service';
import { AssistantMessageDto } from './dto/assistant.dto';

@Controller('api/assistant')
export class AssistantController {
  constructor(private readonly assistant: AssistantService) {}

  /** Entrada unica del cockpit: voz y texto comparten esta ruta. */
  @Post('message')
  message(@Body() dto: AssistantMessageDto) {
    return this.assistant.ask(dto.message, dto.driverId ?? 'demo-driver', dto.voice ?? false);
  }

  /**
   * Audio crudo PCM 16 kHz mono desde VoiceRecorder.kt.
   * El cuerpo llega como Buffer gracias al `express.raw` de main.ts.
   */
  @Post('transcribe')
  async transcribe(
    @Body() pcm: Buffer,
    @Headers('x-driver-id') driverId = 'demo-driver',
  ) {
    const text = await this.assistant.transcribe(Buffer.isBuffer(pcm) ? pcm : Buffer.alloc(0));
    // `text` vacio = el cliente debe usar su reconocimiento local.
    return { text: text ?? '', driverId, remote: text !== null };
  }

  @Get('health')
  health() {
    return this.assistant.health();
  }
}
