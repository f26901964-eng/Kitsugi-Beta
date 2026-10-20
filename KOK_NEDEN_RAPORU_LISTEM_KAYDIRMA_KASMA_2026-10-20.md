# KÖK NEDEN RAPORU — LİSTEM SAYFASINDA KAYDIRMA/TIKLAMA KASMASI VE DONMA (2026-10-20)

## Belirti
Hesaplar bağlandıktan ve veriler geldikten sonra **Listem** sayfası her alanda kilitleniyor:
kaydırma, kart tıklama, detay sayfası açılışı, filtre/arama kullanımı. Küçük listelerde fark
edilmez; 500–2000+ kayıtta ekran neredeyse kullanılamaz hâle gelir.

## Kök nedenler (önem sırasıyla)

### 1. Kaydırmanın HER karesinde tüm ekranı yeniden kompoze eden state yazımı (ANA NEDEN)
`MyListScreen.kt` içinde sekmelerin kaydırma konumunu tutan `savedTabIndices` / `savedTabOffsets`
`rememberSaveable { mutableStateOf<List<Int>> }` idi. Her sekmenin `snapshotFlow` koleksiyonu
kaydırmanın **her karesinde** iki `MutableList` tahsis edip bu state'lere YAZIYORDU:

```kotlin
savedTabIndices = savedTabIndices.toMutableList().also { it[pageTabIndex] = index }
savedTabOffsets = savedTabOffsets.toMutableList().also { it[pageTabIndex] = offset }
```

Bu değerler kompozisyonda (`tabScrollStates` kurulumunda) okunduğu için her yazım, ekran
gövdesinin tek composition kapsamını geçersiz kılıyordu → başlık, sekme çubuğu, tüm pager
sayfaları ve görünür tüm kartlar **saniyede 60–120 kez** yeniden kompoze oluyordu. Liste
uzadıkça kare başına maliyet büyüdüğünden kasma tam olarak "veriler geldikten sonra" başlıyordu.
Kart tıklaması ve detay geçişi de bu fırtınanın ortasında gerçekleştiği için onlar da donuk
hissediliyordu.

**Not:** Aynı hata AppViewModel.myListScrollIndex için daha önce düzeltilmişti (o alanlar bilinçli
olarak `mutableStateOf` DEĞİL, düz alan); MyListScreen'deki bu yerel kopya gözden kaçmış.

**Çözüm:** Konumlar `rememberSaveable(saver) { IntArray(...) }`'a taşındı. Dizi yazımı hiçbir
kompozisyonu geçersiz kılmaz; süreç ölümü/detay dönüşü kalıcılığı Saver + AppViewModel'in düz
alanlarıyla korunur. Restore efekti artık yalnızca `LazyListState.Saver`'ın konumu kurtaramadığı
durumda (boş liste kelepçelenmesi) devreye girer, Saver'ın doğru restore ettiği konumu başa çekmez.

### 2. Birleşik kütüphane gruplamasının ANA iş parçacığında senkron çalışması
`groupMyListEntries` (satır başına kimlik anahtarları + 2. tur union-find birleştirme) ve
`MyListIdentityCrosswalk.build` + rozet/entry haritaları kompozisyon içinde senkron çalışıyordu.
Her veritabanı emisyonunda (senkron yazımı, kardeş satır aynalama, +18 geri yazımı…) 1000+
kayıtta yüzlerce ms ana iş parçacığı kilitleniyordu.

**Çözüm:** Kimlik köprüsü + gruplama + haritalar TEK `produceState` hesabında
`Dispatchers.Default` üzerinde üretilir; sonuç `CombinedMyListLibrary` olarak birlikte
yayınlanır (eski liste + yeni rozet gibi uyuşmayan ara kare imkânsızlaşır). Son sonuç
`MyListViewModel.cachedCombinedLibrary` içinde saklanır → detay sayfasından dönüşte liste
bir-iki kare boşalıp dolmaz.

### 3. `entriesFlow` ana iş parçacığında ağır dönüşüm yapıyordu
`withCrossSourceAdultFlags()` tüm tabloyu entity→domain'e çevirip satır başına
`MediaIdentity.keys` hesaplıyordu ve toplayıcı iş parçacığında (Main) çalışıyordu.

**Çözüm:** Akışa `distinctUntilChanged + conflate + flowOn(Dispatchers.Default)` eklendi:
- hesap artık arka planda,
- içerik değişmemiş emisyonlar UI yeniden kurulumunu tetiklemez,
- ardışık yazım patlamalarında (ör. +18 tulu satır satır günceller) yalnızca en güncel
  anlık görüntü işlenir.

Ayrıca `withCrossSourceAdultFlags` kimlik anahtarlarını satır başına BİR kez hesaplar (eski
sürüm yetişkin olmayan her satır için iki kez hesaplıyordu) ve `MediaIdentity` içindeki
`setOf(...)`/`listOf(...)` sabitleri çağrı başına tahsis yerine tek kez oluşturulur.

### 4. Arama filtresinde gereksiz NFKC normalizasyonu
`filterMyListEntries`, `normQueryMatch` değerini HER satır için önden hesaplıyordu (satır
başına 3 × `Normalizer.normalize(NFKC)` + regex); her tuş vuruşunda tüm liste × normalizasyon
ana iş parçacığında koşuyordu. Zincirin sonuna alındı → ucuz `contains` eşleşirse
normalizasyon hiç çalışmaz (sonuç değişmez; tüm ifadeler yan etkisiz).

### 5. Başlık sıralamasında O(n log n) String tahsisi
`sortedBy { it.title.lowercase() }` seçiciyi her karşılaştırmada çağırıyordu. "title" /
"title_desc" dalları anahtarları satır başına bir kez hesaplayan Schwartzian transform'a
alındı.

### 6. Aktif olmayan pager sayfalarında gereksiz gruplama
`MyListContentPage`, aktif olmayan sayfa için durum gruplamasını her yeniden kompozisyonda
yeniden hesaplıyordu (6 × tam liste taraması; kaydırma jestinde komşu sayfalar da kompoze
olur). `remember(pageEntries)` ile önbelleğe alındı.

## Değişen dosyalar
- `ui/screens/mylist/MyListScreen.kt` — kare-başına state yazımı kaldırıldı (IntArray),
  birleşik kütüphane tek arka plan hesabına taşındı, restore efekti Saver ile uyumlu hale getirildi
- `ui/screens/mylist/MyListViewModel.kt` — birleşik kütüphane sonucu için dönüş-güvenli önbellek
- `ui/screens/mylist/MyListComponents.kt` — aktif olmayan sayfa gruplaması remember'landı
- `ui/screens/mylist/MyListFilterHelpers.kt` — arama normalizasyonu tembel, başlık sıralaması tek-tahsis
- `data/local/MediaEntryRepository.kt` — flowOn(Default) + distinctUntilChanged + conflate,
  tek-hesaplı +18 bayrağı yayılımı
- `model/MediaIdentity.kt` — sabit koleksiyon önbelleği

## Davranış değişikliği yok
Filtreleme/sıralama/birleştirme sonuçları birebir aynıdır; yalnızca hesaplamanın yeri ve
sıklığı değişti. `MediaEntryRepositoryAdultPropagationTest`, `MyListFilterHelpersTest`,
`MyListLibraryGroupingTest` semantiği korur.
