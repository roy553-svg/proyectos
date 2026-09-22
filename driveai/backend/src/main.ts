import 'reflect-metadata';
import { Logger, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { NestExpressApplication } from '@nestjs/platform-express';
import { existsSync } from 'fs';
import { join } from 'path';
import { AppModule } from './app.module';
import { AppConfig } from './config/app.config';
import { raw } from 'express';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    logger: ['log', 'warn', 'error'],
    bodyParser: true,
  });

  // El audio de VoiceRecorder.kt llega como binario crudo, no como JSON.
  app.use('/api/assistant/transcribe', raw({ type: () => true, limit: '2mb' }));

  app.enableCors({ origin: true, credentials: false });
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: false,
    }),
  );
  // El cockpit compilado se sirve desde el mismo origen que la API: la
  // radio carga http://<gateway>:8080/ y no sufre CORS ni DNS extra.
  const cockpit = join(__dirname, '..', 'public');
  if (existsSync(cockpit)) {
    app.useStaticAssets(cockpit, { maxAge: '7d', index: false });
    // Fallback de aplicacion de una sola pagina, sin tocar /api.
    app.use((req: any, res: any, next: any) => {
      if (req.method !== 'GET' || req.path.startsWith('/api/')) return next();
      res.sendFile(join(cockpit, 'index.html'), (err: unknown) => {
        if (err) next();
      });
    });
  }

  app.enableShutdownHooks();

  await app.listen(AppConfig.port, '0.0.0.0');
  new Logger('DriveAI').log(
    `Gateway escuchando en :${AppConfig.port} | IA remota: ${
      AppConfig.gemini.apiKey ? 'activa' : 'desactivada (motor determinista local)'
    } | proveedor vehicular: ${AppConfig.vehicle.provider}`,
  );
}

// Nunca dejamos el proceso colgado sin explicacion.
process.on('unhandledRejection', (reason) =>
  new Logger('DriveAI').error(`Rechazo no manejado: ${reason}`),
);

void bootstrap();
