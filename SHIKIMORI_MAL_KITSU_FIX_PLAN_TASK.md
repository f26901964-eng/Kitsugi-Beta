# Shikimori Arama + MAL/Kitsu Detay Düzeltmesi — Plan & Görev Kaydı

**Tarih:** 2026-10-08
**Dal:** `arena/6aa6380a-kitsugi-beta`
**Temel commit:** `05d1d17` (main)
**Düzeltme commit'i:** `12d0e5a`
**Durum:** Derlenmedi, test edilmedi (bu ortamda JDK/Gradle yok). Yalnızca okuma, grep ve parantez dengesi kontrolü yapıldı.

---

## 1. Bildirilen sorunlar

1. **Shikimori Arama:** Aranan başlık listede yok, alakasız öğeler (müzik/PV/reklam) görünüyor. Sorgu metni bilinmiyor.
2. **MAL detay** (ApiResultDetailPage, "Sousou no Frieren"): Karakterler, Ekip, Öneriler ve İlişkiler sonsuz skeleton; Bölümler spinner; sayfa çok yavaş açılıyor.
3. **Kitsu detay** ("your name.", Kitsu 11614): Karakterler boş skeleton.

## 2. Kök nedenler

### 2.1 Ortak istek hattı (KitsugiApiBase, RetryInterceptor)
- Jikan çağrıları (`runWithRateLimit`) tek bir global `Mutex` altında sıraya giriyordu. Kilit; retry'lar ve 429 beklemesi dahil çağrının tamamında tutuluyordu.
- RetryInterceptor 429'da `Retry-After` değerini 30 sn'ye kadar `Thread.sleep` ile bekliyordu.
- Sonuç: Karakterler, Ekip, Öneriler ve İlişkiler birbirinin arkasında bekliyordu.
- `/pictures` ve Jikan destek (`/full`) çağrıları hız sınırlayıcının dışındaydı; 429 fırtınası çıkarıyordu.

### 2.2 Sekme yükleme (ApiResultDetailViewModel, MediaEntryDetailViewModel, TvDetailScreen)
- **Bölümler yanlış indeksten yükleniyordu:** `loadTab(7)` (Yorumlar) çağrılıyordu, Bölümler indeksi 8. Sezon değişiminde (`setTargetSeason`) aynı hata vardı.
- **TV ekranında indeksler bir kaymıştı:** indeks 1 "Resimler" (no-op) olduğu için TV'de Karakterler hiç yüklenmiyordu.
- Sekme başına tekil-uçuş koruması yoktu; `LaunchedEffect` tekrar tetiklenince aynı istekler kuyruğa giriyordu.
- Sekme isteklerinde zaman sınırı yoktu; takılan istek skeleton'ı sonsuza bırakıyordu.
- `loadResult` eski sonucun sekme işlerini iptal etmiyordu; eski sonuç yeni state'e yazılabiliyordu.
- `CancellationException`, `catch (Exception)` ile yakalanıp "Hata" olarak yazılıyordu.
- **Açılış yavaşlığı:** MAL ana detay; tema, `/pictures`, ARM ve Jikan destek çağrılarının hepsi bitene kadar dönmüyordu (`coroutineScope`).

### 2.3 Kitsu karakterleri (KitsugiCharacterClient, KitsuClient)
- Veri vardı: `anime-characters` 11614 için karakter döndürüyor, `castings` seslendirmeleri döndürüyor.
- Sekme, Jikan/AniList seslendirme birleştirmesi bitene kadar gösterilmiyordu.
- Sayfalamada `return@repeat` döngüyü kırmıyordu; son kısmi sayfa tekrar tekrar eklenip kopya karakter üretiyordu.
- `anime-characters` boş dönerse yedek uç nokta (`anime/{id}/characters`) yoktu.

### 2.4 Shikimori Arama (KitsugiShikimoriClient, SearchViewModel)
- Metin aramasında Shikimori `music`, `pv` ve `cm` öğelerini de döndürüyor.
- Çoklu-platform satırı yalnızca 10 öğe aldığı için gürültü, istenen başlığı dışarı itebiliyordu.
- Metin aramasında varsayılan tür filtresi yoktu.

## 3. Yapılan değişiklikler

| Dosya | Değişiklik |
|---|---|
| `core/network/KitsugiHttpClient.kt` | `metadataClient` eklendi (aynı havuz, `callTimeout` 20 sn) |
| `core/network/RetryInterceptor.kt` | `Retry-After` üst sınırı 30 sn → 5 sn |
| `data/remote/KitsugiApiBase.kt` | Global Mutex kaldırıldı; slot rezervasyonu (istek başlangıçları arasında 450 ms, kilit yalnızca slot hesabında); 429 cezası yalnızca `api.jikan.moe` ve `shikimori.io` için; `performGet` ve `executeGetRequestOrThrow` `metadataClient` kullanıyor |
| `data/remote/KitsugiMalDetailClient.kt` | Zenginleştirme ayrı kapsamda, en fazla 6 sn bekleniyor; `/pictures` ve destek çağrısı `runWithRateLimit` içinde |
| `data/remote/KitsugiCharacterClient.kt` | VA'lı Kitsu listesi hemen dönüyor; VA birleştirme ayrı kapsamda, en fazla 8 sn |
| `data/remote/KitsuClient.kt` | Sayfalama durdurma koşulu düzeltildi; `anime/{id}/characters` yedeği; castings sayfalama |
| `data/remote/KitsugiShikimoriClient.kt` | Metin aramasında varsayılan tür: `tv,movie,ova,ona,special,tv_special`; `censored=true` |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | Bölümler = 8; tekil-uçuş (`force` yalnızca sezon değişiminde); sekme başına süre sınırı (25 sn, Bölümler 90 sn); `CancellationException` yeniden fırlatılıyor; `loadResult` eski işleri iptal ediyor |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | `setTargetSeason`: `loadTab(7)` → `loadTab(8)` |
| `ui/tv/detail/TvDetailScreen.kt` | Sekme indeksleri düzeltildi (Karakterler 2, Ekip 3, İlişkiler 5, Grafikler 6, Yorumlar 7, Bölümler 8) |
| `ui/screens/search/SearchViewModel.kt` | Çoklu-platform Shikimori satırı 10 → 24 öğe |

## 4. Kabul ölçütleri (çalışma tanımı)

- Hiçbir sekme sonsuza kadar skeleton'da kalmaz. Süre dolunca hata durumuna geçer; sekme yeniden seçilince tekrar dener.
- Bölümler sezon değişiminde yeniden yüklenir.
- Kitsu karakterleri (VA'lı liste) Jikan'ı beklemeden görünür.
- Shikimori metin aramasında müzik/PV/reklam, kullanıcı tür seçmedikçe gelmez. Sunucunun sıralaması korunur.

## 5. Doğrulama durumu

- **Derleme ve birim test:** yapılmadı (JDK/Gradle yok).
- **Yapılan kontroller:** referans/grep kontrolü, değiştirilen dosyalarda parantez dengesi, `git diff --check`, gizli anahtar taraması.
- **Canlı testler:** Jikan bu ortamdan erişilemedi. Shikimori ve Kitsu uç noktaları kısmen sorgulandı; Shikimori GraphQL 404 döndü, test edilemedi.

## 6. Açık kalanlar

- Shikimori'de kullanıcının tam sorgu metni bilinmiyor. Çok kelimeli ve İngilizce sorgularda sunucunun bulanık eşleşmesi sürebilir. Yerel yeniden sıralama yapılmadı (REST listesinde İngilizce/eş adlar yok).
- `JikanSearchClient` içindeki karakter, kişi ve yapımcı listeleme çağrıları hâlâ hız sınırlayıcının dışında (4 sn zaman sınırı nedeniyle değiştirilmedi).
- Jikan `/full` çağrılarındaki tekrar birleştirilmedi.
- Zaman sınırlarının sonucu: çok yavaş durumda fragman, `/pictures` veya Kitsu seslendirmeleri boş kalabilir.
- Kitsu staff uç noktası değiştirilmedi.

## 7. Önerilen doğrulama adımları

1. Android Studio'da derle (Build > Make Project). Hata çıkarsa çıktıyı paylaş.
2. MAL: Frieren (Sousou no Frieren) detayında tüm sekmeleri gez; Bölümler'de sezon değiştir.
3. Kitsu: "your name." (11614) → Karakterler.
4. Shikimori Arama: aranan sorguyu ve beklenen başlığı not et.
