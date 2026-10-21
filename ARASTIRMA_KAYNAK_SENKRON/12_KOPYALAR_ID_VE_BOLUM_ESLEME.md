# Tur-3 — Kopya Kayıtlar, Yanlış ID ve Bölüm-Sayısı Eşleme Felaketleri (2026-10-21)

## A. Bölüm-numarası taksonomisi (ani-gui PR #264 — tek başına bir araştırma)
Bir uygulamanın Kitsu-bölüm-numarasını AniList/MAL'e aynen yazması neden kırılır: **8 kategori**
(tablo PR'dan): (1) tek Kitsu girişi tracker'ın girişi+devamlarına zincir (Ranma ½: 161→AniList
18+143); (2) tarihler Kitsu koşumunun dışında; (3) Kitsu kendi parçasını iddia eder; (4) paylaşılan
giriş toplam-uyumsuz; (5) parçalar-birleştirilmiş (Haruhi 2009: 28 vs 14 — asla tamamlanmaz!);
(6)UBW: AniList "Episode 0 - Prologue" sayar → **13 vs 12, her zaman 1 eksik, satır asla complete
olmaz**; (7) tracker'ın sunduğu fazlalıklar; (8) hiçbir açıklama yok (108-152 show kırık).
**PR'ın çıkardığı güvenli-kural seti (Kitsugi ile karşılaştırılmalı):**
- final bölüm yazımında tracker'ın toplamı gönderilir (hangi taraf sayarsa saysın);
- diğer bölümler kendi numarasıyla, tracker girişi kısaysa **tracker toplamına sıkıştırılır**;
- toplam bilinmiyorsa (AniList null / MAL 0) yazım DEĞİŞTİRİLMEZ;
- **hiçbir yazım mevcut ilerlemeyi GERİ ALMAZ**; rewatch rewatch kalır; complete satıra progress
  yazılmaz.
https://github.com/JoaoPucci/ani-gui/pull/264

## B. Kopya-kayıt sınıfları
1. **Sunucu-tarafı sayfalama kopyası:** AniList non-unique sort → sayfalar arası tekrar/atlama
   (anilist_moe #19; Eki-2026'da tiebreaker ile düzeldi). **Kitsugi: `sort:[MEDIA_ID]` ✓.**
2. **Çapraz-platform kopya:** her platform kendi ID sistemi; başlık-eşleştirme "şamatik"
   ("Shingeki no Kyojin"/"Attack on Titan"/"A.O.T." ayrı satır) → rehber: **ID-strict eşleşme,
   başlık asla tek-kriter değil** (lifetips alibaba analizi 2026-01).
   https://lifetips.alibaba.com/tech-efficiency/streamline-anime-watchlists-across-mal-anilist-kitsu
3. **Kaynak-tarafı yanlış MAL ID'si sync'i öldürüyor:** MALSync List-Sync wiki: "ilişki,
   anilist/kitsu'nun verdiği MAL ID'leriyle yapılır; **yanlışsa kaynağı-anilist/kitsu'da DÜZELT**"
   → çapraz-senkronun ilk şartı kaynak-veri kalitesi.
   https://github.com/MALSync/MALSync/wiki/List-Sync
4. **Yerel-uigü kopya:** giriş-girişleri iki farklı provider satırı olarak yaşar, birleştirilmez
   (Animebox PR #4 — "hidden, not deleted: they reappear when switching back"); webtoon-tracker
   PR #72/#73 kopya-birleştirme + "olası kopya" öneri akışı kurmuş.
5. **AniList'e 2 kez okuma** (unyo #87: mediaListEntry iki ayrı query'de) — quota israfı sınıfı.
6. **MAL XML içe-aktarımda `update_on_import=1`** → tüm satırlar default-overwrite (malscraper
   notu) — toplu içe-aktarımların kopya/ezme silahşörü.

## C. ID/kimlik tarafında Kitsugi'nin durumu (denetim)
| Savunma | Durum |
|---|---|
| AniList import sıralaması `sort:[MEDIA_ID]` | ✓ (B1 tuzağı yok) |
| Çapraz kimlik: `CrossProviderIdentity` + `persistNewlyResolvedIds` (malId/simklId/tmdbId geri-yazımı), AniList 100M-offset, Bangumi lokal cache, Simkl lookup→handle-cache | ✓ round 1-5 |
| Başlık-eşleşmesi tek-kriter değil (`titleMatchingAllowed` + guard) | ✓ |
| Kitsu→MAL ID'si anahtarı | ⚠️ Kaynak-AniList/Kitsu'daki yanlış MAL ID'ye bağlılık (kural: g14 çözümü `resolveVerifiedMalId...` + `knownMalId` geçişi var ama **kaynak-veri hatası tamamen önlenemez**) → belge-grensi |
| Bölüm-sıkıştırma (tracker-toplamı clamp) | ⚠️ MAL'de delil-yoktu, clamp EDİLMEDİ (Açık-5); AniList toplam-null-kuşkusu → ani-gui'nin kural seti AÇIK-SEÇENEK olarak raporlandı (kullanıcı kararı) |
| Simkl içe-aktarda tarih alanları | ℹ️ `SimklImportManager` tarih çekmiyor (watched_at yok) → 1970-YER-TUTUCU sızıntısı riski YOK; ama Simkl'den tarih-içe-aktarma da YOK (bilinçli/sınırlı davranış) |
