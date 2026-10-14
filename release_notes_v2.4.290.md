# Kitsugi Beta v2.4.290

## 🇹🇷 Türkçe

### Depolama Ekranına "Wiki Önbelleği" Kalemi
- **Görünür Wiki Verisi:** Ayarlar > Depolama ekranında artık **Wiki önbelleği** kalemi de yer alır; Fandom ve Wikipedia maddeleri, bilgi kutuları, karakter sayfaları ve bölüm içerikleri için tutulan kayıtların boyutu ve dosya sayısı buradan görülür.
- **Güvenli Temizlik:** Kalemin "Temizle" eylemi yalnızca **yeniden üretilebilir** wiki kayıtlarını (Room `persistent_details_cache` tablosundaki `fandom:v1:` kayıtları) siler; kullanıcının "doğru / hayır, yanlış" eşleşme kararları korunur. Silinen içerik bir sonraki açılışta ağdan yeniden çekilir.
- **Doğru Ölçüm:** SQLite dosyası silme sonrası küçülmediği için (VACUUM gerekir) kalem, disk dosyası yerine **mantıksal veri boyutunu** raporlar; "Temizle" sonrası geri kazanılan miktar da bu ölçümden hesaplanır. 30 günden eski kayıtlar arka plan bakımında otomatik atılmaya devam eder.

### Uygulama Hakkında → Sürüm Notları
- **GitHub'dan Son Sürüm Notları:** "Uygulama Hakkında" ekranına **Sürüm Notları** kartı eklendi. Kart, GitHub Releases üzerindeki **en son** sürümün başlığını, yayın tarihini ve değişiklik notlarını markdown işaretlerinden arındırılmış biçimde gösterir.
- **Kurulu Sürüm Güncelken de Görünür:** Güncelleme akışı yalnızca yeni sürüm varsa pencere açtığı için, uygulama güncelken sürüm notları hiçbir yerde görünmüyordu. Yeni kart, yeni sürüm koşulu aramaz; "Yeni sürüm hazır" / "Kurulu sürüm en güncel" rozetiyle durumu belirtir.
- **İstek Sınırı Koruması:** Son başarılı yanıt 6 saat süreyle diskte önbelleklenir (GitHub kimliksiz istek sınırı saatte 60 istek); ekran her açıldığında ağa gidilmez. "Yenile" düğmesi önbelleği atlayarak zorla tazeler, "GitHub'da tüm sürümler" bağlantısı tarayıcıda sürüm listesini açar.
- **Ortak Görünüm:** Sürüm notları, güncelleme penceresindeki biçimlendirmenin aynısıyla (başlık, madde imi, ayraç sadeleştirmesi) gösterilir.

### Wiki'de "Orijinal / Çeviri" Geçişi Her Yere
- **Açılır Wiki Sayfası Bilgi Kutusu:** Bilgi kutusunda artık uygulama içi çeviri varsa, harici çeviri uygulamasını açan düğmenin **hemen solunda** "Orijinal" düğmesi çıkar; dokununca bilgi kutusunun tüm alanları wiki'nin özgün diline döner ("Çeviri" ile geri alınır). Bu, karakter wiki sekmesindeki davranışla birebir aynıdır.
- **Açılır Wiki Sayfası Açıklaması ve Bölümleri:** Seçili wiki maddesinin açıklaması ile açılan her madde bölümünde de aynı geçiş düğmesi eklendi; "Orijinal" seçiliyken bölüm gövdesi çevrilmemiş metni gösterir (çeviri bellekte kalır, ağa tekrar gidilmez).
- **Wikipedia Kaynakları Dahil:** Kaynak Fandom ya da Vikipedi (TR) olsun aynı bileşenler kullanıldığı için geçiş düğmesi her iki kaynakta da çalışır; kaynak değiştirildiğinde durum ilgili anahtara göre ayrı tutulur.
- **Bilgi Kutusunu Kopyala:** Açılır wiki sayfasının bilgi kutusuna "bilgi kutusunu kopyala" düğmesi eklendi. Kopyalanan metin yalnızca ekranda görünen alanları içerir, spoiler koruması açıkken gizli alanların değeri `[gizli]` ile maskelenir ve "Orijinal" seçiliyken çeviriler değil özgün metin kopyalanır.

---

## 🇬🇧 English

### New "Wiki Cache" Entry on the Storage Screen
- **Visible Wiki Data:** Settings > Storage now lists a **Wiki cache** entry showing the size and file count of the records kept for Fandom/Wikipedia articles, infoboxes, character pages and section contents.
- **Safe Clearing:** Its "Clear" action removes only **reproducible** wiki records (`fandom:v1:` rows in the Room `persistent_details_cache` table) and preserves the user's "correct / wrong match" decisions. Cleared content is re-fetched from the network on the next visit.
- **Accurate Measurement:** Because SQLite does not shrink after deletes (VACUUM is required), the entry reports **logical data size** instead of the database file size, and the reclaimed amount is derived from the same measurement. Records older than 30 days are still pruned automatically by background maintenance.

### About the App → Release Notes
- **Latest Notes Straight from GitHub:** A **Release Notes** card was added to the "About the App" screen. It shows the title, publication date and markdown-stripped changelog of the **latest** GitHub release.
- **Shown Even When Up to Date:** The update flow only opens a dialog when a newer version exists, so release notes were invisible on an up-to-date install. The new card does not require a newer version and labels the state with a "New version available" / "Installed version is up to date" badge.
- **Rate-Limit Friendly:** The last successful response is cached on disk for 6 hours (GitHub's unauthenticated limit is 60 requests/hour), so the screen does not hit the network on every open. "Refresh" bypasses the cache and forces a re-fetch, while "All releases on GitHub" opens the release list in the browser.
- **Consistent Styling:** Release notes are rendered with the exact same formatter as the in-app update dialog (headings, bullets and separators are cleaned up).

### "Original / Translation" Toggle Everywhere in the Wiki
- **Popup Wiki Infobox:** When an in-app translation exists, the infobox now shows an "Original" button **immediately left of** the external-translate button; tapping it restores all infobox fields to the wiki's source language ("Translation" switches back). This matches the character wiki tab behaviour exactly.
- **Popup Article Summary and Sections:** The same toggle was added to the selected article's summary and to every expanded article section; while "Original" is selected the section body shows the untranslated text (the translation stays in memory, no extra network request).
- **Wikipedia Sources Included:** Because the same components render both Fandom and Wikipedia (TR) sources, the toggle works on either source and its state is tracked per translation key when switching sources.
- **Copy Infobox:** The popup infobox gained a "copy infobox" button. The copied text contains only the fields currently on screen, masks hidden fields as `[hidden]` while spoiler protection is on, and copies the source text rather than the translation when "Original" is selected.
