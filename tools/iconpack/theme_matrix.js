// Tema / vurgu rengi uyum sayfası: ikonlar koyu, AMOLED ve açık temada;
// ardından 6 farklı vurgu renginde (seçili durum hâlesi) nasıl durduğunu gösterir.
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || '/home/user/.iconwork/node_modules/sharp');
})();
const fs = require('fs');
const path = require('path');
const { KEYMAP } = require('./icons.map.js');

const ICON_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const OUT = '/home/user/ikon_tema_uyumu.png';
const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const fileFor = (key) => path.join(ICON_DIR, 'ic_kitsugi_' + (KEYMAP[key] || key).replace(/-/g, '_') + '.png');

const ROWS = [
  ['account', 'Kitsugi Hesabı'],
  ['palette', 'Görünüm & Tercihler'],
  ['download', 'İndirmeler'],
  ['history', 'İzleme Geçmişi'],
];
const NAV = ['nav_explore', 'nav_list', 'nav_search', 'nav_profile', 'nav_settings'];

const ACCENTS = [
  ['Mint', '#C8F4EF', '#8FE3D6'],
  ['Pembe', '#F472B6', '#FB7185'],
  ['Mor', '#A78BFA', '#8B5CF6'],
  ['Mavi', '#60A5FA', '#38BDF8'],
  ['Yeşil', '#4ADE80', '#22C55E'],
  ['Turuncu', '#FB923C', '#F97316'],
];

const W = 1440;
const PAD = 48;

const svgHeader = (inner, h) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${h}">
<defs>
${ACCENTS.map((a, i) => `<radialGradient id="glow${i}"><stop offset="0" stop-color="${a[1]}" stop-opacity="0.55"/><stop offset="0.55" stop-color="${a[1]}" stop-opacity="0.18"/><stop offset="1" stop-color="${a[1]}" stop-opacity="0"/></radialGradient>`).join('\n')}
<radialGradient id="shade"><stop offset="0" stop-color="#0B1020" stop-opacity="0.26"/><stop offset="0.6" stop-color="#0B1020" stop-opacity="0.10"/><stop offset="1" stop-color="#0B1020" stop-opacity="0"/></radialGradient>
</defs>
${inner}</svg>`;

const FONT = 'DejaVu Sans, sans-serif';

(async () => {
  const layers = [];
  const comps = [];
  const push = (file, left, top, size) => comps.push({ file, left: Math.round(left), top: Math.round(top), size: Math.round(size) });

  let y = 0;
  const parts = [];

  // ── Başlık
  parts.push(`<rect x="0" y="0" width="${W}" height="120" fill="#080A10"/>`);
  parts.push(`<text x="${PAD}" y="62" fill="#F4F7FB" font-size="34" font-weight="700" font-family="${FONT}">İkon seti × tema &amp; renk seçimi</text>`);
  parts.push(`<text x="${PAD}" y="94" fill="#A9B0C2" font-size="17" font-family="${FONT}">Aynı 3B set: koyu tema · AMOLED · açık tema · 6 vurgu rengi (gradyan dâhil) — plaka yok</text>`);
  y = 120;

  // ── Koyu tema
  const darkTop = y + 16;
  parts.push(`<rect x="0" y="${y}" width="${W}" height="330" fill="#080A10"/>`);
  parts.push(`<text x="${PAD}" y="${y + 42}" fill="#727A8F" font-size="15" font-weight="700" letter-spacing="1.4" font-family="${FONT}">KOYU TEMA · zemin #080A10 · kart #10131D</text>`);
  ROWS.forEach(([key, label], i) => {
    const cy = darkTop + 34 + i * 70;
    parts.push(`<rect x="${PAD}" y="${cy - 28}" width="${W - PAD * 2}" height="60" rx="16" fill="#10131D" stroke="#252B3A"/>`);
    parts.push(`<text x="${PAD + 66}" y="${cy + 1}" fill="#F4F7FB" font-size="18" font-weight="600" font-family="${FONT}">${esc(label)}</text>`);
    parts.push(`<text x="${PAD + 66}" y="${cy + 21}" fill="#727A8F" font-size="12" font-family="${FONT}">satır açıklaması</text>`);
    push(fileFor(key), PAD + 14, cy - 21, 42);
  });
  y += 330;

  // ── AMOLED
  parts.push(`<rect x="0" y="${y}" width="${W}" height="330" fill="#000000"/>`);
  parts.push(`<text x="${PAD}" y="${y + 42}" fill="#726E6E" font-size="15" font-weight="700" letter-spacing="1.4" font-family="${FONT}">AMOLED · zemin #000000 · kart #080808</text>`);
  ROWS.forEach(([key, label], i) => {
    const cy = y + 74 + i * 70;
    parts.push(`<rect x="${PAD}" y="${cy - 28}" width="${W - PAD * 2}" height="60" rx="16" fill="#080808" stroke="#1F1F1F"/>`);
    parts.push(`<text x="${PAD + 66}" y="${cy + 1}" fill="#F4F7FB" font-size="18" font-weight="600" font-family="${FONT}">${esc(label)}</text>`);
    parts.push(`<text x="${PAD + 66}" y="${cy + 21}" fill="#727A8F" font-size="12" font-family="${FONT}">satır açıklaması</text>`);
    push(fileFor(key), PAD + 14, cy - 21, 42);
  });
  y += 330;

  // ── Açık tema (gölge destekli)
  parts.push(`<rect x="0" y="${y}" width="${W}" height="330" fill="#F3F4F6"/>`);
  parts.push(`<text x="${PAD}" y="${y + 42}" fill="#6B7280" font-size="15" font-weight="700" letter-spacing="1.4" font-family="${FONT}">AÇIK TEMA · zemin #F3F4F6 · kart #FFFFFF · ikon arkasında yumuşak gölge</text>`);
  ROWS.forEach(([key, label], i) => {
    const cy = y + 74 + i * 70;
    parts.push(`<rect x="${PAD}" y="${cy - 28}" width="${W - PAD * 2}" height="60" rx="16" fill="#FFFFFF" stroke="#E5E7EB"/>`);
    parts.push(`<text x="${PAD + 66}" y="${cy + 1}" fill="#111827" font-size="18" font-weight="600" font-family="${FONT}">${esc(label)}</text>`);
    parts.push(`<text x="${PAD + 66}" y="${cy + 21}" fill="#9CA3AF" font-size="12" font-family="${FONT}">satır açıklaması</text>`);
    parts.push(`<circle cx="${PAD + 35}" cy="${cy}" r="23" fill="url(#shade)"/>`);
    push(fileFor(key), PAD + 14, cy - 21, 42);
  });
  y += 330;

  // ── Vurgu renkleri
  parts.push(`<rect x="0" y="${y}" width="${W}" height="340" fill="#080A10"/>`);
  parts.push(`<text x="${PAD}" y="${y + 42}" fill="#727A8F" font-size="15" font-weight="700" letter-spacing="1.4" font-family="${FONT}">VURGU UYUMU · seçili sekmede vurgu hâlesi (kullanıcı ne seçerse o)</text>`);
  const cellW = Math.floor((W - PAD * 2) / ACCENTS.length);
  ACCENTS.forEach(([name, c1, c2], i) => {
    const cx = PAD + i * cellW;
    const top = y + 62;
    parts.push(`<rect x="${cx}" y="${top}" width="${cellW - 22}" height="212" rx="20" fill="#0E1119" stroke="#1E2433"/>`);
    // hâle + nav ikonu
    const navKey = NAV[i % NAV.length];
    parts.push(`<circle cx="${cx + (cellW - 22) / 2}" cy="${top + 74}" r="46" fill="url(#glow${i})"/>`);
    push(fileFor(navKey), cx + (cellW - 22) / 2 - 27, top + 47, 54);
    parts.push(`<rect x="${cx + 22}" y="${top + 138}" width="${cellW - 66}" height="10" rx="5" fill="url(#acc${i})"/>`);
    parts.push(`<text x="${cx + 22}" y="${top + 176}" fill="#F4F7FB" font-size="16" font-weight="600" font-family="${FONT}">${esc(name)}</text>`);
    parts.push(`<text x="${cx + 22}" y="${top + 196}" fill="#727A8F" font-size="12" font-family="${FONT}">${c1} → ${c2}</text>`);
  });
  // gradyan tanımları
  const accDefs = ACCENTS.map((a, i) => `<linearGradient id="acc${i}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="${a[1]}"/><stop offset="1" stop-color="${a[2]}"/></linearGradient>`).join('');
  parts[0] = parts[0]; // sıra korunur
  const defsExtra = `<defs>${accDefs}</defs>`;
  y += 340;

  // ── Not
  parts.push(`<rect x="0" y="${y}" width="${W}" height="96" fill="#0A0D14"/>`);
  parts.push(`<text x="${PAD}" y="${y + 40}" fill="#A9B0C2" font-size="15" font-family="${FONT}">Kural: 3B görsel yalnızca "konu" ikonlarında (satır, sekme, gezinme, durum). Kapat / geri / onay / ok gibi</text>`);
  parts.push(`<text x="${PAD}" y="${y + 64}" fill="#727A8F" font-size="15" font-family="${FONT}">kontrol glifleri bilinçli olarak 2B çizgi kalır; 20 dp altındaki boyutlarda 3B okunmaz.</text>`);
  y += 96;

  const svg = svgHeader(parts.join('\n'), y).replace('<defs>', defsExtra + '<defs>');
  layers.push({ input: Buffer.from(svg), top: 0, left: 0 });
  for (const c of comps) {
    layers.push({ input: await sharp(fs.readFileSync(c.file)).resize(c.size, c.size).png().toBuffer(), left: c.left, top: c.top });
  }
  await sharp({ create: { width: W, height: y, channels: 4, background: '#080A10' } }).composite(layers).png().toFile(OUT);
  console.log('yazıldı', OUT, W + 'x' + y);
})();
