# UYGULAMA RAPORU — Eklenti Vekil Sunucu Anahtarı, Akış Listesi Performansı, MAL Galeri Ortaklığı

**Tarih:** 2026-10-11
**Sürüm:** 2.4.237 → **2.4.238**
**Kapsam:** 3 kullanıcı bildirimi · 11 dosya (9 düzenleme + sürüm/not dosyaları)

---

## 1. Belirtiler

| # | Kullanıcı bildirimi | Ekran |
|---|---|---|
| 1 | "Eklenti & Akış Ayarları"ndaki **GitHub Vekil Sunucu (jsDelivr)** anahtarı açılmıyor | Eklenti & Akış Ayarları → Video Sağlayıcıları |
| 2 | Eklenti video verileri çekilirken liste **feci kasıyor** (60+ sonuç) | Frieren S1B1 akış seçici (DiziMom 1 / Anizium 60 / AnimeSiteleri 6 …) |
| 3 | **MAL ve MAL'a bağlı kaynaklar** detay sayfalarının resim galerisine ortak olmalı; böylece (özellikle manga için) daha fazla görsel elde edilir | Anime/dizi/film + manga detay sayfaları |

---

## 2. Kök neden analizleri ve düzeltmeler

### 2.1 GitHub Vekil Sunucu anahtarı — iki katmanlı hata

**Belirti:** Anahtara dokunuluyor, hiçbir şey olmuyor; değer "kapalı"da kalıyor.

**Katman 1 (asıl neden) — parametreler hiç geçilmiyordu.**
`KitsugiAddonsSettingsDialog`, `useGithubProxy`/`onUseGithubProxyChanged` için varsayılan değer
taşıyor:

```kotlin
useGithubProxy: Boolean = false,
onUseGithubProxyChanged: (Boolean) -> Unit = {},
```

Diyalogu açan üç çağrının **hiçbiri** bu parametreleri geçmiyordu:

| Çağrı yeri | Durum |
|---|---|
| `ui/screens/settings/SettingsScreen.kt` (Ayarlar sekmesi) | ✅ geçiyordu (`addon.useGithubProxy`) |
| `ui/screens/stream/KitsugiStreamScreen.kt:632` (akış seçici diyaloğu — ekran görüntüsündeki ekran) | ❌ eksik |
| `ui/screens/manga/MangaBrowseScreen.kt:361` (manga kaynakları sekmesi) | ❌ eksik |

Bu yüzden `Switch` her zaman `checked = false` ile çiziliyor, dokunuş boş lambda'ya gidiyordu —
"açılmıyor" görüntüsünün tamamı bu.

**Katman 2 — ayar okunmuyordu.**
`CloudstreamRepoRepository` içinde ayar hiç sorgulanmıyordu; yalnızca "doğrudan istek başarısız
olursa jsDelivr'a düş" yedeği vardı. Yani anahtar bir şekilde açılsa bile ilk istekler yine
`raw.githubusercontent.com`'a gidiyordu.

**Yapılan düzeltmeler**

| Dosya | Değişiklik |
|---|---|
| `KitsugiStreamScreen.kt` | Telefon diyaloğu ve `TvStreamSettingsDialog` çağrılarına `useGithubProxy` + `onUseGithubProxyChanged` bağlandı; TV diyaloğuna `🌐 GitHub Vekil Sunucu (jsDelivr)` `TvSettingsActionRow` satırı eklendi (TV'de bu ayar hiç erişilebilir değildi) |
| `MangaBrowseScreen.kt` | `SettingsDataStore` + `AppSettings` + `rememberCoroutineScope` eklendi; diyaloğa iki parametre bağlandı |
| `CloudstreamRepoRepository.kt` | `settingsDataStore` alanı ve `useGithubProxySetting()` yardımcısı; `seedDefaultRepoIfEmpty`, `addRepo`, `fetchPluginsForRepo`, `syncAndAutoUpdate` (+ repo migrasyonu) artık `preferProxy` kullanıyor |

**Davranış sözleşmesi (artık net):**

- Anahtar **açık** → repo manifesti ve eklenti listeleri **doğrudan jsDelivr** üzerinden istenir.
- Anahtar **kapalı** → doğrudan GitHub; başarısız olursa jsDelivr yedeği devreye girer (eski davranış korunur).
- Anahtar değişikliği anında DataStore'a yazılır; yeniden başlatma gerekmez.

### 2.2 Akış listesi kasması — dört ayrı kök neden

**Belirti:** Kaynaklar çekilirken ve liste kaydırılırken arayüz kilitleniyor; rozetler geç geliyor.

**Neden 1 — her öğede giriş animasyonu (en büyük etki).**
`StreamResultsList.renderStreamResultsItems` her akış kartını şöyle sarıyordu:

```kotlin
AnimatedVisibility(
    visible = true,
    enter = fadeIn(tween(300)) + slideInVertically(tween(300), initialOffsetY = { it / 3 })
) { StreamCard(...) }
```

`visible` sabit `true` olduğu için bu animasyon listeye **her yeni kart girdiğinde**, yani her
kaydırma hareketinde yeniden oynuyordu. LazyColumn'da sürekli tetiklenen bir enter animasyonu,
kaydırma sırasında kare düşmesinin doğrudan sebebidir. **Kaldırıldı.**

**Neden 2 — kontrolsüz ağ ölçümü.**
`StreamCard`, compose edilmesiyle birlikte `StreamProbe.probe()` çağırıyordu (HLS master playlist
indirme ya da HTTP HEAD). 60 akışta hızlı kaydırma, onlarca eşzamanlı ölçüm talebi üretiyordu.
Artık:

- Ölçüm yalnızca **kart görüş alanındayken** istenir (`LazyListState.layoutInfo.visibleItemsInfo`
  tabanlı `derivedStateOf`; görünürlüğü değişmeyen kart recompose edilmez).
- Kart görünür olduktan sonra **350 ms yerleşme gecikmesi** vardır; hızlı kaydırmada coroutine
  gecikme dolmadan iptal edilir → **hiç ağ isteği gitmez**.
- Kaynak (CloudStream/Stremio) kalite + ses + altyazı bilgisini **zaten** verdiyse ölçüm hiç
  yapılmaz (`StreamProbe.isProbeNeeded`) — ölçümün rozete katacağı yeni bilgi yoktur.

**Neden 3 — ölçüm kapısının (semaphore) kilitlenmesi.**
Ölçümler bloklayıcı `OkHttp.execute()` ile yapılıyordu:

```kotlin
KitsugiHttpClient.client.newCall(request).execute().use { ... }   // eski
```

Coroutine iptal edilse bile bloklayıcı çağrı arka planda devam ediyor ve `Semaphore(3)` iznini
8 saniyeye kadar **tutuyordu**. Kullanıcı listeyi kaydırdıkça onlarca terk edilmiş ölçüm izinleri
tüketiyor, yeni görünen kartların rozetleri hiç gelmiyordu. Artık:

```kotlin
private suspend fun awaitResponse(call: Call): Response? = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { call.cancel() } }
    call.enqueue(object : Callback { /* onFailure → null, onResponse → response */ })
}
```

İptal artık isteği GERÇEKTEN durduruyor ve izin anında serbest kalıyor. Eşzamanlılık kapasitesi
3 → 2'ye indirildi (resim yükleme ve genel gezinme ile radyo/bağlantı havuzu rekabetini azaltmak için).

**Neden 4 — filtre maliyeti.**
`filteredAddonStates`, filtre aktif olmasa bile her `addonStates` güncellemesinde (her eklenti
bittiğinde) 60+ akış için `resolveStreamQuality` / `resolveStreamLangInfo` (çok sayıda regex +
önbellek sorgusu) çalıştırıyordu. Aktif arama/filtre yokken bu iş tamamen atlanır. Ek olarak
eklenti çipleri artık `key` ile üretiliyor (her recompose'da yeniden kurulmuyor).

### 2.3 MAL / MAL'a bağlı kaynakların galeriye ortak edilmesi

**Mevcut durum:** Detay sayfalarının galerisi iki ayrı ViewModel'de üretiliyor
(`ApiResultDetailViewModel` = API/kaynak detayları, `MediaEntryDetailViewModel` = kütüphane
kayıtları). İkisinde de:

- Anime/dizi/film: Fanart.tv + TMDB + Shikimori ekran görüntüleri çekiliyor, `detail.pictures`
  (varsa) ekleniyordu. Ancak **MAL kayıtları dışındaki kaynaklarda** `pictures` boştu; MAL
  `/pictures` uç noktası yalnızca MAL/Jikan kaynaklı detaylarda doldurulduğu için diğer
  platformlarda galeri eksik kalıyordu.
- Manga: Erken dönüş nedeniyle galeri yalnızca kapağı gösteriyordu; MAL ek görselleri hiç
  katılmıyordu.

**Yapılan düzeltmeler**

| Dosya | Değişiklik |
|---|---|
| `data/remote/KitsugiMalDetailClient.kt` | `/pictures` için 80 kayıtlık `BoundedCache` (`mal.pictures`); boş sonuç önbelleğe yazılmaz (geçici 429/ağ hatası görselleri kalıcı olarak engellemesin) |
| `ApiResultDetailViewModel.kt` | Anime/film: `resolveMediaGalleryIdentity()` ile MAL kimliği çözülür, `malDef` **paralel** (Fanart/TMDB/Shikimori ile aynı `coroutineScope`) çekilir; manga: erken dönüş öncesi MAL görselleri eklenir. Yeni `fetchMalGalleryItems()` (12 sn bütçe, URL'den kategori çıkarımı, kaynak etiketi "Jikan (MAL)") |
| `MediaEntryDetailViewModel.kt` | Aynı birleştirme kütüphane detayları için de yapıldı; `realMalIdForGallery()` güvenli kimlik çözümü (AniList/Kitsu/Bangumi stable ID'leri ve TMDB/Simkl kimlikleri MAL ID yerine kullanılmaz) |

**Kimlik güvenliği (kritik):** MAL'a gönderilen ID yalnızca kanıtlanmış eşlemelerden gelir:
Jikan/MAL/Shikimori kayıtlarının gerçek MAL kimliği, çapraz eşlemeyle çözülen `realMalId`, ya da
`bangumiCross.malId`. Stable/offset ID'ler (AniList +100M, Kitsu +300M, Bangumi) ve TMDB/Simkl
kimlikleri **asla** MAL ID olarak kullanılmaz — aksi halde galeri alakasız yapımların görselleriyle
dolardı.

**Sonuç:** Anime/dizi/film detaylarında galeri artık MAL `/pictures` görsellerini de içerir
(çapraz eşleme varsa), manga detaylarında ise galeri kapağın ötesine geçer. Görseller tek istekte
ve paralel çekilir; tekrar ziyaretlerde önbellekten gelir.

---

## 3. Değişen dosyalar

| Dosya | Satır etkisi |
|---|---|
| `ui/screens/stream/KitsugiStreamScreen.kt` | +27 |
| `ui/screens/manga/MangaBrowseScreen.kt` | +15 |
| `data/repository/CloudstreamRepoRepository.kt` | +31 / −4 |
| `data/repository/StreamProbe.kt` | +87 |
| `ui/screens/stream/StreamCard.kt` | +27 |
| `ui/screens/stream/StreamResultsList.kt` | +51 / −18 |
| `ui/screens/stream/StreamScreenContent.kt` | +24 |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | +68 |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | +64 |
| `data/remote/KitsugiMalDetailClient.kt` | +16 |
| `app/build.gradle.kts`, `RELEASE_NOTES.md`, `release_notes_v2.4.238.md` | sürüm 2.4.238 + notlar |

**Toplam:** 9 kod dosyası, +293 / −53 satır.

---

## 4. Doğrulama notları

- Bu ortamda JDK/Gradle/Android SDK bulunmadığı için derleme **yapılamadı**; tüm değişiklikler
  satır satır gözden geçirildi (tip/imza uyumu, suspend-inline kuralları, Compose API kullanımı,
  `internal` görünürlük).
- Cihaz doğrulaması için önerilen senaryolar:
  1. Eklenti & Akış Ayarları → anahtar açılır, uygulama yeniden başlatıldığında **açık kalır**;
     anahtar açıkken repo yenileme jsDelivr üzerinden başarılı olur.
  2. 60+ sonuçlu bir bölümde liste kaydırma akıcı; rozetler görünen kartlarda sırayla dolar,
     hızlı kaydırmada gereksiz ağ isteği oluşmaz.
  3. AniList/Kitsu/Bangumi kaynaklı bir anime detayında galeride "Jikan (MAL)" kaynaklı ek
     görseller görünür; manga detayında galeri kapağın ötesine geçer.

## 5. Riskler / geri alma

- `StreamProbe` artık iptal edilebilir `Call.enqueue` kullanır: davranış aynı, yalnızca iptal
  gerçekten uygulanır. Geri alma gerekirse ilgili dosya tek başına eski hâline döndürülebilir.
- MAL galerisi kimlik yoksa **hiç istek yapmaz**; yanlış eşleşme riski taşımaz.
- Görünürlük tabanlı ölçüm, kaydırma sırasında rozetlerin kısa süre "Kalite ?" kalmasına yol
  açabilir (350 ms + ölçüm süresi). Bu bilinçli bir tercih: doğru veri, akıcı liste.
