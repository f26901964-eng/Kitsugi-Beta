# PLAN & TASK — Ortak Kaynak/Altyazı Omurgası (Kaynak Kayırmama)
**Oluşturma:** 2026-10-25 · **Dal:** `arena/8eba864b-kitsugi-beta`
**Commit:** `36c1c1f` (dal: `arena/8eba864b-kitsugi-beta`, GitHub'a push edildi)
**Drive:** https://drive.google.com/drive/folders/1n8IODscB408nymMEWlRe0Uh_i3sZY6yJ
**Durum:** 🟡 Faz 1+2 kodlandı (derleme doğrulaması YAPILMADI — bkz. §7), Faz 3 bekliyor
**Bu dosya kaldığın yerden devam etmek için tasarlandı.** Her fazın altında "SONRAKİ ADIM" maddesi var.

> Amaç: Hangi giriş noktasından / hangi kaynaktan izlenirse izlensin **video eklentileri** ve
> **altyazı eklentileri** aynı kimlikle, aynı havuzdan, eşit ve hızlı çalışsın. Kaynak kayırma yok.

---

## 1. Hızlı durum özeti

| Faz | Konu | Durum |
|---|---|---|
| 0 | Kök neden analizi (11 madde) | ✅ `KOK_NEDEN_RAPORU_KAYNAK_ALTYAZI_ESITSIZLIGI_2026-10-25.md` |
| 1 | Ortak kimlik (MediaIdentity + Registry) | ✅ kodlandı |
| 2 | Ortak altyazı havuzu (SubtitleMemoryStore + SubtitleHub) | ✅ kodlandı |
| 2b | Oynatıcı entegrasyonu (kaynak değişiminde kayıp yok) | ✅ kodlandı |
| 2c | Akış tarafı eşitlik (doğrudan URL = tohum, erken çıkış kaldırıldı) | ✅ kodlandı |
| 2d | Hash önbelleği | ✅ kodlandı |
| 2e | R8/Proguard: kalıcı JSON sınıfları | ✅ kodlandı |
| 2f | İndirme (AnimeDownloader) ortak merkeze bağlandı | ✅ kodlandı |
| 2g | Aynı anda çift altyazı taraması engellendi + bellek tazelik penceresi | ✅ kodlandı |
| 2h | Devre kesici eşiği, sağlayıcı altyazı TTL'i, tohum satır adı, medya tipi | ✅ kodlandı |
| 3 | Altyazı eklentisi sınıflandırma / idPrefixes / CS SubtitleAPI | ⏳ bekliyor |
| 4 | Cihaz testi + sürüm notu | ⏳ bekliyor |

---

## 2. Faz 1 — Ortak kimlik (TAMAM)

### Yeni dosyalar
- `app/src/main/java/com/kitsugi/animelist/core/identity/MediaIdentity.kt`
  - Tüm giriş noktaları için tek veri modeli (başlık + varyantlar + MAL/AniList/TMDB/Bangumi + IMDb/Kitsu + sezon/bölüm).
  - `contentKey` (ID varsa ID, yoksa normalize başlık + yıl), `subtitleKey`, `subtitleKeyCandidates`.
  - `withResolved(ResolvedIds)` → eksik ID'leri tamamlar.
- `app/src/main/java/com/kitsugi/animelist/core/identity/MediaIdentityRegistry.kt`
  - Çözülen kimlikleri **hem içerik anahtarı hem başlık+yıl anahtarı** ile saklar.
  - `lookupByTitle(title, year)` → ID'siz girişlerde kimliği geri kazandırır.
  - Kalıcılık: SharedPreferences + Gson, 400 kayıt, 30 gün TTL. **Room şeması değişmedi.**

### Değiştirilen dosyalar
- `ui/screens/stream/StreamViewModel.kt`
  - `fetchStreams()` içinde ID çözümünden hemen sonra `MediaIdentityRegistry.remember(...)` çağrısı eklendi (satır ~545-571).
    Böylece akış ekranının çözdüğü kimlikler oynatıcıya/altyazı katmanına **yayılır**.
- `ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
  - `currentIdentity()`: player'daki tüm bilgilerden (title/english/romaji/native/synonyms + registry) kimlik üretir.
  - `init {}` içinde registry + bellek başlatılır.

**SONRAKİ ADIM (Faz 1):** `KitsugiStreamActivity.start(...)` çağrılarının hepsi `titleEnglish/titleRomaji/titleNative/synonyms` gönderiyor mu denetle.
Bilinen eksikler: `ui/screens/history/WatchHistoryScreen.kt` (~L162) yalnızca `title` gönderiyor; geçmiş kaydında
(`WatchHistoryEntry`) başlık varyantı alanı yok → geçmişe varyant eklemek için Room migration gerekir (Faz 3'te değerlendir).

---

## 3. Faz 2 — Ortak altyazı havuzu (TAMAM)

### Yeni dosyalar
- `core/subtitle/SubtitleMemoryStore.kt`
  - İçerik+bölüm anahtarına göre altyazı saklar; **kaynak bağımsız**.
  - `providerBound` altyazılar (sağlayıcı içinden çıkarılanlar) 3 gün, ağ eklentilerinden gelenler 14 gün TTL.
  - 600 anahtar / anahtar başına 80 kayıt sınırı; en eskiler kırpılır.
  - SharedPreferences + Gson → migration yok.
- `core/subtitle/SubtitleHub.kt`
  - Tek giriş noktası: `load(identity, videoUrl, headers, filename)`.
  - Sıra: (1) bellek (anında) → (2) kimlik zenginleştirme (registry + resolver + son çare TMDB başlık araması) → (3) ağ sorguları → (4) bulunanları belleğe yaz.
  - `buildQueries()`: IMDb → Kitsu → AniList → MAL → **tüm başlık varyantları**; en fazla 7 sorgu.
  - `rememberProviderSubtitles()`: kaynağın gömülü altyazılarını havuza yazar.
  - `clearMemory()`: ayarlar ekranından "altyazı belleğini temizle" için hazır.

### Değiştirilen dosyalar
- `ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
  - `subtitleHub` (lazy), `publishKnownSubtitles()`, `mergeSubtitlesIntoCurrent()`, `dedupeSubtitleInputs()`, `applyStreamSubtitles(stream)` eklendi.
  - `fetchAutoSubtitles()` yeniden yazıldı: eski ~60 satırlık sorgu üretimi yerine `subtitleHub.load(...)`; kaynak altyazıları da listeye dahil.
  - **4 sıfırlama noktası kaldırıldı** (`playEpisode`, `changeStreamSource`, `retryCurrentSource`, `tryNextSource`) → `applyStreamSubtitles(stream)`.
  - `initialize()`: ilk kaynağın gömülü altyazıları havuza yazılır.
  - "Ağdan yeni altyazı gelmedi ama elimizde var" → `SUCCESS` (eskiden `EMPTY` + `LoadFailed`).
  - Tekilleştirme URL yerine **dil+ad** ile; yerel dosya yolu uzak URL'ye tercih edilir (çift parça önlenir).
- `core/player/OpenSubtitlesHasher.kt`
  - 10 dakikalık, 12 kayıtlı hash önbelleği (`clearCache()`); başarısız hash önbelleğe yazılmaz.
- `SubtitleMemoryStore` **tazelik** API'si: `lastUpdatedAt(keys)`, `isFresh(keys, maxAgeMs)`.
- `SubtitleHub.load(..., forceRefresh: Boolean = false)`:
  bellek 2 saat içinde doldurulduysa ağ turu **atlanır** (kaynak değişimi anında);
  `forceRefresh = true` ile (kullanıcı "yenile") ağa çıkılır.
- `KitsugiPlayerViewModel`:
  - `subtitleFetchJob` ile **eşzamanlı çift tarama** engellendi (init + kaynak değişimi + retry yarışı).
  - `initialize()` içinde `publishKnownSubtitles(...)`: video hazır olmadan da bilinen altyazılar listede.
  - `refreshSubtitles()` → `fetchAutoSubtitles(forceNetwork = true)`.
- `core/download/AnimeDownloader.kt`: kendi ID/sorgu mantığı kaldırıldı, **`SubtitleHub`** kullanıyor.
  İndirilen bölüm ile oynatılan bölüm aynı altyazı havuzunu paylaşır.
- `app/proguard-rules.pro`: `MediaIdentityRegistry$Entry` ve `SubtitleMemoryStore$StoredSubtitle`
  için R8 keep kuralları. **Bu olmadan release derlemede JSON geri okunamaz ve tüm ortak omurga
  sessizce devre dışı kalırdı** (alanlar kırpılır/yeniden adlandırılır).

---

## 4. Faz 2c — Akış tarafında eşitlik (TAMAM)

`ui/screens/stream/StreamViewModel.kt` — `fetchStreams()`:
- Doğrudan `cs3Url` sonucu artık **tohum**: anında gösterilir, ardından **tüm eklentiler taranır**.
- Eski `return` kaldırıldı; `directSeed` değişkeni ile sonuç korunur.
- Stremio görev listesi kurulurken tohum satırı silinmiyor (`seededStates`).
- CS eklenti döngüsünde tohum sonuçları o eklentinin sonuçlarına **eklenir** (URL/infoHash ile tekilleştirme).
- Placeholder'lar tohum satırı varsa ikinci kez eklenmez (çift satır yok).

**Beklenen davranış:** geçmişten, keşfetten, eklenti detayından veya detay sayfasından giriş → aynı eklenti havuzu, aynı sayıda kaynak.

---

## 5. Faz 3 — Sıradaki işler (ÖNCELİK SIRASI)

### 3.1 Altyazı eklentisi sınıflandırması (R11) — *yüksek öncelik*
- [ ] `Eklentiler & Kaynaklar` ekranında altyazı addon'ları **"Altyazı Eklentileri"** grubunda göster
      (`ManagedAddonEntity.subtitleTypes != null` veya `looksLikeSubtitleAddon(...)` → altyazı).
      Dosyalar: `ui/components/addons/*`, `ui/app/AddonViewModel.kt`, `data/repository/CloudstreamRepoRepository.kt`.
- [ ] Altyazı addon'ları **akış listesinde sorgulanmasın** (`StreamViewModel.fetchStreams` → `buildStremioStreamTasks` filtresi).
- [ ] Tanılama: "bu addon altyazı sağlıyor mu" bilgisini `CsTrace`e yaz.

### 3.2 `.cs3` altyazı sağlayıcıları (R11) — *tasarım kararı gerekli*
- Runtime'da `com.lagradost.cloudstream3.syncproviders.SubtitleAPI` **yok** (AAR taraması: yalnız `SyncIdName`).
- İki seçenek:
  - **A)** `SubtitleAPI` sınıfını uyumluluk katmanı olarak projeye ekle (yansıma/`SubtitleFile` tabanlı çağrı) → `.cs3` altyazı eklentileri çalışır.
  - **B)** Altyazı kaynaklarını Stremio addon yoluna yönlendir ve `.cs3` altyazı eklentilerini "desteklenmiyor" olarak işaretle.
- Karar verilmeden kod yazma. Mevcut durumda bu eklentiler **akış sağlayıcısı** gibi görünüp boş dönüyor.

### 3.3 idPrefixes / tip filtresi (R8)
- [ ] `parseIdPrefixes()` (şu an ölü kod) ile **altyazı sorgusu başına** uyum kontrolü:
      ID `tt...` değilse `tt` prefix'li eklentiye sorma; `kitsu:`/`mal:` prefix'li eklentilere yalnız uygun ID ile git.
- [ ] `SubtitleRepositoryImpl` dedup anahtarını `sub.id` yerine `id + url + lang` yap.

### 3.4 Oturum bloğu (R10)
- [ ] `CsPluginStatusTracker`: 3 hata bloğu **bölüm bazında** değil "kaynak başına 10 dk" olsun.
- [ ] `fetchStreams` içinde "bu bölüm için zaten denendi" bilgisini `CsTrace` ile görünür yap.

### 3.5 Performans
- [ ] `SubtitleHub`: bellek isabeti varsa ağ sorgusunu **arka planda** yap (şu an her açılışta 7 sorgu).
- [ ] Zaten `SubtitleFileCache` var; bellek isabetinde dosyayı önden indirip `SubtitleInput.url`'ü yerel yol yap → anında altyazı.
- [ ] `SubtitleMemoryStore` kırpma işini IO dispatcher'da yap (şu an çağıran thread'de).

### 3.6 UX
- [ ] Oynatıcı altyazı sayfasında kaynak etiketi: "bellek" / "kaynak (gömülü)" / eklenti adı.
- [ ] Ayarlar → "Altyazı belleğini temizle" butonu (`SubtitleHub.clearMemory()` hazır).
- [ ] `Kaynak seç` sayfasında "X/Y eklenti sonuç buldu" sayacına **hata nedeni** rozetleri (zaten kısmen var).

---

## 6. Cihaz testi kontrol listesi (derleme sonrası İLK iş)

0. **Birim test:** `./gradlew :app:testDebugUnitTest --tests '*MediaIdentityTest*'` (9 test).
1. **Eşitlik:** Frieren S01E01'i (a) AniList detayı, (b) bir CS eklentisinin kataloğu, (c) izleme geçmişi üzerinden aç.
   → "Kaynak seç" ekranındaki **eklenti sayısı ve video sayısı aynı** olmalı (küçük oynama normal, tek kaynağa düşme HATA).
2. **Altyazı sürekliliği:** A kaynağında altyazı listesini aç (not al) → oynatıcıda kaynak değiştir (B) → **aynı altyazılar hâlâ listede** olmalı.
3. **Altyazı kimliği:** Katalogdan (ID'siz) aç → altyazı listesinde hem Türkçe hem İngilizce girişler görünmeli.
4. **Torrent kaynağı:** hash hesaplanamayan kaynakta altyazı listesi boşalmamalı (bellek + başlık sorguları devreye girer).
5. **Tekrar açılış:** Aynı bölümü kapat/aç → altyazılar **anında** gelmeli (bellek), sonra ağ sonuçları eklenmeli.
6. **Çift parça kontrolü:** Altyazı menüsünde aynı dil+eklenti **tek satır** olmalı.
7. **Log kontrolü:** `adb logcat -s SubtitleHub SubtitleMemory MediaIdentityRegistry KitsugiPlayerViewModel StreamViewModel`
   - `Altyazı toplandı: bellek=…, ağ=…`
   - `Doğrudan URL … tohum olarak alındı`
   - `Kimlik kayıt defterinden kurtarıldı: mal=… anilist=…`

---

## 7. ⚠️ Doğrulama durumu (önemli)

Bu çalışma **derlenmeden** teslim edildi: sandbox'ta JDK, Android SDK ve Gradle yok
(`java: command not found`, `ANDROID_HOME` boş). Yapılanlar:

- ✅ Tüm yeni dosyalar için parantez/süslü parantez dengesi kontrolü
- ✅ Kullanılan API imzalarının kaynak koddan doğrulanması (`ResolvedIds`, `KitsugiIdResolver.resolveIds`,
  `SubtitleRepositoryImpl.getSubtitles`, `StreamSource`, `SubtitleInput`, `JikanSearchResult.tmdbId: Int?` vb.)
- ✅ **4 yeni dosya GERÇEKTEN DERLENDİ** — Kotlin derleyici npm üzerinden (`kotlin-compiler@2.4.21`,
  `kotlin-compiler@2.0.21`) + JDK (pypi `jdk4py`, Temurin 25) çekildi; yeni dosyalar için
  Android/Gson/coroutines/uygulama tipleri stub'landı ve `kotlinc` ile **0 hata** alındı
  (`MediaIdentity`, `MediaIdentityRegistry`, `SubtitleMemoryStore`, `SubtitleHub`).
- ✅ Tip çıkarımı riskli ifadeler ayrı bir "harness" dosyasında birebir kopyalanıp derlendi
  (`runCatching{}.getOrDefault(emptyList())`, `firstNotNullOfOrNull`, destructuring, dedupe).
- ✅ Değiştirilen 4 dosya **sözdizimi** açısından derleyiciden geçirildi (yalnızca "unresolved
  reference" hataları var; sözdizimi hatası yok).
- ❌ Tam **AGP/Gradle derlemesi** yapılmadı (Android SDK ve bağımlılıklar yok).

**Derlemede ilk bakılacak yerler:**
1. `KitsugiPlayerViewModel` — `subtitleHub` lazy alanının sınıf gövdesinde mi kaldığı (satır ~1848 civarı)
2. `StreamViewModel` — `directSeed` destructuring (`takeIf { (name, _) -> … }`) → harness ile doğrulandı
3. `OpenSubtitlesHasher` — `cache[key]?.let { … return entry.result }` non-local return
4. `AnimeDownloader` — yeni `MediaIdentity(...)`/`SubtitleHub(...)` çağrılarının import biçimi
   (tam nitelikli yazıldı, import gerekmez)

---

## 8. Değişen dosya listesi (diff özeti)

| Dosya | Tür | Not |
|---|---|---|
| `core/identity/MediaIdentity.kt` | YENİ | Ortak kimlik modeli |
| `core/identity/MediaIdentityRegistry.kt` | YENİ | Paylaşılan kimlik defteri (prefs) |
| `core/subtitle/SubtitleMemoryStore.kt` | YENİ | Kalıcı altyazı belleği (prefs) |
| `core/subtitle/SubtitleHub.kt` | YENİ | Altyazı orkestrasyonu |
| `core/player/OpenSubtitlesHasher.kt` | DEĞİŞTİ | Hash önbelleği |
| `core/download/AnimeDownloader.kt` | DEĞİŞTİ | Altyazılar `SubtitleHub`'a bağlandı |
| `app/proguard-rules.pro` | DEĞİŞTİ | R8 keep kuralları (JSON kalıcılığı) |
| `app/src/test/.../core/identity/MediaIdentityTest.kt` | YENİ | 9 birim test (anahtar sözleşmesi) |
| `ui/screens/stream/StreamViewModel.kt` | DEĞİŞTİ | Tohum modeli + kimlik yayını |
| `ui/screens/fullscreen/KitsugiPlayerViewModel.kt` | DEĞİŞTİ | Ortak altyazı entegrasyonu |

Sürüm notu henüz yazılmadı — bir sonraki sürümde `release_notes_v2.4.341.md` benzeri dosya eklenmeli.

---

## 9. Sonraki oturum için başlangıç komutu

```bash
git log --oneline -5
git diff --stat HEAD
# Faz 3.1 ile başla: altyazı addon sınıflandırması
grep -rn "subtitleTypes" app/src/main/java/com/kitsugi/animelist/ui/ | head -40
```

### 9.1 Durum (2026-10-25, son commit `b56db5b`)

| Commit | İçerik |
|---|---|
| `36c1c1f` | Omurga: kimlik defteri, kalıcı altyazı belleği, `SubtitleHub`, hash önbelleği |
| `94bb559` | Raporlar + kod yaması |
| `f0ac667` | Denetim 1: R8 keep, indirme yolu, çift tarama guard'ı, tazelik penceresi |
| `13cbe73` | Denetim 2: devre kesici eşiği, altyazı TTL'i, tohum satır adı, medya tipi |
| `b56db5b` | Denetim 3: `resetCircuitBreakers()`, kök neden tablosu R12–R14 |

### 9.2 Drive eşlemesi (klasör `1n8IODscB408nymMEWlRe0Uh_i3sZY6yJ`)

| Dosya | Drive ID |
|---|---|
| `PLAN_TASK_…md` (bu dosya) | `17ZESQHAq29fs9DPn5dOp0mVHslAHf3sa` |
| `KOK_NEDEN_RAPORU_…md` | `1VVPDYcklKEAav7L2grMh29nyEciB7tNQ` |
| `COZUM_RAPORU_…md` | `1NGyd0tzJfEnpHTbf52XAJ7uKoL-QkV1j` |
| Tam yama (af10c04..b56db5b) | `1pHrra-HeuOkwWQxaU_d3Tt_DNRRYuZoJ` |
| Kaynak dosyalar klasörü | `1pf2Xnaif0LYJUe1cIbX7HHqKDXKuqJuT` |

> Not: Drive'da dosya güncellemek için `update_file_content` KULLANMA (kabuk ikamesini
> yorumlamıyor, literal `$(cat …)` yazıyor). Sil + `upload_file` ile yeniden yükle.

### 9.2b Tek yol haritası (2026-10-25 sonrası)

| Toplama işi | Tek sahibi |
|---|---|
| CS eklenti akış sorgusu | `CsStreamFanout` (`queryPlugin` / `resolveAll`) |
| CS eklenti gömülü altyazıları | `CsStreamFanout` → `SubtitleMemoryStore.rememberProviderSubtitles` |
| Stremio `/stream` | `buildStremioStreamTasks` + `AddonStreamClient` + `EpisodeStreamFilter` |
| Stremio `/subtitles` | `SubtitleRepositoryImpl` (yalnız `SubtitleHub` çağırır) |
| Kimlik çözümü | `KitsugiIdResolver` + `MediaIdentityRegistry` + `SubtitleHub.enrichIdentity` |
| Oynatıcı bölüm geçişi | `KitsugiPlayerViewModel.fetchStreamsForEpisode` → `CsStreamFanout` |
| İndirme | `AnimeDownloader` → `SubtitleHub` |
| Çevrimdışı oynatım | `DownloadsScreen` → kimlik aktarımı → aynı oynatıcı |

### 9.3 Kalan işler (öncelik sırası)

1. **R11** — CS altyazı eklentisi sınıflandırması: AAR'da `SubtitleAPI` **yok** (doğrulandı,
   `classes.jar` içinde yalnız `SubtitleFile`/`SubtitleHelper` var). İki seçenek:
   (a) eklenti tipini "altyazı" olarak işaretle ve akış listesinden çıkar (dürüst davranış),
   (b) kendi `SubtitleProvider` köprümüzü yaz (plugin `newSubtitleFile` üretiyorsa kullan).
2. **R8** — `parseIdPrefixes()` (ölü kod) → `ManagedAddonEntity.idPrefixes` ile sorgu kimliği seçimi.
3. **R10** — devre kesici politikasının kalıcılığı (süreç ömrü yerine oturum + kullanıcı geri bildirimi).
4. `WatchHistoryScreen` geçmiş girişinde yalnız `title` geçiyor → Room migration ile id taşınmalı.
5. `./gradlew :app:testDebugUnitTest --tests '*MediaIdentityTest*'` ardından
   `./gradlew assembleDebug` (bu ortamda Android SDK yok, **çalıştırılamadı**).
