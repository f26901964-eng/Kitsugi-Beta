// Fluent 3D PNG'lerini Android drawable-nodpi'ye normalize eder.
// - saydam kenarları kırpar (trim)
// - her ikonu aynı optik kutuya oturtur (192px tuval, %92 doldurma)
// sharp, tools/iconpack içinde `npm install sharp` ile kurulur; yoksa geliştirme
// ortamındaki ortak kuruluma düşer.
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || '/home/user/.iconwork/node_modules/sharp');
})();
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const SRC = path.join(ROOT, 'raw');
const DST = path.join(ROOT, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const CANVAS = 192;
const FILL = 0.92;
// Koyu temada kaybolan varlıklar için hafif parlaklık düzeltmesi (optik dengeleme).
const BRIGHTEN = { nav_profile: 1.32, decoder: 1.10, player: 1.12 };

(async () => {
  fs.mkdirSync(DST, { recursive: true });
  const files = fs.readdirSync(SRC).filter((f) => f.endsWith('.png'));
  let total = 0;
  for (const f of files) {
    const key = path.basename(f, '.png');
    const input = path.join(SRC, f);
    const bright = BRIGHTEN[key] || 1;
    let pipe = sharp(input);
    if (bright !== 1) pipe = pipe.modulate({ brightness: bright });
    const trimmed = await pipe.trim({ threshold: 1 }).toBuffer();
    const meta = await sharp(trimmed).metadata();
    const box = Math.round(CANVAS * FILL);
    const scale = box / Math.max(meta.width, meta.height);
    const w = Math.max(1, Math.round(meta.width * scale));
    const h = Math.max(1, Math.round(meta.height * scale));
    const body = await sharp(trimmed)
      .resize(w, h, { fit: 'fill' })
      .png({ compressionLevel: 9, palette: true })
      .toBuffer();
    const out = path.join(DST, 'ic_kitsugi_' + key + '.png');
    await sharp({
      create: { width: CANVAS, height: CANVAS, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
    })
      .composite([{ input: body, left: Math.round((CANVAS - w) / 2), top: Math.round((CANVAS - h) / 2) }])
      .png({ compressionLevel: 9, palette: true })
      .toFile(out);
    const size = fs.statSync(out).size;
    total += size;
    console.log(key.padEnd(14), String(size).padStart(6) + ' B');
  }
  console.log('\n' + files.length + ' ikon, toplam ' + (total / 1024).toFixed(0) + ' KB -> ' + DST);
})();
