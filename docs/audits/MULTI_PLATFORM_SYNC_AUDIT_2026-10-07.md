# Beş platform, yerel liste ve yedekleme denetimi

**Tarih:** 2026-10-07  
**Kapsam:** AniList, MyAnimeList, Kitsu, Shikimori ve önceki Simkl onarımları; liste okuma, yazma, silme, kimlik eşleme, çapraz birleştirme, yerel import, çevrimdışı kuyruk, JSON yedek ve geri yükleme.  
**Karar:** Önemli kod hataları giderildi ve 40 otomatik kontrol çalıştırıldı. **Tam Android uygulaması ve beş gerçek hesapla uçtan uca doğrulama tamamlanmadı; sürüm onayı değildir.**

## 1. Özet

Simkl'deki problem tek başına değildi. Ortak katmanda bütün platformları etkileyen üç ana sorun vardı:

1. **Medya kimliklerinin tür/kaynak kapsamı eksikti.** Anime #1 ile manga #1 veya TMDB film #100 ile TV #100 aynı öğe sanılabiliyordu. Yerel import, başlığı aynı kayıtları `allowDelete=false` olsa bile silebiliyordu.
2. **Başarısızlık/eksiklik başarı gibi gösteriliyordu.** Bağlı servisin listesini okuyamamak boş kütüphane kabul ediliyor; mevcut kayıt güncellenemese de hata sayacı artmayabiliyor; birleşik hedef veri sunucudaymış gibi yerelde saklanabiliyordu.
3. **Yedek ve kuyruk veri modeli eksikti.** Yedek dosyası birçok MediaEntry alanını içermiyordu. Bekleyen gönderimler beş başarısız denemeden sonra otomatik siliniyordu.

Kodu incelemeden bütün bu alanların düzgün olduğunu söylemek mümkün değildi. Düzeltilenler ve kalan işler aşağıda ayrıldı.

## 2. Düzeltmeler

### Ortak kimlik ve yerel import — yüksek öncelik

**Dosyalar:** `model/MediaIdentity.kt`, `MediaEntryRepository.kt`, `MediaEntryBackup.kt`, `AuthViewModel.kt`.

- Provider kimlikleri medya türüyle birlikte değerlendirilir; çıplak sayısal ID artık yeterli değildir.
- Aynı başlık, aynı sağlayıcıya ait çelişen kimlikleri geçersiz kılamaz.
- İki bilinen farklı yapım yılı başlık üzerinden birleştirilmez. Unicode normalizasyonu `Locale.ROOT` kullanır; Türkçe cihaz dili başlık anahtarını değiştirmez.
- Yerel import birden fazla mevcut kayıtla eşleşirse kayıt silmek yerine hata verir. Önceki hayalet/çift kayıtlar otomatik temizlenmiş sayılmaz.
- `allowDelete=false` artık başlık benzerliği gerekçesiyle de kayıt silmez.
- Bir sayfa içinde aynı doğrulanmış kimliğin tekrarı yeni kopya oluşturmaz.
- Geri yüklemede `mal`/`jikan` aynı platform olarak tanınırken AniList, MAL ve Kitsu'nun ayrı yerel kütüphane kayıtları korunur.

**Sınır:** Tür ve çelişki kontrolleri yanlış eşleşme riskini azaltır. Ortak sağlayıcı ID'si bulunmadığında kullanılan benzersiz tam başlık/yıl eşleşmesi yine bir sezgiseldir; resmi bir kimlik eşlemesi değildir. Tüm katalogların/sezonların aynı olduğunu garanti etmez. Yeni yaklaşım bazı eskiden sessizce birleştirilen belirsiz kayıtları durdurabilir; bu veri güvenliği tercihidir.

### AniList

**Bulgu:** `SaveMediaListEntry(score: 8)` kullanıcının seçtiği puan formatında yorumlanır. 100 puanlık hesapta 8/10 yerine 8/100 yazılabilir; 5/3 ölçekli hesaplarda da yanlış davranabilir.

**Düzeltme:** Yereldeki 0–10 puan `scoreRaw: 0–100` olarak gönderilir. İçe aktarmadaki `scoreRaw` alias'ı da açıkça `POINT_100` ister. İçe aktarmada HTTP 200 içindeki GraphQL `errors` dizisi kontrol edilir.

Ek olarak:
- `updateAniListEntry` null dönerse tekil ve toplu güncelleme başarı sayılmaz.
- İlk bulanık başlık arama sonucuna otomatik yazma/silme yapan AniList fallback kaldırıldı; doğrulanmış ID/eşleme bulunamazsa atlama/hata beklenir.
- Yanlış Kitsu/MAL ID dönüşümü ve sentetik AniList ID aralığı sınırlandırıldı.
- Tekil favori mutation'ı false dönerse başarı kabul edilmez.

**Kalan:** AniList liste kaydı ID'sinin hesap değişimi/başka hesaptan yedek geri yükleme sonrasındaki sahiplik kontrolü; 3/5/10/100 hesaplarda gerçek API read-back testi; advancedScores, favori toggle tekrar denemesi ve özel alanların ayrı kabul testi. Yerel model tam sayı puan kullandığı için 7.5 gibi değerler birebir korunamaz.

### MyAnimeList

- Anime/Manga dışındaki içeriklerin anime uç noktasına yazılması doğrudan manager seviyesinde engellenir.
- Eksik/sentetik MAL kimliğiyle sessiz `return` artık başarı sanılmaz; açık hata verir.
- Sayfalama `data` alanının gerçekten dizi olmasını ister; tekrarlayan next URL ve farklı host/scheme reddedilir.
- Çapraz eşitlemede ilk liste okuma hatası boş kütüphane gibi kullanılmaz.
- Mevcut puanlar arasındaki fark güncelleme tetikler; başarısız güncelleme hata sayacına/günlüğe girer.

**Kalan:** MAL token refresh eşzamanlılığı, gerçek 401/429/403 davranışı, yorum/etiket/tarih temizleme semantiği ve Manga cilt/tekrar okuma karşılaştırmalı testi. Bunlar bu çalışma kapsamında canlı sunucuda doğrulanmadı.

### Kitsu

- Liste sayfalarındaki HTTP hatası artık 0 kayıt/normal liste sonu sayılmaz. Veri yapısı bozuksa kısmi liste başarı olarak dönmez.
- Mevcut library-entry sorgusu başarısızsa “kayıt yok” sayılıp POST yapılmaz.
- Kayıt ID önbelleği hesap kullanıcı ID'si + anime/manga türüyle kapsamlanır. Eski hesap-kapsamsız önbellek bilinçli olarak okunmaz.
- Import sırasında gerçek MAL ID öğrenildiğinde **Kitsu yerel medya ID'si kaybedilmez:** kullanıcı/tür/MAL → Kitsu eşlemesi ayrı önbelleğe yazılır. Güncelleme/silme bunu kullanabilir.
- Güncelleme öncesinde sunucudan mevcut library-entry sorgulanır; yalnızca eski cache ID'sine güvenilmez.
- 0 puan eskiden minimum 2/20'ye yükseliyordu; artık null/puan temizleme anlamına gelir.
- Başarısız silmeden sonra kayıt ID önbelleği kaldırılmaz. Doğrulanmış “kayıt zaten yok” silme için idempotent sonuçtur.
- Eksik remote timestamp'e “şimdi” verilmez; bilinmeyen timestamp 0'dır.

**Kaynak kontrolü:** Kitsu upstream `LibraryPaginator` gerçekten 500 üst sınırını doğruluyor. Dolayısıyla 500'ü tek başına kök neden ilan etmedim; asıl gözlenen hata HTTP başarısızlığını liste sonu sanmaktı.

**Kalan:** Kitsu refresh-token yaşam döngüsü, birden fazla cihazın eşzamanlı create/PATCH yarışı, mapping cache bulunmayan yedeklerden kimlik kurtarma; reconsuming/reconsumeCount, notes/private ve başlangıç/bitiş tarihleri henüz tam import/export eşitliğine sahip değil. Bunlar “hepsi senkronize oluyor” diye sunulmamalı.

### Shikimori

- Liste HTTP, JSON biçimi ve parse hataları kısmi başarılı listeye dönüştürülmez.
- User-rate cache kullanıcı ID'si + hedef türüyle kapsamlanır.
- Güncelleme için upstream tarafından belgelenen **POST /api/v2/user_rates upsert** yolu kullanılır: sunucu user_id + target_type + target_id varsa mevcut kaydı günceller. Böylece eski cache ID'sinin geçersiz olması yeniden eşitlemeyi kalıcı bozmaz.
- Başarısız silme cache'i silmez; 404 silme için idempotent kabul edilir.
- Bilinmeyen timestamp “en yeni kayıt” gibi davranmaz.
- Anime/Manga dışındaki türler açıkça reddedilir.

**Kalan:** Shikimori katalog ID'lerinin her medya için MAL ile aynı olduğuna dair genel varsayım hâlâ mevcut; ayrışan/yeni katalog öğeleri için açık mapping modeli gerekir. Silmede önbellek yoksa remote user-rate lookup eksik. Cilt, tekrar sayısı ve metin alanları adapter'da tam taşınmıyor. Bunlar çözülmüş kabul edilmedi.

### Çapraz senkronizasyon ve sonuç ekranı

- Dört platformun ilk liste indirme hataları da artık Simkl gibi tüm-kütüphane-boş varsayımına çevrilmiyor. Yazma fazından önce geçerli kaynak görüntüsü gerekiyor.
- Toplu işlem platformun `syncEnabled...` ayarını dikkate alır; en az iki bağlı ve eşitlemesi açık hesap gerekir.
- Mevcut öğe güncelleme hataları sayılır/günlüğe girer. AniList null onayı başarı değildir.
- Birleştirilmiş hedef kayıtları platform adına kopyalayan yerel kayıt yolu kaldırıldı. Yazmalardan sonra **her bağlı/açık servisin gerçek listesi yeniden okunur** ve yerel listeye bu görüntü aktarılır.
- Bu okuma, hedeflenen bütün alanların tek tek eşit olduğunun matematiksel doğrulaması değildir. Nihai read-back assertion motoru hâlâ gereklidir.
- Çapraz akışın iptal/istisna halinde çalışma bayrağı `finally` ile temizlenir.
- Temel otomatik MAL arama fallback'i ilk sonucu değil tek bir tam tür/başlık/yıl eşleşmesini kullanır. Yine de resmi ID mapping'i daha güçlü kanıttır.

### JSON yedekleme ve geri yükleme

**Önce:** Notlar, tarihler, etiketler, öncelik, tekrar izleme/okuma alanları, cilt ilerlemesi, gizlilik alanları, kaynak timestamp, alternatif başlıklar, MAL list ID, TMDB ve Simkl ID yedekte yoktu.

**Şimdi:** `schemaVersion=2` bu MediaEntry alanlarını taşır. Yerel Room satır `id`'si bilerek yeniden atanır. Eski sürüm 1 yedekler okunur; desteklenmeyen gelecek sürümler ve bozuk kayıtlar sessizce değiştirilmez/atlanmaz. Boş fakat geçerli bir yedek, bozuk dosyadan ayrılır.

`replaceAll` artık Room DAO `@Transaction` yöntemine gider; önce silip sonra transaction dışında insert etmez. Disk/SQLite rollback davranışı cihazda ayrıca test edilmeli.

Bu yedek **kütüphane verisi yedeğidir**: OAuth token, şifre, eklenti, uygulama ayarları, indirilen dosyalar ve pending queue bu JSON kapsamına dahil değildir. Platform list ID'leri başka hesaba ait olabilir; hesap değiştirilerek geri yüklemede sahiplik kontrolü gerekir.

### Çevrimdışı kuyruk

- Beş denemesi biten işler otomatik silinmez; otomatik deneme durur, kayıt korunur.
- Bozuk payload da silinmez, kurtarma için tutulur.
- Aynı yerel kayıt/kaynak/tür için yeni niyet önce eklenir, sonra eski kuyruk işleri kaldırılır; UPDATE → DELETE gibi geçişler eski UPDATE'i geride bırakmaz.
- Eşzamanlı iki drain aynı işi göndermez; mutex ile tek tüketici çalışır.
- Hiçbir hedefe gönderim yapılmayan boş sonuç, bekleyen işin onayı değildir.
- Kuyruk payload'ı tam MediaEntry yedek codec'ini kullanır; eksik tarih/not/kimlik/timestamp sorunu azaltıldı.
- Coroutine iptali sıradan API hatasına dönüştürülmez; iş korunur ve kilit bırakılır.

**Kalan kritik işler:** Hedef-platform başına ve hesap-sahipliği bağlı kalıcı outbox hâlâ yok. Kısmi başarısızlık tekrarında başarılı platformlar tekrar yazılabilir; hesap kapatma/değiştirme niyeti tam modellenmiyor. Durdurulmuş işleri gösteren/yeniden deneyen UI yok. Mutex süreç içi; insert+eski işleri temizleme crash-safe transaction değildir. Bu yüzden “offline exactly-once garantili” iddiası yok.

## 3. Ortak kalan riskler — yayın öncesi gerekli

1. **Merkezi HTTP hız sınırlaması/retry bütün istemcilere bağlı değil.** UI'da platform limiter var ama importer/tekil yolların tamamı aynı kapıdan geçmiyor. Ortak `RetryInterceptor` 429 beklemesini 30 saniyeye kesiyor; yanıtsız mutation tekrarının idempotency riski var. 35'lik batch/gecikme evrensel garanti değil.
2. **Simkl Activities + kalıcı delta cursor yok; çok sezonlu TV bölüm eşlemesi eksik.** Önceki Simkl raporundaki bu sınırlar devam ediyor. Eklenen read-back daha fazla tam okuma yapar; sık otomatik eşitleme için delta tasarımı şart.
3. **Tekil Simkl yolu toplu yolla tam birleşmedi.** Kümülatif bölüm sayısını tek bölüm gibi gönderen yol ve kimlik fallback'leri ayrıca bitirilmeli.
4. **Birleştirme politikası birebir ayna değil.** Maksimum ilerleme, dolu puanı tercih etme, silmeleri taşımama ve desteklenmeyen alanların atlanması mevcut. Tekrar izlemeye başlayıp ilerlemeyi düşürme, puan temizleme, özel not/gizlilik alanları için açık ürün politikası gerekir. Gizli içeriği diğer platforma taşıma izinleri de ele alınmalı.
5. **Yerel okunmamış offline değişikliklerle remote import çatışması.** Pending-local-write işaretleme/cursor tabanlı uzlaştırma eksik; uzaktan okuma yerel daha yeni niyeti görünümde geriletebilir.
6. **Geniş kütüphane performansı.** Güvenli/benzersiz eşleşme kontrolü şu anda tarama yapar; büyük listelerde O(n²) davranışı ölçülüp tür-kapsamlı indeksle optimize edilmeli.
7. **Tam Android/Room/API doğrulaması yok.** Aşağıdaki testler gerçek sunucu veya Android runtime testi değildir.

## 4. Çalıştırılan testler

### A. 26 üretim sözleşmesi kontrolü — GEÇTİ

Komut: `scripts/run_sync_contract_checks.sh`

Kotlin 2.0.21, Java 21 ve gerçek `org.json:json:20240303` ile şu **üretim dosyaları derlendi ve çalıştırıldı**:
- `MediaEntry.kt`, `MediaIdentity.kt`
- `SyncSafety.kt`
- `MediaEntryBackup.kt`
- `SimklSyncContract.kt`

Kapsam: tüm MediaEntry alanlarıyla yedek roundtrip (Long ID ve Unicode dahil), v1/v2/boş/bozuk yedekler, tür/kaynak ayrımı, kimlik çatışması, platform kayıtlarının korunması, puan dönüşümleri, iptal, Simkl zarfları ve yanıt semantiği.

### B. 14 repository/kuyruk kontrolü — GEÇTİ

Komut: `scripts/run_sync_repository_checks.sh`

Gerçek `MediaEntryRepository`, `MediaEntryDao`, entity dönüşümleri, `PendingSyncDrainer` ve kuyruk DAO/entity kodu derlendi. Android Context, Room annotation'ları, DAO depolama ve harici ağ sınırı test ikameleriyle çalıştırıldı.

Kapsam: anime/manga import ayrımı, silmesiz import, belirsizlikte durma, aynı sayfadaki duplicate ID, platform izolasyonu, restore transaction metoduna yönlendirme, latest-intent kuyruk, türe göre kuyruk izolasyonu, tükenmiş işlerin korunması, no-op onay reddi, payload roundtrip, başarısızlık retry, iptal ve eşzamanlı drain.

**Önemli:** Transaction metodunun çağrılması test edildi; gerçek SQLite rollback/Room kod üretimi test edilmedi. Fake network kullanımı gerçek API başarısı kanıtı değildir.

### C. Diğer kontroller

- `git diff --check`: geçti.
- Değişen Kotlin/Gradle kaynakları syntax parser'dan geçti (parser'ın eski kodda da bulunan satır-başı catch kısıtı analiz metninde normalize edildi).
- Java kurularak tam Gradle testi yeniden denendi: Gradle 8.13 bootstrap indirmesi `services.gradle.org` ağ/SSL kısıtında durdu. Android SDK/dependency çözümleme ve APK derleme aşamasına ulaşılamadı.
- Önceki `SimklSyncContractTest` JUnit sınıfının Gradle koşusu çalışmadı. Yukarıdaki 40 kontrol ayrı, gerçekten çalıştırılmış executable testlerdir; bunu “tüm Android testleri geçti” diye yorumlamayın.
- Hiçbir kullanıcı hesabında canlı yazma/silme yapılmadı.

Scriptler bağımlılıkları otomatik indirmez. `KOTLINC`, `JAVA_HOME`, `JSON_JAR` ve repository kontrolleri için `COROUTINES_JAR` verilmelidir. Çalışma ortamında araçlar/cache repo dışında tutuldu; JAR/APK/JDK projeye eklenmedi. JSON test artifact SHA-256: `3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed`.

## 5. Yayın kabul matrisi

Önce mevcut kütüphanenin yedeği alınmalı; büyük toplu işlem yerine küçük kontrollü hesap/listeler kullanılmalı.

| Senaryo | Beklenen |
|---|---|
| Her platformda anime + manga #1 | Ayrı kayıtlar; yanlış türde güncelleme yok |
| 501+ Kitsu/Shikimori kaydı, ikinci sayfada HTTP hata | Kısmi liste tam liste olarak kullanılmaz; yazma fazı başlamaz |
| AniList 3/5/10/100 puan ayarı | Yerel 8/10, `scoreRaw=80` ile doğru okunur |
| Mevcut MAL/Kitsu/Shikimori puanı 6 → 8 | Güncelleme tetiklenir; sunucudan 8 geri okunur |
| Hesap A → çıkış → hesap B | A'ya ait Kitsu/Shikimori cache ID'si kullanılmaz |
| Kitsu cache var, kayıt web'den silinmiş | Sunucu sorgusu sonrası doğru create yolu |
| Shikimori cache eski | v2 upsert mevcut hedefi günceller; kopya yok |
| 401/403/429/500 veya GraphQL errors | “Eksiksiz başarı” yok; yerel sahte onay yok |
| Yedekte not/tarih/gizlilik/ID/tekrar/cilt | Kaydet–geri yükle eşit; başarısız replace eski DB'yi korur |
| Çevrimdışı UPDATE → DELETE, sonra bağlantı | Eski UPDATE ile yeniden dirilme yok |
| Kuyruk 5 kez başarısız | Kayıt korunur; kullanıcı kurtarma/yeniden deneme tasarımı test edilir |
| Eşitlemesi kapalı platform | Toplu yazmaya katılmaz |
| Son read-back başarısız | Onaylı senkronizasyon olarak sunulmaz |

## 6. Resmî kaynaklar

- [AniList SaveMediaListEntry sözleşmesi — score/scoreRaw ayrımı](https://github.com/AniList/ApiV2-GraphQL-Docs/blob/03281c0a4bbf0c7f2097e0c935cddaed1096aa65/docs/reference/mutation.md)
- [Kitsu LibraryEntry resource alanları/filtreleri](https://github.com/hummingbird-me/kitsu-server/blob/e6575ed9fd73ba8cccb920fe2e3f1ef873a71333/app/resources/library_entry_resource.rb)
- [Kitsu LibraryPaginator — 500 üst sınırı](https://github.com/hummingbird-me/kitsu-server/blob/e6575ed9fd73ba8cccb920fe2e3f1ef873a71333/lib/library_paginator.rb)
- [Shikimori v2 user_rates — create içindeki upsert](https://github.com/shikimori/shikimori/blob/a900114c48be8fdbc212139017b18e81a1c9a76f/app/controllers/api/v2/user_rates_controller.rb)
- [Shikimori anime/manga listesi sayfalama](https://github.com/shikimori/shikimori/blob/a900114c48be8fdbc212139017b18e81a1c9a76f/app/controllers/api/v1/users_controller.rb)
- Simkl resmî kaynak ve önceki bulgular: `SIMKL_SYNC_AUDIT_2026-10-07.md`.

MAL için bu ortamda resmî canlı dokümana/API'ye erişilemedi; MAL değişiklikleri kaynak kodundaki sessiz no-op, kimlik/tür ve hata akışının denetimine dayanır. Bu sınırlama saklanmamalı.
