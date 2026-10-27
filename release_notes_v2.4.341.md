# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🧩 Listem'de Çift Kartlar Bitti — Kaynak İçi İkizler + Birleşmeyen Kopyalar
- **Simkl kategori çakışması çözüldü:** Simkl aynı anime filmini hem `movies` hem `anime` ucunda döndürüyor, aynı dizi `shows` + `anime` altında gelebiliyordu. Üç liste tekilleştirilmeden birleştirildiği için aynı yapım `Movie`/`TvShow` + `Anime` olarak **iki satır** yazılıyordu. Artık `simklId` üzerinden tek satıra iniyor (Anime etiketi korunur).
- **"Tümü" sekmesi bayat kimlik ipuçlarına takılmıyor:** çapraz kimlik önbelleğinde kalan eski bir `mal:`/`shikimori:` değeri, aynı başlığın iki karta bölünmesine yol açıyordu. İpuçları artık yalnızca **birleşmeye yardım eder**, engellemez.
- **Platform sekmeleri de eritiliyor:** AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi sekmeleri aynı motoru kullanır, ancak yalnızca **kendi kaynağının** kopyalarını birleştirir (bir sekmede başka kaynağın satırı belirmez).
- **Sekme sayaçları görünenle eşleşiyor:** kaynak sayıları artık ekrandaki kart sayısıyla birebir.
- **±1 yıl toleransı:** AniList sezon yılını, Kitsu/MAL prömiyer yılını sakladığı için Aralık/Ocak sınırında aynı yapım 1 yıl farklı görünüp iki kart oluyordu. Başlık eşleşmesi artık ±1 yıl toleranslı; gerçek remake (Hunter x Hunter 1999/2011) ayrı kalır.
- **Tür etiketi farkı artık sorun değil:** aynı `simklId`/Bangumi `subject_id` kanıtı varsa `Movie` ↔ `Anime` ↔ `TvShow` kopyaları tek kart olur. Anime ↔ Manga **asla** birleşmez; TMDB kimliği bu kanıtta kullanılmaz (film/dizi ID'leri çakışır).

### 🛠️ Kalıcı Onarım + Yazma Güvenliği
- **Tek seferlik çift kayıt onarımı:** mevcut kitaplıktaki aynı kaynak kopyaları birleştirilir; ilerleme, durum, puan, favori, +18 işareti, notlar ve tarihler korunur (hiçbir alan gerilemez).
- **Kaynak başına yazma kilidi:** açılış yenilemesi (sil + yaz) ile içe aktarma (oku + yaz) aynı kaynağa eşzamanlı dokunamaz — yeni kopya oluşması engellenir.
- **İçe aktarım tekilleştirmesi:** yedek birleştirme ve kaynak yeniden yazımı da aynı kopyaları ayıklar.

### 📺 Android TV
- TV kütüphanesi ve istatistik rozetleri aynı eritmeyi kullanır; aynı yapım iki kez sayılmaz.

---

## 🇬🇧 English

### 🧩 Duplicate Cards in My List Are Gone — In-Source Twins & Unmerged Copies
- **Simkl category overlap fixed:** Simkl returns the same anime film under both `movies` and `anime` (and a series under `shows` + `anime`). The three lists were concatenated without deduplication, so the same title was stored twice as `Movie`/`TvShow` + `Anime`. Rows now collapse by `simklId`, keeping the Anime label.
- **"All" tab no longer trips over stale identity hints:** an outdated cached `mal:`/`shikimori:` value used to "conflict" with a real provider ID and split one title into two cards. Hints now only **help** a match — they never block it.
- **Provider tabs deduplicate too:** AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi reuse the same engine but merge only their **own** source's copies — another provider's row never leaks into a tab.
- **Counters match the grid:** tab counters are computed from the visible, deduplicated list.
- **Type-label differences are handled:** copies sharing a verified `simklId`/Bangumi `subject_id` collapse across `Movie` ↔ `Anime` ↔ `TvShow`. Anime ↔ Manga never merge, and TMDB IDs are excluded as evidence (movie/TV ids collide).

### 🛠️ One-Time Repair + Write Safety
- **One-shot duplicate repair:** existing same-source duplicates are merged (progress, status, score, favourite, +18 flag, notes and dates are preserved — nothing regresses).
- **Per-source write lock:** background refresh (delete + write) and imports (read + write) can no longer interleave on the same provider.
- **Import deduplication:** backup merge and full source rewrites also drop duplicates.

### 📺 Android TV
- The TV library and its stat badges use the same deduplication; a title is no longer counted twice.
