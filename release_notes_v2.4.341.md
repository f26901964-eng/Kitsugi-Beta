# Kitsugi Beta v2.4.341 — Manga Okuyucu: Gerçek Tam Ekran + Okuma Deneyimi

## 🇹🇷 Türkçe

### 🖥️ Tam Ekran Artık Gerçekten Tam Ekran
- **Kök neden:** Alt bardaki "tam ekran" sanılan ikon yalnızca sığdırma modunu değiştiriyordu; sistem çubuklarını gizleyen hiçbir kod yoktu. Yeni `MangaReaderSystemUi` bileşeni durum çubuğu, gezinme çubuğu ve tablet görev çubuğunu gizler (`hide(systemBars)`), kenardan kaydırınca geçici olarak gösterir, okuyucudan çıkınca geri getirir.
- **Ayrı tam ekran düğmesi:** Üst ve alt barda `Fullscreen / FullscreenExit` ikonu; basınca hem sistem çubukları hem uygulamanın kendi barları gizlenir. Sığdırma modu artık ayrı ikon.
- **Ekranı açık tut:** Okuma boyunca ekran kapanmaz (kapatılabilir).
- **Dokunma bölgeleri düzeltildi:** Merkeze dokununca barların açılmamasının nedeni, dokunuşun zoom katmanı tarafından yutulmasıydı. Olayları tüketmeyen yeni dedektör sayesinde sol/orta/sağ dokunma, uzun basma ve pinch-zoom aynı anda çalışır.

### 📖 Okuma Modları
- **"Dikey" modu artık dikey:** Önceden yatay sayfalama çiziliyordu; yeni `MangaVerticalPagerReader` ile gerçek dikey sayfa geçişi.
- **Yeni sığdırma modları:** Doldur (kırp) ve Orijinal boyut.
- **Yeni renk filtreleri:** Sıcak, Soğuk, Sepya.
- **Aynalanmış sayfalar:** "Sayfaları aynala" anahtarı ile ters servis edilen sayfalar düzeltilir.

### ⚙️ Ayar Kimlikleri Düzeltildi (sessiz ayar kaybı)
- Ayarlar ekranı `GrayScale / Inverted / Warm / Cool / WebtoonZoom` yazıyor, okuyucu `Grayscale / Invert / Webtoon` bekliyordu. Bu yüzden bu seçimler sessizce yok sayılıyordu. Tek doğruluk kaynağı (`MangaSettingIds`) eklendi; eski değerler geriye dönük uyumlu okunuyor.

### 💾 İlerleme
- Webtoon'da yanlış sayfa kaydedilmesi, bölüm değişiminde konumun sıfırlanmaması ve son sayfanın (debounce yüzünden) kaybolması düzeltildi.
- Parlaklık sürgüsü anında tepki verir; diske yazma 250 ms geciktirilir.

### 🧹 Temizlik
- Ölü `MangaPageLoader` (v1), kullanılmayan `MangaScreen` + ikinci `MangaViewModel` silindi.
- Okuyucu ekranındaki 450 satırlık UI bloğu ve 3 kez kopyalanmış chip bloğu tek bileşene indirildi.
- Bölüm listesi paneli, bölüm sonu geçiş kartı anahtarı ve ses tuşlarıyla sayfa geçişi eklendi.

### 🧑‍🤝‍🧑 Aynı Bölüm, Birden Çok Çevirmen: Tek Satırda Gruplama
- **Sorun:** Eklentiler aynı bölümü AnimeWho / WebtoonTR / Tempest Fansub gibi birden çok çevirmen üzerinden sunuyordu. Detay ekranında bölüm 3 kez listeleniyor, "sonraki bölüm" ise farkında olmadan **başka bir çevirmene** atlıyordu.
- **Gruplama:** Bölüm numarasına göre gruplanır; liste tek satır + `N çevirmen` rozeti gösterir. Satıra dokununca tüm çevirmenler açılır (yıkıcı değil, her sürüm erişilebilir).
- **Ayrık görünüm:** Üst bardaki birleştir/ayır ikonu ile eski düz liste de kullanılabilir.
- **Çevirmen filtresi:** Bölüm başlığının altındaki çiplerle yalnızca seçilen çevirmenin bölümleri.
- **Hatırlanan tercih:** Seçilen çevirmen manga bazında saklanır; "Tercih: AnimeWho" rozeti ile görünür, X ile temizlenir.
- **Okuyucuda ayrıntılı geçiş penceresi:** Sonraki/önceki bölüme geçerken hedef bölümün birden çok çevirmeni varsa, her çevirmenin adı, bölüm adı, yükleme tarihi ve **kaldığın sayfa** ile birlikte seçim penceresi açılır. "Bu çevirmeni hatırla" ve "Bir daha sorma" anahtarları var.
- **Ekstralar:** Bölüm seçicide gruplu liste + "Önerilen" işareti, üst barda çevirmen rozeti, webtoon bölüm sonu kartında "%d çevirmen" ipucu, TV'de odak bozulmasın diye ön seçili sürümün sessiz onayı.

### 🔎 Arama ve Bölüm Listesi Kolaylıkları
- **Son aramalar:** Keşfet ekranındaki arama geçmişi çipleri — tek dokunuşla tekrar ara, X ile tek tek veya toplu temizle.
- **Arama kutusu:** Sağdaki X ile temizleme, klavye "ara" tuşu.
- **Sıralama:** Sonuçları İlgi sırası veya A-Z olarak sırala.
- **Boş sonuç ekranı:** Hangi kaynakların yanıt vermediğini gösterir, son aramaları önerir, "Kaynak ekle" düğmesi sunar.
- **Bölüm arama:** Uzun bölüm listelerinde numara/ad ile arama ("42" → 42, 42.0, 42.5).
- **Yalnızca okunmamış** filtresi ve **filtreleri temizle**.
- **Okundu/okunmadı işaretleme:** Bölüm veya tek bir çevirmen sürümü için onay ikonu; "görünenleri okundu işaretle" (onaylı).


### 🎨 Tema Uyumu ve Düzen
- Okuyucu artık **tema vurgu rengini** kullanıyor: ayar paneli, bölüm seçici, çevirmen penceresi, "tekrar dene" ve bölüm sonu düğmesi sabit mor yerine seçtiğin vurgu rengiyle çiziliyor.
- Ayar/çevirmen panellerindeki sabit beyaz yazılar tema metin renklerine geçti → **açık, koyu ve AMOLED** temalarda okunaklı.
- Çevirmen penceresi tek kaydırmalı liste + ekran yüksekliğine göre sınır → küçük/yatay ekranda taşma yok.
- Bölüm seçici paneli sabit yükseklik yerine ekranın %62'si; detay başlığındaki düğmeler küçültüldü, başlık tek satırda kırpılıyor; yatay telefonda keşfet ekranındaki bilgi kartı gizleniyor.

### 🖼️ Sayfa Görüntüleme Sağlamlığı
- Webtoon'da yer tutucular artık son yüklenen sayfanın **en-boy oranına** göre çiziliyor (sabit 480 dp kaldırıldı) → kaydırma sıçraması azaldı.
- **Arşiv (ZIP/CBZ) sayfaları artık gerçekten çiziliyor**: giriş akışı diske yazılıp önbellekten servis ediliyor.
- Sayfa önbelleği kontrolü yeniden çizimlerde diske gitmiyor; 0 baytlık dosya "hazır" sayılmıyor.


---

## 🇬🇧 English

### 🖥️ Fullscreen Is Now Actually Fullscreen
- **Root cause:** The icon believed to enable fullscreen only cycled the fit mode; nothing ever hid the system bars. New `MangaReaderSystemUi` hides status/navigation/task bars, shows them transiently on edge swipes, and restores them when leaving the reader.
- **Dedicated fullscreen button** in the top and bottom bars (`Fullscreen / FullscreenExit`); it hides both system bars and the in-app bars. Fit mode is now a separate control.
- **Keep screen on** while reading (optional).
- **Tap zones fixed:** taps were swallowed by the zoom layer. A new observer runs on the Initial pass without consuming events, so left/center/right taps, long-press and pinch-zoom all work together.

### 📖 Reading Modes
- **"Vertical" mode is now vertical** (previously it silently rendered the horizontal pager).
- **New fit modes:** Fill (crop) and Original size.
- **New color filters:** Warm, Cool, Sepia.
- **Mirror pages** toggle to fix pages served mirrored/flipped.

### ⚙️ Settings IDs Fixed (silent setting loss)
- Settings wrote `GrayScale / Inverted / Warm / Cool / WebtoonZoom` while the reader expected `Grayscale / Invert / Webtoon`, so those choices were silently ignored. `MangaSettingIds` is now the single source of truth, with backward-compatible parsing.

### 💾 Progress
- Fixed wrong page saves in webtoon mode, stale position on chapter change, and lost last-page progress (debounce flush on exit).
- Brightness slider updates instantly; disk writes are debounced by 250 ms.

### 🧹 Cleanup
- Removed dead `MangaPageLoader` (v1), unused `MangaScreen` and the duplicate `MangaViewModel`.
- Extracted the reader's 450-line UI block and the 3 duplicated chip blocks into shared components.
- Added a chapter list sheet, a chapter-transition toggle and volume-key page turning.

### 🧑‍🤝‍🧑 One Chapter, Many Scanlators: Merged Into a Single Row
- **Problem:** Extensions expose the same chapter through several scanlators (AnimeWho / WebtoonTR / Tempest Fansub). The chapter appeared three times in the list, and "next chapter" silently jumped to a **different scanlator**.
- **Merging:** Chapters are grouped by number; the list shows one row plus an `N scanlators` badge. Tapping expands every scanlator version — nothing is hidden.
- **Split view:** The merge/split button in the header still gives the old flat list.
- **Scanlator filter:** Chips under the section header narrow the list to one scanlator.
- **Remembered preference:** The chosen scanlator is stored per manga, shown as a "Preferred: AnimeWho" chip that can be cleared.
- **Detailed transition dialog:** When the next/previous chapter has multiple scanlators, a picker shows each scanlator with its chapter name, upload date and **your saved page**. "Remember this scanlator" and "Don't ask again" switches included.
- **Extras:** Grouped in-reader chapter picker with a "Suggested" marker, scanlator badge in the top bar, "%d scanlators" hint on the webtoon end card, and a silent pre-selected pick on TV so focus handling stays intact.

---

### 🔎 Search and Chapter List Comfort
- **Recent searches:** history chips on Discover — one tap to re-run, X to delete one, Clear to wipe all.
- **Search field:** X to clear, keyboard "search" action.
- **Sorting:** results by Relevance or A-Z.
- **Empty results:** shows which sources failed, suggests recent searches, and offers "Add source".
- **Chapter search:** find chapters by number or name in long lists ("42" also matches 42.0 and 42.5).
- **Unread-only** filter plus **clear filters**.
- **Mark read/unread:** one tap per chapter (or per scanlator version) and a confirmed "mark visible as read".

### 🎨 Theme Compatibility and Layout
- The reader now uses your **theme accent color**: settings panel, chapter picker, scanlator dialog, "retry" and the chapter-end button are tinted with your accent instead of a fixed purple.
- Hardcoded white text in the panels moved to theme text colors → readable in **light, dark and AMOLED** themes.
- The scanlator dialog is a single scrollable list capped to the screen height → no overflow on small or landscape screens.
- The chapter picker is capped to 62% of the screen height; detail header buttons were shrunk and the title truncates; the Discover info card hides on landscape phones.

### 🖼️ Page Rendering Robustness
- Webtoon placeholders now follow the **aspect ratio** of the last loaded page (no more fixed 480 dp) → fewer scroll jumps.
- **ZIP/CBZ archive pages actually render now**: the entry stream is written to disk and served from cache.
- Cache checks no longer hit the disk on every recomposition, and zero-byte files are no longer treated as ready.

