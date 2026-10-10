# PLAN/TASK — 4 ekran görüntüsü düzeltmesi (eşitleme uyarısı, eklentiler sayfası, stüdyo kapakları, Bangumi düzenleme)

Tarih: 2026-10-11
Durum: Kod düzeltmeleri tamamlandı; **derleme/cihaz doğrulaması bekliyor** (bu ortamda Java/Gradle/Android SDK yok, değişiklikler okuma + diff denge kontrolüyle doğrulandı).

Kapsam: kullanıcının gönderdiği 4 ekran görüntüsündeki 4 ayrı sorun.

---

## 1) "Çok Yönlü Eşitleme" diyaloğunda tekrarlanan metinler

**Belirti:** Uyarı/yasal metin hem üstte kısa hâliyle hem altta uzun hâliyle duruyor; alttaki turuncu kutu dilogun düzenini bozuyor, metin satır içinde eziliyor.

**Kök neden:** `SafetyNote()` tek bir `Row` içinde üç kardeş öğe (ikon + kısa özet + `CrossSyncDisclaimerText(full = true)`) çiziyordu. Tam genişlik isteyen uzun yasal metin satır içinde diğer öğeleri eziyor, kısaltma (`maxLines`) yüzünden bilgi kayboluyor ve aynı içerik iki farklı yerdeymiş gibi görünüyordu.

**Yapılan işler:**
- `KitsugiCrossSyncDialog.kt` → `SafetyNote()` tek bir **katlanabilir karta** dönüştürüldü:
  - `Surface` (yuvarlatılmış, ince kenarlıklı) + başlık satırı: Security ikonu, tek cümle özet ("Veri silinmez; yalnızca güvenli eşleşmeler eklenir/güncellenir. Raporda erişim anahtarları gizlenir."), sağda "Uyarı/Kapat" + ok ikonu.
  - Satıra dokununca `AnimatedVisibility` ile ayırıcı + `CrossSyncDisclaimerText(full = true)` açılır; metin artık satır içinde kısaltılmaz, kaydırma alanının doğal parçası olur.
- Dikey ve yatay (compact) düzenin ikisi de aynı bileşeni kullanır; dosyada **tek** disclaimer kullanımı kaldı.

---

## 2) "Eklentiler ve Kaynaklar" sayfası

**Belirti:** Sayfadaki işaretli "RealDebrid Ayarları" alanı istenmiyor; sayfa genel olarak dağınık.

**Yapılan işler:**
- **RealDebrid kartı ve token giriş alanı kaldırıldı** (`StremioAddonTab.kt`). Kayıtlı token hâlâ okunur ve Torrentio ön ayarının adresine otomatik eklenir; token girişi TV arayüzünde (`TvAddonsScreen`) duruyor. Çağıran ekranların (ayarlar / manga / akış) imzası bozulmasın diye `initialDebridToken` / `onSaveDebridToken` parametreleri "artık kullanılmıyor" notuyla korundu.
- **Sekmeler yenilendi:** Material `TabRow` yerine ikonlu segment seçici (`AddonsSegmentedTabs`): "Torrent & Akış / Video Kaynakları / Manga Kaynakları". Uzun Türkçe adlar dar ekranda kesilmez, seçili sekme vurgulu kapsül olur.
- **Başlık:** Başlığın altına "Kaynakları ekleyin, güncelleyin ve yönetin" açıklaması eklendi.
- **Gömülü mod:** Ayarlar alt sayfasında alttaki gereksiz "Tamam" şeridi kaldırıldı (üstte geri oku var). Daha önce var olmayan `R.string.tamam` referansı `R.string.action_ok` ile düzeltildi.
- **Ortak görsel dil:** Her üç sekme kısa bilgi şeridiyle açılır (`AddonInfoBanner`); bölüm başlıkları adet rozetlidir (`AddonListHeader`); boş durumlar aynı karttır (`AddonEmptyCard`) — "Henüz eklenti deposu/manga kaynağı/eklenti yok" metinleri tek biçime indirildi. Stremio sekmesi tümüyle ortak kart bileşenlerine geçirildi (`AddonSectionCard`, `AddonHintText`, `PresetAddonRow`).
- Sekme tabanlı liste durumları (`LazyListState`) aynen korundu; kaydırma konumu sekmeler arası paylaşılmaz.

---

## 3) Shikimori stüdyo sayfasında kapaksız yapımlar

**Belirti:** Stüdyo detay sayfası artık açılıyor ama birçok yapımda kapak yerine baş harf yer tutucusu görünüyor.

**Kök neden:** Stüdyo yapım listesi Shikimori REST `image` alanını okuyordu. Kapakları yıllar önce yeni depoya taşınan yapımlarda bu alan sessizce `/assets/globals/missing_original.jpg` döner (bazı kayıtlarda alan düz dizge gelir); `absoluteShikimoriImageUrl` bunu "görsel yok" sayıp `null`'a çevirir → kart baş harfle çizilir. Gerçek kapaklar yalnızca GraphQL `poster` alanında bulunur.

**Yapılan işler (`KitsugiStudioClient.kt`):**
- `withShikimoriWorkPosters(works)` eklendi: `mediaImageUrl == null` olan yapımların kimlikleri `ShikimoriPosterResolver.resolve(Kind.ANIME, ids)` ile **50'lik gruplar hâlinde tek istekte** çözülür (`cardUrl`), limiter + negatif önbellek mevcut altyapıdan gelir.
- Hem `fetchShikimoriStudioDetail` (ilk sayfa) hem `fetchShikimoriStudioWorksPage` (sonsuz kaydırma sayfaları) bu tamamlamadan geçirildi.
- Ayrıştırıcı artık `image` alanının **düz dizge** biçimini de kabul ediyor (nesne/dizge iki biçim).
- Stüdyo adı/logosu: `GET /api/animes/{id}` → `studios[]` araması artık "ad zaten biliniyorsa" atlanmıyor (logo genelde yalnızca orada) ve ilk yapım stüdyoyu listelemiyorsa en fazla `SHIKIMORI_STUDIO_LOOKUP_LIMIT = 3` aday denenir (sayfa açılışını geciktirmemek için).

---

## 4) Bangumi'de "Düzenle" sayfasının Simkl olarak açılması

**Belirti:** Bangumi detay sayfasında "✎ Düzenle"ye basınca düzenleyici Simkl markasıyla/Simkl alanlarıyla açılıyor.

**Kök neden:** Düzenleyicinin markası kaydın kendi `source` alanından çözülüyordu (`AppDialogHost.resolvedSource`). Bangumi sayfasında eşleşen yerel kayıt, çapraz eşitleme sırasında Simkl kimliği kazanmışsa (`simklId` dolu + Simkl bağlı) kaynak `simkl`'e çözülüyor ve Simkl görünümü açılıyordu. Ek olarak `AppViewModel.addApiSelectionToList` hiçbir zaman `source = "bangumi"` yazmaz (TvShow/Movie→simkl, sonra anilist → mal → simkl merdiveni); Bangumi kayıtlarını yalnızca içe aktarma yazar (`BangumiImportManager.smartImport("bangumi", …)`, `malId = subject_id + 500_000_000`) ve `MediaEntry`'de `bangumiId` alanı yoktur.

**Yapılan işler (uçtan uca zincir):**
- `ApiDetailLeftPanel` → "✎ Düzenle" artık `onEditClick(existingEntry, displayResult.source)` çağırır.
- `ApiResultDetailPage.onEditClick: (MediaEntry, String)` (varsayılan `{ _, _ -> }`) → `AppRootDetailPages.onEditEntryWithSource` → `AppRoot.editingSourceHint` → `AppDialogHost(editingSourceHint = …)`.
- `AppDialogHost` ipucu **projenin kanonik eşleyicisinden** geçirir (`canonicalMediaSourceId`): `bangumi|bgm|bgm.tv → bangumi`, `shikimori|shiki`, `kitsu`, `anilist`, `mal|jikan|myanimelist`, `simkl`; `tmdb → simkl` (düzenleyici tmdb'yi Simkl olarak ele alır). İpucu varsa `resolvedSource`'u geçersiz kılar; kaydın kimlikleri/senkron davranışı **değişmez**.
- Yeni parametrelerin tümü varsayılan değerli: TV (`TvRootScreen`) ve diğer çağrı yerleri etkilenmez.
- **Bangumi'ye özel düzenleme görünümü** (`KitsugiEditMediaSheet`): Bangumi rozeti (renk `0xFFF09199`, isKitsu/isShikimori'den sonra, isAniList/isMal/isSimkl'den **önce** kontrol edilir); durum pill'leri Bangumi terminolojisiyle (`bangumiStatusLabel`: İzliyorum / İzledim / Bekletiyorum / Bıraktım / İzleyeceğim / Tekrar İzliyorum-Okuyorum) — `BangumiSyncManager` eşlemesiyle birebir; ilerleme etiketi "İzlenen Bölüm" / "Okunan Bölüm"; yeni "Bangumi Koleksiyonu" satırı (`BangumiIdNamespace.rawIdFromStable` → `#subject_id` ya da "Başlıkla eşleştirilir") + Gizli + Etiketler.
- AniList'e özgü alanlar (özel listeler, detaylı puanlama) artık yalnızca kaydın **gerçekten** AniList kimliği varsa yükleniyor (`hasAniListIdentity` guard) — sayfa kaynağı yüzünden yanlış puan yazılmasını önler.

---

## Değişen dosyalar (12)

1. `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
2. `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStudioClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/app/AppDialogHost.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAddonsSettingsDialog.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCrossSyncDialog.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiEditMediaSheet.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CloudstreamExtensionTab.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/MangaExtensionTab.kt`
10. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/StremioAddonTab.kt`
11. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailLeftPanel.kt`
12. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`

---

## Doğrulama adımları (cihazda)

1. **Eşitleme:** Ayarlar → Çok Yönlü Eşitleme → başlat ekranı ve sonuç ekranı → "Uyarı" kartına dokun → tek kopya metin açılır/kapanır; hiçbir yerde ikinci bir uyarı bloğu yok, metin dialog dışına taşmaz.
2. **Eklentiler ve Kaynaklar:** Sayfada RealDebrid alanı yok; üç sekme ikonlu segment olarak görünür; her sekme bilgi şeridiyle başlar; boş durumlar tek tip karttır; ayarlar alt sayfasında altta "Tamam" şeridi yoktur.
3. **Shikimori stüdyo:** Bir Shikimori stüdyo sayfası aç → kapaklar görünür; aşağı kaydırıp 2+ sayfa yükle → sonraki sayfaların kapakları da gelir.
4. **Bangumi düzenleme:** Bangumi detay sayfası → "✎ Düzenle" → rozet **Bangumi**, durum pill'leri Bangumi terimleri ("İzliyorum/İzledim…"), ilerleme etiketi "İzlenen Bölüm", "Bangumi Koleksiyonu" satırı görünür; kaydet → kayıt normal şekilde güncellenir.
5. **Regresyon:** AniList/MAL/Simkl/Kitsu sayfalarından düzenleme hâlâ kendi markalarıyla açılır; TV arayüzü etkilenmez.

> Not: Bu ortamda derleme yapılamadı; `./gradlew assembleDebug` ile derleyip yukarıdaki adımları denemeniz gerekir.
