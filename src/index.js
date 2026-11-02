import { createServer } from './server.js';
import { isDemo } from './lib/demo.js';

const port = Number(process.env.PORT || process.env.OMNI_PORT || 7000);
const host = process.env.HOST || '0.0.0.0';

const server = createServer();
server.listen(port, host, () => {
  console.log(`Omni Index escuchando en http://${host}:${port}`);
  console.log(`  Manifiesto:  /manifest.json`);
  console.log(`  Configurar:  /configure`);
  console.log(`  Estado:      /api/status`);
  if (isDemo()) console.log('  Modo demo activo: las respuestas de las APIs son sintéticas (sin red).');
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
  });
}
