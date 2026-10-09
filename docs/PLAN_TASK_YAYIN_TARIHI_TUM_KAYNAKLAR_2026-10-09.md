# Kitsugi — Yayın Tarihi Mantığının Tüm Kaynaklarda Birleştirilmesi: Teşhis & Uygulama

**Tarih:** 2026-10-09 · **Dal:** `arena/9ef3f334-kitsugi-beta` · **Temel commit:** `fc5e622` (v2.4.224)
**Hedef sürüm:** v2.4.225 · **Durum:** Kod değişiklikleri TAMAM; sandbox'ta Android SDK/google Maven erişimi yok → tam Gradle derlemesi kullanıcı tarafında. Çekirdek biçimlendirici sandbox'ta Kotlin 2.4.21 + JDK ile **35/35 doğrulama kontrolüyle** çalıştırıldı.

---

## 1. Kullanıcı İstekleri

> 1. "Yakında Yayında sayfalarında şu [detay sayfalarında] işaretlediğim yayın tarihi
>    bilgileri vb. düzgün şekilde olsun."
> 2. "Tüm kaynaklardaki bu ayrıntıların olması gereken açılır sayfalara veya kutucuklarda
>    bu yayın tarihleri görünmeli — hangi kaynağa ait olursa olsun bu mantık ile çalışması
>    gereken kısım varsa…"

Ekran işaretleri:
- **Detay (TMDB):** `Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında` → İSTENEN biçim.
- **Yakında Yayında listesi:** `Bölüm -1 · 19 gün sonra yayında` → bozuk (anlamsız "Bölüm -1", tarih YOK).

## 2. Teşhis — Üç Sorun

| # | Sorun | Kanıt |
|---|---|---|
| 1 | **Tarih yok / "Bölüm -1"**: Listelerdeki `NextAiringChip` yalnız geri sayım gösteriyor; TMDB listeleri `"-1|epoch"` (bilinmeyen bölüm) taşıdığı için `Bölüm -1` yazılıyor; filmler de `-1` alıyor. | `NextAiringChip.kt`, `TmdbDiscoverClient.parseTmdbDiscoverList*` |
| 2 | **Biçim dağınıklığı**: TMDB detay düz metin (`"Bölüm 2, 2026-10-15 tarihinde yayında"`), AniList `"ep|epoch"` → detay sayfası iki farklı çıktı üretiyor; geri sayım metninde tarih, tarih metninde geri sayım yok. | `TmdbMediaDetailClient.kt:137`, `DetailSharedComponents.rememberAiringCountdownText`, `DetailedSeasonalMediaCard`, `ExploreComponents.AiringSoonCountdownText` |
| 3 | **Kaynak boşlukları**: Simkl/Shikimori detayları `nextAiringEpisode` doldurmuyor; `enrichDetail` AniList yedeği yalnız simkl/jikan/anilist/kitsu/bangumi kimliklerini çözümlüyordu ("shikimori", "tmdb" → `else -> null`); TMDB filmlerinde yaklaşan vizyon tarihi hiç gösterilmiyordu. | `KitsugiDetailClient.enrichDetail`, `KitsugiShikimoriClient.fetchDetail`, `TmdbMediaDetailClient` |

## 3. Uygulama

### 3.1 Yeni ortak katman — `utils/NextAiringFormat.kt`
- `parse()`: `"ep|epoch"` + eski TMDB düz metni (tarih/bölüm numarasına indirgenir, indirgenemezse korunur).
- Konvansiyon (`AiringEntry` ile aynı): `ep > 0` bölüm · `ep == 0` **Film** · `ep < 0` **Dizi** (bölüm bilinmiyor).
- `isoDateToEpoch()`: yerel gece yarısı → `formatDate()` geri dönüşümü her zaman aynı takvim tarihi (TMDB `air_date` bir takvim tarihidir).
- `detailText()`: `Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)` / `Film, … tarihinde vizyonda (…)` / `Dizi, … tarihinde yayında (…)` / yayında sonrası `Bölüm 2 yayınlandı!`.
- `chipText()`: `Bölüm 2 · 2026-10-15 · 6 gün sonra yayında` / `Film · … · … vizyonda` / `Dizi · …`.
- `countdownText()`: `az sonra` → `N dakika` → `N saat` → `N gün/hafta/ay` skalası.

### 3.2 UI — tek tip görüntüleme
| Bileşen | Yeni davranış |
|---|---|
| `NextAiringChip` (keşif/ranking/see-all kartları) | `chipText` ile tarih + geri sayım; `Bölüm -1` → `Dizi`; statik overload da aynı biçimi kullanır |
| `DetailedSeasonalMediaCard` | Kendi geri sayım döngüsü kaldırıldı → ortak `chipText` |
| `ExploreComponents.AiringSoonCountdownText` (şerit) | Tarih eklendi (`Bölüm 5 · 2026-10-12 · 3 gün sonra yayınlanacak`); `ep <= 0` "çıkıyor" sözcüğü + renk mantığı korundu |
| `DetailSharedComponents` (`rememberAiringCountdownText`, `AiringCountdownCard`) | `detailText` ile **işaretlenen biçim + geri sayım**; iki kopya tek akışta birleşti |
| `KitsugiAiringCalendarComponents.AiringTimeText` | `2026-10-15 14:30 • 19 gün sonra yayında` (tarih + saat) |

### 3.3 Veri katmanı — tüm kaynaklar
| Kaynak | Değişiklik |
|---|---|
| TMDB detay | `next_episode_to_air` → `"ep|epoch"` (ep bilinmiyorsa `-1`); gelecek vizyon tarihli **filmler** `0|epoch`; tarih çözülemezse eski düz metin yedeği |
| TMDB listeler | Film `0|epoch`, dizi `-1|epoch` (eski: ikisi de `-1`) |
| Shikimori | `next_episode_at` → `"ep|epoch"` (`episodes_aired + 1`); yoksa yedek |
| `enrichDetail` yedek zinciri | + `"shikimori"` (önce `resolveMalIdFromShikimori`) ve `"tmdb"` (`realMalId`) |
| `ExploreViewModel` TMDB şeridi | Sabit `-1|` yerine `entry.episode` (Film=0, prömiyer=1, bilinmeyen=-1) |
| AniList / MAL / Kitsu / Bangumi / Simkl | mevcut yedek zincirle kapsandı (kanıt: `enrichDetail` → `fetchNextAiringEpisodeOnly`) |

Önbellek uyumu: Eski TMDB düz metinleri `parse()` ile normalize edilip aynı biçimde gösterilir.

## 4. Doğrulama

- **Sandbox (yapıldı):** `NextAiringFormat` Kotlin 2.4.21 ile derlendi; `NextAiringFormatTest` beklentilerinin birebir kopyası **35/35 PASS**. Düzenlenen 13 Kotlin dosyasında sözdizimi taraması temiz (0 syntax hatası).
- **Kullanıcı tarafı:** `./gradlew :app:compileFossDebugKotlin :app:testFossDebugUnitTest`
  - `NextAiringFormatTest` (15 test) + `TmdbMediaDetailClientTest` (2 yeni test) yeşil olmalı.
- **Görsel kontrol:**
  1. TMDB → "Yakında Yayında" (Tümü/see-all): kartlarda `Film · 2026-10-28 · 19 gün sonra vizyonda` / `Dizi · 2026-10-15 · …` — `Bölüm -1` YOK.
  2. Her kaynakta detay sayfası: `Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)` — TMDB'de olduğu gibi AniList/MAL/Shikimori/Bangumi/Simkl/Kitsu detaylarında da.
  3. Yaklaşan vizyon tarihli TMDB filmi detayında "Yaklaşan Yayın: Film, … tarihinde vizyonda (…)".
  4. Haftalık takvim kartlarında tarih + saat + geri sayım.

## 5. Notlar / Sınırlar

- TMDB yalnız tarih (saat yok) verir → geri sayım yerel gece yarısına göre yaklaşık; AniList kesin saat verir.
- `next_episode_to_air` tarihi geçmişte kalan TMDB kayıtları `Bölüm N yayınlandı!` gösterir (eski davranış "tarihinde yayında" ile geçmişi yanlış sunuyordu).
- Shikimori `next_episode_at` alanı bazı kayıtlarda yoktur → AniList yedeği devreye girer.
- Tam Gradle derlemesi sandbox'ta ağ kısıtı (google Maven erişilemez) nedeniyle çalıştırılamadı.
