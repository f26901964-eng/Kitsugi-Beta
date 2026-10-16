# Kitsugi Beta v2.4.292

## 🇹🇷 Türkçe

### 🌐 Wiki Otomatik Çeviri & Orijinal/Çeviri Geçişi Güvenilirliği
- **Bağlantılı Paragraf Koruması:** Tıklanabilir bağlantı içeren wiki paragraflarında çeviri servisinin koruma jetonlarını düşürmesi durumunda tüm paragrafın çevrilmeden orijinal kalması sorunu giderildi.
- **Akıllı Metin Ayrıştırma:** Jeton bozulsa dahi bağlantılar arasındaki düz metin parçaları bağımsız olarak çevrilir; tıklanabilir bağlantı adları ve hedefleri hiçbir bozulmaya uğramadan korunur.
- **Kısmi Hata Dayanıklılığı:** Tek bir cümlenin veya parçanın çevirisi başarısız olsa bile paragrafın geri kalan çevrilebilir kısımları işlenmeye devam eder.
- **Açıklama Kartı Geçiş Düğmesi:** "Orijinal / Çeviri" düğmesi sayfa başlığından doğrudan etkilediği Açıklama kartının içine taşındı; böylece uzun bilgi kutusundan sonra kullanıcı hangi metni değiştirdiğini net biçimde görür.
- **Ortak Durum Yönetimi:** Karakter wiki bölümleri, açılır madde bölümleri ve tam wiki okuyucusu aynı `hasWikiTranslation` geçiş kontrolünü kullanır; yapılandırılmış çeviri blok blok akarken bile geçiş düğmesi anında görünür hale gelir.
- **Kaynak Başına Bellek:** Fandom ve Vikipedi kaynakları arasında geçiş yapıldığında çeviriler ve "Orijinal" tercihi kaynak bazında ayrı saklanır; kaynağa geri dönüldüğünde gereksiz ağ isteği yapılmadan çeviri anında geri yüklenir.
- **Gereksiz Çeviri Engelleme:** Kaynak dil zaten hedef dille aynıysa (örneğin Türkçe Vikipedi maddesi) açılır bölümler gereksiz yere çeviri servisine gönderilmez.

---

## 🇬🇧 English

### 🌐 Reliable Wiki Automatic Translation & Original/Translation Toggle
- **Protected Linked Paragraphs:** Fixed an issue where corrupted link protection tokens returned by the translation provider caused entire paragraphs to drop back to their original language.
- **Smart Segment Splitting:** Even if tokens are dropped, text segments between clickable links are translated independently while keeping link labels, URLs, and click targets completely intact.
- **Fault-Tolerant Translation:** A failure in translating a single text segment no longer aborts the entire paragraph; remaining translatable chunks continue to be processed.
- **Summary Card Toggle Placement:** Moved the "Original / Translation" toggle from the top page title bar directly into the Summary card header, making it immediately clear which text block is affected.
- **Unified State Checking:** Character wiki sections, article detail sections, and the full wiki reader now use unified `hasWikiTranslation` logic, showing the toggle immediately even while structured blocks are still streaming in.
- **Per-Source State Persistence:** Switching between Fandom and Wikipedia preserves translations and "Original" toggles independently per source, instantly restoring cached translations upon return without re-fetching.
- **Skipping Same-Language Sources:** If the source language matches the user's target language (e.g., Turkish Wikipedia articles), structured sections are not sent for redundant translation.
