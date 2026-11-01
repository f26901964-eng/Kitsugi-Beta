// 55 ikonun TEK TEK kurulmuş 3B kompozisyonları.
//
// Her ikon kendi ilkellerinden oluşuyor: gövde, kapak, çerçeve, sap, mercek,
// ayak, kadran, ibre… Parçalar gerçek 3B hacimler; birleşim yerleri gerektiğinde
// yumuşak kaynaşma (k), gerektiğinde keskin ek yeri ile kuruluyor. Renk ekseni
// (dirDeg), rampa başlangıcı (phase) ve malzeme ailesi ikon başına farklı.
//
// Bu dosya yalnızca GEOMETRİ + MALZEME tanımlar; render scene3d.js'te.

const S = require('./scene3d.js');
const { rotator, clamp } = S;

// ── yerleştirme yardımcıları ───────────────────────────────────────────────
function place(o, local) {
  const rot = o.rot ? rotator(o.rot[0] || 0, o.rot[1] || 0, o.rot[2] || 0) : null;
  const cx = o.cx || 0, cy = o.cy || 0, cz = o.cz || 0;
  const f = rot
    ? (x, y, z) => {
      const [X, Y, Z] = rot(x - cx, y - cy, z - cz);
      return local(X, Y, Z);
    }
    : (x, y, z) => local(x - cx, y - cy, z - cz);
  const p = { f, mat: o.mat || 'gloss' };
  if (o.k) p.k = o.k;
  if (o.sub) p.sub = true;
  if (o.col) p.col = o.col;
  return p;
}
// yuvarlatılmış kutu
const box = (cx, cy, cz, hx, hy, hz, r, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => S.sdRoundBox(x, y, z, hx, hy, hz, r));
// küre / elipsoid
const sph = (cx, cy, cz, r, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => S.sdSphere(x, y, z, r));
const ell = (cx, cy, cz, rx, ry, rz, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => {
  const k = Math.min(rx, ry, rz);
  const d = Math.hypot(x / rx, y / ry, z / rz) - 1;
  return d * k;
});
// kapsül
const cap = (x1, y1, z1, x2, y2, z2, r, o = {}) => place({ ...o }, (x, y, z) => S.sdCapsule(x, y, z, x1, y1, z1, x2, y2, z2, r));
// silindir (axis: 'x'|'y'|'z')
const cyl = (axis, cx, cy, cz, r, h, rr, o = {}) => {
  const local = axis === 'y' ? (x, y, z) => S.sdCylY(x, y, z, r, h, rr)
    : axis === 'z' ? (x, y, z) => S.sdCylZ(x, y, z, r, h, rr)
      : (x, y, z) => S.sdCylZ(z, x, y, r, h, rr);
  return place({ cx, cy, cz, ...o }, local);
};
// torus (axis: eksen adı)
const tor = (axis, cx, cy, cz, R, r, o = {}) => {
  const local = axis === 'z' ? (x, y, z) => S.sdTorusZ(x, y, z, R, r)
    : axis === 'y' ? (x, y, z) => S.sdTorusY(x, y, z, R, r)
      : (x, y, z) => S.sdTorusX(x, y, z, R, r);
  return place({ cx, cy, cz, ...o }, local);
};
// koni: taban y=0'da, tepe y=h'de
const cone = (cx, cy, cz, r1, r2, h, rr, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => S.sdCone(x, y, z, r1, r2, h, rr));
// 2B çokgeni z boyunca ekstrüde et
const poly = (pts, cz, hz, r, o = {}) => place({ cz, ...o }, (x, y, z) => S.extrudeZ(x, y, z, pts, hz, r) - r * 0);

const D2R = Math.PI / 180;
const rot2 = (deg, x, y) => {
  const a = deg * D2R, c = Math.cos(a), s = Math.sin(a);
  return [x * c - y * s, x * s + y * c];
};
// yay ucuna teğet oturan ok başı (taban y=0, tepe y=h; +y ekseni yön)
function tangentHead(cx, cy, tangent, r1, h, rr, o = {}) {
  const [tx, ty] = tangent;
  const ang = Math.atan2(-tx, ty);
  return cone(cx, cy, 0, r1, 0.28, h, rr, { rot: [0, 0, ang], ...o });
}

// yay üzerinde kapsül zinciri
function arc(radius, a0, a1, r, seg, o = {}) {
  // segment sayısı: kiriş aralığı yarıçapın %55'ini geçmesin → pürüzsüz yay
  const len = Math.abs(a1 - a0) * radius;
  seg = Math.max(seg, Math.min(48, Math.ceil(len / (r * 0.55))));
  const out = [];
  for (let i = 0; i < seg; i++) {
    const t0 = a0 + (a1 - a0) * (i / seg);
    const t1 = a0 + (a1 - a0) * ((i + 1) / seg);
    const x0 = Math.cos(t0) * radius, y0 = Math.sin(t0) * radius;
    const x1 = Math.cos(t1) * radius, y1 = Math.sin(t1) * radius;
    out.push(cap(x0, y0, 0, x1, y1, 0, r, { ...o, k: o.k === undefined ? 1.0 : o.k }));
  }
  return out;
}

// ── ikon başına renk ekseni (isimden türetilen, tekrar etmeyen dağılım) ────
function sig(name) {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 131 + name.charCodeAt(i)) % 100000;
  return {
    dirDeg: 222 + (h % 96),                // sıcak uç üstte, çivit altta; eğim ikon başına
    phase: ((h >> 5) % 16) / 100,
    span: 0.86 + ((h >> 9) % 12) / 100,
  };
}

const BB = [-10.8, 10.8, -10.8, 10.8, -5, 5];
// nesne glifleri (obje öneki ile kaydedilir)
const OBJ_NAMES = new Set(['sync', 'sliders', 'download', 'upload', 'timer', 'code', 'tv', 'tag', 'backup']);

// ══════════════════════════════════════════════════════════════════════════
// KONTROL GLİFLERİ
// ══════════════════════════════════════════════════════════════════════════
const ICONS = {};

// kapat: iki çapraz kaburga, ortada kaynaşmış
ICONS.close = () => {
  const c = 6.2, r = 1.75;
  return { parts: [
    cap(-c, -c, 0, c, c, 0, r, { mat: 'gloss' }),
    cap(-c, c, 0, c, -c, 0, r, { mat: 'gloss', k: 1.5 }),
  ] };
};

// eksi: tek yumuşak hap
ICONS.minus = () => ({ parts: [cap(-5.8, 0, 0, 5.8, 0, 0, 1.85, { mat: 'clay' })] });

// chevron: iki kaburga, tepe noktasında kaynaşmış; 4 yön
function chevron(deg) {
  const [ax, ay] = rot2(deg, -6.4, -3.3);
  const [bx, by] = rot2(deg, 0, 2.7);
  const [cx2, cy2] = rot2(deg, 6.4, -3.3);
  return { parts: [
    cap(ax, ay, 0, bx, by, 0, 1.6, { mat: 'gel' }),
    cap(bx, by, 0, cx2, cy2, 0, 1.6, { mat: 'gel', k: 1.6 }),
  ] };
}
ICONS.chevron_up = () => chevron(0);
ICONS.chevron_down = () => chevron(180);
ICONS.chevron_left = () => chevron(-90);
ICONS.chevron_right = () => chevron(90);

// ok: gövde + koni baş; 4 yön
function arrow(deg) {
  const P = (x, y) => rot2(deg, x, y);
  const [x0, y0] = P(0, -7.2);
  const [x1, y1] = P(0, 0.4);
  const [cx, cy] = P(0, 0.9);
  const parts = [
    cap(x0, y0, 0, x1, y1, 0, 1.45, { mat: 'gel' }),
    cone(cx, cy, 0, 2.9, 0.3, 4.9, 0.75, { rot: [0, 0, (deg + 180) * D2R - Math.PI], mat: 'gel', k: 0.9 }),
  ];
  return { parts };
}
ICONS.arrow_up = () => arrow(0);
ICONS.arrow_down = () => arrow(180);
ICONS.arrow_left = () => arrow(-90);
ICONS.arrow_right = () => arrow(90);

// göz: badem sklera + krom halka + obsidyen iris + cam bebek
ICONS.eye = () => ({
  parts: [
    ell(0, 0, 0, 8.4, 4.9, 1.75, { mat: 'glass' }),
    tor('z', 0, 0, 1.55, 2.8, 0.5, { mat: 'cream' }),
    cyl('z', 0, 0, 1.15, 2.4, 1.05, 0.3, { mat: 'obsidian' }),
    sph(0, 0, 2.25, 1.3, { mat: 'obsidian' }),
    sph(0.5, 0.62, 3.0, 0.42, { mat: 'cream', col: [252, 244, 236] }),
  ],
  bbox: [-9.2, 9.2, -5.8, 5.8, -3, 4],
});

// göz kapalı: göz + çapraz bant
ICONS.eyeOff = () => ({
  parts: [
    ell(0, 0.4, 0, 8.6, 5.0, 1.6, { mat: 'glass' }),
    tor('z', 0, 0.4, 1.4, 3.0, 0.66, { mat: 'chrome' }),
    cyl('z', 0, 0.4, 1.0, 2.5, 1.0, 0.3, { mat: 'obsidian' }),
    cap(-7.6, -7.0, 1.2, 7.6, 7.8, 1.2, 1.15, { mat: 'obsidian' }),
  ],
  bbox: [-9.6, 9.6, -8.6, 9.0, -3, 4],
});

// huni: koni gövde + krom ağız + boyun + jöle bant
ICONS.funnel = () => ({
  parts: [
    cone(0, 1.0, 0, 1.35, 5.7, 6.4, 0.6, { mat: 'glass' }),
    tor('y', 0, 7.3, 0, 5.6, 0.34, { mat: 'cream', col: [232, 214, 194] }),
    cyl('y', 0, -0.2, 0, 1.35, 1.35, 0.4, { mat: 'glass', k: 0.8 }),
    tor('y', 0, -1.6, 0, 1.4, 0.34, { mat: 'cream', col: [236, 220, 200] }),
  ],
  bbox: [-6.6, 6.6, -3.6, 8.2, -3, 3],
});

// sıralama: alçalan metal çubuklar + parlak ok başı
ICONS.sort = () => ({
  parts: [
    cap(-8.4, 6.0, 0, 4.6, 6.0, 0, 1.15, { mat: 'metal' }),
    cap(-8.4, 0, 0, 2.0, 0, 0, 1.15, { mat: 'metal' }),
    cap(-8.4, -6.0, 0, -0.6, -6.0, 0, 1.15, { mat: 'metal' }),
    cap(7.6, 5.4, 0, 7.6, -4.4, 0, 1.25, { mat: 'gloss' }),
    cone(7.6, -6.0, 0, 2.5, 0.3, 3.0, 0.6, { mat: 'gloss', k: 0.8 }),
  ],
  bbox: [-10, 10, -9.4, 8, -3, 3],
});

// ızgara: obsidyen tepsi + 4 mat karo
ICONS.grid = () => ({
  parts: [
    box(0, 0, -1.2, 8.9, 8.9, 1.2, 1.0, { mat: 'obsidian' }),
    box(-4.15, 4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
    box(4.15, 4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
    box(-4.15, -4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
    box(4.15, -4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
  ],
  bbox: [-9.6, 9.6, -9.6, 9.6, -4, 3],
});

// liste: 3 satır + madde işaretleri
ICONS.list = () => ({
  parts: [
    sph(-7.4, 5.4, 0, 1.15, { mat: 'obsidian' }),
    sph(-7.4, 0, 0, 1.15, { mat: 'obsidian' }),
    sph(-7.4, -5.4, 0, 1.15, { mat: 'obsidian' }),
    cap(-4.2, 5.4, 0, 8.2, 5.4, 0, 1.35, { mat: 'clay' }),
    cap(-4.2, 0, 0, 8.2, 0, 0, 1.35, { mat: 'clay' }),
    cap(-4.2, -5.4, 0, 5.4, -5.4, 0, 1.35, { mat: 'clay' }),
  ],
  bbox: [-9.4, 9.6, -7.2, 7.2, -3, 3],
});

// akış: iki kalın kauçuk şerit
ICONS.stream = () => ({
  parts: [
    box(0, 3.3, 0, 8.2, 1.75, 1.5, 1.4, { mat: 'rubber' }),
    box(0, -3.3, 0, 8.2, 1.75, 1.5, 1.4, { mat: 'rubber' }),
  ],
  bbox: [-9.4, 9.4, -5.6, 5.6, -3, 3],
});

// carousel: orta kart + iki yan plaka (hafif dönük)
ICONS.carousel = () => ({
  parts: [
    box(0, 0, 0.5, 4.5, 6.2, 1.0, 0.85, { mat: 'gloss' }),
    box(-7.4, 0, -0.4, 2.0, 4.5, 0.8, 0.7, { rot: [0, 0.55, 0], mat: 'gloss', k: 0.4 }),
    box(7.4, 0, -0.4, 2.0, 4.5, 0.8, 0.7, { rot: [0, -0.55, 0], mat: 'gloss', k: 0.4 }),
  ],
  bbox: [-10.4, 10.4, -7.2, 7.2, -3, 3],
});

// üç nokta: üç jöle küre
ICONS.more = () => ({
  parts: [
    sph(0, 6.2, 0, 1.75, { mat: 'gel' }),
    sph(0, 0, 0, 1.75, { mat: 'gel' }),
    sph(0, -6.2, 0, 1.75, { mat: 'gel' }),
  ],
  bbox: [-3, 3, -8.4, 8.4, -3, 3],
});

// dışa aç: C çerçeve + çapraz ok
ICONS.external = () => ({
  parts: [
    cap(-6.6, 5.6, 0, -6.6, -5.6, 0, 1.3, { mat: 'obsidian' }),
    cap(-6.6, -5.6, 0, 1.6, -5.6, 0, 1.3, { mat: 'obsidian' }),
    cap(-6.6, 5.6, 0, -1.2, 5.6, 0, 1.3, { mat: 'obsidian' }),
    cap(0.4, 0.4, 0.5, 5.2, 5.2, 0.5, 0.85, { mat: 'gloss' }),
    poly([[-1.9, 0], [1.9, 0], [0, 2.5]], 0.5, 0.85, 0.5, { cx: 6.5, cy: 6.5, rot: [0, 0, -0.74], mat: 'gloss', k: 0.5 }),
  ],
  bbox: [-9, 10.4, -8.4, 9.4, -3, 3],
});

// tam ekran: 4 köşe dirseği
ICONS.fullscreen = () => {
  const parts = [];
  for (const sx of [-1, 1]) for (const sy of [-1, 1]) {
    parts.push(cap(sx * 7.7, sy * 3.0, 0, sx * 7.7, sy * 7.7, 0, 1.25, { mat: 'obsidian' }));
    parts.push(cap(sx * 3.0, sy * 7.7, 0, sx * 7.7, sy * 7.7, 0, 1.25, { mat: 'obsidian', k: 1.4 }));
  }
  return { parts, bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3] };
};

// kırpma: iki dirsek
ICONS.crop = () => ({
  parts: [
    cap(-4.2, 7.4, 0, -4.2, -4.2, 0, 1.25, { mat: 'gloss' }),
    cap(-4.2, -4.2, 0, 7.4, -4.2, 0, 1.25, { mat: 'gloss', k: 1.3 }),
    cap(4.2, -7.4, 0, 4.2, 4.2, 0, 1.25, { mat: 'gloss' }),
    cap(4.2, 4.2, 0, -7.4, 4.2, 0, 1.25, { mat: 'gloss', k: 1.3 }),
  ],
  bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3],
});

// döndür: yay + ok başı
ICONS.rotate = () => {
  const a0 = 140 * D2R, a1 = -60 * D2R, R = 6.2;
  const parts = arc(R, a0, a1, 1.15, 12, { mat: 'gloss', k: 1.8 });
  const ax = Math.cos(a1) * R, ay = Math.sin(a1) * R;
  const tan = [Math.sin(a1), -Math.cos(a1)];   // azalan açı → saat yönü
  parts.push(tangentHead(ax + tan[0] * 1.2, ay + tan[1] * 1.2, tan, 2.3, 4.2, 0.6, { mat: 'gloss', k: 0.9 }));
  return { parts, bbox: [-9.6, 9.6, -9.6, 9.6, -3, 3] };
};

// en-boy: çerçeve + iki ince bölme
ICONS.aspect = () => ({
  parts: [
    box(0, 0, 0, 9.0, 6.0, 1.1, 0.95, { mat: 'gloss' }),
    box(-2.9, 0, 1.35, 0.42, 4.4, 0.5, 0.3, { mat: 'obsidian' }),
    box(2.9, 0, 1.35, 0.42, 4.4, 0.5, 0.3, { mat: 'obsidian' }),
    box(0, 0, -0.2, 9.0, 6.0, 0.6, 0.5, { mat: 'clay', k: 1.0 }),
  ],
  bbox: [-9.8, 9.8, -6.8, 6.8, -3, 3],
});

// bulanıklık: damla (küre merdiveni + yumuşak kaynaşma → organik gözyaşı)
ICONS.blur = () => ({
  parts: [
    sph(0, -3.6, 0, 4.2, { mat: 'glass' }),
    sph(0, -0.6, 0, 3.5, { mat: 'glass', k: 1.6 }),
    sph(0, 1.9, 0, 2.7, { mat: 'glass', k: 1.5 }),
    sph(0, 3.9, 0, 1.8, { mat: 'glass', k: 1.3 }),
    sph(0, 5.4, 0, 0.85, { mat: 'glass', k: 1.1 }),
    sph(0, 6.5, 0, 0.4, { mat: 'glass', k: 0.8 }),
  ],
  bbox: [-5.4, 5.4, -8.2, 7.4, -4, 4],
});

// hizalama üçlüsü
ICONS.alignLeft = () => ({
  parts: [
    cap(-8.0, 5.6, 0, 8.0, 5.6, 0, 1.35, { mat: 'clay' }),
    cap(-8.0, 0, 0, 3.4, 0, 0, 1.35, { mat: 'clay' }),
    cap(-8.0, -5.6, 0, 5.4, -5.6, 0, 1.35, { mat: 'clay' }),
  ],
  bbox: [-9.6, 9.6, -7.2, 7.2, -3, 3],
});
ICONS.alignCenter = () => ({
  parts: [
    cap(-8.0, 5.6, 0, 8.0, 5.6, 0, 1.35, { mat: 'clay' }),
    cap(-5.2, 0, 0, 5.2, 0, 0, 1.35, { mat: 'clay' }),
    cap(-6.6, -5.6, 0, 6.6, -5.6, 0, 1.35, { mat: 'clay' }),
  ],
  bbox: [-9.6, 9.6, -7.2, 7.2, -3, 3],
});
ICONS.alignJustify = () => ({
  parts: [
    cap(-8.0, 5.6, 0, 8.0, 5.6, 0, 1.35, { mat: 'rubber' }),
    cap(-8.0, 0, 0, 8.0, 0, 0, 1.35, { mat: 'rubber' }),
    cap(-8.0, -5.6, 0, 8.0, -5.6, 0, 1.35, { mat: 'rubber' }),
  ],
  bbox: [-9.6, 9.6, -7.2, 7.2, -3, 3],
});

// kenarlık: içi boş kare çerçeve + iç plaka
ICONS.border = () => ({
  parts: [
    box(0, 0, 0, 8.6, 8.6, 1.0, 1.0, { mat: 'obsidian' }),
    box(0, 0, 0, 6.6, 6.6, 2.2, 0.6, { mat: 'obsidian', sub: true }),
    box(0, 0, -0.2, 4.6, 4.6, 0.9, 0.45, { mat: 'clay' }),
  ],
  bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3],
});

// tümünü seç: 8 kesikli çizgi
ICONS.selectAll = () => {
  const parts = [];
  const R = 7.8, seg = 2.6, gap = 3.0;
  for (const s of [-1, 1]) {
    for (const a of [-gap / 2 - seg, gap / 2]) {
      parts.push(cap(a, s * R, 0, a + seg, s * R, 0, 0.95, { mat: 'rubber' }));
      parts.push(cap(s * R, a, 0, s * R, a + seg, 0, 0.95, { mat: 'rubber' }));
    }
  }
  return { parts, bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3] };
};

// sürükle: iki tutamak çubuğu + krom boncuklar
ICONS.drag = () => ({
  parts: [
    box(0, 2.4, 0, 5.6, 0.85, 1.0, 0.8, { mat: 'rubber' }),
    box(0, -2.4, 0, 5.6, 0.85, 1.0, 0.8, { mat: 'rubber' }),
    sph(-4.4, 2.4, 1.3, 0.72, { mat: 'chrome' }),
    sph(4.4, 2.4, 1.3, 0.72, { mat: 'chrome' }),
    sph(-4.4, -2.4, 1.3, 0.72, { mat: 'chrome' }),
    sph(4.4, -2.4, 1.3, 0.72, { mat: 'chrome' }),
  ],
  bbox: [-7, 7, -4.4, 4.4, -3, 3],
});

// yoğunluk: üç ince satır
ICONS.density = () => ({
  parts: [
    cap(-8.2, 5.2, 0, 8.2, 5.2, 0, 0.95, { mat: 'paper' }),
    cap(-8.2, 0, 0, 8.2, 0, 0, 0.95, { mat: 'paper' }),
    cap(-8.2, -5.2, 0, 8.2, -5.2, 0, 0.95, { mat: 'paper' }),
    cap(-8.2, 2.6, 0, 8.2, 2.6, 0, 0.5, { mat: 'paper' }),
    cap(-8.2, -2.6, 0, 8.2, -2.6, 0, 0.5, { mat: 'paper' }),
  ],
  bbox: [-9.6, 9.6, -6.4, 6.4, -3, 3],
});

// klavye: kauçuk gövde + parlak tuş başlıkları
ICONS.keyboard = () => {
  const parts = [box(0, 0, 0, 9.4, 5.6, 1.15, 0.95, { mat: 'rubber' })];
  const key = (cx, cy, hx, hy) => parts.push(box(cx, cy, 1.5, hx, hy, 0.62, 0.35, { mat: 'gloss' }));
  for (let r = 0; r < 3; r++) {
    const y = 3.2 - r * 2.4;
    for (let c = 0; c < 5; c++) key(-7.0 + c * 2.6 - (r === 2 ? 0 : 0.1), y, 0.95, 0.85);
  }
  parts.push(box(2.2, -3.4, 1.5, 3.6, 0.75, 0.6, 0.35, { mat: 'gloss' }));
  return { parts, bbox: [-10, 10, -6.4, 6.4, -3, 3] };
};

// giriş alanı: yuvarlak alan + üç nokta + krom imleç
ICONS.input = () => ({
  parts: [
    box(0, 0, 0, 8.8, 5.4, 1.2, 1.0, { mat: 'gloss' }),
    box(-2.6, 0, 1.5, 4.4, 3.0, 0.35, 0.3, { mat: 'obsidian' }),
    sph(-6.4, 0, 1.75, 0.75, { mat: 'obsidian' }),
    sph(-4.2, 0, 1.75, 0.75, { mat: 'obsidian' }),
    sph(-2.0, 0, 1.75, 0.75, { mat: 'obsidian' }),
    cyl('z', 3.6, 0, 1.5, 0.55, 1.9, 0.25, { mat: 'chrome' }),
  ],
  bbox: [-9.6, 9.6, -6.2, 6.2, -3, 3],
});

// çift ok: iki dışa bakan ok
ICONS.doubleArrow = () => ({
  parts: [
    cap(-2.2, 3.4, 0, -7.4, 0, 0, 1.35, { mat: 'gel' }),
    cap(-7.4, 0, 0, -2.2, -3.4, 0, 1.35, { mat: 'gel', k: 1.5 }),
    cap(2.2, 3.4, 0, 7.4, 0, 0, 1.35, { mat: 'gel' }),
    cap(7.4, 0, 0, 2.2, -3.4, 0, 1.35, { mat: 'gel', k: 1.5 }),
  ],
  bbox: [-9.4, 9.4, -5, 5, -3, 3],
});

// ev: gövde + beşik çatı + obsidyen kapı + baca
ICONS.home = () => ({
  parts: [
    box(0, -3.4, 0, 6.6, 3.5, 1.5, 0.9, { mat: 'clay' }),
    poly([[-8.0, 0], [8.0, 0], [0, 4.8]], 0, 1.75, 0.95, { cy: 1.0, mat: 'clay', k: 0.7 }),
    cyl('y', 3.3, 5.2, -0.6, 0.78, 1.5, 0.32, { mat: 'obsidian' }),
    box(0, -4.3, 1.6, 1.85, 2.35, 0.55, 0.45, { mat: 'obsidian' }),
    sph(1.15, -4.3, 2.15, 0.3, { mat: 'cream', col: [252, 242, 228] }),
    box(-4.1, -1.4, 1.55, 1.35, 1.2, 0.5, 0.4, { mat: 'obsidian' }),
  ],
  bbox: [-9.4, 9.4, -8.4, 7.8, -3, 3],
});

// yaprak: eğik elipsoid + sap
ICONS.leaf = () => ({
  parts: [
    ell(0, 0, 0, 7.2, 4.0, 1.6, { rot: [0, 0, 0.72], mat: 'gel' }),
    cap(-6.6, -6.6, 0, -3.0, -3.0, 0, 0.95, { mat: 'clay', k: 1.2 }),
  ],
  bbox: [-8.4, 8.4, -8.4, 8.4, -3, 3],
});

// kar tanesi: konik kollar (uçta incelir) + göbek + ince V dalları
ICONS.snow = () => {
  const parts = [sph(0, 0, 1.0, 1.5, { mat: 'clay' })];
  for (let i = 0; i < 6; i++) {
    const a = i * 60 * D2R;
    const dx = Math.cos(a), dy = Math.sin(a);
    // ana kol: göbekten uca incelen koni
    parts.push(cone(dx * 0.8, dy * 0.8, 0, 1.15, 0.42, 6.4, 0.4, {
      rot: [0, 0, Math.atan2(-dx, dy)], mat: 'glass',
    }));
    // iki ince dal
    for (const sgn of [-1, 1]) {
      const ba = a + sgn * 0.72;
      const bx = Math.cos(ba), by = Math.sin(ba);
      parts.push(cone(dx * 4.2, dy * 4.2, 0, 0.42, 0.16, 2.6, 0.16, {
        rot: [0, 0, Math.atan2(-bx, by)], mat: 'glass', k: 0.35,
      }));
    }
  }
  return { parts, bbox: [-8.4, 8.4, -8.4, 8.4, -3, 3] };
};

// kek: krom tabak + sünger + krema + 3 mum
ICONS.cake = () => ({
  parts: [
    cyl('y', 0, -6.0, 0, 7.1, 0.72, 0.32, { mat: 'cream', col: [250, 236, 214] }),
    cyl('y', 0, -3.3, 0, 5.6, 2.4, 0.6, { mat: 'clay' }),
    tor('y', 0, -0.5, 0, 4.9, 0.95, { mat: 'gel' }),
    cyl('y', -3.0, 1.9, 0, 0.72, 2.4, 0.32, { mat: 'gloss' }),
    cyl('y', 0, 2.5, 0, 0.72, 2.9, 0.32, { mat: 'gloss' }),
    cyl('y', 3.0, 1.9, 0, 0.72, 2.4, 0.32, { mat: 'gloss' }),
    sph(-3.0, 4.7, 0, 0.78, { mat: 'cream', col: [255, 216, 150] }),
    sph(0, 5.8, 0, 0.82, { mat: 'cream', col: [255, 224, 160] }),
    sph(3.0, 4.7, 0, 0.78, { mat: 'cream', col: [255, 216, 150] }),
  ],
  bbox: [-8.2, 8.2, -7.4, 6.4, -3, 3],
});

// kamera: gövde + namlu + krom halka + cam mercek + deklanşör
ICONS.camera = () => ({
  parts: [
    box(0, 0.3, 0, 7.7, 4.7, 2.0, 1.15, { mat: 'gloss' }),
    box(-6.2, 0.1, 1.1, 1.3, 3.6, 0.9, 0.55, { mat: 'obsidian' }),
    box(4.5, 3.9, -0.3, 1.45, 0.75, 1.05, 0.3, { mat: 'cream', col: [246, 232, 214] }),
    box(-4.9, 3.6, 0.9, 1.4, 0.5, 0.7, 0.22, { mat: 'cream', col: [246, 232, 214] }),
    cyl('z', 0, -0.5, 2.35, 3.3, 1.05, 0.45, { mat: 'obsidian' }),
    tor('z', 0, -0.5, 2.68, 2.85, 0.48, { mat: 'cream', col: [250, 238, 222] }),
    cyl('z', 0, -0.5, 3.15, 2.3, 0.55, 0.28, { mat: 'ink', col: [30, 14, 46] }),
    sph(0.8, 0.4, 3.6, 0.42, { mat: 'cream', col: [255, 250, 244] }),
  ],
  bbox: [-8.6, 8.6, -6.0, 6.0, -3, 4],
});

// tırnak: iki trapez plaka — tipografik çift tırnak, kalın 3B hacim
const QUOTE_MARK = [[-1.25, -3.1], [1.25, -3.1], [0.95, 3.1], [-0.95, 3.1]];
ICONS.textQuote = () => ({
  parts: [
    poly(QUOTE_MARK, 0, 1.45, 0.55, { cx: -2.5, cy: 1.0, rot: [0, 0, 0.10], mat: 'clay' }),
    poly(QUOTE_MARK, 0, 1.45, 0.55, { cx: 2.5, cy: 1.0, rot: [0, 0, 0.10], mat: 'clay' }),
  ],
  bbox: [-5.6, 5.6, -3.4, 5.4, -3, 3],
});

// diyez: 4 eğik çubuk
ICONS.textHash = () => ({
  parts: [
    box(-2.5, 0, 0, 0.85, 7.6, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    box(2.5, 0, 0, 0.85, 7.6, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    box(0, 2.5, 0, 7.6, 0.85, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    box(0, -2.5, 0, 7.6, 0.85, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
  ],
  bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3],
});

// ── nesne glifleri ────────────────────────────────────────────────────────
// eşitleme: yay + ok başı
ICONS.sync = () => {
  const R = 6.2, parts = [];
  const segs = [[20, 160], [200, 340]];
  for (const [d0, d1] of segs) {
    const a0 = d0 * D2R, a1 = d1 * D2R;
    parts.push(...arc(R, a0, a1, 1.15, 12, { mat: 'gloss', k: 1.8 }));
    const ax = Math.cos(a1) * R, ay = Math.sin(a1) * R;
    const tan = [-Math.sin(a1), Math.cos(a1)];  // artan açı
    parts.push(tangentHead(ax + tan[0] * 0.9, ay + tan[1] * 0.9, tan, 2.2, 3.8, 0.55, { mat: 'gloss', k: 0.9 }));
  }
  return { parts, bbox: [-9.8, 9.8, -9.8, 9.8, -3, 3] };
};

// kaydırıcılar: 3 ray + 3 krom topuz
ICONS.sliders = () => ({
  parts: [
    box(0, 5.6, 0, 7.8, 0.75, 0.9, 0.72, { mat: 'obsidian' }),
    box(0, 0, 0, 7.8, 0.75, 0.9, 0.72, { mat: 'obsidian' }),
    box(0, -5.6, 0, 7.8, 0.75, 0.9, 0.72, { mat: 'obsidian' }),
    cyl('z', -2.6, 5.6, 1.1, 1.75, 1.5, 0.5, { mat: 'chrome' }),
    cyl('z', 2.2, 0, 1.1, 1.75, 1.5, 0.5, { mat: 'chrome' }),
    cyl('z', -0.8, -5.6, 1.1, 1.75, 1.5, 0.5, { mat: 'chrome' }),
  ],
  bbox: [-9.4, 9.4, -7.8, 7.8, -3, 3],
});

// indir: aşağı ok + krom tepsi
ICONS.download = () => ({
  parts: [
    cap(0, 7.4, 0, 0, 0.6, 0, 1.45, { mat: 'gel' }),
    cone(0, -0.2, 0, 3.15, 0.35, 5.2, 0.85, { rot: [0, 0, Math.PI], mat: 'gel', k: 0.9 }),
    cap(-7.6, -8.0, 0, 7.6, -8.0, 0, 1.35, { mat: 'chrome' }),
  ],
  bbox: [-9.4, 9.4, -10, 9, -3, 3],
});

// yükle: yukarı ok + parlak tepsi
ICONS.upload = () => ({
  parts: [
    cap(0, -7.2, 0, 0, -0.4, 0, 1.45, { mat: 'paper' }),
    cone(0, 1.2, 0, 3.15, 0.35, 5.2, 0.85, { mat: 'paper', k: 0.9 }),
    cap(-7.6, -9.0, 0, 7.6, -9.0, 0, 1.35, { mat: 'gloss' }),
  ],
  bbox: [-9.4, 9.4, -10.4, 7, -3, 3],
});

// kronometre: krom çerçeve + kadran + tepe + ibre
ICONS.timer = () => ({
  parts: [
    tor('z', 0, -0.6, 0.55, 5.9, 0.8, { mat: 'cream', col: [244, 230, 212] }),
    cyl('z', 0, -0.6, 0.15, 5.5, 0.85, 0.35, { mat: 'clay' }),
    cyl('y', 0, 5.9, 0, 0.95, 1.0, 0.3, { mat: 'cream', col: [244, 230, 212] }),
    cyl('y', 0, 7.0, 0, 1.8, 0.45, 0.2, { mat: 'cream', col: [244, 230, 212] }),
    box(0.9, 0.7, 1.1, 2.1, 0.34, 0.45, 0.25, { rot: [0, 0, 0.6], mat: 'ink', col: [58, 30, 74] }),
    sph(0, -0.6, 1.3, 0.55, { mat: 'ink', col: [58, 30, 74] }),
    sph(3.1, 2.3, 1.2, 0.28, { mat: 'cream', col: [250, 238, 222] }),
    sph(-3.3, 1.6, 1.2, 0.28, { mat: 'cream', col: [250, 238, 222] }),
    sph(0.4, -3.6, 1.2, 0.28, { mat: 'cream', col: [250, 238, 222] }),
  ],
  bbox: [-7.6, 7.6, -7.4, 8.4, -3, 3],
});

// kod: iki açılı parantez
ICONS.code = () => ({
  parts: [
    cap(-4.6, 4.8, 0, -8.8, 0, 0, 1.35, { mat: 'obsidian' }),
    cap(-8.8, 0, 0, -4.6, -4.8, 0, 1.35, { mat: 'obsidian', k: 1.5 }),
    cap(4.6, 4.8, 0, 8.8, 0, 0, 1.35, { mat: 'obsidian' }),
    cap(8.8, 0, 0, 4.6, -4.8, 0, 1.35, { mat: 'obsidian', k: 1.5 }),
  ],
  bbox: [-10.4, 10.4, -6.4, 6.4, -3, 3],
});

// TV: kauçuk kabin + obsidyen ekran + krom ayak
ICONS.tv = () => ({
  parts: [
    box(0, 1.6, 0, 9.0, 6.2, 1.7, 1.1, { mat: 'rubber' }),
    box(0, 2.0, 1.2, 7.5, 4.7, 0.9, 0.5, { mat: 'ink', col: [52, 30, 78] }),
    box(0, 3.6, 1.9, 6.4, 2.0, 0.35, 0.3, { mat: 'ink', col: [86, 54, 122] }),
    cyl('y', 0, -6.4, 0, 0.65, 1.3, 0.28, { mat: 'cream', col: [240, 226, 208] }),
    box(0, -8.0, 0, 3.4, 0.75, 1.3, 0.45, { mat: 'cream', col: [240, 226, 208] }),
  ],
  bbox: [-9.8, 9.8, -9.4, 8.4, -3, 3],
});

// etiket: ekstrüde etiket gövdesi + krom kuşgözü
ICONS.tag = () => ({
  parts: [
    poly([[-8.6, 1.4], [-8.6, 8.6], [-1.4, 8.6], [8.6, -1.4], [1.4, -8.6], [-1.4, -8.6]].map(([x, y]) => [x, y]), 0, 1.5, 0.85, { rot: [0, 0, -0.35], mat: 'paper' }),
    tor('z', -5.4, 5.4, 1.55, 1.3, 0.5, { mat: 'cream', col: [242, 228, 210] }),
    cyl('z', -5.4, 5.4, 1.25, 0.8, 1.1, 0.28, { mat: 'ink', col: [54, 28, 72] }),
  ],
  bbox: [-10.4, 10.4, -10.4, 10.4, -3, 3],
});

// yedekle: bulut + yukarı ok
ICONS.backup = () => ({
  parts: [
    sph(-4.2, 0.4, 0, 3.0, { mat: 'gel' }),
    sph(2.2, -0.2, 0, 2.8, { mat: 'gel' }),
    sph(-0.4, 2.6, 0, 3.0, { mat: 'gel', k: 2.4 }),
    box(0, -1.8, 0, 5.4, 2.1, 1.9, 1.5, { mat: 'gel', k: 2.0 }),
    box(0, -2.2, 3.0, 0.55, 1.7, 0.55, 0.45, { mat: 'cream', col: [252, 242, 230] }),
    poly([[-1.65, 0], [1.65, 0], [0, 2.7]], 3.0, 0.58, 0.3, { cy: 0.45, mat: 'cream', col: [252, 242, 230] }),
  ],
  bbox: [-8.2, 8.0, -5.4, 6.4, -3, 4],
});

// ── harf glifleri (maske→SDF ile kalın ekstrüzyon) ────────────────────────
const LETTERS = {
  textBold: { ch: 'B', weight: 800, size: 18 },
  textItalic: { ch: 'I', weight: 800, size: 18, skew: 0.18 },
  textTitle: { ch: 'A', weight: 800, size: 18 },
  textStrike: { ch: 'S', weight: 800, size: 18, strike: true },
};

function scene(name) {
  if (ICONS[name]) {
    const s = ICONS[name]();
    return Object.assign({ bbox: BB, tilt: 0.30 }, s, sig(name));
  }
  // harfler ve isim eşlemeleri çağıran tarafta
  return null;
}

module.exports = { ICONS, LETTERS, OBJ_NAMES, scene, sig, BB, helpers: { box, sph, ell, cap, cyl, tor, cone, poly, arc, rot2 } };
