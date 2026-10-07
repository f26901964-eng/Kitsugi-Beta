# Uygulama Planı — Çapraz Eşitleme Hata Raporu

## Amaç
Çoklu platform eşitlemesinde oluşan hata, uyarı, atlanan işlem ve medya eşleştirme kararlarını kaybolmadan inceleyebilmek; belirsiz eşleşmelerin diğer hesaplara yazılmasını engellemek.

## Adımlar
1. Canlı ekranda kullanılan son 500 satırlık günlükten bağımsız, eşitleme oturumu boyunca eksiksiz bir rapor günlüğü tut.
2. Her medya grubunun kaynak başlıklarını, tür/yılını, kaynak platformunu, harici kimliklerini ve birleştirilmiş durum/ilerleme değerlerini kaydet.
3. API/yerel kayıt hatalarında platformu, işlemi, hata zincirini ve mevcut stack-frame ayrıntılarını rapora ekle; kimlik bilgilerini dışa aktarmadan önce maskele.
4. Aynı başlıkta çelişen sağlayıcı ID'leri, ID eşleşip başlıkların uyuşmaması veya çözülemeyen çoklu aday durumlarında güvenlik için hedef platformlara yazmayı atla ve tüm adayları raporla.
5. Her eşitleme sonunda ayrı bir TXT raporu otomatik kaydet; kullanıcıya sistem dosya seçicisiyle başka konuma dışa aktarma olanağı ver.
6. Rapor biçimi, gizli anahtarların maskelenmesi ve sağlayıcı kimlik çakışmaları için birim testleri ekle.

## Doğrulama
`git diff --check` temiz geçti. Gradle/JUnit testleri, çalışma ortamında Java/JDK bulunmadığından çalıştırılamadı (`JAVA_HOME`/`java` yok).
