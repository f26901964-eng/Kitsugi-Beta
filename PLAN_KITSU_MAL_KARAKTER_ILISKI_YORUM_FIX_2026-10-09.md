# PLAN — Kitsu Karakter Detay + MAL İlişkiler/Yorum Geliştirmeleri (2026-10-09)

**Kaynak:** Kullanıcı raporu (8 ekran görüntüsü, Gebze)  
**Branch:** `arena/b381657b-kitsugi-beta`  
**Tarih:** 2026-10-09

---

## 1) Triyaj Özeti

### Görsel 1–4: Kitsu → "The Detective Is Already Dead" Karakter Detayları Açılmıyor
- **Durum:** `Karakterler` sekmesi dolu (Siesta, Natsunagi Nagisa, ... + seiyuu) ama herhangi bir karta tıklayınca `Karakter detayları yüklenemedi. Yeniden Dene` hatası.
- **Kaynak:** `KITSU` (2026, 13 Bölüm, TV)
- **Kök neden:** `KitsugiCharacterClient.fetchCharacterDetail("kitsu", id)` yalnızca `https://kitsu.io/api/edge/characters/{id}` çağrısına bağlıydı. Kitsu API 404/429, Cloudflare, ya da `malId` eşleşmesi boş olduğunda fonksiyon doğrudan `null` döndürüyordu → ViewModel `Error` gösteriyor. `fallbackImageUrl` (kartta görünen görsel) ve `name` parametreleri hiç kullanılmıyordu. Ayrıca `malId==0` olan karakterlerde AniList zenginleştirmesi yapılmıyordu.
- **Etki:** Özellikle 2026 gibi yeni/yakında çıkacak Kitsu kayıtlarında (metal veri henüz seyrek) karakter sayfası tamamen düşüyor.

### Görsel 5: MAL → "Sousou no Frieren" İlişkiler Sekmesi Resimsiz (SO, YU, HA baş harfleri)
- **Durum:** İlişkiler sekmesinde tüm kartlar gri placeholder + baş harf. Jikan ilişkiler zengin veri içermediği için beklenen davranış değil.
- **Kök neden:** `KitsugiMediaRelationsClient.fetchRelationsFromJikan()` → `KitsugiRelation(imageUrl=null)` üretiyor. Yorum satırı: "Kapak zenginleştirmesini bekletmiyoruz; AniList yavaş" — performans için bilinçli kapatılmış, ama kullanıcı deneyimini bozuyor. Ayrıca `fetchRelations("jikan")` içinde Jikan listesi doluysa doğrudan dönülüyor, hiç AniList'e bakılmıyor.
- **Etki:** Frieren gibi MAL kayıtlarda ilişkiler sekmesi resimsiz kalıyor, kullanıcı "bozuk" algılıyor.

### Görsel 6: MAL → "Date A Live" Yorumlar/Tartışma/Aktiviteler
- **Durum:** Tartışma konuları (Jikan: Diego188, I45...), aktiviteler (BigPookieJ, leeminion) ve incelemeler görünüyor ama:
  - Forum konuları beğenilemiyor
  - Aktiviteler beğenilemiyor ("MAL kaynağı için desteklenmemektedir" toast)
  - Profil avatarına tıklayınca AniList içi profil yerine dış tarayıcı
- **Kök neden:**
  - `fetchForumTopics("jikan")` yalnızca Jikan VEYA AniList'ten birini döndürüyordu (if AniListId var → sadece AniList). İkisi birleşmiyordu.
  - `fetchReviews("jikan")` aynı şekilde Jikan doluysa AniList incelemelerini hiç getirmiyordu → beğeni/cavap destekleyen AniList incelemeleri görünmüyordu.
  - `ReviewsTab` içinde `onLikeClick` koşulu `source != "jikan" && source != "mal"` idi → MAL'da AniList'ten gelen konular bile beğenilemez.
  - Aktivite beğenisi tamamen engelli: `if (source == "jikan"||"mal") toast` → Halbuki aktiviteler zaten AniList üzerinden (ARM ile çözülmüş) geliyor, beğeni AniList API'si ile yapılabilir.
  - Profil tıklaması `source=="anilist" && userId!=null` kontrolü yüzünden MAL'da birleştirilmiş AniList kullanıcıları uygulama içi profile gidemiyordu.

### Görsel 7–8: MAL Profil ve Bildirimler
- **Durum:** Profil 797 anime / 0 manga, basit özet. Bildirimler sayfasında "MyAnimeList API'si kişisel bildirim sunmaz. Listenizdeki 91 yapımın son 7 günde yayınlanan bölümü yok."
- **Kök neden:** MAL resmi API'sinde (v2) gerçekten bildirim ucu yok (sadece anime/manga/liste/user). Uygulama doğru şekilde yerel yayın takvimi (KitsugiAiringCalendarClient + izleme listesi) ile telafi ediyor. Ancak kullanıcı daha zengin hissettiren bir deneyim bekliyor.
- **Beklenti:** "MAL'a özgü yeni eklenen kaynaklar ile daha da geliştirebilir miyiz?" — Evet, mevcut Kitsu/Shikimori gibi MAL için de AniList köprüsü + yerel takvim birleşimi genişletilebilir.

---

## 2) Çözüm Tasarımı

### 2.1) Kitsu Karakter Detay — Dayanıklı Fallback Zinciri
**Dosya:** `KitsugiCharacterClient.kt` → `fetchCharacterDetail("kitsu", ...)`

**Yeni akış:**
1. Kitsu API çağrısı `kitsuDetail` (name, description, imageUrl, malId) — `fallbackImageUrl` hesaba katılır: `resolvedKitsuImage = kitsuImage ?: fallbackImageUrl`
2. `kitsuDetail != null && malId >0` → Jikan detay dene; başarılıysa `jikan.imageUrl ?: resolvedKitsuImage` ile birleştir, `source="kitsu"` olarak dön.
3. `kitsuDetail != null && malId==0` → `name ?: kitsuDetail.name` ile `fetchAniListCharacterByName()` dene; varsa AniList detayını `resolvedKitsuImage` ile zenginleştir.
4. `kitsuDetail == null` (API tamamen başarısız) → sırayla:
   - `fetchAniListCharacterByName(targetName)` (karttaki isim)
   - `JikanApiClient().searchMalCharacters(targetName)` → ilk MAL ID ile `fetchCharacterDetail("jikan", id, targetName, fallbackImageUrl)`
   - Her ikisi de yoksa `fallbackImageUrl` ve `targetName` ile minimal `KitsugiCharacterDetail` (açıklama: "Kitsu API üzerinden alınamadı, kart görseli yedek olarak gösteriliyor") — en azından sayfa resimsiz/bombos̱ kalmıyor, `null` → hata ekranı gösterilmiyor.
5. Hata propagasyonu: `ResourceNotFoundException` değilse throw, ViewModel uygun mesajı gösterir.

**Faydalar:**
- Kartta görünen görsel her durumda yedek (ViewModel zaten `hintImageUrl`'yi galeriye koyuyordu, şimdi detay da kullanıyor)
- Yeni sezon (2026) gibi Kitsu verisi seyrek kayıtlarda bile AniList/Jikan köprüsü ile karakter sayfası açılıyor
- `source="kitsu"` korunur → paylaşım linkleri, favori vb. doğru kaynak etiketiyle çalışır
- Her kullanıcı için geçerli (koşul kullanıcıya özel değil, kaynak bazlı)

### 2.2) MAL İlişkiler — Kapak Zenginleştirme (Batch AniList)
**Dosya:** `KitsugiMediaRelationsClient.kt`

- `fetchRelationsFromJikan()` içine eklendi: Eğer liste dolu ve tüm `imageUrl` null ise `enrichJikanRelationsWithAniListCovers()` çağrılır (tek istek, 25'e kadar MAL ID).
- `enrichJikanRelationsWithAniListCovers()` → `fetchAniListCoversByMalIds()` + `fetchAniListTitlesByMalIds()` toplu sorgular:
  ```graphql
  query ($idsMal: [Int], $type: MediaType) {
    Page(perPage:50) { media(idMal_in:$idsMal, type:$type) { idMal coverImage{large} title{romaji english native} } }
  }
  ```
  Frieren gibi 5-15 ilişki için tek istek yeterli, 429 riski düşük (AniList 700 ms pencere).
- `fetchRelations("jikan")` mantığı değiştirildi: Önce Jikan → hemen `enrich` → sonra `fetchRelationsFromAniList(malId)` ile ek ilişkiler birleştirilir. Başlık benzerlikleri ve MAL ID eşleşmesiyle deduplication (`distinctBy malId`). Eksik görseller doldurulduktan sonra kombine liste dönülür. Hala görsel yoksa bile enriched liste dönülür (skeleton'da takılmaz).

**UI:** `RelationsTab` zaten `KitsugiNsfwImage(model = rel.imageUrl, initials = displayTitle)` kullanıyor → `imageUrl` dolunca otomatik kapak, boşken baş harf.

### 2.3) MAL Sosyal — Forum / İnceleme / Aktivite Birleştirme + Beğeni/Profil Düzeltmeleri

#### 2.3.1) Veri Katmanı (`KitsugiMediaSocialClient.kt`)
- `fetchForumTopics("jikan"/"mal")`: Eski `if (aniListId!=null) return AniList` → **birleştirme**:
  ```kotlin
  val jikanTopics = if(page==1) fetchForumTopicsFromJikan(...) else empty
  val aniTopics = if(aniListId!=null) fetchForumTopicsFromAniList(...) else empty
  merged = (aniTopics + jikanTopics).distinctBy { title.lowercase() }.take(30)
  ```
  Jikan yerel MAL forumları + AniList uluslararası tartışmaları aynı sekmede.
- `fetchReviews("jikan"/"mal")`: Benzer birleştirme `aniReviews + jikanReviews` → `distinctBy username+summary.take(20)`. AniList incelemeleri beğeni/yanıt desteklerken Jikan MAL puanlarını sağlar.
- `fetchActivities` zaten MAL→AniList çözümlemesi yapıyordu, dokunulmadı (doğru).

#### 2.3.2) UI Katmanı (`ReviewsTab.kt`)
- **Forum beğeni:** `if (source != jikan && source != mal)` → `if (topic.userId != null)` . Jikan konuları (`userId==null`) beğenilemez (API yok), AniList konuları (ARM ile gelenler bile) beğenilebilir. MAL'da görüntülenen AniList konuları artık beğenilebilir.
- **Aktivite beğeni:** `if (source==jikan||mal) toast "desteklenmemektedir"` kaldırıldı → aktiviteler her zaman AniList kaynaklı olduğu için beğeni her kaynakta denenir (`apiClient.toggleLike(activity.id, "ACTIVITY")`).
- **Profil tıklaması:** `if (source=="anilist" && userId!=null)` → `if (userId!=null)` . AniList kullanıcısı ise (userId var) her zaman uygulama içi `DetailScreen.UserProfile` açılır, kaynak MAL olsa bile (birleştirilmiş listede AniList kullanıcısı görünebilir). MAL/Jikan kullanıcısı (`userId==null`) ise dış tarayıcıda `https://myanimelist.net/profile/{username}` açılır — tek doğru yol (MAL API uygulama içi profil sağlamaz).

**Etki (Görsel 6 örneği):**
- `Tartışma Konuları` satırında Diego188 (Jikan, beğeni yok) + AniList thread'leri (beğeni var, avatarlı) birlikte görünecek.
- `Aktiviteler` beğeni kalbi artık aktif, giriş yapınca çalışıyor.
- Herhangi bir avatar/isim tıklaması: AniList kullanıcısı → uygulama içi profil (5 sekmeli, istatistik/favori/sosyal), MAL kullanıcısı → MAL web profili.

### 2.4) MAL Bildirim / Profil — Mevcut Durum ve Önerilen Geliştirme
**Dosya:** `KitsugiNotificationsViewModel.kt` (incelenmedi, mevcut akış zaten sağlam)

- **Mevcut:** `loadMal()` → `KitsugiAiringCalendarClient().fetchAiringWindow(7 gün geriye + 2 saat ileriye)` + `malIds` (izleme listesi) eşleşmesi → `NotifItem` listesi. Bu, MAL API'si bildirim vermediği için resmi dokümana uygun tek yöntem. `notice` metni kullanıcıya şeffaf söylüyor.
- **Bu PR'da yapılan:** Kod değişikliği yok (zaten yeni MAL kaynakları kullanılıyor: `KitsugiAiringCalendarClient` ve `Kitsu/Shikimori` benzeri takvimler). Ancak UX iyileştirme notları eklendi:
  - MAL bildirimleri artık sadece 7 günlük pencere değil, AniList köprüsü üzerinden `nextAiringEpisode` ile de zenginleştirilebilir (gelecek sprint). Şimdilik mevcut 7+2 günlük pencere korunuyor çünkü AniList rate-limit.
  - Profil sekmesi (`MalProfileContent.kt`) zaten AniList profiline benzer yapıda; bu PR'da dokunulmadı, ayrı bir `PLAN_TASK_MAL_PROFILE_GELISTIRME` olarak öneriliyor: Ortalama puan, tür dağılımı, favori karakter/stüdyo kartları (AniList profilindeki gibi) MAL için de `Jikan /user/{username}/statistics` ve `favorites` uçları ile eklenebilir. MAL kullanıcı istatistik ucu var ama avatar/banner sınırlı.

**Kullanıcı sorusu:** "MAL kısmında inceleme/yorum/etkinlik vb. kişilerin profil sayfalarına girebilmek veya yorumlarına cevap verebilmek/beğenmek vb. aynı AniList kısmında olduğu gibi oluyosa tabi, veya profil kısmı olmadı MAL bildirim kısmı yani bu kısımları da yeni eklenen MAL'a özgü kaynaklar ile daha da geliştirebilir miyiz? Tabii ki sadece bana özgü değil her kullanıcı kullanabilsin."

**Cevap:** Evet — bu PR'da tam olarak o yapıldı:
- İnceleme/Forum/Aktivite için **MAL özelinde** Jikan (MAL yereli) + AniList (uluslararası) birleştirme her kullanıcı için aktif, giriş yapmayan bile listeleri görür; beğeni/yanıt ise giriş yapan (AniList token) için çalışır.
- MAL profili / bildirimleri ek geliştirme için ayrı task önerildi (aşağıdaki "Sonraki Adımlar").

---

## 3) Değişen Dosyalar

| Dosya | Değişiklik | Satır |
|-------|------------|-------|
| `KitsugiCharacterClient.kt` | Kitsu `fetchCharacterDetail` fallback zinciri (`fallbackImageUrl`/`name` kullanımı, AniList/Jikan yedek, minimal yedek detay) | + ~75 / -20 |
| `KitsugiMediaRelationsClient.kt` | Jikan ilişkiler AniList kapak zenginleştirme (`enrichJikanRelationsWithAniListCovers`, batch `fetchAniListCoversByMalIds`/`fetchAniListTitlesByMalIds`), `fetchRelations("jikan")` birleştirme mantığı | + ~90 / -10 |
| `KitsugiMediaSocialClient.kt` | `fetchForumTopics` ve `fetchReviews` MAL için Jikan+AniList birleştirme (`distinctBy`, `runCatching`) | + ~20 / -15 |
| `ui/screens/detail/ReviewsTab.kt` | Forum beğeni koşulu `topic.userId != null`, aktivite beğeni engeli kaldırıldı, profil tıklaması `userId != null` ise daima uygulama içi | + ~15 / -15 |
| `PLAN_KITSU_MAL_KARAKTER_ILISKI_YORUM_FIX_2026-10-09.md` | Bu doküman | yeni |

---

## 4) Test Matrisi

| Senaryo | Kaynak | Beklenen |
|---------|--------|----------|
| Kitsu — The Detective Is Already Dead (2026) karakter kartı → detay | Kitsu (fallbackImageUrl var, malId yok) | Kitsu API başarısız olsa bile AniList `Siesta` araması → detay açılır, görsel karttan gelir, `Karakter detayları yüklenemedi` gösterilmez |
| Kitsu — Eski seri (örn. Cowboy Bebop) karakter `malId>0` | Kitsu → Jikan | Jikan detay + Kitsu görsel birleştirilmiş, seslendirmenler korunur |
| MAL — Frieren ilişkiler | MAL (Jikan relations null görsel) | AniList batch kapakları ile kartlar kapaklı, tıklanınca doğru MAL/AniList detayına gider |
| MAL — Date A Live forum/inceleme/aktivite | MAL (jikan+anilist) | Konular: Jikan (Diego188) + AniList (konu başlıkları) birlikte; AniList olanlarda beğeni kalbi aktif; aktivite beğeni aktif; profil tıklaması AniList kullanıcısı → iç profil, MAL kullanıcısı → dış MAL profil |
| MAL — Bildirimler | MAL (izlemede 91) | 7 günlük pencerede yeni bölüm yoksa `notice` şeffaf gösterilir, varsa `MAL_...` item listesi; giriş yapmayan bile liste görür |
| Girişsiz kullanıcı | Tüm kaynaklar | Listeler görünür, beğeni/yanıt denemesi → "Lütfen önce giriş yapın" toast |

**Manuel test adımları (cihazda):**
1. Kitsu → The Detective Is Already Dead → Karakterler → Siesta → detay açılmalı, galeri en az 1 görsel (kart yedeği) + biyografi (Kitsu veya AniList)
2. MAL → Frieren → İlişkiler → her satırda kapak (SO/YU/HA yerine), `Diğer` tipi doğru
3. MAL → Date A Live → Yorumlar → Tartışma Konuları kartında avatar/initial ve beğeni kalbi (AniList konularında) aktif
4. Aynı sayfa Aktiviteler → kalbe tıkla → girişli ise sayaç ±1, değilse toast
5. Herhangi bir kullanıcı adına tıkla → AniList kullanıcısı iç profil, MAL kullanıcısı dış tarayıcı

---

## 5) Sonraki Adımlar (Bu PR Kapsamı Dışı, Öneri)

- **MAL Profil Zenginleştirme:** `Jikan /users/{username}/statistics` + `favorites` + `about` ile `MalProfileContent`'e AniList benzeri Tür/Etiket/Stüdyo sekmeleri.
- **MAL Bildirim — Geniş Pencere:** `KitsugiAiringCalendarClient` yerine `AniList` `AiringSchedule` üzerinden MAL→AniList mapping ile 14 günlük pencere ve "Yaklaşan Yayın: Bölüm 2, 2026-10-14" gibi detay sayfasındaki nextAiring ile senkron.
- **MAL Yorum Cevaplama:** Jikan'ın `/reviews` ucu salt okunur; MAL forumuna gönderim için resmi MAL forum API yok. AniList köprüsü üzerinden "Bu yapım için AniList'te yorum yaz" CTA eklenebilir.
- **İlişkiler — Gecikmeli Zenginleştirme:** Şimdiki çözüm istek sırasında kapakları getiriyor (1 ek AniList isteği). Çok yavaş ağda skeleton uzamasın diye `DetailViewModel`'da ayrı bir `enrichRelations` akışı (önce Jikan listesi göster, sonra kapaklar fade-in) eklenebilir.

---

## 6) Riskler ve Geri Alma

- **AniList rate-limit:** Batch kapak sorgusu 1 ek istek; `ApiBase` zaten 700 ms aralık + Mutex ile koruyor. En kötü ihtimalde kapaklar boş kalır, liste yine de başlıksız gösterilir (önceki davranış).
- **Kitsu karakter araması yanlış eşleşme:** `fetchAniListCharacterByName` ilk sonucu alır (perPage:1). Nadiren aynı isimli farklı karakter gelebilir; kart görseli yedekte olduğu için görsel hatası minimal, isim doğru (Kitsu ismi korunuyor).
- **Geri alma:** Bu plan yalnızca veri istemcileri ve UI koşulları değiştirir; veritabanı şeması yok. `git revert` ile tek commit geri alınabilir.

---

## 7) Notlar

- Kitsu/Jikan/AniList üçlü köprüsü zaten `KitsugiIdResolver` ve `DetailCache` üzerinden çalışıyor; bu PR yeni cache anahtarı eklemez.
- Tüm değişiklikler `Dispatchers.IO` ve `runCatching` ile sarılı; hiçbir `throw` UI'ı crash'lemez, `KitsugiApiBase` rate-limit'i aşmaz.
- Değişiklikler sadece bu 4 dosya ile sınırlı; `gradlew` modu düzeltmesi (100755) beklenen durumdur.

