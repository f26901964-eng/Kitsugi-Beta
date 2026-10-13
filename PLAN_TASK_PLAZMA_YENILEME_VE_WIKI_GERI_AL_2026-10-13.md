# PLAN TASK — Plazma Küre Yenileme Animasyonu & Wiki "Hayır, yanlış" Geri Alma

**Tarih:** 2026-10-13  
**Durum:** Uygulandı  
**Sürüm:** 2.4.282

## Amaç

1. Aşağı doğru çekince gelen tüm yenileme (pull-to-refresh) animasyonlarının, tam ekran yüklenme ekranındaki parlayan plazma küre animasyonunun aynısı olmasını sağlamak.
2. Wiki sekmesinde yanlışlıkla "Hayır, yanlış" seçeneğine tıklayan kullanıcının bu kararı geri alabilmesini sağlamak; geri dönüşü olmayan ret akışını düzeltmek.

## Kapsam ve uygulama

### 1. Plazma küre yenileme göstergesi

- Yeni ortak bileşen eklendi: `ui/components/KitsugiPullToRefresh.kt` → `KitsugiPullToRefreshIndicator(state, isRefreshing, modifier)`.
  - Varsayılan Material göstergesi (siyah yuvarlak içinde dönen ok) yerine `KitsugiPlasmaLoader` (plazma küre) çizer.
  - Küre, çekiş mesafesiyle (`state.distanceFraction`) belirip büyür; yenileme sürerken tam boyunda (40.dp) gösterilir.
  - Çekiş yokken (`fraction <= 0.01`) hiç çizilmez; boşta duran sayfalarda sonsuz animasyon / Canvas maliyeti oluşmaz.
- Aşağıdaki 8 `PullToRefreshBox` kullanımının tamamında varsayılan gösterge ortak bileşenle değiştirildi:
  - Profil ekranı (`KitsugiProfileScreen`) — MyAnimeList/AniList vb. sekmeler
  - Keşfet (`ExploreScreen`)
  - Listem (`MyListComponents`)
  - Medya detay iskeleti (`DetailPageScaffold`) — `ApiResultDetailPage` ve `MediaEntryDetailPage` bunu kullanır
  - Karakter detayı (`CharacterDetailPage`), Ekip detayı (`StaffDetailPage`)
  - Kullanıcı medya listesi (`KitsugiUserMediaListScreen`)
  - Kullanıcı profili (`KitsugiUserProfileScreen`) — önceden özel göstergesi yoktu (varsayılana düşüyordu), açıkça eklendi
- `ApiResultDetailPage` ve `MediaEntryDetailPage` içindeki kullanılmayan `PullToRefreshBox/PullToRefreshDefaults/rememberPullToRefreshState` importları temizlendi.

### 2. Wiki ret geri alma

- **Veri katmanı** (`FandomWikiPrefs.kt` → `FandomUserChoices`):
  - `unrejectPage(...)`, `unrejectWiki(...)` eklendi.
  - `clearRejections(...)` eklendi: yalnızca `page:`/`wiki:` ret anahtarlarını siler, onaylanan eşleşmeler (`accepted-*`) korunur.
- **Repository** (`FandomRepository.kt`):
  - `undoRejectMediaWiki(...)`: yapım wiki reddini kaldırır + wiki önbellek anahtarlarını (bellek + DB) düşürür.
  - `undoRejectCharacterPage(...)`: sayfa reddini kaldırır; wiki isimle bulunmuş ve kesinleşmemişse (`isWikiUnconfirmedNameMatch`) wiki reddini de kaldırır + karakter/wiki önbelleklerini düşürür.
  - `restoreRejectedMatches(...)`: tüm retleri siler + Fandom önbelleğini (bellek + `KEY_PREFIX` DB) düşürür; onaylar korunur.
- **ViewModel'ler**:
  - `FandomMediaWikiViewModel` ve `FandomCharacterWikiViewModel` içine `lastRejected: StateFlow` eklendi.
  - `reportWrongMatch()` reddi kaydettikten sonra `rebind()` çağırır ve **sonrasında** `lastRejected` değerini koyar; böylece kart yeni durumda da (sıradaki aday bulunsa da bulunamasa da) görünür kalır.
  - `undoLastReject()` ve `dismissLastRejected()` eklendi.
  - Kart, bağlama anahtarı (`lastRejectedBindingKey`) değişince — yani başka bir yapım/karaktere geçilince — otomatik düşer; aynı yapımda `rebind()` sırasında korunur.
- **Arayüz**:
  - Yeni kart: `WikiRejectedUndoCard` (`CharacterWikiTabContent.kt`) — "Eşleşme yanlış işaretlendi" + `"<ad>" bir daha gösterilmeyecek…` + **[Kapat]** / **[Geri al]**.
  - Yapım Wiki sekmesinde (`MediaWikiTabContent` / `MediaWikiTabSlot`) ve karakter/ekip Wiki sekmelerinde (`CharacterWikiTabSlot` + `CharacterDetailPage` iki yerleşim) sekmenin en üstünde gösterilir; `Unavailable` durumunda bile görünür.
  - Geri almada kullanıcıya Toast gösterilir: "Geri alındı, eşleşme tekrar gösterilecek."
- **Ayarlar → Fandom Wiki** (`FandomWikiSettingsSection.kt`):
  - "Reddedilen eşleşmeleri geri getir" (`Icons.Rounded.History`) öğesi eklendi; kart kapatılmış olsa bile kurtarma yolu sağlar. Toast: "Reddedilen eşleşmeler geri getirildi."
- **Metinler** (TR `values/strings.xml` + EN `values-en/strings.xml`):
  - Yeni: `fandom_reject_undo_title/desc/action/done`, `fandom_settings_restore_rejected(_desc)`, `fandom_settings_rejected_restored`
  - Güncellenen: `fandom_confirm_desc` — yanlış denen eşleşmenin sonradan geri alınabileceği belirtiliyor.

### 3. Sürüm

- `app/build.gradle.kts`: `appVersionName = "2.4.282"`
- `release_notes_v2.4.282.md` (TR + EN) eklendi.

## Kabul kriterleri

- [ ] Profil, Keşfet, Listem, detay ve kullanıcı ekranlarında aşağı çekince siyah yuvarlak/ok yerine plazma küre görünür; küre çekişle büyür, yenilerken tam boyda kalır.
- [ ] Boşta duran sayfalarda yenileme göstergesi çizilmez (performans regresyonu yok).
- [ ] Wiki sekmesinde "Hayır, yanlış"a basınca üstte geri alma kartı belirir; "Geri al" eşleşmeyi geri getirir.
- [ ] Geri alma kartı kapatılırsa ret kalıcı olur ama Ayarlar → Fandom Wiki → "Reddedilen eşleşmeleri geri getir" ile topluca geri getirilebilir; onaylanmış eşleşmeler silinmez.
- [ ] Başka bir yapım/karaktere geçince eski geri alma kartı görünmez.

## Değişen dosyalar

- `app/build.gradle.kts` (2.4.282)
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPullToRefresh.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiProfileScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserProfileScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt` (ölü import temizliği)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt` (ölü import temizliği)
- `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomWikiPrefs.kt`
- `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt` (`WikiRejectedUndoCard`)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabSlot.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiSettingsSection.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `release_notes_v2.4.282.md`
- `PLAN_TASK_PLAZMA_YENILEME_VE_WIKI_GERI_AL_2026-10-13.md` (bu dosya)

## Doğrulama

- `PullToRefreshDefaults.Indicator` kullanımı kalmadı (grep ile doğrulandı).
- TR/EN `strings.xml` XML ayrıştırması başarılı.
- Değişen tüm Kotlin dosyalarında küme/parantez dengesi kontrolü başarılı.
- Gradle derlemesi çalıştırılamadı; sandbox ortamında Java/JAVA_HOME ve Android SDK bulunmuyor. Cihaz/yerel derlemede kontrol edilmesi gerekir.
