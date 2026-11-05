# Kitsugi Beta v2.4.343

## 🇹🇷 Türkçe

### 🔎 "Oku" Butonu Artık Seçtiğin Dilde Arıyor (büyük düzeltme)
Şikâyetin tam olarak buydu: başlık dili **İngilizce** seçili olmasına rağmen "Oku" butonu kafasına göre (genelde Romaji ya da orijinal) bir adla kaynak arıyordu. Kök neden: arama sorgusu, ekranda/DB'de ne varsa onunla yapılıyordu; **ayrı bir arama dili** kavramı hiç yoktu.

- **Yeni: Kaynak Arama Dili ayarı** (`Ayarlar → Görünüm → Manga Arama Dili`): İngilizce ad / Romaji / Orijinal ad / Yerel ad.
- **"Başlık dilini takip et"** anahtarı: açıkken arama dili, uygulamada seçili başlık diline otomatik uyar (varsayılan). Kapalıyken sabit seçtiğin dil kullanılır.
- **Tek merkezli sorgu motoru (`MangaSearchQueryBuilder`)**: sıra seçilen dile göre kurulur; ilk ad bulunamazsa yedekler denenir (İngilizce → Romaji → orijinal → alternatif adlar). Arama **asla boş isimle** gitmez.
- **Tüm yüzeyler aynı mantığı kullanır:** detay ekranı "Oku", eşlemesi olmayan kitaplık kaydı, API/keşif detay sayfası, manhwa/manhua/novel (aynı kaynak altyapısı) ve kaynak tarayıcısı.
- **Kaynak tarayıcısında görünür kontrol:** arama çubuğunun üstünde dil çipi (`Arama: İngilizce ad`). Dokun → sıradaki dile geçer ve kalıcı kaydedilir.
- **Kaynak bazlı yedek arama:** seçili adla hiç sonuç gelmezse kaynak, sıradaki dildeki adla (en fazla 3 yedek) tekrar denenir — boş ekran yerine sonuç gelir.



### 🗂️ Bölüm Listesi Sırası — Artan Sırada Gelen Kaynaklar
Bazı kaynaklar bölümleri **1 → N** sırasıyla döndürüyor; "En yeni üstte" seçiliyken 1. bölüm en üstte görünüyordu.
- `MangaChapterOrdering` artık numaralara bakıp listenin artan mı azalan mı geldiğini anlıyor (en az 3 numaralı bölüm varsa).
- Artan sırada gelen kaynakta liste otomatik "en yeni üstte" olacak şekilde düzeltiliyor.
- Numarasız bölümler ("Duyuru") her durumda sonda kalıyor; iki satırlık listelerde tahmin yapılmıyor.

### 🎛️ Dil Çipi Artık Gerçekten Yeniden Arıyor
Önceki hâlde çip yalnızca etiketi değiştiriyordu. Artık:
- "Oku" akışı, serinin **tüm adlarını** (`MangaTitleSet`) tarayıcıya taşır.
- Çipe dokununca arama **yeni dilde yeniden** yapılır (ör. İngilizce → Orijinal ad) ve seçim kalıcı kaydedilir.
- Başlık kümesi yoksa (tarayıcı elle açıldıysa) mevcut arama metni korunur, davranış bozulmaz.

### 🐞 Bölüm Geçişi Sırası (yön) Düzeltildi
Bazı kaynaklar bölüm listesini **eskiden yeniye** veriyordu; okuyucudaki ileri/geri bölüm düğmeleri listeyi hep "yeniden eskiye" kabul ettiği için ters çalışıyordu.
- Yeni `MangaChapterSequence`: bölümler **numaraya göre artan** okuma sırasına dizilir (numara yoksa adından çözülür: "Bölüm 137", "Ch. 12.5"), hiç numara yoksa kaynak sırasının tersi kullanılır.
- "Sonraki bölüm" / "önceki bölüm" düğmeleri, bölüm sonu kartı ve `hasNext/hasPrevious` artık kaynaktan bağımsız doğru.

### 📖 Çift Sayfa Modu (v2.4.342'den hatırlatma)
Tablet/yatay ekranda poster oranlı sayfalar ikişer yan yana; geniş (çift sayfa) taramalar tek başına tam genişlik. Taşma yok, sıra/yön fark etmez, jestler (zoom, dokunma alanları, parlaklık) korunur. Alt bardaki çift sütun düğmesi veya `Ayarlar → Okuyucu Ayarları → Çift sayfa`.

### 🔧 Diğer kontroller
- Yeni `R.string` anahtarlarının **tümü** TR + EN'de mevcut; ayarlar aramasına "manga arama dili" eklendi.
- Ayar kalıcılığı: `manga_search_title_language`, `manga_search_follow_title_language` (bulut yedeklemesine de dahil).
- `MangaSearchQueryBuilderTest` + `MangaChapterSequenceTest` + `MangaTitleSetTest` eklendi.

---

## 🇬🇧 English

### 🔎 The "Read" Button Now Searches in Your Selected Language
Root cause: the source search query used whatever title happened to be on screen, and there was no dedicated **search language** concept at all — so an English title preference still searched with a romaji/native name.

- **New setting:** `Settings → Appearance → Manga Search Language` (English / Romaji / Native / Localized).
- **"Follow title language"** toggle (default on) makes search follow the app's title language.
- **Single query engine (`MangaSearchQueryBuilder`)**: builds a priority-ordered query list and falls back (English → Romaji → native → synonyms). It never searches with an empty name.
- **One rule everywhere:** detail-page Read button, library entries without a mapping, API/discover detail pages, manhwa/manhua/novel sources and the source browser.
- **Visible control in the browser:** a language chip above the search bar (`Search: English title`) — tap to cycle and persist.
- **Per-source fallback:** if a source returns nothing for the primary title, the next title in the fallback chain is tried (max 3).



### 🗂️ Chapter List Order — Sources That Return Ascending
Some sources list chapters 1 → N; with "Newest first" selected, chapter 1 ended up on top.
- `MangaChapterOrdering` now detects the direction from chapter numbers (needs ≥ 3 numbered rows).
- Ascending lists are fixed to newest-first automatically.
- Unnumbered rows ("Announcement") always stay last; no guessing on two-row lists.

### 🎛️ The Language Chip Now Really Re-Searches
Previously the chip only changed its label. Now:
- The Read flow passes **every known title** (`MangaTitleSet`) to the browser.
- Tapping the chip re-runs the search in the new language (e.g. English → native title) and persists the choice.
- When no title set exists (browser opened manually), the current query is kept.

### 🐞 Chapter Navigation Direction Fixed
Sources that return chapters oldest-first made the next/previous buttons go the wrong way (the code always assumed newest-first). New `MangaChapterSequence` builds a true reading order from chapter numbers (resolved from names when missing) and falls back to reversed source order when nothing is numbered.

### 📖 Dual Page Mode (from v2.4.342)
Two portrait pages side by side on tablets/landscape; wide double-page scans stay full width. No overflow, order/direction independent, gestures preserved.

### 🔧 Also checked
- All new `R.string` keys exist in TR + EN; settings search includes the new option.
- New preferences are persisted and cloud-synced.
- Added `MangaSearchQueryBuilderTest`, `MangaChapterSequenceTest` and `MangaTitleSetTest`.
