# ÇÖZÜM RAPORU — Ortak Kaynak/Altyazı Omurgası
**Tarih:** 2026-10-25 · **Dal:** `arena/8eba864b-kitsugi-beta` · **Durum:** Faz 1 + Faz 2 kodlandı

---

## 1. Tek cümlelik çözüm

Uygulamaya **kaynak-bağımsız ortak kimlik** (`MediaIdentity` + `MediaIdentityRegistry`) ve
**kalıcı ortak altyazı havuzu** (`SubtitleMemoryStore` + `SubtitleHub`) eklendi; akış tarafında
doğrudan URL artık **tohum** (tek başına yeterli değil). Böylece hangi giriş noktasından veya
hangi kaynaktan izlenirse izlensin aynı kimlik, aynı eklenti havuzu ve aynı altyazı listesi kullanılır.

---

## 2. Davranış karşılaştırması

### 2.1 Akış (video) tarafı

| Senaryo | ÖNCE | SONRA |
|---|---|---|
| Detay sayfasından "İzle" | 9 eklenti taranır, ~92 video | Aynı |
| Bir eklentinin kataloğundan | **Yalnızca o eklenti** (erken `return`) | Tohum anında gösterilir + **tüm eklentiler** taranır |
| İzleme geçmişinden devam | **Yalnızca o eklenti** | Tohum + tüm eklentiler |
| Manuel eklenti aramasından | **Yalnızca seçilen eklenti** | Tohum + tüm eklentiler |
| Kimlik çözümü | Ekran başına tekrar | Bir kez çözülür, `MediaIdentityRegistry` ile paylaşılır |

### 2.2 Altyazı tarafı

| Senaryo | ÖNCE | SONRA |
|---|---|---|
| A kaynağında altyazı vardı, B'ye geçildi | Liste **sıfırlanır** (`= stream.subtitles`), ağ boş dönerse altyazı yok | Bellekteki tüm altyazılar + kaynağın kendi altyazıları birleşir |
| Kaynağın içinden çıkan (gömülü) altyazı | Sadece o kaynakta, nesneyle birlikte kaybolur | Havuza yazılır → **her kaynakta** görünür |
| Aynı bölüm ikinci kez açıldı | Tüm eklentiler sıfırdan sorgulanır | Bellekten **anında**, sonra ağ tazelemesi |
| Katalogdan (ID'siz) açıldı | Sadece ana başlıkla sorgu | Registry'den ID kurtarma + **tüm başlık varyantları** |
| Torrent/HLS kaynağı (hash yok) | Hash'e dayalı eklentiler boş | Bellek + başlık/ID sorguları devreye girer |
| Aynı altyazı iki biçimde (uzak URL + yerel dosya) | İki satır | Tek satır (yerel dosya tercih edilir) |
| Ağdan yeni sonuç yok, elde altyazı var | "Bulunamadı" + `LoadFailed` | `SUCCESS`, mevcut liste korunur |

### 2.4 Ek düzeltmeler (denetim sonrası)

| Konu | ÖNCE | SONRA |
|---|---|---|
| Release derleme (R8) | Yeni Gson sınıfları korunmuyordu → JSON geri okunamaz, omurga sessizce ölür | `proguard-rules.pro`'a keep kuralları eklendi |
| İndirme akışı | Kendi ID/sorgu mantığı: yalnız IMDb+Kitsu, önbellek yok | `SubtitleHub` kullanıyor → indirilen/oynatılan aynı havuz |
| Eşzamanlı tarama | init + kaynak değişimi + retry aynı anda 2-3 tarama başlatıyordu | `subtitleFetchJob` ile önceki iptal edilir |
| Kaynak değişiminde ağ | Her değişimde 7 sorgu × N eklenti | Bellek 2 saat içinde doluysa ağ atlanır (`forceRefresh` ile zorlanabilir) |
| Oynatıcı açılışı | Altyazılar yalnız ağ sonrası | `initialize()`'da bellekten anında yayınlanır |
| Kimlik geri kazanımı | Yalnız ana başlıkla arayış | Ana başlık + İngilizce/romaji/native/synonyms ile arayış |
| Yıl farkı | Yıl bilinmeyen girişte anahtar tutmuyordu | Aday anahtarlara **yılsız** ve **alternatif başlıklı** varyantlar eklendi |

### 2.3 Performans

| Konu | ÖNCE | SONRA |
|---|---|---|
| OpenSubtitles hash | Her sorguda yeniden (N × 3 istek) | 10 dk önbellek, tek hesaplama |
| Altyazı sorgu sayısı | 4-6 (yalnız IMDb/Kitsu/ana başlık) | 7 (IMDb/Kitsu/AniList/MAL + başlık varyantları), sınırlı |
| Tekrar açılış | Tam ağ turu | Bellek anında, ağ arka planda |

### 2.5 İkinci denetim turunda bulunan ince riskler

| Risk | Neden tehlikeli | Düzeltme |
|---|---|---|
| Devre kesici eşiği (3) | Merkez artık 7 sorgu atıyor; bilmediği ID biçimine 404 dönen eklenti, geçerli sorgusuna sıra gelmeden 5 dk bloklanıyordu | Eşik 8'e çıkarıldı (`CIRCUIT_BREAKER_THRESHOLD`) |
| Sağlayıcı altyazı TTL'i 3 gün | İmzalı linkler saatler içinde ölüyor → menüde "açılmayan altyazı" | 24 saate indirildi |
| Oynatıcı kimliğinde `mediaType = null` | Film/dizi ayrımı başlıktan tahmin ediliyordu | Film ise `MediaType.Movie` |
| Tohum satırı adı | Geçmiş/katalog eklenti **ID'si** gönderirse satır adı eşleşmiyor, aynı eklenti iki kez listelenebiliyordu | Satır adı her zaman eklentinin görünen adı (`seedLabel`) |

### 2.6 "Tek yol" garantisi — akış toplamanın birleştirilmesi

Eskiden **iki ayrı** akış toplama kodu vardı ve ikisi farklı davranıyordu:

| Yol | Nerede | Eksikleri |
|---|---|---|
| A (eski) | `StreamViewModel.fetchStreams` | referans davranış (tam) |
| B (eski) | `KitsugiPlayerViewModel.fetchStreamsForEpisode` (sonraki bölüm / bölüm değiştirme) | tip filtresi ✗, blok kontrolü ✗, `preResolvedIds` ✗, cs3 modunda **erken çıkış** ✗, tekrar ayıklama ✗ |

Artık **tek** yol var: `data/cloudstream/CsStreamFanout.kt`

```
StreamViewModel ─┐
                 ├─→ CsStreamFanout.queryPlugin/resolveAll ─→ CsStreamRunner.getStreams
PlayerVM (bölüm) ┘        (tip filtresi · blok · ID ön çözümü · tohum · dedupe)
```

Stremio tarafı da tek: `/subtitles` yalnızca `SubtitleRepositoryImpl` (SubtitleHub üzerinden),
`/stream` yalnızca `buildStremioStreamTasks` + `AddonStreamClient` + `EpisodeStreamFilter`.

Altyazı tarafında da toplama anı değişti:

| Durum | ÖNCE | SONRA |
|---|---|---|
| Bir eklentinin gömülü altyazıları | Yalnız o kaynak **oynatılınca** havuza yazılıyordu | **Tarama anında** tüm eklentilerin altyazıları havuza yazılır (R18) |
| ID'siz giriş (geçmiş/katalog) | Defter yalnız yazılıyor, okunmuyordu | Akış ekranı da defterden ID kurtarır (R19) |

Ek düzeltmeler:
- Çevrimdışı oynatım (`DownloadsScreen`) artık kimlik (MAL/AniList/TMDB + sezon/bölüm) taşıyor.
- `PlayerGeneratorViewModel` ölü kod olarak işaretlendi; ileride bağlanırsa `CsStreamFanout` kullanmalı.

---

## 3. Mimari

```
            ┌──────────────────────────────┐
            │  Giriş noktaları             │
            │  detay · katalog · arama ·   │
            │  geçmiş · otomatik oynatma   │
            └──────────────┬───────────────┘
                           ▼
             ┌─────────────────────────────┐
             │  MediaIdentity              │  ← tek model, tüm ID'ler + başlık varyantları
             └──────────────┬──────────────┘
                            ▼
             ┌─────────────────────────────┐
             │  MediaIdentityRegistry      │  ← ID'siz girişlerde kimliği geri kazandırır
             └──────────────┬──────────────┘
              ┌─────────────┴──────────────┐
              ▼                            ▼
   ┌───────────────────┐        ┌──────────────────────┐
   │ StreamViewModel   │        │ SubtitleHub          │
   │  tohum + tüm      │        │  bellek → kimlik →   │
   │  eklentiler       │        │  ağ → belleğe yaz    │
   └───────────────────┘        └──────────┬───────────┘
                                           ▼
                              ┌─────────────────────────┐
                              │ SubtitleMemoryStore     │  ← kalıcı, kaynak bağımsız
                              └─────────────────────────┘
```

---

## 4. Eklenen genel API'ler

```kotlin
// Kimlik
MediaIdentity.fromResolved(title, alternativeTitles, mediaType, isMovie, startYear,
                           malId, aniListId, tmdbId, bangumiId, season, episode, ids)
identity.contentKey / identity.subtitleKey / identity.subtitleKeyCandidates
MediaIdentityRegistry.initialize(context); MediaIdentityRegistry.remember(identity)
MediaIdentityRegistry.lookupByTitle(title, year): ResolvedIds?

// Altyazı
SubtitleHub(context).cachedOnly(identity): List<Subtitle>               // ağ yok, anında
SubtitleHub(context).load(identity, videoUrl, headers, filename): Result // bellek + ağ
SubtitleHub(context).rememberProviderSubtitles(identity, addonName, subs)
SubtitleHub(context).clearMemory()

// Hash
OpenSubtitlesHasher.clearCache()
```

---

## 5. Riskler ve geri alma

- **Derleme doğrulaması yok** (sandbox'ta JDK/Android SDK yok). İlk `assembleDebug` çıktısına göre
  `PLAN_TASK…md §7` içindeki 4 noktaya bakın.
- Tüm yeni durum **SharedPreferences + Gson** ile saklanıyor → **Room migration yok**, şema değişmedi.
  İstenirse `SubtitleMemoryStore.clear()` / `MediaIdentityRegistry.clear()` ile sıfırlanır.
- Yeni ağ davranışı: altyazı sorgusu 7'ye çıkabilir. Kullanıcı rahatsız olursa
  `SubtitleHub.MAX_REMOTE_QUERIES` tek satırdan düşürülebilir.
- Geri alma: yeni 4 dosyayı sil + `StreamViewModel`/`KitsugiPlayerViewModel` içindeki 3 değişiklik
  bloğunu geri al (git revert ile tek commit).

---

## 6. Kapsam dışı bırakılanlar (Faz 3)

R8 (idPrefixes), R10 (oturum bloğu), R11 (altyazı eklentisi sınıflandırması / `.cs3` `SubtitleAPI`).
Ayrıntı ve sıralama: `PLAN_TASK_ORTAK_KAYNAK_ALTYAZI_OMURGASI_2026-10-25.md §5`.
