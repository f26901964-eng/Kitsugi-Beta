# PLAN / TASK — Kaynak Listesi Bilgi Doğruluğu (Stream Info Accuracy)

**Dal:** `arena/e4d43fa1-kitsugi-beta`
**Commit:** `6cbb44e` — _fix(stream): kaynak rozetlerinde tahmin yerine gerçek veri_
**Temel alınan commit:** `5ef3c9f` (v2.4.194)
**Tarih:** 2026-10-07

---

## 1. Problem Tanımı

Video izleme kaynak listesi (eklentilerden otomatik video bulma sayfası), kaynaklardan gelen
gerçek verileri değil **tahminleri** gösteriyordu:

- Her kaynakta `400p` kalite rozeti
- Kaynak bilgisi olmadığı halde `Altyazılı`, `Dublaj`, `TR`, `EN` rozetleri
- Torrent olmayan akışlarda `Önbellekte` rozeti
- Kalite bilinmediğinde uydurma `1080p (HD)` / `HD` / `720p`

## 2. Kök Neden Analizi

| # | Belirti | Kök neden | Dosya |
|---|---------|-----------|-------|
| 1 | **"400p"** | CloudStream `Qualities.Unknown.value == 400`; `getQualityString()` bunu `"${quality}p"` yapıyordu | `CsStreamRunner.kt` |
| 2 | Uydurma `1080p (HD)` / `HD` | "bulamazsan varsayılan yaz" fallback'leri; belirsiz `hd`/`sd` token'ları | `StreamHelpers.kt`, `StreamSorter.kt`, `KitsugiStreamSelectorBottomSheet.kt` |
| 3 | Her TR eklentisinde `Altyazılı` | `detectStreamLang` içindeki `isTrAddon` kuralı | `StreamHelpers.kt` |
| 4 | Rastgele `Dublaj`/`Altyazılı` | alt-dize eşleşmesi: "**Sub**aru", "**Dub**ai" | `StreamHelpers.kt` |
| 5 | Yanlış `Önbellekte` | `getCacheState` varsayılanı `CACHED` | `StreamHelpers.kt`, `KitsugiStreamSelectorBottomSheet.kt` |
| 6 | Fallback'lerde sabit kalite | `ÖLÜ KANAL` → `720p`, ham URL → `HD`/`1080` | `CsStreamRunner.kt` |

Referans: [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream) —
`library/.../utils/ExtractorApi.kt` (`Qualities`), `MainAPI.kt` (`DubStatus`, `Episode`).

## 3. Uygulanan Çözüm

### 3.1 Kanıt kaynağı modeli — `StreamInfoResolver.kt` (YENİ)
```
StreamInfoOrigin = PROVIDER | MEASURED | FILENAME | NONE
```
Öncelik: **MEASURED > PROVIDER > FILENAME > NONE (rozet yok)**

### 3.2 Gerçek ölçüm — `StreamProbe.kt` (YENİ)
- HLS master playlist → `#EXT-X-STREAM-INF ... RESOLUTION=WxH` → gerçek varyant çözünürlükleri
- `#EXT-X-MEDIA:TYPE=AUDIO|SUBTITLES ... LANGUAGE="tr"` → gerçek ses / altyazı dilleri
- Progressive dosya → `HTTP HEAD` `Content-Length` → gerçek boyut
- URL bazlı önbellek, eşzamanlılık sınırı 3, 8 sn timeout, yalnızca bilgi eksikse çalışır

### 3.3 Sağlayıcı meta verisi
- `Qualities.Unknown (400)` → `null` (`csQualityHeightOrNull`)
- `CsEpisodeMatcher.findDubStatusForEpisodeData()` → `StreamSource.providerAudioKind`
- `loadLinks` altyazı dosyaları → `CC TR` / `CC EN` rozeti

### 3.4 Arayüz
- Bilinen kalite → renkli rozet; bilinmiyorsa `Kalite ?` veya `Oto (HLS)`
- Dil rozeti yalnızca kanıt varsa; `🎬 Standart` uydurması kaldırıldı
- Ölçülmüş / sağlayıcı kaynaklı rozetlerde `✓` işareti
- Önbellek rozeti yalnızca torrent akışlarında
- Filtre çipleri (Altyazı / Dublaj / 1080p / 720p) aynı kanıta dayalı veriyi kullanır

## 4. Değişen Dosyalar (13)

| Dosya | Durum |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/repository/StreamInfoResolver.kt` | YENİ |
| `app/src/main/java/com/kitsugi/animelist/data/repository/StreamProbe.kt` | YENİ |
| `app/src/test/java/com/kitsugi/animelist/data/repository/StreamInfoResolverTest.kt` | YENİ |
| `docs/STREAM_INFO_ACCURACY.md` | YENİ |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsEpisodeMatcher.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/data/repository/AddonStreamRepository.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/data/repository/StreamSorter.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamCard.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamHelpers.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiStreamSelectorBottomSheet.kt` | değişti |
| `app/src/main/java/com/kitsugi/animelist/ui/tv/stream/TvStreamScreen.kt` | değişti |

Toplam: **+1086 / −189 satır**

## 5. Doğrulama (TASK)

- [x] Kök neden analizi ve CloudStream kaynak kodu referansı
- [x] Kanıta dayalı bilgi katmanı + gerçek akış ölçümü
- [x] Birim testleri (`StreamInfoResolverTest`)
- [x] Dokümantasyon
- [ ] **Yerelde derleme:** `./gradlew assembleDebug` (sandbox'ta JDK/Gradle yok)
- [ ] **Birim testleri:** `./gradlew testDebugUnitTest --tests "*StreamInfoResolverTest*"`
- [ ] Cihazda görsel doğrulama: Doraemon S1B1 → `400p` yerine `Kalite ?` / gerçek çözünürlük
- [ ] Performans kontrolü: uzun kaynak listesinde HLS prob yükü

## 6. Sonraki Adımlar (kapsam dışı)

1. **Yanlış içerik eşleşmesi:** `RecTV → Stand By Me Doraemon 2` sonucu Doraemon S1B1 için
   dönüyor → `CsTitleMatcher` / `CsEpisodeMatcher` sıkılaştırması gerekiyor.
2. Ölçülen kalite bilgisinin izleme geçmişi ve indirme kayıtlarına da yazılması.
3. `StreamProbe` sonuçlarının disk önbelleğine alınması (oturumlar arası).
4. Ayarlara "kaynakları ölç (ağ kullanır)" anahtarı eklenmesi.
