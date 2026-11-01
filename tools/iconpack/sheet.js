// Tüm 3B ikon setinin etiketli önizleme sayfası (docs/ikon-seti-onizleme.png).
const sharp = (() => {
  try { return require('sharp'); } catch (e) {}
  return require(process.env.SHARP_MODULE || '/home/user/.iconwork/node_modules/sharp');
})();
const fs = require('fs');
const path = require('path');

const ICON_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const OUT = path.join(__dirname, '..', '..', 'docs', 'ikon-seti-onizleme.png');

const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const BG = '#0A0D14', CELL_BG = '#10131D', BORDER = '#232A38', T1 = '#F4F7FB', T2 = '#A9B0C2', T3 = '#727A8F';

// anahtar -> Türkçe etiket
const LABELS = {
  account: 'Kitsugi Hesabı', connections: 'Hesap Bağlantıları', palette: 'Görünüm & Tercihler',
  player: 'Oynatıcı', extensions: 'Eklentiler', integrations: 'Entegrasyonlar', database: 'Veri / Yedek',
  storage: 'Depolama', tune: 'İndirme Ayarları', download: 'İndirmeler', history: 'İzleme Geçmişi',
  info: 'Hakkında', feedback: 'Geri Bildirim', warning: 'Hata Kodları', help: 'Yardım',
  memo: 'Not / Metin', play: 'Oynat', pause: 'Duraklat', next: 'Sonraki', rewind: 'Geri sar',
  gestures: 'Hareketler', decoder: 'Kod çözücü', subtitles: 'Altyazılar', audio: 'Ses',
  buttons: 'Özel Buton', code: 'Kod Düzenleyici', advanced: 'Gelişmiş', speed: 'Hız',
  timer: 'Zamanlayıcı', sun: 'Aydınlık', moon: 'Karanlık tema', tv: 'TV / Yayın',
  keyboard: 'Klavye', wrench: 'Araçlar', network: 'Ağ / DNS', sync: 'Eşitleme',
  key: 'Şifre / Anahtar', star: 'Puan / Yıldız', image: 'Görsel', delete: 'Sil',
  eye: 'Görünürlük', heart: 'Favori', clipboard: 'Kopyala', chart: 'İstatistik',
  check_circle: 'Tamamlandı', lock: 'Güvenlik', folder: 'Klasör', bell: 'Bildirim',
  calendar: 'Takvim', sparkles: 'Yenilik', thumbup: 'Beğeni', house: 'Ana sayfa',
  mobile: 'Mobil cihaz', pin: 'Konum',
  nav_explore: 'Keşfet (nav)', nav_list: 'Listem (nav)', nav_search: 'Arama (nav)',
  nav_profile: 'Profil (nav)', nav_settings: 'Ayarlar (nav)',
};

// nav_settings ayrı dosya değil -> advanced görselini kullan
const ORDER = [
  'account', 'connections', 'palette', 'player', 'extensions', 'integrations',
  'database', 'storage', 'tune', 'download', 'history', 'info',
  'feedback', 'warning', 'help', 'memo', 'play', 'pause',
  'next', 'rewind', 'gestures', 'decoder', 'subtitles', 'audio',
  'buttons', 'code', 'advanced', 'speed', 'timer', 'sun',
  'moon', 'tv', 'keyboard', 'wrench', 'network', 'sync',
  'key', 'star', 'image', 'delete', 'eye', 'heart',
  'clipboard', 'chart', 'check_circle', 'lock', 'folder', 'bell',
  'calendar', 'sparkles', 'thumbup', 'house', 'mobile', 'pin',
  'nav_explore', 'nav_list', 'nav_search', 'nav_profile',
];

const COLS = 6, CELL = 220, PAD = 26, HEADER = 150, GRID_TOP = HEADER;
const ROWS = Math.ceil(ORDER.length / COLS);
const FOOTER = 190;
const W = COLS * CELL + PAD * 2;
const H = GRID_TOP + ROWS * CELL + FOOTER;

(async () => {
  const parts = [];
  const comps = [];

  parts.push(`<text x="${PAD}" y="66" fill="${T1}" font-size="40" font-weight="700" font-family="DejaVu Sans, sans-serif">Kitsugi 3B İkon Seti</text>`);
  parts.push(`<text x="${PAD}" y="106" fill="${T2}" font-size="19" font-family="DejaVu Sans, sans-serif">${ORDER.length} ikon · saydam zemin · arka plansız · Microsoft Fluent Emoji 3D (MIT)</text>`);

  ORDER.forEach((key, i) => {
    const x = PAD + (i % COLS) * CELL;
    const y = GRID_TOP + Math.floor(i / COLS) * CELL;
    parts.push(`<rect x="${x + 8}" y="${y + 8}" width="${CELL - 16}" height="${CELL - 16}" rx="24" fill="${CELL_BG}" stroke="${BORDER}"/>`);
    parts.push(`<text x="${x + CELL / 2}" y="${y + CELL - 46}" fill="${T2}" font-size="17" text-anchor="middle" font-family="DejaVu Sans, sans-serif">${esc(LABELS[key] || key)}</text>`);
    comps.push({ key, left: Math.round(x + CELL / 2 - 52), top: y + 26, size: 104 });
  });

  // Alt şerit: satırda gerçek boyut (30dp ~ 2x = 60px)
  const fy = GRID_TOP + ROWS * CELL + 20;
  parts.push(`<text x="${PAD}" y="${fy + 26}" fill="${T3}" font-size="17" font-weight="700" letter-spacing="1.2" font-family="DejaVu Sans, sans-serif">SATIR İÇİNDE (30 dp)</text>`);
  const demo = ['account', 'palette', 'extensions', 'storage', 'history'];
  demo.forEach((key, i) => {
    const cx = PAD + 116 + i * 246;
    const cy = fy + 74;
    parts.push(`<rect x="${cx - 150}" y="${cy - 34}" width="220" height="68" rx="18" fill="${CELL_BG}"/>`);
    parts.push(`<text x="${cx - 84}" y="${cy - 2}" fill="${T1}" font-size="19" font-weight="600" font-family="DejaVu Sans, sans-serif">${esc(LABELS[key])}</text>`);
    parts.push(`<text x="${cx - 84}" y="${cy + 20}" fill="${T3}" font-size="14" font-family="DejaVu Sans, sans-serif">örnek ayar satırı</text>`);
    comps.push({ key, left: cx - 138, top: Math.round(cy - 27), size: 54 });
  });

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${parts.join('\n')}</svg>`;
  const layers = [{ input: Buffer.from(svg), top: 0, left: 0 }];
  for (const c of comps) {
    const buf = await sharp(fs.readFileSync(path.join(ICON_DIR, 'ic_kitsugi_' + c.key + '.png')))
      .resize(c.size, c.size).png().toBuffer();
    layers.push({ input: buf, left: c.left, top: c.top });
  }
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  await sharp({ create: { width: W, height: H, channels: 4, background: BG } })
    .composite(layers).png().toFile(OUT);
  await sharp(OUT).resize(Math.round(W / 2)).png().toFile('/home/user/onizleme_tum_ikonlar.png');
  console.log('yazıldı', OUT, W + 'x' + H);
})();
