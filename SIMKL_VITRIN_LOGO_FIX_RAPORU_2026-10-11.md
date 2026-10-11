# Simkl Vitrin Logosu — Kök Neden ve Düzeltme Raporu

**Tarih:** 2026-10-11
**Sürüm:** v2.4.248
**Kullanıcı bildirimi:** "Simkl kısmında halen daha bu resimli logo mantığı çalışmıyor, açık olmasına rağmen."
**Kanıt:** Keşfet ekran görüntüleri — vitrin `S Simkl` çipini ve romaji başlıkları
(`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`) düz metin olarak gösteriyor;
logo görseli yok.

## 1. Beklenen davranış

`HeroLogoPolicy.shouldShowHeroLogo` Simkl'i **her zaman** logo denemesi yapan kaynaklar
arasında sayar (Bangumi/Shikimori ile birlikte, global `showAnimeLogos` ayarından bağımsız).
Vitrin metin başlığı yalnızca logo bulunamazsa yedek olarak gösterilmelidir.

## 2. Kök neden — TMDB başlık doğrulaması romaji başlıklarda yanlış negatif üretiyor

Logo zinciri: `KitsugiHeroSection.fetchHeroLogo` → `KitsugiEpisodeRatingsRepository.getLogoUrl`
→ `tmdbRecordMatches` → `TmdbArtworkIdentity.matches`.

- `getLogoUrl`, TMDB kaydını hem `movie` hem `tv` ucundan çeker ve `expectedTitles`
  listesini kaydın `title` / `original_title` / `alternative_titles` alanlarıyla karşılaştırır.
- İki türde de eşleşme yoksa (`titleMatchedMovie == false && titleMatchedTv == false`)
  logo **bilinçli olarak** verilmez (yanlış yapımın logosunu göstermeme koruması).
- Simkl anime kayıtlarının `title` alanı **romaji**dir ve Simkl çoğu kayıtta
  `title_en`/`title_native` döndürmez. Bu nedenle `expectedTitles` yalnızca romaji başlıktan
  oluşur (`sousounofrieren`), TMDB kaydı ise İngilizce/Japonca adlandırılır
  (`frierenbeyondjourneysend` / `葬送のフリーレン`). Karşılaştırma (`eşit` veya iki yönlü
  `contains`) başarısız olur ve logo hiç istenmez.
- Diğer kaynaklar bu doğrulamayı geçer: MAL/AniList öğeleri `titleEnglish` alanını da
  taşıdığı için İngilizce başlık TMDB kaydıyla eşleşir; Bangumi'nin kendi çapraz kimlik
  akışı vardır. Simkl bu yüzden tek başına logosuz kalıyordu.

Bu, 2026-10-09 tarihli "Simkl keşfet sonuçlarında TMDB kimliğinin korunması" çalışmasının
neden yetmediğini açıklar: **kimlik korunuyordu**, ancak sonraki başlık doğrulaması logoyu
yine reddediyordu.

### İkincil neden — Simkl kimlik anahtarları her uçta aynı değil

Keşfet raflarını besleyen `SimklApiClient.getBestMedia` yalnızca `ids.simkl_id` ve
`ids.tmdb` anahtarlarını okuyordu; `mal` alanı da sayı ya da metin (`"22297"`) olabiliyor.
Simkl yanıtları kayıt türüne/uç noktaya göre `simkl` / `tmdb_id` anahtarlarını da kullanır
(`parseSimklArray`, `getTrending`, `getCalendar` bu varyantları okur; `getBestMedia` okumuyordu).
Eksik okunan bu kimlikler logo aramasının hiç başlamamasına yol açıyordu.

## 3. Yapılan değişiklikler

| Dosya | Değişiklik |
|---|---|
| `ui/components/KitsugiHeroSection.kt` | Simkl vitrin dalı: Simkl'ın kendi kimliklerinde (`ids.tmdb`/`ids.mal`) başlık doğrulaması kaldırıldı; kimlik yoksa `KitsugiSimklDetailClient` üzerinden çapraz kimlik yedeği (`fetchSimklCrossLogo`) eklendi; kayıt başlık kesişimiyle uydurma kimlik koruması. |
| `data/remote/SimklApiClient.kt` | `getBestMedia` ayrıştırması test edilebilir `parseBestMediaArray` işlevine taşındı; `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, sayı veya metin `mal` varyantlarının tümü okunuyor (`simklOptionalId`). `resolveSimklTitle`/`simklNativeTitle` dosya düzeyine alındı. |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | Simkl dalı ve `explicitTmdb` kısa yolu: Simkl kaynaklı kimliklerde başlık doğrulaması uygulanmaz (diğer kaynaklar değişmedi). |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | Aynı kural — Listem girişinden açılan Simkl detay sayfaları. |
| `app/src/test/.../SimklExploreParsingTest.kt` | Yeni birim testleri: kimlik varyantları, metin `mal`, CJK→Latin başlık, anime film ipucu, CDN adresleri, geçici kimliğin MAL/TMDB sanılmaması. |

## 4. Neden bu çözüm güvenli

- **Yanlış logo riski artmadı:** Doğrulama yalnızca **Simkl'ın kendi kaydından çözdüğü**
  kimlikler için kaldırıldı. Türetilmiş (ARM çapraz) kimliklerde doğrulama devam eder;
  vitrin yedeği ayrıca çözülen Simkl kaydının kendi başlık alanlarıyla kesişim arar.
- **Diğer kaynaklar etkilenmedi:** Değişiklikler `source == "simkl"` ile sınırlı; MAL,
  AniList, Kitsu, Bangumi, TMDB ve Shikimori dalları aynı kaldı.
- **Yedek davranış korundu:** Logo bulunamazsa vitrin yine düz metin başlığı gösterir.
- **`getLogoUrl` sözleşmesi değişmedi:** Yalnızca çağıran taraf, doğrulamanın hangi
  durumda uygulanacağını belirler (mevcut `canonicalSource == "tmdb"` kuralının devamı).

## 5. Doğrulama

- `git diff --check` — başarılı.
- Blok/parantez dengesi ve imza denetimi (statik) — başarılı.
- `bash ./gradlew :app:testDebugUnitTest` — **çalıştırılamadı**: bu ortamda JDK/Android SDK
  yok (`java`/`javac`/`kotlinc` bulunamadı, `~/.gradle` ve `~/.m2` yok). Yeni birim testleri
  JDK bulunan bir ortamda çalıştırılmalıdır.
- Cihaz doğrulaması önerisi: Keşfet → Simkl (ve Tümü modunda hero) → vitrin kaydır;
  `Sousou no Frieren` / `Re:Zero` gibi romaji başlıklı Simkl öğelerinde başlık logosunun
  görünmesi beklenir; logo yoksa metin yedeği (mevcut davranış) kalır.

## 6. Kapsam dışı / açık konular

- Simkl kartlarında puan `92/10` biçiminde görünebiliyor (Simkl puanları 0–100 aralığında
  saklanır, `formatScore` 0–10 varsayar). Bu raporun konusu değildir; ayrı bir düzeltme
  gerektirir çünkü `formatScore` tüm kaynaklar tarafından paylaşılır.
- Simkl API'nin hangi uç noktalarda hangi kimlik anahtarını döndürdüğü yalnızca koda ve
  denetim notlarına göre değerlendirildi; bu ortamda `api.simkl.com` erişimi yoktur.
  Ayrıştırıcı tüm bilinen varyantları okuduğu için eksik anahtar riski kapatılmıştır.
