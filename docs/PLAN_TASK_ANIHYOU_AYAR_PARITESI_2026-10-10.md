# PLAN/TASK — AniHyou Ayar Paritesi: Eksik Ayarların Tüm Kaynaklara Bağlanması

Tarih: 2026-10-10
Referans: Kullanıcının paylaştığı 3 ekran görüntüsü (AniHyou "Ayarlar" ekranı —
İçerik / Liste / Bildirimler bölümleri).

## Amaç

Ekran görüntülerindeki uygulama ayarlarında olup Kitsugi'de **olmayan** ayarların
eksiksiz eklenmesi ve her birinin uygulamanın desteklediği **tüm kaynaklara**
(AniList, MyAnimeList + Jikan, Kitsu, Shikimori, Bangumi, Simkl, TMDB) davranış
olarak bağlanması.

## Eklenen Ayarlar (veri katmanı)

`data/settings/AppSettings.kt` + `data/settings/SettingsDataStore.kt`
(DataStore anahtarları, setter'lar, bulut-yedek `cloudKeys` allowlist'i):

| Ayar | Anahtar | Varsayılan | AniHyou karşılığı |
|---|---|---|---|
| Puanlama adımları | `score_step` ("1" / "0.5") | "1" | `score_steps` |
| Ekip & Karakter adı dili | `staff_name_language` (ROMAJI/ENGLISH/NATIVE) | ROMAJI | `staffNameLanguage` |
| Ayrılmış liste tarzını kullan | `separated_list_style` | true | separated list style |
| Düşük önceliği göster | `show_low_priority` | false | `show_low_priority` |
| Öncelik renklerini değiştir | `priority_colors_json` ({"0":argb,"1":argb,"2":argb}) | "" | `color_*_priority` |
| Fuzzy arama kullan | `fuzzy_search_enabled` | true | `use_fuzzy_search` |
| Roman ve mangaları ayır | `separate_novels_manga` | false | `separate_novels_and_manga` |

Yeni `data/settings/KitsugiContentPrefs.kt`: Compose dışı istemcilerin (arama,
manga skorlayıcı, kartlar) senkron okuyabildiği uçucu önbellek
(`KitsugiTranslatePrefs` deseni). `settingsFlow` her emisyonunda güncellenir.
Ayrıca fuzzy eşleşme yardımcısı (`fuzzyMatches`: içerir → kısaltma → alt-dizi →
Levenshtein toleransı).

## Arayüz (Ayarlar → Görünüm & Tercihler → Liste & Puan sekmesi)

`KitsugiPreferencesSettingsDialog.kt` "Liste & Puan" sekmesine yeni satırlar:
- "Ekip & Karakter adı dili" (Romaji / İngilizce / Ana Dilde)
- "Puanlama adımları" (1 / 0,5)
- Yeni **"Liste"** bölümü: Ayrılmış liste tarzı, **Özel Listeler** (AniList
  `UpdateUser` mutasyonu ile oluştur/yeniden adlandır/sil — yeni
  `AniListSyncManager.updateCustomLists`), Düşük önceliği göster,
  Öncelik renklerini değiştir (seviye başına hex renk seçici + Sıfırla),
  Fuzzy arama kullan (ekran görüntüsündeki açıklama metniyle),
  Roman ve mangaları ayır.

Kablolama zinciri: `GeneralSettings` (SettingsScreenParameters) →
`AppRootSettingsExtras` handler'ları → `SettingsDataStore` setter'ları.

## Kaynaklara Bağlantı

1. **Ekip & Karakter adı dili** — kişi adları artık başlık dilinden bağımsız çözülür:
   `CharactersTab`, `CharacterDetailPage`, `StaffDetailPage`, `SearchScreen`
   (karakter/personel arama) `displayPersonName(..., staffNameLanguage, ...)` kullanır.
   AniList GraphQL zaten romaji/native/english taşır; Bangumi istemcileri
   (`KitsugiBangumiCreditsClient`, `KitsugiBangumiDetailClient`) native/romaji/english
   doldurur; Kitsu/Shikimori/Jikan modelleri mevcut alanlarla zarif düşer.
2. **Puanlama adımları** — `KitsugiEditMediaSheet`: 0,5 seçiliyse yıldız sırası yerine
   ±0,5 stepper ("8.5 / 10"); detaylı puan kategorileri de adımı kullanır.
   Kayıt `MediaEntry.score` (0-10 tam sayı) şemasına yuvarlanarak yazılır;
   senkron yöneticileri (AniList/MAL/Kitsu/Shikimori/Bangumi/Simkl) ortak
   `parsedScore` üzerinden beslendiği için tüm kaynaklara uygulanır.
3. **Ayrılmış liste tarzı** — `MyListComponents` + `KitsugiUserMediaListScreen`:
   kapalıyken durum grupları yerine düz akış (`MyListFlatContent`).
4. **Düşük önceliği göster + öncelik renkleri** — `KitsugiMediaEntryCard.PosterView`:
   AniHyou'daki gibi `priority != null && (priority > 0 || showLowPriority)` koşuluyla
   poster üst köşesinde seviye rozeti; renk tohumu `priorityColorsJson`'dan,
   boşsa varsayılan palet (gri/amber/kırmızı). Öncelik girişi zaten
   editör sayfasında (Düşük/Orta/Yüksek) mevcuttu.
5. **Fuzzy arama** — `MyListFilterHelpers` (Listem yerel arama) fuzzy fallback;
   `SearchResultScorer` (manga kaynakları) Levenshtein benzerliğini ayarla açıp kapatır.
6. **Roman/manga ayrımı** — `ExploreScreen`: ayrım kapalıysa romanlar manga
   rafına karışır, açıksa ayrı "Noveller & Light Novel" rafı (AniList `NOVEL`,
   Jikan `novel/lightnovel` rafları zaten kaynak tarafında mevcuttu).

## Bildirimler (ekran 3)

Mevcut karşılıklar doğrulandı: Anlık bildirimler (kaynak bazlı anahtarlar),
Güncelleme sıklığı (`notification_interval`, 24 Saat = Günlük dahil),
Anime yayınlanma bildirimi (`airing_notifications_enabled`), AniList hesap
ayarları + Oturumu kapat + GitHub/Discord bağlantıları zaten vardı.

## Doğrulama

Bu sandbox'ta JDK/Android SDK bulunmadığı için `gradle build` çalıştırılamadı;
tüm düzenlemeler mevcut kod desenleri birebir takip edilerek yapıldı ve her
dokunma noktası dosya içi bağlamıyla doğrulandı. Derleme kontrolü cihaz/CI
tarafında yapılmalıdır.
