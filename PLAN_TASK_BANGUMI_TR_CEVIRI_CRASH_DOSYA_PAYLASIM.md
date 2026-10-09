# 📋 PLAN / TASK — Bangumi Türkçe Çeviri (Tür/Etiket/Rol) + Romaji İsimler + Çökme Dosya Paylaşımı

**Durum:** Kod tarafı TAMAMLANDI ✅ · Derleme/cihaz doğrulaması BEKLİYOR ⏳ (ortamda Android SDK yok)
**Dal:** `arena/6a4e2b5e-kitsugi-beta` · **Sürüm:** 2.4.213
**Kullanıcı talebi:** CLANNAD ~After Story~ (Bangumi kaynağı) detay sayfasındaki Çince/Japonca tür-etiket,
kanji karakter/ekip isimleri, İngilizce kalan kadro rolleri ve çökme diyaloğundaki metin paylaşımı sorunları.

---

## 🎯 Hedef

Bangumi kaynaklı detay sayfasında **her metnin Türkçe (ve dil dosyası üzerinden İngilizce) görünmesi**;
çevrilemeyen adların **romaji/İngilizce** gösterilebilmesi; çökme diyaloğundan hata raporunun
**metin olarak değil, dosya (.txt) olarak** paylaşılması.

---

## ✅ TAMAMLANAN İŞLER (Kod)

### TASK-1 · Bangumi tür/meta etiketleri Çince/Japonca → TR (+EN) ve dil dosyasına bağlama
- [x] `utils/KitsugiTranslations.kt` — `bangumiTagMap` (95 etiket:恋爱, 日常, 游戏改, 日本, 京阿尼, 神作, 麻枝准, 催涙, 治愈, 人生, 科幻, 奇幻, 悬疑 …)
- [x] Dil dosyası bağlantısı: `bangumi_tag_*` kaynakları `values/strings.xml` (TR) + `values-en/strings.xml` (EN); kod önce kaynağı, sonra statik haritayı kullanır
- [x] `String.toTurkishGenre()` ve `String.toEnglishGenreForSearch()` Bangumi etiketlerini kapsar (çip tıklaması İngilizce arama terimine çevrilir)
- [x] `SearchTranslation.translateToTurkishForDisplay/displayLabel/translateToEnglishForSearch` — Etiketler çipleri artık çevriliyor

### TASK-2 · Bangumi kadro/pozisyon rollerinin Türkçeleştirilmesi
- [x] `staffRoleMap` — p1 en etiketleri: "Episode Direction", "Animation Direction", "Script/Screenplay", "Series/Unit/Action Direction"
- [x] `staffRoleMap` — v0 `relation` / p1 jp-cn etiketleri: 監督, 演出, 脚本, シリーズ構成, 絵コンテ, 分镜, 作画監督, 総作画監督, キャラクターデザイン, 色彩設計, 撮影監督, 原画, 動画, 音楽, 音響監督, 美術監督, アニメーション制作, 製作, 企画, 原作, 原案, 監修, プロデューサー, 主題歌, CGI (+ Çince karşılıklar)
- [x] `commonWordsMap` — tekil sözcükler: Direction, Script, Screenplay, Episode
- [x] `toTurkishStaffRole()` — v0'ın ` · ` (ve 、 ， ；) ayracı artık tek tek çevriliyor
- [x] Dil dosyası: `staff_role_episode_direction`, `staff_role_animation_direction`, `staff_role_script_screenplay` (TR + EN)
- [x] Tutarlılık: "Script" → **Senarist**, "Episode Direction" → **Bölüm Yönetmeni** (mevcut MAL/Jikan çevirileriyle aynı terimler)

### TASK-3 · Kanji karakter / seslendirmen / ekip isimlerine romaji + İngilizce (AniList köprüsü)
- [x] `data/remote/KitsugiAniListPersonBridge.kt` (YENİ) — `Media(idMal:)` ile tek önbellekli sorgu; karakter + VA + kadro adları (`full`/`native`/`alternative`)
- [x] Özgün (kanji) ada birebir eşleme (NFKC + boşluk/işaret temizliği); uydurma transliterasyon yok, eşleşmeyen olduğu gibi kalır
- [x] `KitsugiCharacterClient` — Bangumi dalı: karakter + seslendirmen adları zenginleştiriliyor (8 sn tavan, başarısızlıkta orijinal liste)
- [x] `KitsugiStaffClient` — Bangumi dalı: ekip adları zenginleştiriliyor (aynı tavan/önbellek)
- [x] MAL ID çözümü: `sanitizeMalId(realMalId)` öncelikli, yoksa önbellekli `resolveCrossIds`
- [x] Arayüz: mevcut `displayPersonName` + `titleLanguage` (ROMAJI/ENGLISH/NATIVE) plumbing'i veriyi gösteriyor —岡崎朋也 → Tomoya Okazaki

### TASK-4 · Öneriler / İlişkiler sekmesi başlıkları (kanji → romaji/EN)
- [x] `KitsugiBangumiDetailClient.enrichRelationTitles` — her ilişkili kayıt için önbellekli `/v0/subjects/{id}`; infobox'taki İngilizce/romaji başlık varyantları dolduruluyor
- [x] Tek kayıt başına 8 sn tavanı; kalabalık listelerde ilk 16 kayıt zenginleşir, gerisi slim veri
- [x] `fetchRelations` / `fetchRecommendations` zenginleştirmeyi çağırıyor (parse fonksiyonları değişmedi — testler kırılmıyor)

### TASK-5 · Stüdyo / yapımcı adları Japonca/Çince → Latin
- [x] `studioLatinNameMap` + `String.toLatinStudioName()` (33 şirket: 京都アニメーション → Kyoto Animation, ポニーキャニオン → Pony Canyon, ムービック → Movic, 東宝 → Toho …)
- [x] `buildNativeDetail` — stüdyo/yapımcı/kanal/dergi adları Latin'e çevriliyor; bilinmeyen (kurgusal) adlar olduğu gibi kalır
- [x] Stüdyo tıklaması Latin adla daha iyi arama sonucu veriyor

### TASK-6 · v0 yedek yoldaki seslendirme dili
- [x] `KitsugiBangumiCreditsClient.parseVoiceActor` — boş dil yerine "Japonca" varsayılanı → "Seslendirici (Japonca)"

### TASK-7 · Çökme diyaloğu: hata DOSYASI paylaşımı (metin değil)
- [x] `KitsugiCrashRecoveryDialog` — "Dosyayı Paylaş" düğmesi: `ACTION_SEND` + `EXTRA_STREAM` + FileProvider URI (`${packageName}.fileprovider`, `files-path` kökü)
- [x] `FLAG_GRANT_READ_URI_PERMISSION` + chooser ("Hata Dosyasını Paylaş")
- [x] `KitsugiCrashLogger.ensureCrashLogFile` (YENİ) — dosya yoksa/boşsa güncel rapor metniyle `crash_log.txt` oluşturur
- [x] Manifest/FileProvider değişikliği gerektirmedi (zaten tanımlı: manifest 252–258, `@xml/file_paths`)
- [x] "Kopyala" ve "İndirilenler'e kaydet (.txt)" aynen duruyor

### TASK-8 · Testler
- [x] `KitsugiTranslationsTest` (YENİ) — Bangumi tür/etiket çevirileri, geri-çeviri (arama), kadro rolleri (en/jp/cn), `·` ayracı, stüdyo adları; TR lokali taklidi + geri yükleme
- [x] `KitsugiAniListPersonBridgeTest` (YENİ) — özgün ada eşleme (boşluk/büyük-küçük duyarsız), eşleşmeme durumu, romaji/İngilizce aday üretimi
- [x] `KitsugiBangumiDetailClientTest` — stüdyo/yapımcı beklentileri Latin adlara güncellendi (Kyoto Animation, Pony Canyon, Movic)

---

## 📁 DEĞİŞEN DOSYALAR

| # | Dosya | Değişiklik |
|---|-------|-----------|
| 1 | `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt` | Bangumi etiket haritası + dil dosyası bağlantısı, kadro rolleri, stüdyo Latin haritası |
| 2 | `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchTranslation.kt` | Bangumi etiketleri çeviri hattına bağlandı |
| 3 | `app/src/main/res/values/strings.xml` | `bangumi_tag_*` (TR) + 3 yeni `staff_role_*` |
| 4 | `app/src/main/res/values-en/strings.xml` | `bangumi_tag_*` (EN) + 3 yeni `staff_role_*` |
| 5 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt` | Stüdyo Latin dönüşümü, ilişki/öneri başlık zenginleştirme |
| 6 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAniListPersonBridge.kt` | **YENİ** — AniList ad köprüsü |
| 7 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt` | Bangumi karakter/VA ad zenginleştirme |
| 8 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStaffClient.kt` | Bangumi ekip adı zenginleştirme |
| 9 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiCreditsClient.kt` | VA dili varsayılanı "Japonca" |
| 10 | `app/src/main/java/com/kitsugi/animelist/core/diagnostics/KitsugiCrashLogger.kt` | `ensureCrashLogFile` yardımcısı |
| 11 | `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCrashRecoveryDialog.kt` | Hata dosyası paylaşımı (FileProvider) |
| 12 | `app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClientTest.kt` | Stüdyo beklentileri güncellendi |
| 13 | `app/src/test/java/com/kitsugi/animelist/utils/KitsugiTranslationsTest.kt` | **YENİ** |
| 14 | `app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiAniListPersonBridgeTest.kt` | **YENİ** |

---

## 🧪 DOĞRULAMA

- [x] XML sözdizimi (values/strings.xml, values-en/strings.xml) — statik kontrol OK
- [x] Kotlin blok dengesi — eklenen kod blokları dengeli (önce/sonra delta karşılaştırması)
- [x] Harita mükerrer anahtar kontrolü — yeni girdiler çakışmıyor
- [x] Mevcut testlerin passthrough davranışı (TR dışı lokal) korunuyor
- [ ] **YAPILMASI GEREKEN (lokal):** `./gradlew :app:testDebugUnitTest --tests "*Kitsugi*"` + `assembleDebug`
- [ ] Cihazda: CLANNAD ~After Story~ (Bangumi) → Bilgi/Karakterler/Ekip/Öneriler/İlişkiler sekmeleri + çökme diyaloğu "Dosyayı Paylaş"

---

## ⚠️ BİLİNEN SINIRLAR

- Bölüm başlıkları (Bölümler sekmesi) Bangumi API'de İngilizce/romaji alanı taşımıyor — özgün ad gösterimi tasarım gereği korunuyor.
- Eşleşmeyen karakter/ekip adları kanji kalabilir (AniList karşılığı yoksa) — uydurma çeviri üretilmez.
- Kurgusal stüdyo/yapımcı adları (örn. 光坂高校演劇部) Latin haritada karşılığı olmadığı için özgün kalır.
- İlişki listesinde ilk 16 kaydın ötesindeki başlıklar slim (özgün) veri olarak kalır.
