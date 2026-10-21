# Kitsugi Beta v2.4.337

## 🇹🇷 Türkçe

### 🛡️ 6 Kaynaklı Çapraz Eşitleme Güvencesi & Yanlış Eşleşme Ön-Kapısı (Cross-Sync Hardening)
- **Tüm Hedeflere Genişletilen Kimlik Ön-Kapısı (`CrossSyncIdentityGuard` & `crossWriteBlocked`):**
  - Daha önce yalnızca AniList'e yazarken çağrılan MAL resmi katalog doğrulaması (başlık + tür + tam yıl); artık **MAL, Simkl, Kitsu, Shikimori, Bangumi ve AniList** hedeflerinin tamamına genişletildi.
  - Hatalı ID eşlemelerinin (Simkl `ids.mal`, Kitsu mappings vb.) başka bir eserin puanını, izleme durumunu veya bölümünü sessizce ezmesi tamamen engellendi. Doğrulanamayan eşleşmeler sessizce atlanır (`OUTCOME_SKIPPED_IDENTITY`) ve senkron özeti ekranında açıkça belirtilir.
- **Yanlış-Kimlikli Silme Koruması (`deleteEntry`):**
  - Doğrulanmamış veya yanlış ID eşleşmesine sahip öğelerin diğer servislerdeki gerçek eserleri silmesini önlemek amacıyla 6 hedefin tamamına silme ön-kapısı getirildi.
- **Simkl, Shikimori & Bangumi İyileştirmeleri:**
  - Simkl API için saniyede en fazla 10 GET / 1 POST istek kilidi (`awaitSimklRateLimit`) ve bilinen toplam bölümü aşan ilerlemelerin sıkıştırılması (clamp) eklendi.
  - Shikimori domain fallback (`shikimori.tv`) ve Kitsu alternatif host desteği devreye alındı.
- **15 Kapsamlı Kaynak Araştırma ve Denetim Dokümanı:**
  - `ARASTIRMA_KAYNAK_SENKRON/` dizinine 15 parçalık birinci el API semantiği, hız limitleri ve çapraz denetim raporu (`14_CAPRAZ_ESITLEME_DENETIMI.md`) eklendi.

---

## 🇬🇧 English

### 🛡️ 6-Provider Cross-Sync Identity Guard & Mismatch Protection
- **Extended Identity Gate Across All 6 Targets (`CrossSyncIdentityGuard` & `crossWriteBlocked`):**
  - Official MAL catalog verification (strict title + media type + release year matching), previously only enforced when writing to AniList, is now applied across all 6 targets: **MAL, Simkl, Kitsu, Shikimori, Bangumi, and AniList**.
  - Prevents inaccurate third-party mapping IDs from silently overwriting scores, progress, or statuses on unrelated titles. Unverifiable cross-writes are safely skipped (`OUTCOME_SKIPPED_IDENTITY`) with transparent user notices.
- **False-Identity Deletion Guard (`deleteEntry`):**
  - Prevents accidental remote item deletion caused by ambiguous or mismatched cross-provider IDs across all 6 sync destinations.
- **Simkl, Shikimori & Bangumi Protections:**
  - Enforced Simkl 10 GET / 1 POST per second rate-limiters (`awaitSimklRateLimit`) and episode progress clamping to known totals.
  - Added Shikimori domain fallback (`shikimori.tv`) and robust Kitsu host resolution.
- **Research & Cross-Sync Audit Suite:**
  - Added 15 comprehensive audit and API semantics documents under `ARASTIRMA_KAYNAK_SENKRON/` including `14_CAPRAZ_ESITLEME_DENETIMI.md`.
