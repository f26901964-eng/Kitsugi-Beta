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

### 2.3 Performans

| Konu | ÖNCE | SONRA |
|---|---|---|
| OpenSubtitles hash | Her sorguda yeniden (N × 3 istek) | 10 dk önbellek, tek hesaplama |
| Altyazı sorgu sayısı | 4-6 (yalnız IMDb/Kitsu/ana başlık) | 7 (IMDb/Kitsu/AniList/MAL + başlık varyantları), sınırlı |
| Tekrar açılış | Tam ağ turu | Bellek anında, ağ arka planda |

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
