# Plan / Task — Kullanıcı Anime–Manga Listesini “Listem” ile Tam Pariteye Getirme

**Tarih:** 2026-10-09  
**Dal:** `arena/4670d550-kitsugi-beta`  
**Kapsam:** AniList kullanıcı profillerinden açılan Anime/Manga listesi

## 1. Sorun

Başka bir kullanıcının profilinden açılan Anime/Manga listesi, uygulamanın ana **Listem** ekranıyla yalnızca kart seviyesinde benzerdi. Aşağıdaki Listem yetenekleri ya eksikti ya da farklı uygulanıyordu:

- Listem başlığı ve üst kontrol düzeni
- Görünüm değiştirme
- Hızlı arama ve gelişmiş filtre paneli
- Tam sıralama seçenekleri
- Anime ve Manga arasında ortak veri kümesi üzerinden geçiş
- Durum grupları, sonuç sayısı ve kayan kategori kontrolü
- Rastgele içerik, yukarı çıkma ve poster önizleme
- Favori, tekrar, tarih, öncelik ve güncelleme alanlarının gerçek AniList verisine bağlanması
- Görünüm seçiminin kalıcı ayarlara yazılması

## 2. Hedef

Kullanıcı listesi için ayrı ve eksik bir tasarım sürdürmek yerine, ana Listem ekranının ortak bileşenlerini ve filtreleme sözleşmesini kullanmak. Başkasının listesini değiştiren işlemler güvenli biçimde salt okunur kalacaktır.

## 3. Uygulama Planı ve Tamamlanan İşler

### A. Ortak Listem üst alanı

- `MyListHeaderSection` bağlama göre başlık, alt başlık, geri düğmesi ve arama yer tutucusu alacak şekilde genelleştirildi.
- Görünüm, arama ve filtre simgeleri iki ekranda aynı bileşenden geliyor.
- Tek kaynaklı uzak listeler için Listem kaynak satırıyla aynı görsel yapıda `MyListSingleSourceBar` eklendi.

### B. Ortak filtreleme ve gruplama sözleşmesi

- Arama, durum, medya türü, favori, yetişkin içerik, puan, yıl ve özel filtreler `filterMyListEntries` altında birleştirildi.
- Durum sırası `groupMyListEntriesByStatus` altında ortaklaştırıldı.
- Ana Listem ekranı da bu ortak fonksiyonlara geçirildi; böylece iki ekranın davranışının yeniden ayrışması engellendi.

### C. Kullanıcı listesi ekranı

- Eski büyük Anime/Manga sekmesi ve ayrı sıralama arayüzü kaldırıldı.
- Anime ve Manga koleksiyonları tek ViewModel veri kümesinde birleştirildi.
- İlk açılışta profilde seçilen liste türü aktif filtre olarak korunuyor; kullanıcı daha sonra Anime, Manga veya Tümü seçebiliyor.
- Listem ile aynı kart düzenleri, durum grupları, sonuç sayısı, kategori seçici ve yukarı çıkma düğmesi kullanılıyor.
- Rastgele içerik, aşağı çekerek yenileme, boş/hata durumu ve poster uzun basma önizlemesi eklendi.
- Görünüm değişikliği `SettingsDataStore` üzerinden kalıcılaştırıldı.
- TV hızlı kaydırma ve yetişkin içerik ayarları korundu.

### D. AniList veri kapsamı

Kullanıcı liste sorgusuna aşağıdaki alanlar eklendi:

- Liste girdisi kimliği
- Puan ve ilerleme
- Cilt ilerlemesi
- Tekrar sayısı
- Öncelik
- Gizlilik ve durum listelerinden gizleme bilgisi
- Eklenme/güncellenme zamanı
- Başlangıç/bitiş tarihi
- Romaji, İngilizce ve yerel başlık
- Format, yıl, toplam bölüm/bölüm sayısı ve yetişkin içerik bilgisi

Hedef kullanıcının favorileri ayrıca sayfalı olarak yükleniyor. `Media.isFavourite` oturum sahibini temsil ettiği için hedef kullanıcı favorisi olarak yanlış kullanılmıyor.

### E. Kimlik güvenliği

- AniList ayrıntı sayfası her zaman `AniList mediaId + 100_000_000` kimliğiyle açılıyor.
- MAL kimliği yalnızca `realMalId` üzerinden çapraz eşleştirmede tutuluyor.
- Böylece MAL ve AniList kimliklerinin karışıp yanlış ayrıntı sayfası açması engellendi.

### F. Salt okunur davranış

- Başkasının liste durumu veya ilerlemesi değiştirilmez.
- Ortak kartlardaki hızlı işlem düğmesi kullanıcı listesinde ayrıntı sayfasını açar.
- Platform seçici AniList’e sabittir; başka platformda aynı kullanıcıya ait veri varmış gibi sahte seçenek gösterilmez.

## 4. Değişen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/KitsugiMyListFilterComponents.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListScreen.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListViewModel.kt`
8. `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpersTest.kt` (yeni)
9. `PLAN_TASK_KULLANICI_LISTESI_LISTEM_PARITESI_2026-10-09.md` (bu rapor)

## 5. Test Kapsamı

Yeni birim testleri şunları kapsar:

- Medya türü + puan + yıl filtre kombinasyonu
- Favoriler sözde durum filtresi
- İngilizce/yerelleştirilmiş başlıkla arama
- Tekrar izlenenler filtresi
- Durum gruplarının sabit sırası
- Puana göre sıralama

## 6. Doğrulama Sonuçları

- `git diff --check`: **başarılı**
- Değişen bütün Kotlin dosyaları Tree-sitter Kotlin ayrıştırıcısıyla: **sözdizimi başarılı**
- Gradle birim testi komutu denendi ancak çalışma ortamında Java/JDK ve `JAVA_HOME` bulunmadığından başlatılamadı.

## 7. Manuel Kabul Kontrol Listesi

- [ ] Profil → Anime Listesi ilk açılışta yalnız Anime filtresiyle geliyor.
- [ ] Profil → Manga Listesi ilk açılışta yalnız Manga filtresiyle geliyor.
- [ ] Arama alanındaki Anime/Manga/Tümü geçişleri yeniden ağ isteği yapmadan çalışıyor.
- [ ] Gelişmiş durum, puan, yıl ve özel filtreler doğru sonuç üretiyor.
- [ ] Bütün sıralama yönleri doğru çalışıyor.
- [ ] Dört kart görünümü arasında geçiş yapılıyor ve seçim kalıcı oluyor.
- [ ] Rastgele içerik, yenileme, kategori sayfası ve yukarı çıkma çalışıyor.
- [ ] Poster uzun basma önizlemesi yetişkin bulanıklaştırma ayarına uyuyor.
- [ ] Kart tıklaması doğru AniList ayrıntısını açıyor.
- [ ] Başkasının liste ilerlemesi hiçbir işlemde değişmiyor.
