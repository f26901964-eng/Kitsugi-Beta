# Kitsugi Beta v2.4.314

## 🇹🇷 Türkçe

### 🔁 Liste Ekleme/Güncelleme ve Altı Kaynak (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) Çapraz Senkron Düzeltmesi

Bu sürüm, bir kaynaktan (örn. Bangumi) eklenen veya düzenlenen kaydın seçilen durum (örn. **İzleniyor**), puan, tarih ve bölüm değerleriyle hem kendi kaynağına hem de bağlı tüm kaynaklara aynı anda yazılamaması sorununu 9 kök neden üzerinden tamamen çözer. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md`.

- **Yerel-Öncelikli Ekle/Düzenle Hattı:** Bangumi ekleme ve düzenleme işlemlerindeki "önce uzak sunucuya yaz, başarısız olursa kaydetme" kapısı kaldırıldı (`AppRoot.kt`, `MyListViewModel.kt`, `AppViewModel.kt`). Artık tüm kaynaklar aynı hattı kullanır: değişiklik anında yerel veritabanına işlenir, arayüz gecikmeden güncellenir ve arka planda altı kaynağa birden (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) dağıtılır. Ağ veya yetkilendirme hatalarında bekleyen senkron kuyruğunda otomatik olarak yeniden denenir.
- **Japonca Başlıkla Doğrulama:** Kimlik doğrulama katmanına satırın özgün Japonca başlığı eklendi (`CrossProviderIdentity.kt`, `KitsugiIdResolver.kt`). Bangumi satırlarında görünen başlık Çince olduğundan ve İngilizce başlık bulunmadığından MAL/AniList doğrulamaları düşüyordu; artık İngilizce → Japonca → Görünen Başlık sırasıyla Jikan/API araması yapılarak eşleşme garanti altına alındı.
- **Çözülen AniList Kimliğinin Korunması:** Çapraz kimlik katmanının çözdüğü AniList kimliği artık doğrudan yazma ve favori hattına aktarılıyor (`AniListSyncManager.kt`). Daha önce çözülen kimlik atılıp başlıkla yeniden arandığı için özellikle Bangumi kökenli kayıtlar düşüyordu.
- **Bangumi Manga/Kitap Desteği:** Kimlik köprüsündeki manga erken dönüşü düzeltildi; Bangumi kitap ve manga kayıtları da artık MAL/AniList kimliklerine çözülüp yayılabiliyor.
- **Simkl Bölüm, Puan ve Durum Sıralaması:** Simkl kimlik aramasındaki mantık hatası düzeltildi; kimlik varken bölüm ve puan adımlarının atlanması engellendi. Canlı senkron hattı toplu hatla eşitlendi: bölümler 1..N olarak işaretlenir, puan yazılır ve durum en son uygulanır (`SimklSyncManager.kt`). Uydurma `S01E<N>` yapısı kaldırıldı; geri yazımın yerel kaynak kimliğini ezmesi önlendi.
- **Shikimori Doğrudan Güncelleme:** Shikimori satırları MAL kimliği çözülemese dahi kendi `target_id` değeriyle güncellenebilir hale getirildi (`MalSyncManager.kt`).
- **Kaynak-Duyarlı Kimlik Ayrıştırma:** Ham Shikimori, Bangumi veya Kitsu kimliklerinin yanlışlıkla MAL kimliği sanılması ve hatalı önbelleğe alınması engellendi. Bangumi film/dizi subject kimliği korundu.
- **AniList Tarih Koruma & MAL Puan Normalizasyonu:** Yerel satırda tarih boşken AniList üzerindeki başlangıç/bitiş tarihlerinin sessizce silinmesi önlendi. MAL için 0..100 puan aralığı 1..10 ölçeğine normalize edilerek 400 Bad Request hataları giderildi.
- **Kardeş Satırların Anında Eşitlenmesi:** Bir kaynak sekmesinde yapılan düzenleme veya silme işlemi aynı yapıma ait diğer kaynak sekmelerine ve "Tümü" görünümüne yerelde anında yansıtılır (`MediaEntryRepository.kt`).

### 🧪 Birim Testleri
- `CrossProviderIdentityTest`: Ham Shikimori/Bangumi/Kitsu kimlik izolasyonu, Bangumi subject koruması, kaynak önbellek anahtarları ve kardeş satır kimlik kesişimleri test edildi.
- `CrossSyncIdentityGuardTest`: Özgün Japonca başlığın kimlik eşdeğerliğinde kullanımı doğrulandı.

---

## 🇬🇧 English

### 🔁 List Add/Update & Six-Source (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) Cross-Sync Fix

This release resolves the issue where anime/manga items added or edited from one provider (e.g., Bangumi) would lose selected statuses (e.g., **Watching**), ratings, dates, and episode counts when syncing across connected tracking services. Detailed root-cause breakdown in `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md`.

- **Local-First Add/Edit Pipeline:** Removed the legacy remote-first write barrier for Bangumi (`AppRoot.kt`, `MyListViewModel.kt`, `AppViewModel.kt`). All providers now follow a unified pipeline: changes save immediately to local storage and asynchronously fan out to all 6 providers (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi), with automatic retries via pending sync queue.
- **Original Japanese Title Verification:** Integrated Japanese titles into identity verification (`CrossProviderIdentity.kt`, `KitsugiIdResolver.kt`). Because Bangumi titles are predominantly Chinese with no English equivalent, cross-provider matching previously failed; search order is now English → Japanese → Display title.
- **Preserved AniList Resolution:** Directly propagated resolved AniList IDs into mutation and favorite flows (`AniListSyncManager.kt`) rather than re-querying by title.
- **Bangumi Manga/Book Cross-Sync:** Lifted premature return in cross-provider bridge for manga entries, enabling Bangumi books to map to MAL/AniList entries.
- **Simkl Episode, Rating & Status Flow:** Inverted flawed Simkl lookup logic, eliminating dropped rating/episode updates (`SimklSyncManager.kt`). Synchronized live pipeline with batch rules: marks episodes 1..N, applies ratings, and commits final status. Removed synthetic `S01E<N>` hacks.
- **Direct Shikimori Mutations:** Allowed Shikimori updates to proceed using native `target_id` even when MAL ID is unresolved (`MalSyncManager.kt`).
- **Provider-Aware Identity Parsing:** Prevented raw Shikimori, Bangumi, and Kitsu IDs from being misidentified as MAL IDs in cache storage. Preserved Bangumi subject IDs on TV/movie records.
- **AniList Date Protection & MAL Rating Normalization:** Prevented null local dates from clearing remote start/finish dates on AniList. Normalized 0..100 ratings to the 1..10 scale for MyAnimeList.
- **Immediate Sibling Tab Synchronization:** Mutations made on one provider tab instantly update sibling entries across all provider tabs and the unified "All" feed (`MediaEntryRepository.kt`).

### 🧪 Unit Tests
- `CrossProviderIdentityTest`: Validates non-MAL ID parsing, Bangumi movie subject preservation, namespaced cache keys, and cross-row identity matching.
- `CrossSyncIdentityGuardTest`: Verifies Japanese title matching in canonical equivalence checks.
