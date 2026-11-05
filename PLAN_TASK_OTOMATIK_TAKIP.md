# PLAN_TASK — Oynatıcıdan Otomatik Liste Takibi (7 Kaynak Köprüsü)

> Sürüm hedefi: **v2.4.341** · Branch: `arena/7deae671-kitsugi-beta`
> Kullanıcı isteği: "Hangi kaynaktan açılırsa açılsın içerik oynatılınca listeye işlensin,
> bölüm bitince izlenen bölüm sayısı artsın, film/tek bölüm izlendi olsun, son bölümde
> tamamlandıya geçsin, fragman/jenerik hesaba katılsın ve bitişte pencere değil oynatıcı
> içinde küçük bir puanlama çubuğu çıksın."

## 1. Kapsam (ne yapılacak)

| # | Gereksinim | Uygulama yeri |
|---|------------|----------------|
| 1 | Her oynatmada içerik listeye işlenir, durum **İzleniyor** olur | `AutoTrackPolicy.evaluate` → `START_WATCHING` · `PlayerAutoTrackController` |
| 2 | Bölüm bitince izlenen bölüm sayısı **+1** (bölüm numarası yazılır) | `Action.ADVANCE_EPISODE` + `applyEpisodeProgress` |
| 3 | Film / tek bölümlük içerik doğrudan **izlendi** | `Kind.MOVIE` → `Action.COMPLETE` |
| 4 | Uzun içerikte toplam bölüm otomatik; son bölümde **Tamamlandı** | `total` (bölüm listesi / kaynak toplamı) + `hasNextEpisode` |
| 5 | Fragman ve bitiş jeneriği tamamlanma hesabına girer | `isTrailer`, `outroStartMs`, `minTrackableDurationMs` |
| 6 | Bitişte **pencere değil**, oynatıcı içinde küçük puanlama çubuğu | `QuickRateBar` + `PlayerAutoTrackController.quickRate` |
| 7 | 7 kaynağa **uyumlu köprü** (AniList, MAL, Kitsu, Shikimori, Bangumi, Simkl, TMDB) | `PlaybackListWriter` → `MediaEntryRepository.update` → `ExternalListSyncManager` |
| 8 | Mantıksal ekler: ilerleme kaydı, kaynaklar arası eşitleme, geçmişten geri doldurma | `HistoryRepository.watched`, `backfillFromWatchHistory`, ayarlar |

## 2. Mimari (neden bu katmanlar?)

```
Oynatıcı (Media3/MPV/external)
        │  onPositionChanged / ENDED / ekrandan çıkış
        ▼
PlayerAutoTrackController (ui/screens/fullscreen/runtime)
        │  saf karar: AutoTrackPolicy.evaluate(...)     ← test edilebilir, Android'siz
        ▼
PlaybackListWriter (data/repository)   ← TEK yazma kapısı
        │  resolveEntry → createEntry / updateEntry / rateEntry
        ▼
MediaEntryRepository.update(syncExternal = ...)  → mirrorUserFieldsToSiblings
        ▼
ExternalListSyncManager.syncEntry  → AniList · MAL · Kitsu · Shikimori · Bangumi · Simkl
```

- **Saf politika**: `AutoTrackPolicy` Android/Room/Compose bilmez → birim testi + `PlayerPolicyChecks`.
- **Tek yazma kapısı**: oynatıcı doğrudan DAO/API çağırmaz; böylece "Bırakıldı"ya saygı,
  tarih otomasyonu (`EntryAutoRules`), bekleyen-senkron kuyruğu ve kardeş satır yansıması
  ikinci kez yazılmaz.
- **İki geçmiş deposu**: `watch_history` (Room, `watched` bayrağı) + `WatchHistoryManager`
  (prefs, "devam et"). Üçüncü bir yol açılmaz; oynatıcı anahtarı (`malId ?: aniListId ?: title.hashCode()`)
  her iki yolda **aynı** tutulur.

## 3. Kaynak köprüleri

| Kaynak | Satır bulma | Yeni satır | Yayma |
|--------|-------------|-----------|-------|
| AniList | `Anime:anilist:<100M+id>` | kaynak `anilist`, kararlı kimlik | `AniListSyncManager` |
| MyAnimeList | `Anime:mal:<id>` | kaynak `mal` | `MalSyncManager` |
| Kitsu | `Anime:kitsu:<300M+id>` | kaynak `kitsu` | `KitsuSync` |
| Shikimori | `Anime:shikimori:<400M+id>` | kaynak `shikimori` | `ShikimoriSync` |
| Bangumi | `Anime:bangumi:<500M+id>` | kaynak `bangumi` | Bangumi yazma kapısı |
| Simkl | `simklId` kolonu | kaynak `simkl` | `SimklSyncManager` |
| TMDB (film/dizi) | `tmdb:<id>` anahtarı | kaynak `tmdb` (yoksa `simkl`) | Simkl üzerinden |

Zenginleştirme: `KitsugiIdResolver.resolveIds(malId, aniListId, tmdbId)` — TMDB'den açılan
bir dizi, AniList'te tutulan aynı animeyi bulur. Ağ yoksa ham kimliklerle devam edilir.

## 4. Tamamlanma kuralları

1. Motor `ENDED` → tamamlandı (konumdan bağımsız).
2. Oran ≥ `%85` → tamamlandı.
3. Kapanış jeneriği (`ed`/`mixed-ed`/`ending`/`outro`) başlangıcı geçildi → tamamlandı.
4. Son bölüm / `hasNextEpisode == false` / film → `Completed` + ilerleme = toplam.
5. Fragman (`videoId`) ve `< 60 sn` (ayarlanabilir) klipler → **hiç takip edilmez**.
6. İlerleme **asla azalmaz**; `Completed` satır yeniden izlenince bozulmaz; `Dropped` saygı görür.

## 5. Ayarlar (Ayarlar → Deneysel Özellikler)

**Varsayılan: KAPALI.** Açmak için onay penceresi zorunlu (`ExperimentalSettingsScreen`).

`autoTrackEnabled` (ana anahtar, default `false`), `autoTrackAddNew`, `autoTrackRespectDropped`,
`autoTrackSyncSources`, `autoTrackMinSeconds` (15–300), `quickRateEnabled`, `quickRateSeconds` (5–60).
Alt anahtarlar yalnızca ana anahtar açıkken görünür; kapalıyken oynatıcı hiçbir yazım yapmaz
(controller + policy çift katman kapı).

## 6. Veritabanı

Room **v30** + `MIGRATION_29_30`: `watch_history.watched INTEGER NOT NULL DEFAULT 0`.
Şema `app/schemas/.../30.json`. Yeni DAO sorguları: `markWatched`, `maxWatchedEpisode`,
`resetPositionAsWatched`, `deleteAllForMedia`.

## 7. Test / doğrulama

- `AutoTrackPolicyTest` (~18 vaka), `AniSkipPolicyTest` (7 vaka) — CI allowlist'inde
  `com.kitsugi.animelist.core.player.*` bunları zaten kapsar.
- `scripts/run_player_policy_checks.sh` (gerçek üretim Kotlin'i ile saf politika kontrolü).
- Yerelleştirme paritesi: `scripts/check_localization_resource_parity.py` → PASS.

## 8. Riskler

| Risk | Önlem |
|------|-------|
| Uzak API'leri dövmek | 15 sn yazma tavanı + "değişiklik yok" tespiti (`changed=false`) |
| Bayat tick'in ilerlemeyi düşürmesi | `maxOf(mevcut, hedef)` + toplam sınırı |
| Yanlış yapımın eşleşmesi | Kimlik kanıtı önce; başlık yalnızca son çare; tür uyumu |
| Oynatıcı çıkışında kaybolan ilerleme | `onLeaving` son değerlendirmesi (tavan atlanır) |
| Konum hatırlama kapalıyken bayrak kaybı | `positionOnly` yazımı |
| Dalvik 128-slot tavanı (AppSettings) | `AppSettingsAndCrashDiagnosticsGuardTest` CI'da |

## 9. Teslim

1. Kod + testler + şema.
2. Bu plan ve `OTOMATIK_TAKIP_RAPORU_2026-11-05.md` raporu.
3. `release_notes_v2.4.341.md`, `appVersionName` yükseltmesi.
