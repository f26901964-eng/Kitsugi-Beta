# Kitsugi-Beta v2.4.263 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔗 1. Bildirimden Açılan Detay Sayfası Artık Normal MAL Detayı
- **Belirti:** Bildirimler > MyAnimeList'te bir kart açıldığında detay sayfasının başlığı gerçek anime adı yerine **"New Related Anime"** (MAL'ın kategori adı) oluyordu; bazı kayıtlar da yanlış yapımı açabiliyordu.
- **Kök Neden:** Bildirim kartının başlığı MAL'ın kategori adıdır (`categoryName`). Tıklama, detay sayfasına bu başlığı **başlık olarak** veriyordu ve detay sayfası çağıranın verdiği başlığı API başlığına tercih ediyor. Yani içerik doğru gelse bile ekranda kategori adı kalıyordu.
- **Düzeltme:** Yönlendirme artık medyanın **gerçek adı** (`anime.title` / `animes[].title`) ve MAL kimliğiyle yapılır:
  1. URL'den çıkarılan kimlik varsa doğrudan o kimlikle normal API detayı açılır.
  2. Kimlik yoksa ad üzerinden normal arama API'si (`searchMALOnly`) ile kimlik çözülür; detay o kimlikle açılır (arama sürerken kısa bir gösterge çıkar).
  3. Medya bilgisi olmayan bildirimler (yorum, arkadaşlık, forum) eskisi gibi tarayıcıda açılır.

### 🃏 2. Bildirim Kartları Daha Okunur
- Medya bildirimlerinde **kart başlığı artık medyanın adı** (AniList/Simkl/Kitsu kartlarıyla aynı düzen); MAL'ın kategori adı ve tür ikinci satıra iner: `Umayuru: Full Gate!` + `New Related Anime • ONA`.
- Böylece anime adı kartta iki kez yazılmaz ve liste göz gezdirilerek okunabilir.

### 🧾 3. Tür Karışması: Manga/Novel Kayıtları Anime Sanılmıyor
- **Sorun:** Bildirim türü yalnızca `"manga"` metni aranarak belirleniyordu. **Novel, Light Novel, Manhwa, Manhua, One-shot** kayıtları "anime" sayılıyor, manga kimliği anime aramasına veriliyor ve detay sayfası aynı sayıya sahip **başka bir animeyi** açabiliyordu.
- **Düzeltme:** Manga evreni türleri (`manga, novel, manhwa, manhua, one-shot, doujin`) doğru sınıflandırılır; tür bildirimde yoksa URL'deki `/manga/` yolundan anlaşılır.

### 📜 4. Bildirimlerin Devamı: Kaydırdıkça Daha Eskileri
- **Geçmiş kayıtları:** MAL, okunmuş/eski bildirimleri `historyItems` altında döndürür. Uygulama yalnızca `items` okuduğu için kullanıcı "bildirimlerin devamını" hiç göremiyordu; iki liste artık aynı şemayla ayrıştırılıp `id` ile tekilleştirilir.
- **Sonsuz kaydırma:** Liste sonuna yaklaşıldığında akışın sonraki sayfası denenir (`?page=N`). MAL bu akışın sayfalamasını belgelemediği için karar **veriyle** verilir: dönen kayıtların hiçbiri yeni değilse akış bitmiş sayılır ve tekrar/uydurma kart oluşmaz; liste kısaysa hiçbir şey bozulmaz.
- **Yerel arşiv:** MAL web akışından gelen bildirimler de arşive yazılır; MAL'ın penceresinden düşen bildirimler cihazda kalır (arşiv kayıtları da doğru adla detay açar).

### 🧪 5. Birim Testleri
- `MalNotificationsClientTest`: `historyItems` birleştirme + `id` tekilleştirme, kimliksiz geçmiş kaydının atlanması, `?page=N` adresi üretimi, istenen sayfanın taşınması, gerçek medya adının (`mediaTitle`) açığa çıkması ve manga/novel/manhwa tür sınıflandırması test edildi.

### ⚠️ Doğrulama Notu
- Bu ortamda **Java/JAVA_HOME bulunmadığından** Android Gradle derlemesi ve birim testleri çalıştırılamadı; `git diff --check` temiz.
- Cihazda uçtan uca kontrol: Bildirimler > MyAnimeList > "New Related Anime" kartına dokunun → detay sayfası **anime adı + doğru yapım** ile açılmalı; aşağı kaydırdıkça daha eski bildirimler gelmeli.

---

## 🇬🇧 English

### 🔗 1. Detail Pages Opened From Notifications Now Match Normal MAL Detail
- **Symptom:** Tapping a card under Notifications > MyAnimeList opened the detail page titled **"New Related Anime"** (MAL's category name) instead of the real title, and some entries could open the wrong work.
- **Root Cause:** A notification card's title is MAL's category name (`categoryName`). The tap passed that string to the detail screen **as the title**, and the detail screen prefers a caller-supplied title over the API title — so the category name stuck even when the content was correct.
- **Fix:** Navigation now uses the media's **real name** (`anime.title` / `animes[].title`) and MAL id:
  1. If a URL-derived id exists, the normal API detail opens with that id.
  2. If there is no id, it is resolved through the normal search API (`searchMALOnly`) and the detail opens with the resolved id (a short overlay is shown meanwhile).
  3. Notifications without media (comments, friend requests, forum) still open in the browser.

### 🃏 2. More Readable Notification Cards
- For media notifications the **card title is now the media name** (same layout as AniList/Simkl/Kitsu cards); MAL's category name and type move to the second line: `Umayuru: Full Gate!` + `New Related Anime • ONA`.
- The media name is no longer printed twice, so the list can be scanned at a glance.

### 🧾 3. Type Confusion: Manga/Novel Entries Are No Longer Treated as Anime
- **Problem:** The media type was detected by looking only for the substring `"manga"`. **Novel, Light Novel, Manhwa, Manhua and One-shot** entries were treated as anime, a manga id was sent to the anime search, and the detail page could open a **completely different anime** with the same number.
- **Fix:** Manga-world types (`manga, novel, manhwa, manhua, one-shot, doujin`) are classified correctly; when the payload omits the type, the `/manga/` path of the URL decides.

### 📜 4. See More Notifications While Scrolling
- **History items:** MAL returns read/older notifications under `historyItems`. The app only read `items`, so the rest of the feed was never visible; both lists are now parsed with the same schema and de-duplicated by `id`.
- **Infinite scroll:** When the list nears its end the next page of the feed is attempted (`?page=N`). MAL does not document pagination for this feed, so the decision is **data-driven**: if none of the returned records is new, the feed is considered finished — no duplicate or fabricated cards, and short lists are unaffected.
- **Local archive:** Notifications from the MAL web feed are archived too, so entries that fall out of MAL's window stay on the device (archived records also open the correct detail).

### 🧪 5. Unit Tests
- `MalNotificationsClientTest`: `historyItems` merging with `id` de-duplication, skipping history records without an id, `?page=N` URL building, carrying the requested page, exposing the real media title (`mediaTitle`) and manga/novel/manhwa type classification.

### ⚠️ Verification Note
- Android Gradle build and unit tests could not be run here because **no Java/JAVA_HOME exists in this environment**; `git diff --check` is clean.
- On-device end-to-end check: Notifications > MyAnimeList > tap a "New Related Anime" card → the detail page must open with the **anime name and the correct work**; scrolling down must reveal older notifications.
