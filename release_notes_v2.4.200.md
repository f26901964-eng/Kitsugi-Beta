# Kitsugi-Beta v2.4.200 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌍 TMDB & Simkl Başlık Dili Düzeltmesi (Türkçe → İngilizce → Romaji)

**Sorun:** TMDB sekmesindeki "Trend Animeler", "En Yüksek Puanlı Animeler" ve "Yakında Yayında" şeritlerinde, Türkçe başlık dili seçili olmasına rağmen Türkçesi bulunmayan içerikler Japonca/Çince (CJK) başlıklarla görünüyordu.

**Kök neden:** İngilizce yedek başlık isteği üretilirken `language=` parametresi `Regex("language=[^&]+")` ile değiştiriliyordu. Bu regex `with_original_language=ja` parametresinin içindeki `language=ja` kısmını da yakalayıp `with_original_language=en-US` yapıyordu. Sonuç: anime şeritlerinin (hepsi `with_original_language=ja` kullanır) İngilizce yedek isteği **tamamen farklı bir içerik listesi** döndürüyor, ID eşleşmesi tutmuyor ve Japonca başlıklar ekranda kalıyordu.

**Çözüm:**

- **Güvenli URL üretimi (`TmdbUrlUtils`):** `language` parametresi artık yalnızca tam parametre olarak değiştiriliyor; `with_original_language` ve diğer filtreler korunuyor. Yedek istek aynı içerik listesini döndürdüğü için ID eşleşmesi birebir tutuyor.
- **Merkezi başlık çözümleyici (`MediaTitleResolver`):** Türkçe → İngilizce → Romaji zinciri tek yerde tanımlandı. CJK başlıklar ilk üç adımda asla kabul edilmiyor; yalnızca hiçbir Latin alternatif yoksa (son çare) gösteriliyor.
- **Akıllı yedek istek (`TmdbTitleFallback`):** TMDB istenen dilde başlık bulamazsa orijinal (Japonca) başlığı döndürür. Uygulama bunu "yerelleştirilmiş başlık == orijinal başlık" karşılaştırmasıyla ve CJK taramasıyla yakalıyor; yalnızca gerektiğinde tek bir `en-US` isteği yapıp başlıkları birleştiriyor. Gereksiz ağ trafiği yok.
- **Kapsam:** Keşfet şeritleri (trend/popüler/en yüksek puanlı/yakında yayında + "Tümü" ekranları), arama sonuçları, medya detay sayfası (TMDB `alternative_titles` desteğiyle TR/US/JP alternatifleri), bölüm adları, yayın takvimi, yapım şirketi sayfaları, öneriler/ilişkiler ve oyuncu-ekip çalışmaları.
- **Simkl:** Simkl kayıtlarında önce TMDB üzerinden Türkçe başlık denenir; yoksa Simkl'in İngilizce/romaji alanlarına inilir. Simkl `title` alanı CJK geldiğinde ekrana düşmez.
- **Gösterim güvencesi:** TMDB/Simkl kaynaklı öğeler için kart ve liste başlıkları, seçilen başlık dilinden bağımsız olarak daima Latin alfabesinde bir alternatif bulur (Japonca seçilmediği sürece).
- **Önbellek tazeleme:** Bellek içi ve kalıcı keşfet önbelleği anahtarları artık dil + başlık çözümleme sürümü içeriyor. Böylece eski sürümden kalan hatalı (Japonca) kayıtlar servis edilmiyor, düzeltme anında görünür oluyor.

### 🧾 Ek
- Sürüm adı `2.4.200` olarak güncellendi.

---

## 🇬🇧 English

### 🌍 TMDB & Simkl Title Language Fix (Turkish → English → Romaji)

**Problem:** In the TMDB tab, shelves such as "Trend Animeler", "En Yüksek Puanlı Animeler" and "Yakında Yayında" showed Japanese/Chinese (CJK) titles for entries that had no Turkish translation, even though Turkish was the selected title language.

**Root cause:** When building the English fallback request, the `language=` parameter was rewritten with `Regex("language=[^&]+")`. That regex also matched the `language=ja` fragment inside `with_original_language=ja` and turned it into `with_original_language=en-US`. As a result, the English fallback request for anime shelves (all of which use `with_original_language=ja`) returned a **completely different result set**, ID matching failed, and raw CJK titles stayed on screen.

**Fix:**

- **Safe URL building (`TmdbUrlUtils`):** the `language` parameter is now replaced only as a standalone parameter; `with_original_language` and other filters are preserved, so the fallback request returns the same list and IDs match one-to-one.
- **Single central resolver (`MediaTitleResolver`):** the Turkish → English → Romaji chain is defined in one place. CJK titles are never accepted in the first three steps and appear only as a last resort when no Latin alternative exists.
- **Smart fallback fetch (`TmdbTitleFallback`):** TMDB returns the original (Japanese) title when it has no translation for the requested language. The app detects this by comparing localised vs. original titles and scanning for CJK, then performs a single `en-US` request only when needed.
- **Coverage:** explore shelves (trending/popular/top-rated/upcoming + "see all" screens), search results, media detail pages (via TMDB `alternative_titles`, TR/US/JP), episode names, airing calendar, production company pages, recommendations/relations and person credits.
- **Simkl:** Simkl entries try a TMDB lookup first for a Turkish title, then fall back to Simkl's English/romaji fields; CJK `title` values never reach the UI.
- **Display guarantee:** for TMDB/Simkl items, card and list titles always resolve to a Latin alternative regardless of the selected title language (unless Japanese is explicitly selected).
- **Cache refresh:** in-memory and persistent explore cache keys now include the language plus the title-resolution version, so stale (Japanese) entries from previous builds are ignored and the fix is visible immediately.
