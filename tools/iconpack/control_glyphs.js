// 3dicons setinde KAPAT / CHEVRON / ARTILI OK / FİLTRE / SIRALAMA / GÖZ gibi
// "kontrol" ikonları yok. Bu betik, setin görsel dilini (dikey renk rampası,
// üst parlama, yumuşak gölge, yuvarlak uçlu kalın çizgi) birebir taklit ederek
// eksik kontrol gliflerini üretir.
//
// Rampa, gerçek 3dicons render'larından ölçüldü (dikey bant ortalamaları):
//   üst #E09C7C → #CC6784 → #B04F9B → #8733AA → #743EB1 → alt #6A51A1
//
// Çıktı: app/src/main/res/drawable-nodpi/ic_kitsugi_glyph_<ad>.png (192×192, saydam)
const fs = require('fs');
const path = require('path');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();

const DST = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const RENDER = 384;
const CANVAS = 128;
const FILL = 0.92;
const ALPHA_FLOOR = 26;

const G_STOPS = [
  [0.00, '#E9A57E'],
  [0.28, '#CF6A86'],
  [0.55, '#B04F9B'],
  [0.78, '#8A35AB'],
  [1.00, '#7043B2'],
];

/** path: 24×24 koordinat uzayında; fill=true ise kapalı şekil dolgulu çizilir. */
const GLYPHS = {
  close: { d: 'M6.2 6.2 L17.8 17.8 M17.8 6.2 L6.2 17.8' },
  minus: { d: 'M5.5 12 H18.5' },
  chevron: { d: 'M6.2 14.8 L12 9.2 L17.8 14.8', rot: { up: 0, down: 180, left: 270, right: 90 } },
  arrow: { d: 'M12 19 V5.6 M7.2 10.4 L12 5.6 L16.8 10.4', rot: { up: 0, down: 180, right: 90, left: 270 } },
  eye: {
    d: 'M2.9 12 C5.6 7.6 8.8 5.7 12 5.7 C15.2 5.7 18.4 7.6 21.1 12 C18.4 16.4 15.2 18.3 12 18.3 C8.8 18.3 5.6 16.4 2.9 12 Z M12 14.6 A2.6 2.6 0 1 1 12 9.4 A2.6 2.6 0 1 1 12 14.6 Z',
  },
  eyeOff: {
    d: 'M2.9 12 C5.6 7.6 8.8 5.7 12 5.7 C15.2 5.7 18.4 7.6 21.1 12 C18.4 16.4 15.2 18.3 12 18.3 C8.8 18.3 5.6 16.4 2.9 12 Z M4 20 L20 4',
  },
  funnel: { d: 'M3.6 5.4 H20.4 L13.9 13.1 V19.6 L10.1 17.4 V13.1 Z', fill: true },
  sort: { d: 'M7 19.5 V4.8 M4.1 16.6 L7 19.5 L9.9 16.6 M13 6.5 H20 M13 11.5 H18.4 M13 16.5 H16.8' },
  grid: {
    d: 'M4.6 4.6 h5.1 v5.1 h-5.1 z M14.3 4.6 h5.1 v5.1 h-5.1 z M4.6 14.3 h5.1 v5.1 h-5.1 z M14.3 14.3 h5.1 v5.1 h-5.1 z',
    fill: true,
  },
  list: { d: 'M4.6 7.2 H19.4 M4.6 12 H19.4 M4.6 16.8 H19.4' },
  stream: { d: 'M4.6 8.4 H19.4 M4.6 15.6 H19.4' },
  carousel: { d: 'M8.2 5.8 h7.6 v12.4 h-7.6 z', fill: true, detail: 'M4.8 9.4 V14.6 M19.2 9.4 V14.6' },
  more: { d: 'M12 6.4 v0.02 M12 12 v0.02 M12 17.6 v0.02', width: 3.6 },
  external: { d: 'M13.6 4.8 H19.2 V10.4 M19 5 L11.2 12.8 M17.8 13.6 V18.6 a1.2 1.2 0 0 1 -1.2 1.2 H5.8 a1.2 1.2 0 0 1 -1.2 -1.2 V7.8 a1.2 1.2 0 0 1 1.2 -1.2 H11' },
  fullscreen: { d: 'M4 9.2 V4 h5.2 M14.8 4 H20 v5.2 M20 14.8 V20 h-5.2 M9.2 20 H4 v-5.2' },
  crop: { d: 'M7.2 3.4 v12.2 a1.2 1.2 0 0 0 1.2 1.2 h12.2 M3.4 7.2 h12.2 a1.2 1.2 0 0 1 1.2 1.2 v12.2' },
  rotate: { d: 'M19.6 12 a7.6 7.6 0 1 1 -2.3 -5.4 M19.8 3.6 v4.6 h-4.6' },
  aspect: { d: 'M3.8 7.8 h16.4 v8.4 h-16.4 z', fill: true, detail: 'M8.4 8.4 V15.6 M15.6 8.4 V15.6' },
  blur: { d: 'M7 7 v0.02 M12 7 v0.02 M17 7 v0.02 M7 12 v0.02 M12 12 v0.02 M17 12 v0.02 M7 17 v0.02 M12 17 v0.02 M17 17 v0.02', width: 3.2 },
  alignLeft: { d: 'M4 6.8 H20 M4 12 H13.6 M4 17.2 H17.2' },
  alignCenter: { d: 'M4 6.8 H20 M7.2 12 H16.8 M5.4 17.2 H18.6' },
  alignJustify: { d: 'M4 6.8 H20 M4 12 H20 M4 17.2 H20' },
  border: { d: 'M4 4 h16 v16 h-16 z M8.4 8.4 h7.2 v7.2 h-7.2 z' },
  selectAll: { d: 'M4 4 h16 v16 h-16 z', dash: '2.6 2.2' },
  drag: { d: 'M8.5 9.4 H15.5 M8.5 14.6 H15.5', width: 3.0 },
  density: { d: 'M4 9 H20 M4 12 H20 M4 15 H20', width: 2.4 },
  keyboard: {
    d: 'M3.4 8 h17.2 v8 h-17.2 z', fill: true,
    detail: 'M7 11 v0.02 M10.2 11 v0.02 M13.4 11 v0.02 M16.6 11 v0.02 M7.8 14 H16.2',
  },
  input: { d: 'M6.4 7.8 h11.2 v4.4 l-3.2 4.4 h-4.8 l-3.2 -4.4 z', fill: true, detail: 'M12 4.2 V7.4' },
  doubleArrow: { d: 'M8.4 7.6 L4.2 12 L8.4 16.4 M15.6 7.6 L19.8 12 L15.6 16.4' },
  home: { d: 'M3.8 10.8 L12 4.2 L20.2 10.8 V19.8 h-5.6 v-5.2 h-5.2 v5.2 H3.8 z', fill: true },
  leaf: { d: 'M4.8 19.2 C4.8 11 11 4.8 19.2 4.8 C19.2 13 13 19.2 4.8 19.2 Z M4.8 19.2 L13.4 10.6', fill: true },
  snow: { d: 'M12 3.4 V20.6 M4.4 7.7 L19.6 16.3 M19.6 7.7 L4.4 16.3' },
  cake: { d: 'M4.6 20 V12.6 h14.8 V20 z', fill: true, detail: 'M9 12.4 V9.2 M15 12.4 V9.2 M12 12.4 V7.6' },
  camera: { d: 'M3.8 8.4 h4.4 l2 -2.4 h3.6 l2 2.4 h4.4 v10 h-16.4 z', fill: true, detail: 'M12 16.6 A3.2 3.2 0 1 1 12 10.2 A3.2 3.2 0 1 1 12 16.6 Z' },
};

/** Harf glifleri (B, I, A, #, S) — setin rengiyle çizilir. */
const LETTERS = {
  textBold: { ch: 'B', weight: 700, size: 17 },
  textItalic: { ch: 'I', weight: 700, size: 17, italic: true },
  textTitle: { ch: 'A', weight: 700, size: 17 },
  textHash: { ch: '#', weight: 700, size: 16 },
  textStrike: { ch: 'S', weight: 700, size: 17, strike: true },
  textQuote: { ch: '❝', weight: 400, size: 15 },
};

const gradientDefs = (id, extra = '') => `
  <linearGradient id="${id}" x1="0" y1="0" x2="0.18" y2="1">${G_STOPS.map(([o, c]) => `<stop offset="${o.toFixed(2)}" stop-color="${c}"/>`).join('')}</linearGradient>
  <linearGradient id="hi" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.9"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient>
  <filter id="sh" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="0.5"/></filter>
  <mask id="topMask"><rect x="0" y="0" width="24" height="11.4" fill="#fff"/></mask>
  ${extra}`;

function svgFor(spec) {
  const width = spec.width || 2.75;
  const rotate = spec.rotate || 0;
  const dash = spec.dash ? ` stroke-dasharray="${spec.dash}"` : '';
  const body = (stroke, w, extra = '') =>
    `<path d="${spec.d}" fill="none" stroke="${stroke}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"${dash} ${extra}/>`;
  const fillBody = (fill, extra = '') =>
    `<path d="${spec.d}" fill="${fill}" stroke="${fill}" stroke-width="${width}" stroke-linejoin="round" ${extra}/>`;

  const inner = `
    <g transform="translate(12 12) rotate(${rotate}) translate(-12 -12)">
      <g filter="url(#sh)" opacity="0.5" transform="translate(0 0.85)">
        ${spec.fill ? fillBody('#3A0F4C') : body('#3A0F4C', width + 0.35)}
      </g>
      ${spec.fill ? fillBody('#5C2481') : body('#5C2481', width + 0.75)}
      ${spec.fill ? fillBody('url(#g)') : body('url(#g)', width)}
      ${spec.detail ? `<path d="${spec.detail}" fill="none" stroke="#FFDCC8" stroke-opacity="0.62" stroke-width="${Math.max(1.05, width * 0.42)}" stroke-linecap="round" stroke-linejoin="round"/>` : ''}
      <g mask="url(#topMask)">
        ${spec.fill ? fillBody('url(#hi)', 'opacity="0.42"').replace(/stroke="url\(#hi\)"/, 'stroke="none"') : body('url(#hi)', Math.max(0.9, width * 0.36), 'opacity="0.9"')}
      </g>
    </g>`;

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${RENDER}" height="${RENDER}" viewBox="0 0 24 24">
  <defs>${gradientDefs('g')}</defs>${inner}</svg>`;
}

function svgForLetter(spec) {
  const style = `font-family="DejaVu Sans, sans-serif" font-weight="${spec.weight}" font-size="${spec.size}" ${spec.italic ? 'font-style="italic"' : ''}`;
  const t = (fill, y = 0, extra = '') =>
    `<text x="12" y="${16.6 + y}" text-anchor="middle" fill="${fill}" ${style} ${extra}>${spec.ch}</text>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${RENDER}" height="${RENDER}" viewBox="0 0 24 24">
  <defs>${gradientDefs('g')}</defs>
  <g filter="url(#sh)" opacity="0.5" transform="translate(0 0.7)">${t('#3A0F4C')}</g>
  ${t('#5C2481', 0.12)}
  ${t('url(#g)')}
  <g mask="url(#topMask)">${t('url(#hi)', 0, 'opacity="0.55"')}</g>
  </svg>`;
}

(async () => {
  fs.mkdirSync(DST, { recursive: true });
  let made = 0;
  const jobs = [];
  for (const [name, spec] of Object.entries(GLYPHS)) {
    if (spec.rot) for (const [dir, deg] of Object.entries(spec.rot)) jobs.push([`${name}_${dir}`, { ...spec, rotate: deg }]);
    else jobs.push([name, spec]);
  }
  for (const [name, spec] of Object.entries(LETTERS)) jobs.push([name, spec, true]);

  for (const [name, spec, isLetter] of jobs) {
    const svg = isLetter ? svgForLetter(spec) : svgFor(spec);
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
      .png({ compressionLevel: 9, palette: true })
      .toBuffer();
    await sharp({ create: { width: CANVAS, height: CANVAS, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: body, left: Math.round((CANVAS - w) / 2), top: Math.round((CANVAS - h) / 2) }])
      .png({ compressionLevel: 9, palette: true })
      .toFile(path.join(DST, `ic_kitsugi_glyph_${name}.png`));
    made += 1;
  }
  console.log(`${made} kontrol glifi üretildi -> ${DST}`);
})();
