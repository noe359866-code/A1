import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  normalizeOrigin, validatePrepare, sanitizeHeaders, planConversion, ffmpegArgs, parseConfig
} from './bridge.mjs';

const listen = server => new Promise(r => server.listen(0, '127.0.0.1', () => r(server.address().port)));

test('orígenes y cabeceras se validan antes de tocar FFmpeg', () => {
  assert.equal(normalizeOrigin('https://cdn.example/'), 'https://cdn.example');
  assert.equal(normalizeOrigin('https://cdn.example/path'), null);
  assert.equal(normalizeOrigin('https://user:pass@cdn.example'), null);
  const input = validatePrepare({ url: 'https://cdn.example/a.mkv', headers: { Referer: 'https://a.example', Cookie: 'x', 'X-Bad': 'a\r\nb' } }, ['https://cdn.example']);
  assert.equal(input.url, 'https://cdn.example/a.mkv');
  assert.deepEqual(input.headers, { Referer: 'https://a.example' });
  assert.throws(() => validatePrepare({ url: 'https://other.example/a.mkv' }, ['https://cdn.example']), /origen no permitido/);
  assert.deepEqual(sanitizeHeaders({ Host: 'x', 'X-Ok': '1' }), { 'X-Ok': '1' });
});

test('el plan copia H264/AAC, convierte audio y exige opción para vídeo raro', () => {
  const h264aac = { streams: [{ codec_type: 'video', codec_name: 'h264' }, { codec_type: 'audio', codec_name: 'aac' }] };
  const h264eac3 = { streams: [{ codec_type: 'video', codec_name: 'h264' }, { codec_type: 'audio', codec_name: 'eac3' }] };
  const hevc = { streams: [{ codec_type: 'video', codec_name: 'hevc' }, { codec_type: 'audio', codec_name: 'aac' }] };
  assert.equal(planConversion(h264aac).action, 'remux');
  assert.equal(planConversion(h264eac3).action, 'transcode');
  assert.equal(planConversion(h264eac3).audioCodec, 'aac');
  assert.throws(() => planConversion(hevc), /video-transcode/);
  assert.equal(planConversion(hevc, { videoTranscode: true }).videoCodec, 'libx264');
  const args = ffmpegArgs('https://cdn.example/a.mkv', { Referer: 'https://a.example' }, planConversion(h264eac3), '/tmp/job');
  assert.ok(args.includes('-c:a'));
  assert.ok(args.includes('aac'));
  assert.ok(args.includes('-hls_segment_filename'));
  assert.ok(args.some(a => a.endsWith('playlist.m3u8')));
});

test('la configuración exige allowlist y token fuera de loopback', () => {
  assert.throws(() => parseConfig([]), /allow-origin/);
  assert.throws(() => parseConfig(['--allow-origin', 'https://cdn.example', '--host', '0.0.0.0']), /token/);
  const config = parseConfig(['--allow-origin', 'https://cdn.example', '--host', '0.0.0.0', '--token', 's3cr3t']);
  assert.deepEqual(config.allowlist, ['https://cdn.example']);
  assert.equal(config.token, 's3cr3t');
});

test('HTTP /health, /prepare y /media usan ffprobe/ffmpeg fixture', async () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'omni-ffmpeg-'));
  const probe = path.join(dir, 'probe.mjs');
  const ffmpeg = path.join(dir, 'ffmpeg.mjs');
  writeFileSync(probe, `
const a = process.argv.slice(2);
if (a.includes('-version')) { console.log('ffprobe fixture 1.0'); process.exit(0); }
const url = a[a.indexOf('-i') + 1] || '';
const audio = url.includes('eac3') ? 'eac3' : 'aac';
console.log(JSON.stringify({ streams: [{ codec_type: 'video', codec_name: 'h264' }, { codec_type: 'audio', codec_name: audio }] }));
`);
  writeFileSync(ffmpeg, `
import { writeFileSync } from 'node:fs';
import path from 'node:path';
const a = process.argv.slice(2);
if (a.includes('-version')) { console.log('ffmpeg fixture 1.0'); process.exit(0); }
const out = a[a.length - 1];
const dir = path.dirname(out);
writeFileSync(path.join(dir, 'seg_00000.ts'), Buffer.from([0x47, 0x40, 0x00, 0x10]));
writeFileSync(out, '#EXTM3U\\n#EXT-X-TARGETDURATION:4\\n#EXTINF:4.0,\\nseg_00000.ts\\n');
`);
  const probeSh = path.join(dir, 'ffprobe.sh');
  const ffmpegSh = path.join(dir, 'ffmpeg.sh');
  writeFileSync(probeSh, `#!/bin/sh\nexec "${process.execPath}" "${probe}" "$@"\n`, { mode: 0o755 });
  writeFileSync(ffmpegSh, `#!/bin/sh\nexec "${process.execPath}" "${ffmpeg}" "$@"\n`, { mode: 0o755 });
  const free = net.createServer(); const port = await listen(free); await new Promise(r => free.close(r));
  const bridgeFile = fileURLToPath(new URL('./bridge.mjs', import.meta.url));
  const child = spawn(process.execPath, [bridgeFile, '--host', '127.0.0.1', '--port', String(port),
    '--allow-origin', 'https://cdn.example', '--token', 's3cr3t', '--ffprobe', probeSh, '--ffmpeg', ffmpegSh,
    '--probe-timeout', '2000', '--ready-timeout', '2000'], { stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('arranque lento')), 5000);
      child.stdout.on('data', b => { if (String(b).includes('Omni FFmpeg bridge')) { clearTimeout(t); resolve(); } });
      child.once('exit', c => { clearTimeout(t); reject(new Error('exit ' + c)); });
    });
    const base = `http://127.0.0.1:${port}`;
    const auth = { Authorization: 'Bearer s3cr3t', 'Content-Type': 'application/json' };
    assert.equal((await fetch(base + '/health')).status, 401);
    const health = await fetch(base + '/health', { headers: auth }).then(r => r.json());
    assert.equal(health.ok, true);
    const prepared = await fetch(base + '/prepare', { method: 'POST', headers: auth, body: JSON.stringify({ url: 'https://cdn.example/eac3.mkv', reason: 'ERROR_CODE_DECODER_INIT_FAILED' }) }).then(r => r.json());
    assert.equal(prepared.action, 'transcode');
    assert.equal(prepared.format, 'hls');
    assert.match(prepared.url, /^\/media\/[a-f0-9]{32}\/playlist\.m3u8$/);
    const playlist = await fetch(base + prepared.url, { headers: auth });
    assert.equal(playlist.status, 200);
    assert.match(await playlist.text(), /seg_00000\.ts/);
    const segment = await fetch(base + prepared.url.replace('playlist.m3u8', 'seg_00000.ts'), { headers: auth });
    assert.equal(segment.status, 200);
    assert.equal((await fetch(base + prepared.url.replace('playlist.m3u8', '../playlist.m3u8'), { headers: auth })).status, 404);
    const forbidden = await fetch(base + '/prepare', { method: 'POST', headers: auth, body: JSON.stringify({ url: 'https://other.example/a.mkv' }) });
    assert.equal(forbidden.status, 403);
    const bad = await fetch(base + '/prepare', { method: 'POST', headers: auth, body: '{' });
    assert.equal(bad.status, 400);
    const large = await fetch(base + '/prepare', { method: 'POST', headers: auth, body: 'x'.repeat(70000) });
    assert.equal(large.status, 413);
  } finally {
    child.kill('SIGTERM');
    await new Promise(r => child.once('exit', r));
    rmSync(dir, { recursive: true, force: true });
  }
});
