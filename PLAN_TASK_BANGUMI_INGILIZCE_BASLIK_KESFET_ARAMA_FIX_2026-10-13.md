# PLAN / TASK: Bangumi İngilizce başlıkları — Keşfet, Tümünü Gör ve Arama düzeltmesi

**Tarih:** 2026-10-13  
**Kapsam:** Bangumi kaynaklı sonuçlarda seçili başlık dilinin tüm liste yüzeylerinde korunması.

## Kullanıcı bildirimi

Başlık dili **English** seçiliyken Bangumi anime/dizi/film kayıtları detay sayfasına girilmeden önce Keşfet raflarında, Keşfet'in **Tümünü Gör** sayfalarında ve arama sonuçlarında Japonca/Çince görünüyordu. Detay sayfasında İngilizce başlık bulunabiliyordu.

## Kabul ölçütleri

- Gerçek English başlık varsa Keşfet, Tümünü Gör ve arama sonuçlarında onu göster.
- English yoksa Romaji gibi güvenli Latin geri dönüş kullan; Romaji'yi English alanıymış gibi kaydetme.
- Bangumi türüne uygun kaynak kullan; anime filmiyle canlı-aksiyon filmi için yanlış sağlayıcı eşleşmesini engelle.
- Kısmi cache güncellemesi daha önce bulunan dil alanlarını silmesin.
- Profil ve kütüphane başlıkları da seçili başlık diline uyumlu kalsın.

## Kök neden

- Bangumi'nin kısa arama/liste yanıtları genellikle `infobox` içermez.
- Romaji bazı yollarda English alanına kopyalanıyordu; bu da gerçek English eksikliğini gizleyebiliyordu.
- Bangumi infobox'ında Latin başlık bulunmaması, AniList/TMDB eşleştirmeleri denenmeden önce negatif önbelleğe alınabiliyordu.
- Liste sonuçlarında English, Romaji ve özgün başlık alanları her zaman ayrı taşınmıyordu.

## Uygulama

- `BangumiNameLocalizer`: English yalnızca açık English alanından alınır; Romaji ve özgün başlık ayrı tutulur. English yoksa gösterim Romaji'ye güvenli biçimde düşer.
- `BangumiTitleCache`: kısmi güncellemeleri birleştirir, mevcut başlıkları korur ve eşzamanlı kalıcı yazımda en güncel kaydı kullanır.
- `BangumiSubjectTitleResolver`: anime/manga için birebir normalize başlık eşleşmeli AniList araması; canlı aksiyon için başlık, yıl ve tür doğrulamalı TMDB fallback'i. Belirsiz film kayıtlarında Bangumi subject türü kontrol edilir. Negatif cache ancak ilgili sağlayıcı denemeleri tamamlandıktan sonra yazılır; örtüşen liste istekleri tekilleştirilir.
- `KitsugiBangumiClient` ve `KitsugiBangumiDetailClient`: English/Romaji/native alanlarını sonuçlarda ayrı korur ve doğrulanmış TMDB başlık eşleştirmesini paylaşır.
- `BangumiProfileContent` ve `BangumiLibraryTitleHydrator`: yalnızca Romaji bulunmasını English tamamlandı saymaz; başlık diline göre çözümlemeyi sürdürür.
- Android CI iş akışına ilgili Bangumi birim testleri eklendi.

## Testler ve doğrulama

Eklenen/güncellenen testler; Romaji-only davranışı, English/Romaji alan ayrımı, cache birleştirme, konu türüne göre AniList/TMDB yönlendirmesi, negatif cache koşulları ve kütüphane hydrator istek seçimini kapsar.

- `git diff --check`: başarılı.
- `bash ./gradlew :app:testFossDebugUnitTest --no-daemon`: bu çalışma ortamında `java` ve `JAVA_HOME` bulunmadığından çalıştırılamadı. Derleme ve test sonucu henüz doğrulanmış değildir.
