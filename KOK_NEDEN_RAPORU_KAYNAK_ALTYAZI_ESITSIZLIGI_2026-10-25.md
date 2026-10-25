# KÖK NEDEN RAPORU — "Kaynak Kayırma": Akış & Altyazı Eşitsizliği
**Tarih:** 2026-10-25
**Kapsam:** Aynı bölümün farklı kaynaklardan/giriş noktalarından açılması, video verisinin ve altyazıların eşit gelmemesi
**Kod tabanı:** `app/src/main/java/com/kitsugi/animelist` (Kotlin 2.0.21, compileSdk 36)

---

## 0. Şikâyetin teknik karşılığı

Kullanıcı raporu (özet):
1. Aynı bölüm, bir kaynaktan açıldığında **video verisi daha ayrıntılı/sağlam**, başka bir kaynaktan açıldığında **veri gelmiyor veya eksik**.
2. Aynı video için A kaynağında **altyazılar geliyor**, B kaynağında **hiç gelmiyor**.
3. Beklenti: **hangi kaynaktan izlenirse izlensin**, video eklentileri de altyazı eklentileri de **ortak, eşit ve hızlı** çalışmalı.

Aşağıdaki 11 kök neden, kod üzerinde tek tek doğrulandı. Her biri "giriş noktasına / kaynağa göre değişen davranış" üretiyor.

---

## R1 — Giriş noktasına bağlı KİMLİK KAYBI (en büyük etki)

**Dosya:** `ui/screens/stream/StreamViewModel.kt`, `ui/screens/fullscreen/KitsugiPlayerViewModel.kt`

Oynatıcının altyazı katmanı yalnızca şu alanlara güveniyordu:

```
malId · aniListId · tmdbId · animeTitle
```

Giriş noktalarına göre bu alanların doluluğu değişiyor:

| Giriş noktası | Gönderilen kimlik | Sonuç |
|---|---|---|
| AniList/MAL/TMDB detay sayfası (`ApiDetailTabContents`, `EntryDetailTabContents`) | malId + aniListId + tmdbId + titleEnglish/Romaji/Native + synonyms | Tam sorgu seti |
| CS eklentisi kataloğu (`AddonExploreInline` ~L170/510) | `malId = null, aniListId = null`, tek başlık | Sadece başlıkla sorgu |
| CS eklentisi detay diyaloğu (`KitsugiAddonDetailDialog` ~L465/531) | `cs3Url` + tek başlık | Sadece başlıkla sorgu |
| İzleme geçmişi (`WatchHistoryScreen` ~L162) | `cs3Url` + tek başlık (varyantlar yok) | Başlıkla sorgu + tek eklenti |

Sonuç: IMDb/Kitsu/AniList ile çalışan altyazı eklentileri, katalog/arama/geçmiş girişlerinde **hiç sorgulanamıyor**. Kullanıcının gördüğü: "başka kaynaktan girince altyazı gelmiyor".

---

## R2 — Doğrudan URL'de ERKEN ÇIKIŞ (akış kayırması)

**Dosya:** `ui/screens/stream/StreamViewModel.kt` — `fetchStreams()` başındaki `directUrl/directApiName` bloğu (eski satır ~427-470)

```kotlin
if (resolved.isNotEmpty()) {
    _isResolvingId.value = false
    updateAddonStateSync(directApiName, isLoading = false, streams = resolved)
    return          // ← BURADAN ÇIKIYOR: diğer 8 eklenti hiç taranmıyor
}
```

`cs3Url` + `cs3ApiName` ile gelen her giriş (geçmiş, keşfet kartı, eklenti detayı, manuel arama) **tek eklentiye** kilitleniyordu. Detay sayfasından girişte ise 9 eklenti paralel taranıp ör. 92 video bulunuyordu.
**Aynı bölüm, iki farklı sonuç kümesi.** Rapor ekran görüntüsündeki "5/9 eklenti sonuç buldu · 92 video" ile "tek kaynak" farkının kaynağı budur.

---

## R3 — Kaynak değişiminde altyazı listesinin SIFIRLANMASI

**Dosya:** `ui/screens/fullscreen/KitsugiPlayerViewModel.kt`

Dört ayrı yerde liste tamamen değiştiriliyordu:

| Satır (eski) | Metot |
|---|---|
| ~1186 | `playEpisode()` |
| ~1260 | `changeStreamSource()` |
| ~1310 | `retryCurrentSource()` |
| ~1395 | `tryNextSource()` |

```kotlin
_currentSubtitles.value = stream.subtitles ?: emptyList()
```

Yani B kaynağına geçildiği anda **A kaynağında bulunan tüm ağ altyazıları listeden siliniyordu**. `fetchAutoSubtitles()` sonradan çalışsa da ağ sonucu boş dönerse (R5/R6/R7) kullanıcı altyazısız kalıyordu.

---

## R4 — Sağlayıcı içi (gömülü) altyazılar PAYLAŞILMIYOR

**Dosya:** `data/cloudstream/CsStreamRunner.kt` (L3054, L3187 → `subtitleList`), `data/repository/AddonStreamRepository.kt` (`StreamSource.subtitles`)

CloudStream eklentilerinin extractor'larından çıkan altyazılar (`subtitleCallback`) yalnızca o eklentinin ürettiği `StreamSource` nesnesine yazılıyor:

```kotlin
return finalStreams.map { stream -> stream.copy(subtitles = subtitleList.toList(), …) }
```

A kaynağından çıkarılan altyazı, B kaynağı seçildiğinde **nesneyle birlikte kayboluyor**. Ortak bir havuza yazılmıyordu.

---

## R5 — Ağ altyazıları ÖNBELLEĞE ALINMIYOR

**Dosya:** `data/repository/SubtitleRepositoryImpl.kt`

Her oynatma başlangıcında ve her kaynak değişiminde tüm altyazı eklentileri **sıfırdan** sorgulanıyor. Sonuç kalıcı değil. Eklenti geçici olarak yavaş/timeout verdiğinde veya URL değiştiğinde daha önce bulunmuş altyazılar kayboluyor.

---

## R6 — Sadece TEK başlık ile sorgulama (İngilizce indeks kaybı)

**Dosya:** `ui/screens/fullscreen/KitsugiPlayerViewModel.kt` — `fetchAutoSubtitles()` (eski sorgu bloğu)

Oynatıcı, elinde `titleEnglish`, `titleRomaji`, `titleNative`, `synonyms` olmasına rağmen altyazı sorgularında yalnızca `animeTitle` kullanıyordu:

```kotlin
val titleQuery = animeTitle.trim().takeIf { it.isNotBlank() }
```

İngilizce/romaji başlıkla indeksleyen eklentiler (OpenSubtitles, YTS, AniSub vb.) bu yüzden boş dönüyordu. Aynı eksik, akış ekranında giriş noktası başlık varyantı göndermediğinde de oluşuyor (R1).

---

## R7 — OpenSubtitles hash'i sorgu başına YENİDEN hesaplanıyor

**Dosya:** `core/player/OpenSubtitlesHasher.kt`, `SubtitleRepositoryImpl.getSubtitles()`

Her `getSubtitles` çağrısı kendi hash işini (`async`) başlatıyor: 1 × HEAD + 2 × Range isteği (64 KB + 64 KB).
Qery sayısı N ise → **N × 3 istek** sadece hash için. Ayrıca:

- Torrent kaynaklarında (`infoHash`, URL yok) hash hiç hesaplanamaz,
- HLS/embed/segmentli kaynaklarda `Content-Length` olmadığı için yine hesaplanamaz.

Bu durumda hash'e dayalı eklentiler boş döner → "bu kaynakta altyazı yok".

---

## R8 — Altyazı eklentisi seçimi ve ID ön ekleri

**Dosya:** `data/repository/SubtitleRepositoryImpl.kt`

- `idPrefixes` filtresi **tamamen kaldırılmış**; `parseIdPrefixes()` ölü kod. `idPrefixes=["tt"]` ilan eden eklentilere `kitsu:`, `mal:`, düz başlık gibi ID'ler gönderiliyor → 404/boş.
- `subtitleTypes == null` olan kayıtlar için "isim/URL'de subtitle geçiyor mu" tahmini kullanılıyor (`looksLikeSubtitleAddon`). Bu, aynı eklentinin bir cihazda altyazı, başka cihazda akış eklentisi sayılmasına yol açabilir.
- Sonuçlar `sub.id` ile tekilleştiriliyor; farklı dillerde aynı `id` dönen eklentilerde dil kaybı olabilir.

---

## R9 — "Altyazı yok" durumunun yanlış gösterilmesi (algı)

Eski kod, ağdan yeni sonuç gelmediğinde **elde altyazı olsa bile** `EMPTY` durumuna düşüp `SubtitleEvent.LoadFailed` yayınlıyordu. Kullanıcı listede altyazı görse de "bulunamadı" bildirimi alıyordu.

---

## R10 — Eklenti oturum bloğu ve sessiz eleme

**Dosya:** `data/cloudstream/CsPluginStatusTracker.kt`, `StreamViewModel.startFetch()`

- 3 hata sonrası eklenti **oturum boyunca** bloklanıyor.
- `startFetch` her yeni anahtarda `CsPluginStatusTracker.clear()` çağırıyor → aynı bölüm içinde bir kez hata alan eklenti, o bölümde ikinci kez denenmiyor.
- `KNOWN_BROKEN_PLUGINS` / `KNOWN_BROKEN_DOMAINS` / `domain_fixes.json` "blocked" listeleri geçmişte sessizce `emptyList()` dönüyordu (kısmen düzeltildi, uyarıya çevrildi).

Sonuç: kaynak listesi zaman zaman "nedensiz" daralıyor. Kullanıcı bunu "kaynak kayırma" olarak görüyor.

---

## R11 — Altyazı eklentilerinin sınıflandırılması ve CS `SubtitleAPI` yokluğu

**Ekran görüntüsü 1:** OpenSubtitles v3, AltyazıDB, YTS Subtitles, AniSub → **"Yüklü Akış Eklentileri"** başlığı altında listeleniyor.

**Doğrulama:** `app/libs/library-android-1.0.2-local.aar` içindeki `classes.jar` tarandı:

```
com/lagradost/cloudstream3/syncproviders/SyncIdName.class   ← tek sınıf
```

CloudStream'in altyazı sağlayıcı taban sınıfı `com.lagradost.cloudstream3.syncproviders.SubtitleAPI` bu runtime'da **yok**. Dolayısıyla:

- `.cs3` biçiminde bir **altyazı sağlayıcısı eklentisi** yüklenemez (sınıf çözümlenemez),
- Stremio biçimindeki altyazı addon'ları ise akış eklentisi listesinde görünür ve **akış sağlayıcısı gibi** sorgulanır → sonuç vermez.

Kullanıcı için sonuç: "altyazı eklentim yüklü ama çalışmıyor".

---

## Özet tablo

| # | Kök neden | Etki | Durum (2026-10-25) |
|---|---|---|---|
| R1 | Giriş noktasına bağlı kimlik kaybı | Altyazı/akış eşitsizliği | ✅ Düzeltildi (kimlik defteri + tüm başlık varyantları) |
| R2 | Doğrudan URL'de erken çıkış | Tek kaynağa kilitlenme | ✅ Düzeltildi (tohum modeli) |
| R3 | Kaynak değişiminde liste sıfırlama | Altyazı kaybı | ✅ Düzeltildi |
| R4 | Gömülü altyazıların paylaşılmaması | Kaynak değişince kayıp | ✅ Düzeltildi (ortak bellek) |
| R5 | Ağ altyazısı önbelleği yok | Tekrar tekrar sorgu, kayıp | ✅ Düzeltildi (kalıcı bellek + TTL) |
| R6 | Tek başlıkla sorgu | İngilizce indeks kaybı | ✅ Düzeltildi |
| R7 | Hash'in tekrar tekrar hesaplanması | Gecikme/timeout | ✅ Düzeltildi (10 dk önbellek) |
| R8 | idPrefixes / tip tahmini | Boş sonuçlar | ⏳ Faz 3 |
| R9 | Yanlış "altyazı yok" durumu | Algı hatası | ✅ Düzeltildi |
| R10 | Oturum bloğu / sessiz eleme | Liste daralması | 🟡 Kısmi (yenilemede sıfırlanıyor; kalıcı politika Faz 3) |
| R11 | Altyazı eklentisi sınıflandırması, CS SubtitleAPI yokluğu | "Yüklü ama çalışmıyor" | ⏳ Faz 3 (AAR'da `SubtitleAPI` yok — doğrulandı) |
| R12 | Altyazı devre kesici eşiği (3) çok düşük | Çok sorgulu taramada sağlıklı eklenti 5 dk bloklanıyor | ✅ Düzeltildi (eşik 8 + `resetCircuitBreakers()`) |
| R13 | Sağlayıcı altyazı linklerinin 3 gün saklanması | İmzalı linkler ölü → "altyazı var ama açılmıyor" | ✅ Düzeltildi (24 saat) |
| R14 | Tohum satırının eklenti ID'siyle adlandırılması | Aynı eklenti iki satır / yeniden deneme eşleşmiyor | ✅ Düzeltildi (`seedLabel`) |
| R15 | **Oynatıcı içi bölüm geçişi ayrı toplama yolu** (`fetchStreamsForEpisode`) | Sonraki bölümde kaynak/altyazı sayısı düşüyordu: tip filtresi yok, blok kontrolü yok, `preResolvedIds` yok, cs3 modunda erken çıkış | ✅ Düzeltildi (`CsStreamFanout.resolveAll`) |
| R16 | **Çevrimdışı oynatımda kimlik kaybı** (`DownloadsScreen` → `startWithStreamUrls`) | İndirilen bölümde altyazı "yok" görünüyordu (yalnız başlık geçiyordu) | ✅ Düzeltildi (malId/anilistId/tmdbId/sezon/bölüm aktarımı) |
| R17 | Akış sorgusu politikasının iki kopyası (StreamViewModel + PlayerVM) | Bir düzeltme sadece bir ekranda kalıyordu | ✅ Düzeltildi (tek `CsStreamFanout`) |
| R18 | Gömülü altyazılar yalnız **seçilen** kaynaktan toplanıyordu | A kaynağında 4, B kaynağında 0 altyazı; taranan diğer eklentilerin altyazıları çöpe gidiyordu | ✅ Düzeltildi (tarama anında TÜM eklentilerin altyazıları ortak havuza) |
| R19 | Akış ekranı kimlik defterine yazıyor ama **okumuyordu** | Geçmiş/katalog girişinde ID yoksa IMDb/Kitsu tabanlı addon'lar boş dönüyordu | ✅ Düzeltildi (defterden kurtarma) |

Devam eden plan: `PLAN_TASK_ORTAK_KAYNAK_ALTYAZI_OMURGASI_2026-10-25.md`
