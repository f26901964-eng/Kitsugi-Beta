// Gerçek 3B render hattı (SDF + ışın yürütme) — 3dicons render'larıyla aynı malzeme.
//
// Neden?
// ------
// SVG ile "gövde + kaydırılmış kopya + gradyan" yaklaşımı düz/poster görünüyordu.
// 3dicons nesneleri GERÇEK 3B: yuvarlatılmış kenarlı hacim, yüzey normaline göre
// gölgeleme, geniş spekülar parlama, fresnel kenar ışığı ve temas gölgesi.
//
// Bu modül, glif siluetini (SVG maskesi) alıp:
//   1. işaretli mesafe alanına (SDF) çevirir (kesin Öklid uzaklık dönüşümü),
//   2. silueti kalınlığı `2*halfT` olan, kenarları `r` yarıçapla yuvarlatılmış
//      bir levhaya ekstrüde eder,
//   3. her piksel için ışını yürütüp yüzey normalini bulur,
//   4. plastik/cam malzeme ile boyar: Lambert + Blinn-Phong spekülar + fresnel +
//      kenar karartma (AO) + yumuşak temas gölgesi.
//
// Çıktı: saydam PNG (dünya birimi 24, kamera ortografik, ışık sol-üst-ön).

const sharp = require('./node_modules/sharp');

// ── renk rampası (3dicons front/gradient ölçümü) ────────────────────────────
const RAMP = [
  [0.0, [255, 196, 124]],   // sıcak kayısı
  [0.18, [255, 145, 106]],
  [0.40, [246, 96, 143]],
  [0.60, [209, 66, 176]],
  [0.78, [156, 62, 203]],
  [1.0, [104, 74, 213]],    // derin mor
];

function ramp(t) {
  t = Math.min(1, Math.max(0, t));
  for (let i = 1; i < RAMP.length; i++) {
    if (t <= RAMP[i][0]) {
      const [t0, c0] = RAMP[i - 1];
      const [t1, c1] = RAMP[i];
      const k = (t - t0) / (t1 - t0 || 1);
      return [0, 1, 2].map((j) => c0[j] + (c1[j] - c0[j]) * k);
    }
  }
  return RAMP[RAMP.length - 1][1].slice();
}

// ── 1D kare mesafe dönüşümü (Felzenszwalb & Huttenlocher) ───────────────────
function edt1d(f, n, d, v, z) {
  let k = 0;
  v[0] = 0;
  z[0] = -Infinity;
  z[1] = Infinity;
  for (let q = 1; q < n; q++) {
    let s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    while (s <= z[k]) {
      k--;
      s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    }
    k++;
    v[k] = q;
    z[k] = s;
    z[k + 1] = Infinity;
  }
  k = 0;
  for (let q = 0; q < n; q++) {
    while (z[k + 1] < q) k++;
    d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
  }
}

/** İkili maskeden kesin Öklid mesafe alanı (piksel birimi, içeride negatif). */
function signedDistance(alpha, w, h) {
  const INF = 1e9;
  const fg = new Float64Array(w * h);
  const bg = new Float64Array(w * h);
  for (let i = 0; i < w * h; i++) {
    const inside = alpha[i] > 127;
    fg[i] = inside ? 0 : INF;
    bg[i] = inside ? INF : 0;
  }
  const tmpF = new Float64Array(Math.max(w, h));
  const tmpD = new Float64Array(Math.max(w, h));
  const v = new Int32Array(Math.max(w, h) + 1);
  const z = new Float64Array(Math.max(w, h) + 1);

  const pass = (grid) => {
    for (let x = 0; x < w; x++) {
      for (let y = 0; y < h; y++) tmpF[y] = grid[y * w + x];
      edt1d(tmpF, h, tmpD, v, z);
      for (let y = 0; y < h; y++) grid[y * w + x] = tmpD[y];
    }
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) tmpF[x] = grid[y * w + x];
      edt1d(tmpF, w, tmpD, v, z);
      for (let x = 0; x < w; x++) grid[y * w + x] = tmpD[x];
    }
  };
  pass(fg);
  pass(bg);

  const out = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) out[i] = Math.sqrt(fg[i]) - Math.sqrt(bg[i]);
  return out;
}

/** SDF alanını yumuşatır (ikili maskeden gelen basamakları yok eder). */
function smooth(field, size, passes) {
  let a = Float32Array.from(field);
  let b = new Float32Array(field.length);
  for (let p = 0; p < passes; p++) {
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        let sum = 0, n = 0;
        for (let dy = -1; dy <= 1; dy++) {
          const yy = y + dy;
          if (yy < 0 || yy >= size) continue;
          for (let dx = -1; dx <= 1; dx++) {
            const xx = x + dx;
            if (xx < 0 || xx >= size) continue;
            sum += a[yy * size + xx];
            n++;
          }
        }
        b[y * size + x] = sum / n;
      }
    }
    const t = a; a = b; b = t;
  }
  return a;
}

/** SDF'den iki doğrusal örnekleme (dünya birimi). */
function sampler(sdf, size, world) {
  const scale = size / world; // px per unit
  const W = size, H = size;
  return (x, y) => {
    let px = x * scale + W / 2 - 0.5;
    let py = -y * scale + H / 2 - 0.5;
    px = Math.min(W - 1.001, Math.max(0, px));
    py = Math.min(H - 1.001, Math.max(0, py));
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const fx = px - x0, fy = py - y0;
    const i00 = y0 * W + x0;
    const a = sdf[i00], b = sdf[i00 + 1], c = sdf[i00 + W], d = sdf[i00 + W + 1];
    return ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy) / scale;
  };
}

/**
 * Siluet maskesinden 3B render.
 * @param {Uint8Array} alpha  size*size alfa kanalı (0-255)
 * @param {number} size       maske çözünürlüğü
 * @param {object} opts       { out, halfT, radius, world, light, exposure }
 */
async function render(alpha, size, opts = {}) {
  const world = opts.world || 24;
  const halfT = opts.halfT ?? 1.5;
  const radius = opts.radius ?? 0.95;
  const out = opts.out || 512;
  const exposure = opts.exposure ?? 1.04;
  const lightDir = opts.light || [-0.40, 0.58, 0.72];

  let sdf = signedDistance(alpha, size, size);
  sdf = smooth(sdf, size, 4);
  const sampleXY = sampler(sdf, size, world);

  // nesne sınırları (renk rampası buna yayılır)
  let bx0 = Infinity, bx1 = -Infinity, by0 = Infinity, by1 = -Infinity;
  for (let j = 0; j < size; j++) {
    for (let i = 0; i < size; i++) {
      if (alpha[j * size + i] > 127) {
        const wx = ((i + 0.5) / size) * world - world / 2;
        const wy = -(((j + 0.5) / size) * world - world / 2);
        if (wx < bx0) bx0 = wx;
        if (wx > bx1) bx1 = wx;
        if (wy < by0) by0 = wy;
        if (wy > by1) by1 = wy;
      }
    }
  }
  const bbw = Math.max(0.001, bx1 - bx0);
  const bbh = Math.max(0.001, by1 - by0);

  const L = norm(lightDir);
  const V = [0, 0, 1];
  const Hv = norm([L[0] + V[0], L[1] + V[1], L[2] + V[2]]);
  const px = world / out;

  const col = Buffer.alloc(out * out * 4);
  const shadow = Buffer.alloc(out * out * 4);

  for (let j = 0; j < out; j++) {
    const y = -((j + 0.5) * px - world / 2);
    for (let i = 0; i < out; i++) {
      const x = (i + 0.5) * px - world / 2;
      const s2 = sampleXY(x, y);

      // temas gölgesi (ışık yönünün tersine kaydırılmış silüet)
      const sShadow = sampleXY(x + 0.34, y - 0.46);
      const kS = (j * out + i) * 4;
      if (sShadow < 0.9) {
        const aS = Math.min(1, Math.max(0, (0.9 - sShadow) / 1.9)) * 0.30;
        shadow[kS] = 46; shadow[kS + 1] = 11; shadow[kS + 2] = 62;
        shadow[kS + 3] = Math.round(aS * 255);
      }

      if (s2 > 0) continue; // siluet dışı

      // kesme (coverage) → kenar yumuşatma
      const cover = Math.min(1, Math.max(0, 0.5 - s2 / px));

      // yuvarlatılmış kenar (fillet) — analitik yüzey
      const a = Math.min(radius, Math.max(0, s2 + radius));
      const filletZ = Math.sqrt(Math.max(0, radius * radius - a * a));

      // 2B dış normal
      const e = 0.12;
      const gx = sampleXY(x + e, y) - sampleXY(x - e, y);
      const gy = sampleXY(x, y + e) - sampleXY(x, y - e);
      const gl = Math.hypot(gx, gy) || 1;
      const n2x = gx / gl, n2y = gy / gl;         // dışa doğru
      const k = radius > 0 ? a / radius : 0;
      const N = norm([n2x * k, n2y * k, filletZ / radius]);

      // renk: sol-üst sıcak → sağ-alt mor
      const u = (x - bx0) / bbw;
      const v = (y - by0) / bbh;
      let [r, g, b] = ramp(0.70 * u + 0.30 * (1 - v));

      const ndl = Math.max(0, N[0] * L[0] + N[1] * L[1] + N[2] * L[2]);
      const ndv = Math.max(0, N[2]);
      const ndh = Math.max(0, N[0] * Hv[0] + N[1] * Hv[1] + N[2] * Hv[2]);

      const ambient = 0.60 + 0.10 * Math.max(0, N[1]);
      const diffuse = 0.58 * Math.pow(ndl, 0.9);
      const specTight = Math.pow(ndh, 110) * 1.9;
      const specWide = Math.pow(ndh, 18) * 0.24;
      const sky = Math.max(0, N[1]) * 0.09;
      const fresnel = Math.pow(1 - ndv, 3.4) * 0.26;
      const hx = x + 2.6, hy = y - 3.0;
      const broad = Math.exp(-(hx * hx + hy * hy) / (2 * 3.2 * 3.2)) * 0.26 * ndv;
      const ao = Math.min(1, Math.max(0.66, 1 + s2 * 0.42));

      const sh = (ambient + diffuse) * ao;
      const white = 255 * (specTight + specWide + sky + fresnel * 0.4 + broad);
      r = r * sh + white;
      g = g * sh + white;
      b = b * sh + white * 1.02;

      const lum = 0.299 * r + 0.587 * g + 0.114 * b;
      r = lum + (r - lum) * 1.42;
      g = lum + (g - lum) * 1.42;
      b = lum + (b - lum) * 1.42;

      col[kS] = Math.min(255, Math.max(0, Math.pow(Math.min(255, r) / 255, 0.90) * 255 * exposure));
      col[kS + 1] = Math.min(255, Math.max(0, Math.pow(Math.min(255, g) / 255, 0.90) * 255 * exposure));
      col[kS + 2] = Math.min(255, Math.max(0, Math.pow(Math.min(255, b) / 255, 0.90) * 255 * exposure));
      col[kS + 3] = Math.round(255 * cover * Math.min(1, Math.max(0, 0.5 - s2 / px + 0.5)));
    }
  }

  const obj = await sharp(col, { raw: { width: out, height: out, channels: 4 } }).png().toBuffer();
  const shp = await sharp(shadow, { raw: { width: out, height: out, channels: 4 } })
    .blur(2.4).png().toBuffer();

  return sharp({ create: { width: out, height: out, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: shp, top: 0, left: 0 }, { input: obj, top: 0, left: 0 }])
    .png().toBuffer();
}

function norm(v) {
  const n = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / n, v[1] / n, v[2] / n];
}

module.exports = { render, signedDistance, ramp, RAMP };
