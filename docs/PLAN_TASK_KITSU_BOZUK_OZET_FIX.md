# PLAN/TASK — Kitsu bozuk/alakasız özet (synopsis) temizliği

Tarih: 2026-10-09
Durum: Kod düzeltmeleri ve birim testleri tamamlandı; cihazda/Gradle'da doğrulama bekliyor.

## Hata
- Kitsu'de vandalize edilmiş/hatalı birleştirilmiş (merge) kayıtlar bambaşka bir yapımın özetini taşır.
- Gerçek örnek: **"ROAR" (2004, anime)** Kitsu kaydı — özet (Açıklama) 1997 Fox TV dizisi "Roar"ı anlatıyor:
  _"Roar is an American television show that originally aired on the Fox network in July 1997. In the year AD 400, a young Irish man, Conor, sets out to rid his land of the invading Romans..."_
  Özet ile kaydın geri kalanı (yıl 2004, tür anime, poster, stüdyo) birbiriyle çelişiyor.
- Uygulama Kitsu özetini olduğu gibi gösteriyordu; kullanıcı detay sayfasında alakasız bir dizinin açıklamasını görüyordu.

## Kök neden
- `KitsugiDetailClient.fetchPrimaryDetail` Kitsu dalı, Kitsu'dan gelen detayı doğrulama yapmadan direkt gösteriyordu.
- `KitsugiDetailClient.fetchSynopsis` içinde Kitsu dalı yoktu; özet ya TMDB'den geliyordu ya da hiç gelmiyordu.
- Eski Room önbellek anahtarları (`kitsu_anime_<id>`) bozuk özetleri 24 saat boyunca servis etmeye devam edebilirdi.

## Yapılan işler
- **Yeni: `KitsuSynopsisValidator`** — bir Kitsu özetinin kaydın kimliğiyle (medya türü + yayın yılı) çelişip çelişmediğini düşük yanlış-pozitif oranıyla tespit eder:
  1. Anime/film türündeki kayıt için özet amerikan/ingiliz/kanada/avustralya yapımı live-action TV dizisi tarif ediyorsa ("is an American television show", "aired on the Fox network", "Fox network" vb.) → şüpheli. TvShow türünde bu ifadeler makul olduğu için uygulanmaz; K-dizileri kapsam dışıdır.
  2. Özet yayın-tarihi bağlamında bir yıl içeriyor ("aired/premiered/debuted/released ... in 19XX") ve bu yıl kayıt yılından 2+ yıl farklıysa → şüpheli. Hikâye içinde geçen yıllar ("In July 1997, ...") fiil olmadığı için eşleşmez.
- **Yeni: `KitsugiDetailClient.sanitizeKitsuSynopsis`** — şüpheli Kitsu özetini asla olduğu gibi göstermez:
  1. Özet sağlıklıysa dokunmaz (ek ağ çağrısı yok).
  2. Şüpheliyse MAL/Jikan'dan temiz özet arar: önce kaydın gerçek MAL ID'si, sonra sıkı başlık eşleşmesi. Aday özet de şüpheliyse ya da başlık bu kayıtla akraba değilse (`CrossSyncIdentityGuard.titlesLookRelated`, strict) reddedilir — kimlik karışıklığı yaşanmaz.
  3. Doğrulanmış temiz özet bulunursa yer değiştirir; bulunamazsa özet boşaltılır — UI "Açıklama bulunamadı" gösterir. Bozuk özetin eski otomatik çevirisi de `DetailCache.removeTranslation` ile temizlenir.
- `fetchPrimaryDetail` Kitsu dalına sanitizer eklendi (yedek zincirden sonra); Anime fallback zincirinde (`AniList/MAL → Kitsu`) Kitsu'dan gelen detaylar için de aynı temizlik, kendi 15 sn zaman tavanıyla (3b blok).
- `fetchSynopsis` içine **kitsu dalı** eklendi: Kitsu özeti ancak sağlıklıysa döner; şüpheli özet null döner. (Listeye-ekle diyaloğu ve özet önizlemeleri de bu yoldan korunur.)
- Önbellek sürümleme: Kitsu detay önbellek anahtarı `..._ks1` suffix'i aldı; eski sürüm anahtarları (bozuk özet taşıyabilir) bilerek okunmaz (`legacyKey = null`).
- Birim testleri: `KitsuSynopsisValidatorTest` (15 durum — ROAR olayı, yıl çelişkisi, TvShow/K-drama/Japon-anime negatifleri, boş özet).

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuSynopsisValidator.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt` (`removeTranslation` eklendi)
- `app/src/test/java/com/kitsugi/animelist/data/remote/KitsuSynopsisValidatorTest.kt` (yeni)

## Doğrulama / kalan işler
- Değişen tüm Kotlin dosyaları tree-sitter ile parse kontrolünden geçti (syntax OK).
- Validator mantığı Python portu ile 15/15 test case'i karşılandı (ROAR özeti anime+film türünde şüpheli, TvShow türünde değil; yıl çelişkisi ve negatifler doğru).
- Bu ortamda Java/Android SDK olmadığından Gradle derleme ve cihaz testi çalıştırılamadı. Uygun ortamda:
  `bash gradlew :app:testFossDebugUnitTest --tests com.kitsugi.animelist.data.remote.KitsuSynopsisValidatorTest`
- Cihazda: Keşfet → Kitsu kaynağı → "ROAR" (2004) detay sayfasında Açıklama bölümünün ya doğrulanmış bir özet ya da "Açıklama bulunamadı" gösterdiğini kontrol et. Sağlıklı bir Kitsu kaydı (örn. Cowboy Bebop) için özetin eskisi gibi göründüğünü doğrula.
