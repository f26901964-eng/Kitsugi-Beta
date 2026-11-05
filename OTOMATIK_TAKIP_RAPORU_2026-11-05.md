# OTOMATİK TAKİP RAPORU — 2026-11-05

**Konu:** Oynatıcıdan **7 kaynağa** otomatik liste takibi + bitişte küçük puanlama çubuğu
**Sürüm:** v2.4.341 · **Branch:** `arena/7deae671-kitsugi-beta`

---

## 1. Kullanıcının istediği davranış → karşılığı

| İstek | Durum | Nerede |
|-------|-------|--------|
| Oynatınca listeye işle, durum "İzleniyor" | ✅ | `AutoTrackPolicy.evaluate` → `START_WATCHING` (eşik %5) |
| Bölüm bitince izlenen bölüm sayısını artır | ✅ | `applyEpisodeProgress` → `progress = max(mevcut, bölüm)` |
| Film / tek bölüm → doğrudan izlendi | ✅ | `Kind.MOVIE` → `COMPLETE`, `progress = total ?: 1` |
| Bölümlü içerikte toplam bölüm otomatik | ✅ | `totalEpisodes` = bölüm listesi sonu (yoksa kaynak `total`) |
| Son bölüm izlenince "Tamamlandı" | ✅ | `!hasNextEpisode || episode >= total` |
| Fragman / bitiş jeneriği hesaba katılsın | ✅ | `isTrailer` + `outroStartMs` + `minTrackableDurationMs` |
| Bitişte pencere değil, oynatıcıda küçük puan barı | ✅ | `QuickRateBar` (10 yıldız, geri sayım, TV D-pad uyumlu) |
| 7 kaynağa uyumlu köprü | ✅ | `PlaybackListWriter` → `ExternalListSyncManager` |
| Mantıksal ekler | ✅ | aşağıdaki §3 |

## 2. Bu turda bulunan **gerçek hatalar** ve düzeltmeleri

1. **Jenerik türü hiç eşleşmiyordu (kritik).**
   Kod `it.type == "outro"` arıyordu; AniSkip `ed`/`mixed-ed`, AnimeSkip `ending` üretir.
   Sonuç: "jeneriği atlayınca bölüm bitti sayılsın" ve bitişte "sonraki bölüm" sayacı
   **sessizce hiç çalışmıyordu**. Tek kaynak `AniSkipPolicy.isOutroType/outroStartSec`
   eklendi; oynatıcı, otomatik takip ve atla butonu etiketi artık aynı sözlüğü kullanıyor.
2. **AniSkip geç yüklenirse kayboluyordu.** Aralık yalnızca `bind()` anında okunuyordu;
   ağdan sonra gelen jenerik bilgisi takibe hiç ulaşmıyordu → `skipIntervals.collect`.
3. **Bölüm bitiminde "izlendi" bayrağı siliniyordu.** `onEpisodeEnded` geçmiş satırını
   `deleteProgress` ile siliyordu; az önce yazılan bayrak ve "devam et" dayanağı yok oluyordu.
   Yeni `resetAfterFinish` konumu sıfırlar, bayrağı korur.
4. **Geçmiş anahtarı iki farklıydı.** Otomatik takip `entry.id` kullanıyordu, oynatıcı
   `malId ?: aniListId ?: title.hashCode()`. Aynı bölüm için ikinci satır açılıyordu
   (devam et yanlış yer, backfill hiç sonuç yok) → tek anahtar.
5. **Konum hatırlama kapalıyken izleme kaydı yok.** `saveProgress` erken çıkıyordu; %85'i
   geçmiş bölüm "izlenmedi" sayılabiliyordu → `positionOnly` yolu.
6. **Oynatıcıdan çıkışta son ilerleme kayboluyordu.** 15 sn tavanı son saniyeleri yutuyordu
   → `onLeaving` (çıkışta tavan atlanır, yalnızca eşiği geçen bölüm ilerler).
7. **Film puanlanınca "Tamamlandı · 0/1" kalıyordu** → `progressAfterScore`.
8. **`stringResource` satır içi lambda'da** çağrılıyordu (derlenmez) → önceden hesaplandı.

## 3. Eklenen mantıksal özellikler

- **Geçmişten geri doldurma:** kullanıcı 5. bölümü izlerken geçmişte 1–4 varsa liste geride kalmaz
  (`backfillFromWatchHistory`), tamamlanma kararı verilmez.
- **Kaynaklar arası eşitleme:** her yazım `mirrorUserFieldsToSiblings` ile aynı yapımın diğer
  kaynak satırlarını da güncellesin; `autoTrackSyncSources` kapalıysa yalnızca yerel yazım.
- **Çevrimdışı dayanıklılık:** uzak yazım başarısızsa `MediaEntryRepository` bekleyen-senkron
  kuyruğuna koyar; bağlantı gelince kendiliğinden gönderilir.
- **"Bırakıldı"ya saygı:** otomatik takip kullanıcı iradesini ezmez (ayarla kapatılabilir).
- **Tekrar izleme:** `Completed` kayıt yeniden izlenince ilerleme düşmez, puanlama çubuğu yine çıkar.
- **Canlı yayın koruması:** süre 0 ise yalnızca `ENDED` ile karar verilir.
- **Fragman ayrımı:** YouTube kimliğiyle açılan tanıtım/jenerik oynatmaları hiç takip edilmez.
- **Ayar arayüzü:** Player → Gelişmiş → "Otomatik takip" bloğu (TR + EN, 7 ayar, canlı önizleme).
- **Kaynak rozeti:** puan çubuğunda yazının hangi platforma işlendiği görünür (TMDB/AniList/… ).

## 3b. Kaynak köprüsünün eksik halkaları (2. tur)

| Sorun | Sonuç | Düzeltme |
|-------|-------|----------|
| Bangumi kimliği oynatıcıya hiç ulaşmıyordu | Bangumi'den açılan bölüm liste satırı bulunamıyor, yanlış kaynağa yazılabiliyordu | `bangumiId` → Stream/Player Activity → Screen → ViewModel → `PlaybackIdentity`; yeni satır **500M+id** kararlı kimliğiyle Bangumi uzayında |
| Fragmanlar `videoUrl` ile açıldığında `videoId` boş kalıyordu | Tanıtım/jenerik oynatması "bölüm izlendi" sayılabiliyordu | `treatAsTrailer` bayrağı + tema/fragman çağrılarında `true` |
| İndirilen bölüm oynatılırken kimlik aktarılmıyordu | Çevrimdışı izleme listeye hiç işlenmiyordu | `DownloadedPlayer`/`DownloadsScreen` yollarına `malId/aniListId/tmdbId/season/episode` |
| Geçmiş ekranından devam ederken Bangumi kimliği yoktu | Aynı bölüm ikinci bir anahtarla yazılabiliyordu | `WatchHistoryEntry.bangumiId` + kayıt/oynatma yolları |
| Ekran ile ViewModel geçmiş anahtarı farklıydı (bangumi eksik) | Aynı bölüm için ikinci geçmiş satırı | Ortak anahtar: `malId ?: aniListId ?: bangumiId ?: title.hashCode()` |
| TMDB film/dizi satırı `simklId` ile eşleşmiyordu | Simkl'de tutulan film tekrar eklenebiliyordu | `PlaybackIdentity.simklId` + ayrı probe, TMDB satırı `tmdb` uzayında |

## 3c. Deneysel konum + onay penceresi + varsayılan KAPALI (3. tur)

Kullanıcı isteği: *"ayarlarda uygun bir konumda deneysel özellik olarak, gerekli uyarı/onay
penceresiyle; açılıp kapatılabilir; varsayılan olarak kapalı — elle açmadan çalışmasın."*

| Gereksinim | Uygulama |
|-----------|----------|
| Deneysel olarak konumlandırma | Yeni **Ayarlar → Deneysel Özellikler** sayfası (`ExperimentalFeatures` route) + ana menüde "Deneysel" bölümü, ayarlar aramasında iki kayıt |
| Uyarı/onay penceresi | `KitsugiConfirmDialog` — listelerin değişeceğini, ilerlemenin geri alınamayacağını, tüm platformlara yayılacağını söyleyen metin; yalnızca **açarken** çıkar |
| Açılıp kapatılabilir | Tek anahtar; kapatma anında ve oynatma sırasında bile etkili (`onTick` her tikte ayarı yeniden okur, `bind`/`onLeaving`/`rate` kapılı) |
| Varsayılan KAPALI (eski kurulumlar dahil) | `AppSettings.autoTrackEnabled = false`, `KitsugiContentPrefs.autoTrackEnabled = false`, `AutoTrackPolicy.Settings.enabled = false`; DataStore artık `pref_auto_track_experimental_consent` onayı da arar. Eski sürümün varsayılan `true` kaydı **tek başına izin sayılmaz**; sadece yeni onay diyaloğundan sonra anahtar `true` olur. |
| Bağlantılı özellikler de kapalı | Alt anahtarlar (yeni ekleme, kaynaklara yazma, "Bırakıldı"ya saygı, hızlı puanlama, puan çubuğu süresi ve minimum takip süresi) yalnızca ana anahtar açıkken görünür; puan çubuğu süresi de kendi alt anahtarı açıkken görünür. Kapatınca eski seçimler korunur. |
| Kapalıyken sıfır yazım | `PlayerAutoTrackController.bind` hiç çözümleme yapmaz; `onTick`/`onLeaving`/`rate` kapıda döner; kuyruğa alınmış IO yazımı yazma kilidi içinde yeniden kontrol edilir; `AutoTrackPolicy.evaluate` `NONE` döner. Özellik oynarken açılırsa önce mevcut kayıt salt-okunur çözülür, sonra karar verilir (yanlış yinelenen satırı önler). |
| Oynatıcı ayarlarındaki eski blok | Kaldırıldı (tek yerde yönetim; iki yerden açma riski yok) |

Test: `AutoTrackPolicyTest."auto tracking is OFF by default until the user opts in"` +
`scripts/tests/PlayerPolicyChecks.kt` aynı kontrolü doğrular.

## 4. Dosya haritası

```
core/player/AutoTrackPolicy.kt                 (yeni) saf karar motoru
core/player/AniSkipPolicy.kt                   (+) jenerik tür sözlüğü + outroStartSec
data/repository/PlaybackListWriter.kt          (yeni) resolve/create/update/rate köprüsü
ui/screens/fullscreen/runtime/PlayerAutoTrackController.kt (yeni) runtime denetleyici
ui/screens/fullscreen/components/QuickRateBar.kt           (yeni) küçük puanlama çubuğu
data/local/{HistoryEntity,HistoryDao,HistoryRepository,KitsugiDatabase}.kt  (+) watched + v30
data/settings/{AppSettings,SettingsDataStore,KitsugiContentPrefs}.kt        (+) 7 ayar
ui/screens/fullscreen/{KitsugiPlayerViewModel,KitsugiFullscreenPlayerScreen}.kt (+)
ui/components/player/PlayerParityOptions.kt                  (+) ayar bloğu
res/values*/player_parity_strings.xml                        (+) 16 metin (TR/EN)
app/schemas/.../30.json                                      (yeni) şema
test: AutoTrackPolicyTest.kt, AniSkipPolicyTest.kt           (yeni)
scripts/tests/PlayerPolicyChecks.kt                          (+) politika kontrolleri
```

## 5. Doğrulama

- `python3 scripts/check_localization_resource_parity.py` → **PASS** (17.712 dizge).
- `AutoTrackPolicyTest` 18, `AniSkipPolicyTest` 7 vaka; `core.player.*` CI allowlist'inde.
- Derleme bu ortamda yapılamıyor (JDK/Gradle yok); **CI `android-build.yml`** hem debug hem
  R8 release APK + DEX doğrulaması yapar. Kırmızı olursa `build-log-summary` check-run'ı
  dosya/satır verir.

## 6. Bilinen sınırlar

- TMDB'nin yazma API'si yok: film/dizi satırı TMDB uzayında tutulur, yayma Simkl bağlıysa
  Simkl üzerinden olur (Simkl yoksa yalnızca yerel).
- Bölüm numarası kaynaktan gelir; sezon paketlenmiş ("S2 bölüm 1–12 tek satır") kaynaklarda
  toplam, oynatıcının bildirdiği bölüm listesine göre belirlenir.
- Manga/novel oynatma yolu bu takibin dışında (oynatıcı yok).
