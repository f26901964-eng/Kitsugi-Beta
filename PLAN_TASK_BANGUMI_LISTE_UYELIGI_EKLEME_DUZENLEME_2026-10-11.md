# Plan / Görev — Bangumi Liste Üyeliği, Ekleme ve Düzenleme

**Tarih:** 2026-10-11  
**Kapsam:** Bangumi hesabına ait liste üyeliğinin ayrı ve doğru tutulması; Bangumi arama sonucunu hesaba ekleme/düzenleme; REAL içerik türlerinin doğru eşlenmesi.

## İstek ve kabul ölçütleri

- Bangumi sekmesinde yalnızca Bangumi hesabından içe alınmış veya Bangumi koleksiyonuna başarıyla yazılmış kayıtlar görünür.
- Diğer sağlayıcıların kayıtları ile Kitsugi'de yalnızca yerel/birleşik olarak bulunan kayıtlar Bangumi üyeliği sayılmaz.
- Bangumi detayında düzenleme Bangumi bağlamında açılır; alakasız sağlayıcı düzenleme ekranı/etiketi gösterilmez.
- Ekleme/düzenleme Bangumi API'sine yazılır ve Bangumi subject kimliği yerel kayıtta kendi stable ID alanında korunur.
- Bangumi REAL film/dizi girdileri Movie/TV türüne doğru çevrilir. MUSIC/GAME/bilinmeyen türler Anime olarak içe aktarılmaz.

## Uygulama

1. **Sağlayıcı kapsamlı eşleşme:** `matchesInSource` yalnızca kanonik olarak aynı sağlayıcıdaki kimlik/başlık eşleşmesini kullanır. Bangumi sekmesi ve sayacı yalnızca `source=bangumi` ve Bangumi stable ID aralığındaki kayıtları sayar/gösterir. Bangumi sekmesindeki arama da başka sağlayıcılara genişlemez; manuel kayıt `manual` kaynağında kalır.
2. **Bangumi kimliği ve yazma akışı:** Bangumi arama sonucundan stable ID'li kayıt oluşturulur. Bangumi ekleme/düzenleme önce uzak koleksiyonu günceller; `collectionUpdated` başarılı değilse yerel kayıt eklenmez/değiştirilmez. Başarı sonrası yerel kayıt dış senkronizasyonu atlayarak saklanır; bulunan subject ID stable Bangumi ID'sine çevrilir.
3. **Sağlayıcıya özel düzenleme ve senkronizasyon:** Bangumi kaydı Bangumi düzenleyicisiyle açılır; Bangumi kaynaklı güncellemeler AniList/MAL/Simkl/Kitsu/Shikimori'ye fan-out yapmaz. Silme, Bangumi API'sindeki gerçek silme kısıtı nedeniyle mevcut Bangumi-özel davranışı kullanır.
4. **Tür güvenliği:** Bangumi Anime/Book/REAL türü ve platformu ayrıştırılır; REAL film/dizi Movie/TV olarak eşlenir. Eksik/bilinmeyen API türü varsayılan olarak Anime yapılmaz. İçe aktarma, arama fabrikası ve Bangumi ilişki akışları desteklenmeyen türleri reddeder.
5. **Kapsanan arayüz yolları:** ana arama/detay, Listem araması, profil koleksiyonu, bildirim ve ilişki/kadro detayından açılan Bangumi kayıtları.

## Değişiklik ve test kapsamı

- Kimlik/eşleşme: `BangumiIdNamespace.kt`, `BangumiMediaEntryFactory.kt`, `KitsugiModels.kt`.
- API/import/senkronizasyon: `BangumiApiClient.kt`, `BangumiImportManager.kt`, `BangumiSyncManager.kt`, `ExternalListSyncManager.kt`, `MediaEntryRepository.kt`, `AuthViewModel.kt`.
- UI akışları: `AppRoot.kt`, `AppRootDetailPages.kt`, `AppRootTabPages.kt`, `AppDialogHost.kt`, `AppViewModel.kt`, `KitsugiEditMediaSheet.kt`, detail/MyList/profile/TV ekranları.
- Testler: `BangumiIdNamespaceTest.kt`, `BangumiMediaEntryFactoryTest.kt`, `MediaEntryMatchesTest.kt`, `MyListLibraryGroupingTest.kt`.

## Doğrulama durumu

- `git diff --check`: başarılı.
- Derleme ve birim testleri: bu çalışma ortamında `java`/`JAVA_HOME` ve `kotlinc` bulunmadığından çalıştırılamadı. Gradle testleri Java/Android build ortamı olan bir makinede çalıştırılmalıdır.

## Cihazda önerilen kontroller

1. Bangumi hesabını bağla ve koleksiyonu içe aktar; Bangumi sekmesindeki kayıt ve sayaçları bgm.tv ile karşılaştır.
2. Yalnızca Simkl/yerel kaydı olan aynı başlığı Bangumi sekmesinde arat; Bangumi üyesi gibi görünmediğini doğrula.
3. Bangumi aramasından Anime, Manga, REAL film ve REAL TV kaydını ekle; başarısız uzak yazımın yerel kayıt oluşturmadığını, başarılı yazımın doğru tür ve subject ID'yi sakladığını kontrol et.
4. Bangumi kayıtlarını düzenle; editörün Bangumi bağlamında açıldığını ve durum/puan/ilerlemenin Bangumi hesabına gittiğini doğrula.
5. MUSIC/GAME veya bilinmeyen tür kayıtlarının Anime olarak eklenmediğini kontrol et.
