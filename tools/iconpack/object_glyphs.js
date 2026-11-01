// Nesne glifleri — 3dicons paketinde karşılığı olmayan ama uygulamada farklı
// işlevleri temsil eden kavramlar (eşitleme, indirme/yükleme, zamanlayıcı, kod,
// TV, etiket, ayar kaydırıcıları). Aynı 3B hattı (glyph3d.js) kullanır; böylece
// konu ikonlarıyla aynı hacim/ışık dilinde durur.
//
// Çıktı: app/src/main/res/drawable-nodpi/ic_kitsugi_obj_<ad>.png (192×192, saydam)
const fs = require('fs');
const path = require('path');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();
const g3d = require(path.join(__dirname, 'glyph3d.js'));

const DST = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const RENDER = 384;
const CANVAS = 192;
const FILL = 0.92;
const ALPHA_FLOOR = 26;

const OBJECTS = {
  // iki yay + oklar: eşitleme / yenileme
  sync: {
    d: 'M5.4 9.6 A6.9 6.9 0 0 1 18.1 8.9 M18.1 8.9 L18.0 5.6 M18.1 8.9 L14.9 8.5 ' +
       'M18.6 14.4 A6.9 6.9 0 0 1 5.9 15.1 M5.9 15.1 L6.0 18.4 M5.9 15.1 L9.1 15.5',
    w: 2.9, depth: 11,
  },
  // üç kaydırıcı: ince ayar / parametreler
  sliders: {
    d: 'M3.8 7.4 H20.2 M3.8 12 H20.2 M3.8 16.6 H20.2',
    detail: 'M9.2 5.6 a1.85 1.85 0 1 1 -0.01 0 Z M15 10.2 a1.85 1.85 0 1 1 -0.01 0 Z ' +
            'M7.6 14.8 a1.85 1.85 0 1 1 -0.01 0 Z',
    w: 2.6, depth: 11,
  },
  // tepsiye inen ok
  download: {
    d: 'M12 3.9 V14.3 M7.5 10.2 L12 14.7 L16.5 10.2 M4.4 18.6 H19.6',
    w: 2.9, depth: 11,
  },
  // tepsiye çıkan ok
  upload: {
    d: 'M12 15.1 V4.6 M7.5 9 L12 4.5 L16.5 9 M4.4 18.6 H19.6',
    w: 2.9, depth: 11,
  },
  // kronometre: gövde + tepe düğmesi + ibre
  timer: {
    d: 'M12 6.6 A6.3 6.3 0 1 1 11.99 6.6 Z M12 6.6 V4.2 M9.7 3.1 H14.3 M12 12.4 V9.1',
    w: 2.7, depth: 12,
  },
  // kod: açılı parantezler
  code: {
    d: 'M9.2 7.4 L4.4 12 L9.2 16.6 M14.8 7.4 L19.6 12 L14.8 16.6',
    w: 3.0, depth: 11,
  },
  // TV / yayın: ekran + ayak
  tv: {
    d: 'M3.9 6.9 H20.1 V16.5 H3.9 Z M9.2 20.2 H14.8',
    detail: 'M6.6 9.6 H17.4', w: 2.5, depth: 12,
  },
  // etiket: delikli etiket gövdesi
  tag: {
    d: 'M4.3 11.3 V4.9 H10.7 L19.2 13.4 L13.4 19.2 Z',
    detail: 'M7.7 8.2 a1.3 1.3 0 1 1 -0.01 0 Z',
    w: 2.6, depth: 12,
  },
  // bulut yedeği: bulut + ok
  backup: {
    d: 'M7.4 17.6 A3.9 3.9 0 0 1 7.6 9.9 A5.2 5.2 0 0 1 17.4 10.7 A3.6 3.6 0 0 1 17.2 17.6 Z ' +
       'M12 15.6 V11.2 M9.9 13.3 L12 11.2 L14.1 13.3',
    w: 2.3, depth: 11,
  },
};

(async () => {
  fs.mkdirSync(DST, { recursive: true });
  let made = 0;
  for (const [name, spec] of Object.entries(OBJECTS)) {
    const svg = g3d.svg({
      d: spec.d,
      fill: !!spec.fill,
      detail: spec.detail,
      inner: spec.inner,
      w: spec.w || 2.8,
      depth: spec.depth || 11,
      dx: 0.24,
      dy: 0.30,
    });
    const raw = await sharp(Buffer.from(svg)).png().toBuffer();
    const trimmed = await sharp(raw).trim({ threshold: 1 }).toBuffer();
    const meta = await sharp(trimmed).metadata();
    const box = Math.round(CANVAS * FILL);
    const maxDim = Math.max(meta.width, meta.height);
    const areaRatio = (meta.width * meta.height) / (maxDim * maxDim);
    const boost = Math.min(1.45, Math.max(1, 1 / Math.sqrt(areaRatio)));
    let scale = (box / maxDim) * boost;
    if (maxDim * scale > CANVAS * 0.99) scale = (CANVAS * 0.99) / maxDim;
    const w = Math.max(1, Math.round(meta.width * scale));
    const h = Math.max(1, Math.round(meta.height * scale));
    const resized = await sharp(trimmed).resize(w, h, { fit: 'fill' }).ensureAlpha().raw().toBuffer();
    for (let i = 3; i < resized.length; i += 4) if (resized[i] < ALPHA_FLOOR) resized[i] = 0;
    const body = await sharp(resized, { raw: { width: w, height: h, channels: 4 } })
      .png({ compressionLevel: 9, palette: true }).toBuffer();
    await sharp({ create: { width: CANVAS, height: CANVAS, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: body, left: Math.round((CANVAS - w) / 2), top: Math.round((CANVAS - h) / 2) }])
      .png({ compressionLevel: 9, palette: true })
      .toFile(path.join(DST, `ic_kitsugi_obj_${name}.png`));
    made += 1;
  }
  console.log(`${made} nesne glifi üretildi -> ${DST}`);
})();
