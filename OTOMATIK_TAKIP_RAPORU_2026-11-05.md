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
