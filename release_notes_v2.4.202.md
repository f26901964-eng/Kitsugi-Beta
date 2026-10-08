# Kitsugi-Beta v2.4.202 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔍 Arama "Tümü" — Nesil Koruması, Zaman Aşımı ve MAL Jikan Yedeği
- **Arama Nesil (Generation) Koruması:** Her arama sorgusuna benzersiz bir nesil numarası (`AtomicInteger`) tanımlandı. İptal edilen eski aramaların arka planda geç dönüp yeni sorgunun sonuçlarının üzerine yazması (`race condition`) tamamen engellendi.
- **İptal Güvenliği:** Arama iptal edildiğinde `CancellationException` artık yutulmuyor; yeni arama eski aramadan etkilenmeden tertemiz çalışıyor.
- **20 Saniyelik Dayanıklı Ağ Tavanı:** Kaynak yanıt zaman aşımı sınırı 7 saniyeden 20 saniyeye çıkarıldı (`allSourceTimeoutMs = 20_000L`). Düşük hızlı bağlantılarda (100–150 KB/s) AniList veya MAL kaynaklarının kaybolması önlendi.
- **MyAnimeList Jikan v4 Yedeği:** Resmî MAL API yanıt vermediğinde veya boş döndüğünde Jikan v4 yedeği (`searchJikanFallback`) devreye girer. "MyAnimeList" rafı artık boş kalmaz.

### 📑 Kitsu Kayıtlarında Detay "Yüklenemedi" Hatası ve +18 Çözümü
- **Kimlik Kaybı Önleme:** Kitsu kimlik geçişinde (`KitsuIdentityMigration`) çözülemeyen kayıtlarda `malId` alanının silinmesi durduruldu; önceki turlarda silinmiş olan kayıtlar başlıktan yeniden çözülür (ağ bütçesi 40'tan 120'ye çıkarıldı).
- **Yerel Satır Numarası İzolasyonu:** `externalIdOf` mekanizması ile Kitsu kayıtlarında yerel veritabanı satır numarasının dış API kimliği sanılması engellendi.
- **Kitsu Akıllı Yedek Zinciri:** Kanonik stableId → Kaydın bildiği gerçek MAL ID → Jikan → Sıkı başlık eşleşmeli MAL araması (`fetchMalDetailByTitle`) → Film/Dizi ise başlıktan TMDB.
- **+18 (R18/NSFW) Kitsu İçerik Erişimi:** Kitsu API'sinin anonim kullanıcılardan gizlediği R18/NSFW kayıtlar için kullanıcının Kitsu oturum jetonuyla istek atılması sağlandı (*Imaria*, *Ero Ishi…*, *Menkui!*, *Kouhai* vb. kayıtlar artık sorunsuz açılır).

### 🧾 Ek
- Sürüm `v2.4.202` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English

### 🔍 Search "All" — Generation Guard, Extended Timeout & MAL Jikan Fallback
- **Search Generation Guard:** Introduced generation tracking (`AtomicInteger`) for search requests. Stale jobs from previously canceled queries can no longer overwrite the latest search results.
- **Clean Cancellation:** `CancellationException` is no longer swallowed, ensuring canceled jobs exit cleanly without clearing active source results.
- **Extended Timeout (20s):** Increased individual source timeout from 7s to 20s (`allSourceTimeoutMs = 20_000L`) to prevent AniList/MAL drops on slower mobile networks.
- **MAL Jikan v4 Fallback:** If the official MAL v2 API returns empty or fails, searches automatically fall back to Jikan v4 (`searchJikanFallback`), ensuring the MyAnimeList shelf stays populated.

### 📑 Kitsu "Unable to Load Media Details" Fix & NSFW Access
- **Identity Retention:** Stopped erasing `malId` on unresolved Kitsu items. Unmapped items are re-resolved by title with an expanded network budget (40 → 120).
- **Row ID Isolation:** Introduced `externalIdOf` to ensure local database row IDs are never mistaken for external Kitsu IDs.
- **Robust Fallback Pipeline:** Canonical stableId → Known MAL ID → Jikan → Strict title-matched MAL search → TMDB for movies/series.
- **NSFW/R18 Kitsu Access:** Requests for Kitsu details now use the user's authenticated token, allowing access to entries hidden from anonymous requests (*Menkui!*, *Imaria*, etc.).

### 🧾 Extras
- Version bumped to `v2.4.202`.
- Built and published exclusively as the `foss` release variant.
