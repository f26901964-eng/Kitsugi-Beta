# Görev — Bangumi (bgm.tv) Çapraz Eşitleme (Cross-Sync) Tam Entegrasyonu

Tarih: 2026-10-10 · Branch: `arena/ce8cfe2b-kitsugi-beta`

## 1. İstek ve Arka Plan

Bangumi (bgm.tv) entegrasyonu uygulamaya daha sonradan eklenmişti. Detay sayfasında veya oynatıcıda yapılan anlık değişiklikler `ExternalListSyncManager` üzerinden Bangumi'ye aktarılıyor ve kütüphane tek seferlik içe aktarılabiliyordu. Ancak **toplu çapraz eşitleme ("Tüm Hesapları Birbiriyle Eşitle" / Cross-Sync)** motoruna Bangumi henüz dahil edilmemişti. Arayüzde bağlı hesap sayısı Bangumi hariç tutularak "5 / 5" şeklinde gösterilmekteydi ve toplu eşitleme butonuna basıldığında Bangumi kütüphanesi taranmıyordu.

## 2. Yapılan Değişiklikler

### 2.1 Arayüz ve Uyarı Düzenlemeleri
- **`AccountSettingsSubPages.kt`:**
  - `CrossSyncSettingsContent` composable'ında `connectedCount` hesaplamasına `profile.isBangumiConnected` eklendi.
  - "Bağlı hesaplar: 5 / 5" metni "Bağlı hesaplar: $connectedCount / 6" olarak güncellendi.
  - En az iki platform bağlama uyarısına Bangumi platform adı eklendi.
- **`CrossSyncDisclaimer.kt`:**
  - `CrossSyncDisclaimer.FULL` sorumluluk reddi metnine Bangumi platformu eklendi (`AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi`).
- **`KitsugiAccountConnectionsDialog.kt`:**
  - Bağlı hesap sayısı ve isimleri arasına Bangumi dahil edildi, toplam platform sayısı 6'ya güncellendi.
- **`TvSettingsScreen.kt`:**
  - TV eşitleme satırındaki metin genel platform senkronizasyonunu kapsayacak şekilde güncellendi.

### 2.2 Çapraz Eşitleme Motoru (`AuthViewModel.kt`)
- **`UnifiedSyncItem`:**
  - `var bangumi: MediaEntry? = null` alanı eklendi.
  - `candidates` listesine `bangumi` dahil edildi.
- **`resolveRealMalId`:**
  - Başlık aramasında Japonca başlık da arama ve doğrulama adaylarına eklendi (Bangumi kaynaklı kayıtların MAL kimliğinin çözülmesini kolaylaştırdı).
- **`startCrossSync` / `syncPlatforms`:**
  - `bangumiToken` kontrolü ve `isBangumi` bayrağı eklendi; `connectedPlatforms` listesine "Bangumi" yazıldı.
  - **Faz 1 (Kütüphane Çekme):** `isBangumi` aktifse `BangumiImportManager.fetchAllLists` ile kullanıcının tüm koleksiyonu çekilip başlangıç istatistiklerine kaydediliyor.
  - **Faz 2 (Kümeleme):** `clusterEntries(bangumiEntries) { item, entry -> item.bangumi = entry }` çağrısı eklendi. Bangumi kayıtları diğer platform kayıtlarıyla ortak kimlik ve başlık üzerinden birleştirildi.
  - **Önbellek Öğrenimi:** Birleşik kayıtta MAL ID ve Bangumi ID aynı grupta birleştiğinde `BangumiLocalMappingCache`'e yazılarak gelecekteki arama maliyeti sıfıra indirildi.
  - **Faz 3 (İki Yönlü Eşitleme):**
    - Kimlik güvencesi (`CrossSyncIdentityGuard`) kontrolünden sonra eksik kayıtlar `BangumiSyncManager.syncEntryToBangumi` ile Bangumi koleksiyonuna eklendi (`+ Eklendi`).
    - Mevcut kayıtlar için durum, bölüm ilerlemesi, cilt ilerlemesi veya puan gerideyse güncellendi (`~ Güncellendi`).
    - Eşleşmeyen içerikler güvenle "Atlandı" olarak rapora işlendi.
  - **Faz 4 (Doğrulama ve Smart Import):**
    - Eşitleme tamamlandıktan sonra güncel liste sunucudan okunarak `repository.smartImport("bangumi", ...)` ile yerel veri tabanına yazıldı.

### 2.3 Hız Limiti ve Kimlik Çözümleme İyileştirmeleri
- **`PlatformRateLimiter.kt`:**
  - `"bangumi" to 400L` (~150 istek/dakika) tanımlandı. Bangumi'nin 10 dakikada 3000 istek kuralına tam uyum sağlandı.
- **`BangumiSyncManager.kt`:**
  - `resolveSubjectId` fonksiyonu, Japonca başlık bulunamadığında sırasıyla İngilizce ve ana başlığı da kontrol edecek şekilde döngüye alındı.

### 2.4 Birim Testleri
- **`CrossSyncCandidateIndexTest.kt`:** Bangumi stable ID (`500_000_000 + subjectId`) indeksleme ve eşleştirme testi eklendi.
- **`CrossSyncReportFormatterTest.kt`:** Bangumi kayıtlarının raporda düzgün formatlandığı test edildi.

## 3. Değiştirilen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiSyncManager.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/auth/PlatformRateLimiter.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/components/CrossSyncDisclaimer.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAccountConnectionsDialog.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/AccountSettingsSubPages.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/tv/settings/TvSettingsScreen.kt`
8. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncCandidateIndexTest.kt`
9. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncReportFormatterTest.kt`
10. `PLAN_TASK_BANGUMI_CROSS_SYNC.md`
