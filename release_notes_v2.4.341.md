# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🧩 Eklenti Portalı Araması Düzeltildi — Arama Artık Eklentilerin İçinde Çalışıyor
- **Kök neden:** Eklenti Portalı'ndaki arama kutusu yalnızca eklenti adı süzüyordu; "Tüm Eklentiler"e basmak `setSelectedPlugin` + geri dönüşle kullanıcıyı Kaynaklar arama sayfasına (SearchScreen) fırlatıyor, arama butonu ise içerik araması hiç yapmıyordu.
- **Portal içi içerik araması:** Arama butonuna veya klavyedeki "Ara" tuşuna basmak artık portalda kalır. Video modunda seçili kategorideki aktif eklentilerde paralel arama (`CsStreamRunner.searchWithCfRetry`) yapılır; sonuçlar eklentisine göre gruplu olarak portalın içinde gösterilir, sonuca dokunmak eklenti detay önizlemesini açar. Global arama durumu (SearchViewModel) hiç tetiklenmez.
- **Kapsam düzeltmesi:** İçerik araması, adı sorguyla eşleşen eklentilerle sınırlı değildir — kategori kapsamındaki tüm aktif eklentiler aranır. Sorgu değişirse bayat sonuçlar temizlenir, yeni arama yalnızca açık onayla çalışır.
- **Video / Manga modları:** Portal üstüne Video / Manga sekmeleri eklendi. Manga modunda yüklü manga kaynakları listelenir; arama tüm aday kaynaklarda paralel çalışır (`MangaSourceRepository.searchAllSources`), bir kaynağa dokunmak aramayı o kaynakla sınırlar, sonuca dokunmak manga detayını açar.
- **"Tüm Eklentiler" satırı:** Artık kaynak arama sayfasına atmaz; sorgu varsa tüm aktif eklentilerde portal içinde arar.
- +18 göster/gizle sözleşmesi portal arama sonuçlarına da uygulandı.

---

## 🇬🇧 English

### 🧩 Plugin Portal Search Fixed — Search Now Runs Inside the Plugins
- **Root cause:** The Plugin Portal search box only filtered plugins by name; tapping "All Plugins" called `setSelectedPlugin` and popped back into the Sources search screen (SearchScreen), and the search button never ran a content search at all.
- **In-portal content search:** Pressing the search button or the keyboard "Search" action now stays in the portal. In Video mode it runs a parallel search across the active plugins of the selected category (`CsStreamRunner.searchWithCfRetry`) and shows results grouped by plugin inside the portal; tapping a result opens the plugin detail preview. The global search state (SearchViewModel) is never triggered.
- **Scope fix:** Content search is no longer limited to plugins whose name matches the query — all active plugins in the category scope are searched. Editing the query discards stale results; a new search runs only on explicit confirmation.
- **Video / Manga modes:** Video / Manga tabs were added to the portal. Manga mode lists installed manga sources; search runs in parallel across all candidate sources (`MangaSourceRepository.searchAllSources`), tapping a source scopes the search to it, and tapping a result opens the manga detail.
- **"All Plugins" row:** No longer throws the user into the sources search screen; with a query present it searches all active plugins inside the portal.
- The adult-content show/hide contract now also applies to portal search results.
