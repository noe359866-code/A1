import { createServer } from './server.js';
import { ADDON_NAME, ADDON_VERSION, ADDON_TAGLINE } from './manifest.js';

const port = Number(process.env.PORT || process.env.OMNI_PORT || 7000);
const host = process.env.HOST || '0.0.0.0';

const server = createServer();
server.listen(port, host, () => {
  console.log(`${ADDON_NAME} v${ADDON_VERSION} · ${ADDON_TAGLINE}`);
  console.log(`Escuchando en http://${host}:${port}`);
  console.log(`  Manifiesto:  /manifest.json`);
  console.log(`  Configurar:  /configure`);
  console.log(`  Logo:        /logo.png`);
  console.log(`  Estado:      /api/status`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
  });
}
