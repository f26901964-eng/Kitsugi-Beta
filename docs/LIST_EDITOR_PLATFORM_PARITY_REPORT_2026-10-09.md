# Liste Düzenleme Ekranı — Platform Paritesi Raporu (2026-10-09)

Kapsam: `KitsugiEditMediaSheet` (liste kaydı düzenleme sayfası) ve AniList / MAL / Simkl / Kitsu / Shikimori / Bangumi için yazım (sync) ve içe aktarma (import) yolları.

Referanslar (açık kaynak):
- **Aniyomi** (`aniyomiorg/aniyomi`, anime tracker'ları: Kitsu, Bangumi, Shikimori, Simkl, MAL, AniList) — `KitsuApi.updateLibAnime` (`startedAt`, `finishedAt`, `private`), `BangumiApi.updateLibAnime` (`private`), `AnimeTrack` modeli ve "gizli takip destekleniyor" bayrakları (AniList, Kitsu, Bangumi).
- **Shikimori API 2.0 belgesi** (`/api/doc/2.0/user_rates/create`) — `rewatches`, `text`, `volumes`, `chapters`, `episodes` alanları.
- **Mihon** (`mihonapp/mihon`) — manga tracker'ları için aynı alan setinin doğrulanması (Kitsu/Shikimori/Bangumi).

## Bulunan eksik / hatalı noktalar

| Platform | Sorun | Durum |
|---|---|---|
| Bangumi | Kaynak `bangumi` olarak tanınmıyordu → kayıt **"Yerel Kitaplık / manuel"** gibi açılıyordu (başlık alanları, +18, toplam sayı vb. görünüyordu). | Düzeltildi |
| Bangumi | Sync `comment` (not) ve `tags` alanlarını göndermiyordu; sheet'te gizlilik/etiket yoktu (istemci destekliyordu). | Düzeltildi |
| Kitsu | Sheet'te platforma özel alan yoktu; **Gizli (private)** eksikti. | Düzeltildi |
| Kitsu | Sync yalnızca status/progress/puan gönderiyordu; **başlangıç/bitiş tarihi, not, gizlilik** gitmiyordu. Import da bunları okumuyordu. | Düzeltildi (import + sync) |
| Shikimori | Sheet'te **"Yeniden İzleniyor"** durumu seçilemiyordu; tekrar sayısı yoktu. | Düzeltildi |
| Shikimori | Sync yalnızca status/score/progress gönderiyordu; **not (`text`), `rewatches`, `volumes`** gitmiyordu. Import da okumuyordu. | Düzeltildi (import + sync) |
| Hepsi | Bazı alanlar platforma gönderilmiyor ama sheet'te normal görünüyordu (kullanıcı yanılabilir). | Platforma özel ipucu satırları eklendi |
| Kitsu | Manga "Ciltler" alanı Kitsu'ya gönderilmediği halde gösteriliyordu. | Kitsu'da gizlendi |

## Yapılan değişiklikler

- `ui/components/KitsugiEditMediaSheet.kt`
  - `isBangumi` algılaması; Bangumi artık manuel kayıt gibi davranmıyor. Platform adı/rengi ve sync notu eklendi.
  - Shikimori'de "Yeniden İzleniyor" durum hapı + tekrar sayacı.
  - Kitsu ve Bangumi'ye "Gizli" anahtarı; Bangumi'ye "Etiketler".
  - Manga cilt satırı Kitsu için gizlendi.
  - Gönderilmeyen alanlar için ipucu (Shikimori/Bangumi: tarih+favori; Simkl: not+tarih+favori; MAL: favori).
- `data/auth/KitsuApiClient.kt`: `KitsuEntryExtras` (startedAt, finishedAt, notes, private) — create/update'e opsiyonel parametre; okuma tarafında `notes`, `private`, `startedAt`, `finishedAt` parse ediliyor. Tarihler `yyyy-MM-ddT00:00:00.000Z` biçimine çevriliyor.
- `data/auth/KitsuSyncManager.kt`: ek alanlar tüm yazma çağrılarına iletiliyor.
- `data/auth/KitsuImportManager.kt`: not/gizlilik/tarihler `MediaEntry`'ye aktarılıyor.
- `data/auth/ShikimoriApiClient.kt`: `ShikimoriRate` + parser (`text`, `rewatches`, `volumes`); `createUserRate` opsiyonel `text/rewatches/volumes` gönderiyor.
- `data/auth/ShikimoriImportManager.kt` / `ShikimoriSyncManager.kt`: eşleme ve gönderim.
- `data/auth/BangumiSyncManager.kt`: `comment` ve `tags` (virgülle ayrılmış → liste) gönderiliyor.

Veri kaybı koruması: Uzak alanlar yalnızca **dolu** değerler gönderilerek korunur (boş not / 0 rewatch / boş tarih, uzaktaki değeri silmez). Shikimori/Kitsu import'u artık bu alanları da okuduğu için düzenleme öncesi uzak veri yerelde mevcut olur.

## Bilinen sınırlamalar / sonraki adımlar

1. **Eski kayıtlar:** Bu sürümden önce içe aktarılmış Kitsu/Shikimori kayıtlarında not/gizlilik/tarih/rewatch yerelde boş olabilir. Bu kayıtları düzenlemeden önce ilgili hesabı yeniden içe aktarmak (import) en güvenlisidir; aksi halde Kitsu'daki `private` değeri yerel `false` ile ezilebilir.
2. **Tarih temizleme:** Yerelde tarih silinse bile uzaktaki tarih null gönderilmediği için silinmez (kasıtlı, veri kaybını önlemek için).
3. **Kitsu "reconsuming" (tekrar izleme):** Aniyomi/Mihon kaynaklarında doğrulanamadığı için eklenmedi.
4. **AniList `priority`:** Sheet'te yok ve sync'e eklenmedi; belgesi bu oturumda doğrulanmadı.
5. **Simkl not/tarih/favori ve Bangumi tarih/favori:** Doğrulanmış bir API uç noktası bulunmadığından gönderilmiyor; ipucu satırı bunu belirtiyor.
6. **MAL favori:** MAL v2 listesinde favori alanı yok; yalnızca yerel tutuluyor (ipucu eklendi).
7. **Derleme:** Bu ortamda Android SDK/Gradle bulunmadığı için projenin derlenmesi ve testi yapılamadı. Değişiklikler elle gözden geçirildi; yerelde `./gradlew :app:assembleDebug` ile doğrulanmalı.
