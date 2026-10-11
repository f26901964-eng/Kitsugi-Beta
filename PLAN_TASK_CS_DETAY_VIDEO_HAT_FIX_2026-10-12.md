# PLAN / TASK — Detay Sayfası CS Eklenti Video Hattı (v2.4.254)

**Tarih:** 2026-10-12
**Dal:** `arena/5e1d918e-kitsugi-beta`
**Sürüm:** 2.4.253 → **2.4.254**
**Şikâyet:** Detay sayfasından eklentilere video verisi çekiliyor; eklenti arama UI’sı kullanılmıyor. Eklentiler güncellense bile azınlık çalışıyor. Çoğu süreye uymadan pat diye boş dönüyor. İsim gitse bile devamı (load / sezon / loadLinks) gelmiyor.

---

## 0. Boru hattı (film + dizi) — değiştirilmedi, belgelendi

Detay **İzle / Bölüm** → `KitsugiStreamActivity` → `StreamViewModel.startFetch` → her etkin CS eklentisi için `CsStreamRunner.getStreams`:

1. Çıplak isimle `search` (sorguya sezon/bölüm karışmaz)
2. İçerik sayfasını `load`
3. **Film:** sayfa URL’si → `loadLinks`
4. **Dizi:** bölüm listesinden S/E; yoksa sezon sayfasına in; sonra `loadLinks`

ID’li kaynaklar (`getLoadUrl`) bu hattı atlar — onlar zaten çalışıyordu.

---

## 1. Kök nedenler

| # | Kök neden | Belirti |
|---|---|---|
| KN-1 | Arama aşaması 15 sn; CF WebView çözümü 90–120 sn ve **o kutunun içinde**. Kutu patlayınca çerez yok. | Kart 2–15 sn’de boş |
| KN-2 | Dış provider timeout 75 sn, CF aşaması 120 sn’yi kesiyor. Sabır yalnızca 4 isim + bayat warmup listesi. | “Süreye uymuyorlar” |
| KN-3 | `searchWithCfRetry` yalnızca eklenti arama UI’sında. Detay İzle `safeSearch` kullanıyordu. | İsim gider, devamı gelmez |
| KN-4 | `CsTitleMatcher` Movie↔TvSeries tipini sert reddediyordu. TR eklentileri dizi sonucunu `Movie` etiketliyor. | 1.00 eşleşme “EŞLEŞME YOK” |

Bot korumasını “atlayamamak” değil: çözüme süre verilmiyordu.

---

## 2. Görevler

- [x] T1 — `needsCfPatience()`: çerezi olmayan / challenge görmüş host → 120 sn aşama
- [x] T2 — Aynı hostlarda provider timeout 75 → 240 sn (sağlıklı site hâlâ 1–3 sn’de döner)
- [x] T3 — Arama bütçesi CF’de 60 sn; `searchTimeBudgetMs` artık `var`
- [x] T4 — Detay hattı: ilk batch boş + CF kanıtı → `searchWithCfRetry`
- [x] T5 — Tip uyumsuzluğu: sim ≥ 0.85 ise tut, −0.20 ceza; ikisi varsa doğru tip kazanır. Live/Music hâlâ reddedilir
- [x] T6 — JVM testi: yalnız Movie etiketli “Supernatural” dizi aramasında tutulur
- [x] T7 — Sürüm 2.4.254 + sürüm notları
- [ ] T8 — Kullanıcıda derleme + cihaz: `adb logcat -s CsStreamRunner CS_SEARCH_ERR` → `🔐 CF_PATIENCE` / `İlk arama boş + CF kanıtı`

---

## 3. Değişen dosyalar

| Dosya | Ne |
|---|---|
| `CsStreamRunner.kt` | `needsCfPatience`, timeout, CF retry |
| `CsTitleMatcher.kt` | tip cezası, `isNonVideoType` |
| `CsTitleMatcherTest.kt` | mis-tagged exact title |
| `app/build.gradle.kts` | 2.4.254 |
| `RELEASE_NOTES.md` / `release_notes_v2.4.254.md` | notlar |
| `PLAN_TASK_CS_DETAY_VIDEO_HAT_FIX_2026-10-12.md` | bu dosya |

---

## 4. Bilinçli olarak dokunulmayan

- Eklenti kaynak/domain senkronu (DiziPal API, HDFilmCehennemi2 `.site` …) — eklenti deposu işi
- `CsPluginTypeCompatibility` film-only eklentiyi dizi için atlama (yanlış içerik riski)
- CF/WAF’ı garanti çözmek — süre verilmezse çözülemez; süre verilse de Turnstile bazen çözülmez
