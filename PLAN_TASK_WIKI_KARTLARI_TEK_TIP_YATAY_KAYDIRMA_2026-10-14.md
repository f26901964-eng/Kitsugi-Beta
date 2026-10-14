# PLAN/TASK — Wiki İlişkili Kartları Tek Tip Yapma ve Yatay Kaydırma

**Tarih:** 2026-10-14  
**Sürüm:** 2.4.289 → **2.4.290**  
**Durum:** Uygulandı. Android derlemesi bu ortamda Java/JDK bulunmadığından çalıştırılamadı.

## Kullanıcı isteği

Wiki sekmesi ve uygulama içi Wiki okuyucusunda oyuncu kadrosu, seslendirenler, ekip, müzik ve ilgili Wiki sayfalarındaki kartlar önceki sade bilgi kartı görünümüne dönmeli; uzun Wiki içeriği kartların içine yığılmamalı. Bu kartların tamamı aynı tür ve ölçüde olmalı, sola hizalı yatay sırada kaydırılmalı ve karta dokununca sayfa uygulama içi Wiki okuyucusunda açılmalı.

## Yapılanlar

- Yeni ortak `WikiPagePreviewCard` bileşeni eklendi. İlişkili tüm önizleme kartları **280 × 144 dp** sabit ölçü, aynı yatay görsel/başlık/kısa açıklama düzeni ve aynı görsel stile geçirildi.
- Uzun açıklamalar iki satırla sınırlandırıldı; sayfanın tam içeriği karta gömülmek yerine karta dokununca Wiki okuyucusunda açılıyor.
- Oyuncu kadrosu, seslendiren, ekip ve müzik grupları ile ilgili Wiki sayfaları aynı ortak kartı kullanıyor.
- Yatay listelere sola oturan kart-snap kaydırması eklendi; kart listesi başka Wiki sayfasına geçtiğinde ilk karta sıfırlanıyor.
- Wiki sekmesi ve okuyucudaki ilişkili sayfa şeritleri aynı kart düzenine alındı.
- `appVersionName` 2.4.290'a yükseltildi; TR/EN sürüm notları eklendi.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPagePreviewCard.kt` (yeni ortak kart)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`
- `app/build.gradle.kts`
- `RELEASE_NOTES.md`
- `release_notes_v2.4.290.md`
- `PLAN_TASK_WIKI_KARTLARI_TEK_TIP_YATAY_KAYDIRMA_2026-10-14.md` (bu dosya)

## Doğrulama

- `git diff --check`: başarılı.
- Değiştirilen Kotlin dosyalarında parantez/ayraç dengesi kontrol edildi.
- `bash gradlew :app:compileDebugKotlin`: çalıştırılamadı; ortamda `JAVA_HOME` ve `java` bulunmuyor.

## Kabul ölçütleri

1. Oyuncu kadrosu, seslendirenler, ekip, müzik ve ilişkili Wiki kartlarının tamamı aynı 280 × 144 dp ölçüsündedir.
2. Uzun Wiki metni kartı büyütmez; en fazla iki satır özet görünür.
3. Kartın metin/görsel alanına dokunmak ilgili sayfayı uygulama içi Wiki okuyucusunda açar (okuyucu bağlamında görsele dokunma galeri eylemini korur).
4. Kart sıraları sola hizalı kayar, serbest bırakılınca en yakın kartın başlangıcına oturur ve başka bir Wiki sayfasına geçildiğinde ilk karta döner.
