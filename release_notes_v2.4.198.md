# v2.4.198 — Kaynak Seçimi Kalıcılığı & Kitsu Kimlik Düzeltmesi

## 📌 Kaynak Seçimi Artık Kalıcı (Keşfet / Arama / Listem)
Kullanıcının bir ekranda seçtiği kaynak, uygulama kapatılıp açıldığında sıfırlanıyordu.
Artık her ekran kendi son seçimini hatırlar ve açılışta oradan devam eder:

- **Keşfet:** `ExplorePlatform` seçimi (Tümü / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori)
  `SettingsDataStore` üzerinden `last_explore_platform` anahtarıyla saklanır ve ViewModel
  oluşturulurken geri yüklenir. Otomatik kaynak düşüşleri (ör. TMDB anahtarı bozuksa
  AniList'e geçme) **seçim sayılmaz** ve kaydedilmez — kullanıcı ne seçtiyse o kalır.
- **Arama:** Kaynak motoru + kapsam (`SearchSourceEngine` / `SearchScope`), sekme, platform ve
  medya türü birlikte saklanır; sekme çipleri ile motor seçicisi senkron kalır. Kullanıcı henüz
  arama yapmamışsa açılışta sessizce geri yüklenir (gereksiz arama tetiklenmez).
- **Listem:** Kaynak sekmesi `Kitsugi_list_filters` üzerindeki `tab_index` ile zaten kalıcıydı;
  Keşfet/Arama ile aynı davranış sergilemesi doğrulanıp korundu (sekme kaydırması, eski sıra
  migrasyonu ve scroll konumu korunur).
- **Açılış performans side etkisi:** Son seçili kaynak TMDB veya Tümü değilse, splash sırasında
  yapılan 11 istekli TMDB önbellek ön-yüklemesi atlanır (boşa bant genişliği harcanmaz).
- **Mimari not:** Bu seçimler `AppSettings` içindeki `settingsFlow`'a EKLENMEDİ; ekran her
  yeniden kurulduğunda "ayar değişti" sinyali üretip veri yenilemesine yol açmamaları için ayrı
  dar kapsamlı akışlar (`lastExplorePlatformFlow`, `lastSearchEngineFlow`, `lastSearchScopeFlow`)
  olarak okunur.

## 🦊 Düzeltme: Listem → Kitsu'da Alakasız Veri ile Açılan Ayrıntı Sayfası
**Belirti:** Listem sayfasının Kitsu sekmesinde bir içeriğe dokununca ayrıntı sayfası tamamen
ilgisiz bir yapımın verisiyle açılıyordu (ör. "Kiss Him, Not Me" → "Keep Your Hands Off
Eizouken!" açıklaması; "Fate/Grand Order CMs" → farklı yapım/bölüm sayısı/yayın durumu).

**Kök neden:** Kitsu içe aktarması, MAL eşleşmesi bilinen kayıtların kimlik alanına
(`media_entries.malId`) Kitsu stableId'si (`300_000_000 + kitsuId`) yerine **gerçek MAL ID**'yi
yazıyordu. Sistemin geri kalanı (`MediaIdentity`, `KitsugiDetailClient`, `KitsuExploreClient`,
bölüm puanları/logo/galeri çözücüleri) bu alanı koşulsuz "Kitsu ID" diye yorumladığı için
MAL #35658, Kitsu #35658 (= Keep Your Hands Off Eizouken!) olarak çekiliyordu. Kimlik
uzayları çakıştığı için API 404 değil, geçerli ama yanlış bir kayıt dönüyordu.

**Çözüm:**
- `KitsuImportManager`: Kitsu kayıtlarının kimlik alanı artık **her zaman** offset'li stableId.
  Bilinen MAL ID yalnızca MAL→Kitsu eşleme önbelleğine yazılıyor (çapraz eşitleme oradan okur).
- Yeni `KitsuIdNamespace` (tek doğrulama noktası): aralık kontrolü (`300M+1..399M+1`),
  stableId ↔ ham Kitsu ID dönüşümleri ve kademeli kimlik çözümleyici
  (kayıttaki stableId → MAL→Kitsu eşleme önbelleği → Kitsu `mappings` → sıkı başlık+yıl araması).
  Aralık dışındaki hiçbir değer "Kitsu ID" diye yorumlanmaz.
- `KitsugiDetailClient`: Kitsu ayrıntısı yalnızca kanonik stableId ile çekilir. Kimlik
  çözülemezse alan gerçek MAL ID taşıyorsa Jikan/MAL denenir, ardından başlığı gerçekten
  tutan Kitsu sonucu kabul edilir — tutmayan hiçbir sonuç kullanılmaz. Kimliği belirsiz
  Kitsu kayıtları için diske önbellek YAZILMAZ/OKUNMAZ ve jenerik "başlıkla ara" zinciri
  Kitsu'da devreye girmez (alaka­sız yapım sızıntısının ikinci kaynağıydı).
- Önbellek hijyeni: TMDB için var olan "başlık uyuşmuyorsa önbelleği geçersiz kıl" koruması
  Kitsu için de uygulandı; Kitsu ayrıntısı artık gerçek MAL ID'si üzerinden TMDB zenginleştirmesi
  alabiliyor (özet/bölüm verisi Kitsu'dan, TMDB ID'si doğru kaynaktan).
- `KitsuExploreClient.fetchDetailByStableId`: aralık dışı ID'leri reddeder (eski davranış:
  `if (id >= OFFSET) id - OFFSET else id` → ham MAL ID'yi Kitsu ID sanma).
- `MediaEntryDetailViewModel`: Kitsu'da logo/bölüm puanı/TMDB/Fanart çözümleri yalnızca
  kanonik stableId üzerinden yapılır; `realMalId` olarak yalnızca aralık altındaki değer
  gönderilir; bellek içi `DetailCache` anahtarları kanonik kimlikle kurulur ve Kitsu kayıtları
  catch-all "MAL ID" dalından çıkarıldı.
- `KitsuSyncManager`: Kitsu kimlik çözümleme ve kütüphaneden silme yolları `KitsuIdNamespace`
  üzerinden okur (eski kayıtlarda duran MAL ID eşleme önbelleğiyle çözülür).

### 🩹 Mevcut Kullanıcılar İçin Tek Seferlik Onarım
`KitsuIdentityMigration` (Listem açılışında, sürüm damgalı): Kitsu aralığında olmayan kayıtların
kimliği yerel eşleme önbelleği → Kitsu `mappings` → sıkı başlık araması ile yeniden çözülüp
stableId alanına yazılır; ağ kullanılmadan çözülebilenler için tek istek atılmaz, oturum
yoksa yalnızca yerel eşleme denenir. Kimliği hiç çözülemeyen kayıtlarda yanlış uzaydaki ID
temizlenir (`malId = null`) — böylece ilerideki bir açılışta alakasız veri ihtimali kalmaz.
Çözülemeyen kayıt kaldıysa 12 saat sonra bir kez daha denenir.
