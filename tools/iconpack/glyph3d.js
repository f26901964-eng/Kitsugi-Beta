// 3B glif motoru — kontrol işaretleri, nesne glifleri ve harf glifleri için ortak hat.
//
// 3dicons `front/gradient` render'ları GERÇEKTEN 3B cam nesnelerdir (hacim,
// kalınlık, üst parlama, temas gölgesi). Düz konturlu glifler bu ailede "boyanmış"
// durur. Bu modül aynı görsel kuralları uygular:
//
//   1. TEMAS GÖLGESİ  — altında yumuşak koyu mor elips
//   2. GÖVDE          — şekil N kez sağ-aşağı kaydırılıp koyu gradyanla doldurulur (hacim)
//   3. ÖN YÜZ         — dikey cam rampası (#F9D2B4 → #7B48AF), kalın yuvarlak kontur
//   4. PAH            — sol-üst kenarda ince açık iç çizgi
//   5. PARLAMA        — üst bantta beyaz gradyan, maskeyle yalnızca gövdeye uygulanır
//   6. AYNA/İÇ ÇİZGİ — dolgulu şekillerde ayrıntı (tuş takımı, kamera merceği…)
//
// 24×24 birim ızgarada çizilir; çağıran taraf 384 px'e render edip kırpar.

const RAMP = ['#FFE3C9', '#F3B094', '#E0859A', '#C25EA2', '#A045B0', '#804CB4'];
const SIDE_MID = '#7A3A92';
const SIDE_DARK = '#4B2060';
const SHADOW = '#2E0B3E';

const PALETTE = { face: RAMP, side: [SIDE_MID, SIDE_DARK], shadow: SHADOW, bevel: '#FFF0E2', gloss: '#FFFFFF' };

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Bir şekli (path veya metin) istenen dolgu/konturla çizer. */
function shape(spec, { fill, stroke, w, opacity = 1 }) {
  const common = `fill="${fill}" stroke="${stroke}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"` +
    (opacity !== 1 ? ` opacity="${opacity}"` : '');
  if (spec.text) {
    const style = `font-family="DejaVu Sans, sans-serif" font-weight="${spec.weight || 700}" font-size="${spec.size || 17}"` +
      (spec.italic ? ' font-style="italic"' : '');
    return `<text x="12" y="${spec.baseline || 18}" text-anchor="middle" ${style} ${common}>${esc(spec.text)}</text>`;
  }
  return `<path d="${spec.d}" ${common}/>`;
}

function defs(spec) {
  const w = spec.w || 3.2;
  const maskShape = shape(spec, { fill: spec.fill ? '#fff' : 'none', stroke: '#fff', w });
  return `<defs>
    <linearGradient id="face" x1="0.08" y1="0" x2="0.2" y2="1">
      ${RAMP.map((c, i) => `<stop offset="${(i / (RAMP.length - 1)).toFixed(2)}" stop-color="${c}"/>`).join('')}
    </linearGradient>
    <linearGradient id="side" x1="0" y1="0" x2="0.35" y2="1">
      <stop offset="0" stop-color="${SIDE_MID}"/><stop offset="1" stop-color="${SIDE_DARK}"/>
    </linearGradient>
    <linearGradient id="glossFade" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.9"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </linearGradient>
    <filter id="soft" x="-45%" y="-45%" width="190%" height="190%">
      <feGaussianBlur stdDeviation="${spec.shadowBlur ?? 1.1}"/>
    </filter>
    <mask id="m" maskUnits="userSpaceOnUse" x="-4" y="-4" width="32" height="32">${maskShape}</mask>
  </defs>`;
}

/** Gövde (gölge hariç): extrusion + ön yüz + pah + ayrıntı + parlama. */
function body(spec) {
  const w = spec.w || 3.2;
  const steps = spec.depth ?? 12;
  const dx = spec.dx ?? 0.26;
  const dy = spec.dy ?? 0.30;

  const layers = [];
  for (let i = steps; i >= 1; i--) {
    layers.push(`<g transform="translate(${(dx * i).toFixed(3)} ${(dy * i).toFixed(3)})">` +
      shape(spec, { fill: spec.fill ? 'url(#side)' : 'none', stroke: 'url(#side)', w }) + '</g>');
  }

  const face = shape(spec, { fill: spec.fill ? 'url(#face)' : 'none', stroke: 'url(#face)', w });
  const bevel = `<g transform="translate(-0.36 -0.40)">` +
    shape(spec, { fill: 'none', stroke: PALETTE.bevel, w: +(w * 0.30).toFixed(2), opacity: 0.55 }) + '</g>';
  const strike = spec.strike
    ? `<path d="M7.1 12.9 H16.9" fill="none" stroke="#FFE9D6" stroke-opacity="0.92" ` +
      `stroke-width="${Math.max(1.3, w * 0.55).toFixed(2)}" stroke-linecap="round"/>`
    : '';
  const detail = spec.detail
    ? `<path d="${spec.detail}" fill="none" stroke="#FFE6D2" stroke-opacity="0.7" ` +
      `stroke-width="${Math.max(1.0, w * 0.36).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/>`
    : '';
  const inner = spec.inner
    ? `<path d="${spec.inner}" fill="none" stroke="#FFF6EC" stroke-opacity="0.8" ` +
      `stroke-width="${Math.max(0.9, w * 0.30).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/>`
    : '';
  const gloss = `<g mask="url(#m)">` +
    `<rect x="-4" y="0.6" width="32" height="10.2" fill="url(#glossFade)" opacity="0.62"/>` +
    `<ellipse cx="8.2" cy="4.8" rx="5.6" ry="2.1" fill="#FFFFFF" opacity="0.40"/>` +
    `<ellipse cx="15.4" cy="8.6" rx="3.2" ry="1.1" fill="#FFFFFF" opacity="0.14"/></g>`;

  return `${layers.join('')}${face}${bevel}${detail}${inner}${strike}${gloss}`;
}

/** Tek glif için tam SVG (24×24, saydam). */
function svg(spec) {
  const shadow = `<ellipse cx="12.8" cy="21.6" rx="${spec.shadowRx ?? 6.4}" ry="${spec.shadowRy ?? 1.7}" ` +
    `fill="${SHADOW}" opacity="0.42" filter="url(#soft)"/>`;
  const g = spec.rot ? `<g transform="rotate(${spec.rot} 12 12)">${body(spec)}</g>` : body(spec);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">${defs(spec)}${shadow}${g}</svg>`;
}

module.exports = { svg, body, defs, shape, PALETTE, RAMP };
