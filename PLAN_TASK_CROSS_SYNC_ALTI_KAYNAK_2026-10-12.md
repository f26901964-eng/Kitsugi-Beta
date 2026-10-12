# PLAN & TASK — Çapraz Eşitleme: Altı Kaynağın Tamamı (v2.4.269)

**Tarih:** 2026-10-12 · **Dal:** `arena/9f13aa16-kitsugi-beta` · **Commit:** `cc7fb5d`
**Ayrıntılı teknik rapor:** `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`

## Kullanıcı isteği

> „AL, MAL, Simkl arası eşitleme çalışıyor. Bangumi, Shikimori, Kitsu kaynaklarına otomatik
> eşitlenmiyor. Hangi kaynaktan eklersem ekleyeyim içerik diğer kaynaklarda da otomatik
> eşitlenmeli — net %100 çalışacak şekilde.”

## Kabul kriterleri

- [x] K1 — Bir kaynaktan eklenen/düzenlenen/silinen kayıt, **bağlı olan diğer beş kaynağa** da yazılır.
- [x] K2 — Bangumi satırları diğer kaynaklara yayılır (yanlış yapım riski olmadan).
- [x] K3 — Kimlik çözülemeyen kaynak **hata değil atlama** sayılır; bekleme kuyruğu şişmez.
- [x] K4 — Kullanıcı, hangi kaynağın neden eşitlenmediğini **anında** görür (snackbar + rapor).
- [x] K5 — Yerel satır kendi kaynağının kimliğini (stableId) korur; o kaynağın listesinde görünür kalır.
- [x] K6 — Güvenlik kuralları korunur: doğrulanmamış kimlikle yeni kayıt eklenmez, belirsiz eşleşme yazılmaz.
- [x] K7 — Birim testleri karar tablosunu kapsar; derleme/sözdizimi temiz.

## Görev listesi

- [x] **T1** `data/auth/CrossProviderIdentity.kt` (yeni)
  - `CrossProviderIds` + `decode()`: MAL 1..99M, AniList 100M..299M, Simkl 150M..249M (kaynakla ayrışır),
    Kitsu 300M.., Shikimori 400M.., Bangumi 500M..; film/dizi satırında `malId` asla MAL sayılmaz.
  - `CrossProviderIdentityResolver`: decode → kalıcı önbellek → Shikimori→MAL → ARM → Kitsu `mappings`
    → Bangumi (önbellek/sıkı arama) → Simkl → son çare sıkı Jikan.
  - `CrossProviderIdentityCache` (`cross_provider_identity_v1`), eksik çözümde 6 saat cooldown;
    çözülen gerçek MAL kimliğiyle ikinci kayıt (başka kaynaktan aynı yapım ağa çıkmadan bulsun).
  - `CrossSyncPolicy`: tür limitleri, `allowFanOut`, `shouldRetry`, `summarize` (saf Kotlin, test edilebilir).
  - `ConnectedSources`: bağlı olmayan servis için katalog araması yapılmaz.
- [x] **T2** `ExternalListSyncManager` yeniden yazımı
  - Kimlik tek yerden; Kitsu'ya `knownKitsuMediaId`, Bangumi'ye `knownSubjectId`.
  - `skipped` + `outcomes` alanları; atlama artık `errors` içinde değil.
  - Yerel `malId` yazımı yalnızca satırda kimlik yoksa; `aniListEntryId` geri yazımı orijinal satıra.
  - Her yazmadan önce `PlatformRateLimiter.acquire(...)`; başarılı Bangumi yazmasında MAL↔subject_id öğrenimi.
  - `skipBangumiWrite` parametresi; `deleteEntry` simetrik ve **ağ araması yapmaz**.
- [x] **T3** Provider manager'ları
  - `ShikimoriSyncManager.SyncResult.unresolvedMedia`
  - `BangumiSyncManager.syncEntryToBangumi/deleteEntryFromBangumi(context, entry, knownSubjectId)`
    + `resolveSubjectId` öncelik sırası güncellendi.
- [x] **T4** Kuyruk/bildirim — `MediaEntryRepository` başarı ölçütü `errors.isEmpty()`;
  hata mesajına kaynak bazlı özet (`syncOutcomeNote`). `PendingSyncDrainer` aynı kural.
- [x] **T5** Giriş yolları — `AppViewModel.addApiSelectionToList`: 6 kaynaklık bağlılık kontrolü,
  `primarySource` Kitsu/Shikimori/Bangumi + satırın kendi kimlik alanında kalması;
  `MyListViewModel.saveBangumiEntry` ve `AppRoot` Bangumi düzenleme dalı → `skipBangumiWrite = true`.
- [x] **T6** Toplu eşitleme — `AuthViewModel.resolveRealMalId` önce çapraz önbellek, sonra aynı köprü.
- [x] **T7** Yerelleştirme — `account_sync_sources_skipped`, `account_sync_fanout_blocked_bangumi` (TR+EN).
- [x] **T8** Testler — `CrossProviderIdentityTest` (12 test).
- [x] **T9** Dokümantasyon — teknik rapor + `release_notes_v2.4.269.md` + `RELEASE_NOTES.md` +
  `appVersionName` 2.4.268 → 2.4.269.

## Doğrulama kaydı (bu ortam)

| Kontrol | Sonuç |
|---|---|
| tree-sitter-kotlin sözdizimi ayrıştırması (11 dosya) | hatasız |
| Parantez/süslü parantez dengesi | dengeli |
| `R.string` anahtar çözümlemesi TR/EN | 7009 = 7009, eksik yok |
| strings.xml XML ayrıştırması | temiz |
| Artık kod taraması (`effectiveEntry`, eski `rawAniListId` zinciri) | temiz |
| `gradlew test / assemble` | **koşulamadı** (JDK/Android SDK yok) → cihazda/CI'da koşulmalı |

## Kalan riskler / bilinçli kısıtlar

1. Bangumi eşleşmesi başlık + yıl aramasına dayanır (resmî MAL/AniList eşleme ucu yok) →
   bulunamazsa kayıt Bangumi'de atlanır; bir kez Bangumi'den eklenince köprü önbelleğe alınır.
2. Kitsu/Shikimori anime+manga dışını desteklemez → film/dizi Simkl/Bangumi hattına gider.
3. Shikimori hedef kimliği MAL ID'dir; MAL kimliği hiç çözülemezse Shikimori yazımı atlanır.
4. Tek bir düzenlemede artık 6 HTTP yazımı sıralı yapılır; `PlatformRateLimiter` yalnızca aynı
   platform art arda çağrılırsa bekletir (ilk çağrıda gecikme yok).

## Cihazda test adımları

1. Ayarlar → Hesaplar: 6 hesabı bağla, „eşitleme kaynakları" anahtarları açık.
2. Keşfet'ten **yalnızca Kitsu veya Bangumi** sonucundan anime ekle → snackbar özeti oku.
3. Bölüm ilerlemesini artır → aynı satır tekrar görünmeli; „bekleyen işlem" sayısı sabit kalmalı.
4. Ayarlar → Eşitleme raporu → `Atlanan` satırlarındaki gerekçeyi paylaş (bizim taraf için teşhis verisi).
5. Kaydı sil → 5 kaynakta kalkmalı, Bangumi `抛弃` olur (platform kısıtı).
