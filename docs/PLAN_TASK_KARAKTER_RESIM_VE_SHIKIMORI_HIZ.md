# Kitsugi — Karakter Görselleri + Shikimori Detay Sekmeleri: Teşhis & Düzeltme

**Tarih:** 2026-10-08 · **Dal:** `arena/e198c875-kitsugi-beta` · **Temel commit:** `c8b5f1e` (v2.4.205)
**Durum:** Kod değişiklikleri TAMAM, derleme yapılamadı (sandbox'ta JDK/Gradle yok) → doğrulama kullanıcı tarafında.

---

## 1. Şikâyet (kullanıcı raporu + düzeltme notu)

1. **Karakter detay sayfasında karakterlere ait resimler görünmüyor**; kullanıcı düzeltmesi:
   "…hayali karakterler veya diğer hayali olmayan karakterler de dâhil" → sorun **hem kurgusal
   (anime) karakterleri hem de gerçek kişileri/live-action kadrosunu** kapsıyor ve ikisinin
   düzgün ayrılması gerekiyor.
2. **Shikimori detay sayfalarında** karakter, ekip, ilişkili içerik, öneri, grafik verileri
   "ya hiç gelmiyor ya da aşırı geç geliyor"; sayfa MAL'deki gibi geç açılıyor
   (ekran görüntüleri 4–8: Karakterler/Ekip skeleton'da takılı, Öneriler/İlişkiler/Grafikler "bulunamadı").

---

## 2. Kök Nedenler

### 2.1 Karakter görselleri (bug 1)

| # | Kök neden | Kanıt / etki |
|---|---|---|
| A | `KitsugiCharacterClient.enrichCharactersWithAnimeImages` AniList yapım aramasını **tek başlıkla** yapıyordu. TMDB kaynaklı içerikte bu başlık Türkçeleştirilmiş olduğu için AniList'te eşleşme bulunamıyor, `aniChars` boş kalıyordu → **hiçbir karakter görseli dolmuyordu**. | Sekmelerde yalnızca baş harfler (görsel 1–3) |
| B | Aynı fonksiyonun sonunda `val finalImg = if (isActorImage) null else char.imageUrl` satırı, **gerçek kişi (live-action) kadrosundaki tek görsel olan oyuncu fotoğrafını siliyordu**. | "Hayali olmayan karakterler" için görsel yok |
| C | `fetchCharacterDetail("tmdb")` kişi (person) yedeğine düştüğünde `imageUrl = null` döndürüyordu ve arayüzden gelen kart görseli (nav `imageUrl`) hiçbir zaman kullanılmıyordu. | Karakter **detay** sayfası resimsiz |
| D | Kurgusal karakter / gerçek kişi ayrımı kayboluyordu: kurgusal karakter kaydı da gerçek kişi kaydı gibi aynı yer tutucu biyografiyle dönüyordu. | Kullanıcı: "düzgünce ayırt eder" |
| E | Galeri için Jikan `/pictures` çağrısı **TMDB kişi kimliğiyle** yapılıyordu (404 + gereksiz gecikme). | Detay sayfası galerisi boş |

### 2.2 Shikimori detay sekmeleri (bug 2)

| # | Kök neden | Etki |
|---|---|---|
| F | `KitsugiMediaRelationsClient` (ilişkiler+öneriler), `KitsugiMediaSocialClient` (grafikler+yorumlar), `KitsugiMediaTabsClient.fetchEpisodes` (bölümler) içinde **`"shikimori"` dalı yoktu** → `else -> emptyList()` | Sekmeler anında "bulunamadı" |
| G | `KitsugiStaffClient` Shikimori dalı yalnızca `/roles`'u okuyordu; Shikimori'de personel kaydı olmayan yapımlarda **Ekip sekmesi boş** kalıyordu (MAL/Jikan yedeği yok). | Ekip boş |
| H | `MediaEntryDetailViewModel.loadTab` içinde **zaman aşımı ve tek-uçuş koruması yoktu** (bunlar yalnızca `ApiResultDetailViewModel`'de vardı) → yavaş bir zincir sekmeyi **sonsuz skeleton**'da bırakıyordu. | Görsel 4–5 skeleton |
| I | Tek-uçuş guard'ı aynı sekmeyi ikinci kez tetiklendiğinde **sessizce yutuyordu**: detay gelmeden (realMalId=null) yapılan ilk deneme boş dönünce, detay geldikten sonraki tetikleme de atlanıyor ve sekme **boş sonuçla kilitli** kalıyordu. | "Ya hiç gelmiyor ya da geç geliyor" |
| J | Shikimori kimliği MAL kimliği sanılarak TMDB/Fanart galerisi, logo, bölüm puanları ve bölüm listesi sertifikalarında kullanılabiliyordu. | Alakasız içerik riski |
| K | Karakter/kişi adları için her ad başına ayrı Google Translate isteği atılıyordu (önceki turda toplu hâle getirildi); karakter detayındaki isim/açıklama çevirileri de bütçesizdi. | Sekmelerin dakikalarca sürmesi |

---

## 3. Yapılan Değişiklikler

### 3.1 Karakter görselleri

| Dosya | Değişiklik |
|---|---|
| `data/remote/KitsugiCharacterClient.kt` | • `enrichCharactersWithAnimeImages` artık **başlık adayları listesi** alıyor: önce MAL ID'si, sonra sırayla Türkçe/romaji/İngilizce/Japonca/eşanlamlı başlıklar denenir ve **karakter adıyla eşleşme üreten ilk aday** kullanılır (yanlış yapıma bağlanma engellenir).<br>• AniList yapım sorgusu `fetchAniListMediaCharacters` olarak ayrıldı; isim eşleştirme kuralları (birebir / token / altküme / alternatif ad) `matchAniListCharacter` altında korunmuş hâlde yeniden kullanılıyor.<br>• **Görseli boş kalan karakterler için AniList karakter adı araması** eklendi (`enrichMissingImagesFromCharacterSearch` + `searchAniListCharacterByName`): en fazla 8 karakter, toplam 6 sn bütçe; karakterin yer aldığı yapımlar `idMal` ile doğrulanır (yanlış eşleşme yok).<br>• **Oyuncu fotoğrafı artık silinmiyor**: eşleşme bulunamazsa mevcut görsel korunur (gerçek kişi kadrosunda doğru davranış).<br>• `buildTitleCandidates(title, detail)` yardımcısı eklendi (tüm başlık varyantları).<br>• `fetchCharacterDetail` yeni `fallbackImageUrl` parametresi alır: kaynakta görsel yoksa arayüzden gelen kart görseli kullanılır.<br>• `"tmdb"` dalı yeniden yazıldı: **kurgusal karakter** (ad verilmiş) → kimlik karakter adıyla korunur, oyuncu/seiyuu `voiceActors` olarak iliştirilir, görsel boş bırakılmaz; **gerçek kişi** (ad yok) → kişinin kendi profili (fotoğraf/biyografi) döner. |
| `data/remote/JikanApiClient.kt` | `fetchCharacterDetail` facade'ı `fallbackImageUrl` parametresini geçirir. |
| `ui/screens/detail/CharacterDetailPage.kt` | `LaunchedEffect` artık `imageUrl` ipucunu da anahtarlıyor ve ViewModel'e iletiyor. |
| `ui/screens/detail/CharacterDetailViewModel.kt` | • `loadCharacter(..., hintImageUrl)` — karttan gelen görsel anında galeriye konur (sayfa resimsiz kalmaz).<br>• Detay isteği `fallbackImageUrl` ile yapılır.<br>• `buildCharacterGallery` Jikan `/pictures`'ı yalnızca **MAL uzayındaki kimliklerle** çağırır (TMDB kişi kimliğiyle 404 isteği bitti).<br>• `friendlySourceOf` yardımcısı. |

### 3.2 Shikimori sekmeleri & hız

| Dosya | Değişiklik |
|---|---|
| `data/remote/KitsugiShikimoriClient.kt` | • **`fetchSimilarRecommendations`** (`/animes/{id}/similar`) ve **`fetchRelatedRelations`** (`/animes/{id}/related`) eklendi: MAL/ARM eşlemesi gerektirmeyen, tek istekle dolan yerel kaynaklar; kapaklar `ShikimoriPosterResolver` ile **toplu** çözülür (her öğe için ayrı istek yok).<br>• `kindToMediaType` eşlemesi. |
| `data/remote/KitsugiMediaRelationsClient.kt` | `"shikimori"` dalları: ilişkiler → Shikimori `/related`, öneriler → Shikimori `/similar`; sonuç boşsa gerçek MAL ID'si çözülüp Jikan → AniList yedeği. |
| `data/remote/KitsugiMediaSocialClient.kt` | Grafikler → AniList (varsa) → Jikan `/statistics`; Yorumlar → Jikan `/reviews` (MAL ID'si çözülerek). |
| `data/remote/KitsugiMediaTabsClient.kt` | Bölümler → gerçek MAL ID'si **bir kez** çözülür ve hem Jikan bölüm listesi hem TMDB bölüm adı/görseli için kullanılır (Shikimori ID'si MAL sanılmaz). |
| `data/remote/KitsugiStaffClient.kt` | Shikimori personel kaydı boşsa MAL ID'si çözülüp Jikan personel listesi gösterilir (Ekip sekmesi boş kalmaz). |
| `data/remote/KitsugiIdResolver.kt` | `resolveMalIdFromShikimori` üç kademeli + önbellekli: detay önbelleği → **Shikimori REST `myanimelist_id`** → ARM. Sonuç (bulunamasa bile) bellek önbelleğinde tutulur; sekme başına tekrar ağa çıkılmaz. |
| `data/remote/KitsugiDetailClient.kt` | `fetchSynopsis` Shikimori dalı: Shikimori özeti (çevrilir), yoksa MAL/Jikan özeti. |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | • **Sekme başına zaman aşımı** (25 sn; bölümler 45 sn) + `fetchTabWithTimeout` (çocuk `async` + `await` sınırı) eklendi → sekme artık sonsuz skeleton'da kalmaz, hata durumuna düşer.<br>• **Tek-uçuş + istek anahtarı**: aynı parametrelerle çalışan istek varsa tekrar başlatılmaz; parametreler iyileştiğinde (detaydan gerçek MAL ID'si gelince) eski istek iptal edilip **yeniden denenir** (kilitlenme bitti).<br>• Kayıt değişiminde `cancelTabLoads()` (eski yanıt yeni kaydın state'ine yazamaz).<br>• Shikimori ID'si MAL ID yerine kullanılmaz: `fetchEpisodeRatings`, `fetchLogo`, `fetchFanartGallery` (TMDB ID + Fanart MAL fallback) artık gerçek MAL ID'sini çözer. |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | Aynı istek-anahtarı düzeltmesi; Shikimori için bölüm puanları, galeri TMDB ID'si, Fanart MAL fallback'i ve MDBList IMDb çözümü gerçek MAL ID'si üzerinden yapılır. |

---

## 4. Doğrulama Adımları (kullanıcı tarafı)

1. Projeyi derle: `./gradlew :app:assembleRelease` (veya Android Studio).
2. **Karakter görselleri**
   - TMDB kaynaklı bir anime/dizi aç → *Karakterler* sekmesi: hem kurgusal karakterlerin hem de
     gerçek kişilerin/live-action kadrosunun görselleri görünmeli.
   - Bir kurgusal karaktere gir → detay sayfasında görsel (kart görseli veya AniList karakter görseli)
     ve "Resimler" galerisi dolu olmalı; biyografi artık yer tutucu değil (AniList/Jikan bulunduysa).
   - Bir gerçek kişiye (live-action oyuncu) gir → kişinin fotoğrafı + biyografisi gelmeli.
3. **Shikimori sekmeleri** (Frieren gibi bir Shikimori kaydı)
   - Detay sayfası ~1–3 sn içinde açılmalı (özet/kapak/puan).
   - Karakterler + Ekip: skeleton en fazla birkaç saniye; liste gelmeli (Ekip, Shikimori'de
     personel yoksa MAL'dan doldurulur).
   - Öneriler (Shikimori "similar"), İlişkiler (Shikimori "related"), Grafikler (MAL/AniList
     istatistiği), Yorumlar ve Bölümler sekmeleri veri getirmeli.
   - Sekme verisi gelmezse **sonsuz skeleton yerine hata/boş durum** görülmeli.
4. Log kontrolü: `adb logcat -s KitsugiCharacterClient KitsugiShikimoriClient KitsugiMediaTabsClient`.

---

## 5. Kalan İşler / Notlar

- [ ] **Derleme yapılamadı** (bu ortamda JDK/Gradle yok). İlk derlemede olası küçük tip uyumsuzlukları için
      `KitsugiCharacterClient`, `MediaEntryDetailViewModel` ve `KitsugiShikimoriClient` öncelikli kontrol edilmeli.
- [ ] `app/build.gradle.kts` `appVersionName` → `2.4.206` + `RELEASE_NOTES.md` bölümü (TR/EN) eklenmedi.
- [ ] Uçtan uca test: Shikimori `/related` ve `/similar` yanıt şemaları canlıda doğrulanmalı
      (şema beklenenden farklıysa kod sessizce yedek zincire — MAL/Jikan — düşer).
- [ ] İsteğe bağlı: Shikimori → MAL eşlemesi ARM yerine tamamen Shikimori REST ile çözülüyor;
      ARM çağrısı yalnızca yedek olarak kalıyor.

---

## 6. Paketlenen Değişiklikler

- `Kitsugi-Beta-degisiklikler.patch` — tüm değişikliklerin `git diff` çıktısı (uygulanabilir yama).
- `degisen-dosyalar/` — değişen 13 kaynak dosyanın **birebir kopyası** (repo'daki yollarıyla).
- `UYGULAMA-ADIMLARI.md` — yamayı ya da dosyaları nasıl uygulayacağınız.
