# Kitsugi Beta v2.4.337

## 🇹🇷 Türkçe

### 🗑️ Aktivite Silme: Açılır Panel Kaldırıldı
- **Tek dokunuş silme (`ProfileActivityComponents.kt`):** Kendi AniList profilindeki aktivite kartlarında üç nokta (⋮) düğmesi ve tek satırlık "Sil" açılır menüsü kaldırıldı. Yerine doğrudan, kartın sağ üstünde duran kırmızı çöp kutusu düğmesi geldi.
- **Onay korundu:** Düğme, çağrı yerindeki mevcut `KitsugiConfirmDialog`u açar; yani kazara silme hâlâ engellenir, yalnızca gereksiz ara panel yok.
- **Bağımlılık temizliği:** `KitsugiDropdownMenu` / `KitsugiMenuItem` içe aktarımları ve `showMenu` durumu kaldırıldı.

### 🌐 Kullanıcı Biyografilerinde Otomatik Çeviri (Wiki Mantığı)
- **Yeni ortak altyapı (`KitsugiAutoTranslation.kt`, `KitsugiHtmlTranslation.kt`):** Wiki sayfası çevirisinin kuralları profillere taşındı: ayar açıksa çeviri uygulama içinde yapılır, sonuç bellekte tutulur (tekrar istek yok), çeviri sürerken çeviri ikonu galeri butonuyla aynı nabız/halka animasyonunu gösterir ve dar başlık satırına sığan kompakt "Orijinal / Çeviri" çipi sunulur.
- **HTML güvenli çeviri:** Biyografi HTML'i yalnızca **metin düğümleri** üzerinden çevrilir. `<br>`, `<a>`, `<img>`, renkler ve `href` adresleri aynen korunur; `<script>`, `<style>`, `<code>` içerikleri hiç çevrilmez. Bir düğüm çevrilemezse orijinali kalır, hiçbiri çevrilemezse çeviri hiç uygulanmaz.
- **Kapsam:** AniList "Hakkında" (kendi profili), AniList kullanıcı profili "Hakkında", Kitsu biyografisi, Shikimori biyografisi, Simkl biyografisi ve MyAnimeList "Hakkında" kartı (düz metin + genişletilmiş HTML görünümü).
- **WebView canlı güncelleme (`KitsugiHtmlWebView.kt`):** Çeviri tamamlandığında veya "Orijinal" düğmesine basıldığında WebView yeniden yaratılmadan yeni HTML yükleniyor. Önceden `AndroidView` yalnızca ilk veriyi gösterdiği için çeviri ekrana hiç yansımıyordu.
- **İkon davranışı:** Çeviri ikonu wiki ile aynı kalır — dokununca metin kullanıcının tercih ettiği harici çeviri uygulamasında açılır; uygulama içi çeviri yalnızca "Otomatik çeviri" ayarıyla çalışır.

### 👥 Takip Butonu Metni Düzeltildi
- **`profile_following_button` → "Takiptesin":** AniList'te takip edilen profilde buton "Takip Ediliyorsun" (edilgen: takip **ediliyorsun**) yazıyordu; yanlış anlam veriyordu. Türkçe metin "Takiptesin" yapıldı; İngilizce karşılığı "Following" olarak kaldı. Profil başlığındaki "Sizi takip ediyor" bilgisi (takipçi yönü) olduğu gibi korundu.

### 📐 Listem Rozetleri: Köşelere Yapışık ve Kompakt
- **Poster köşe rozetleri (`KitsugiMediaEntryCard.kt`):** Izgara, büyük ve rahat görünümlerdeki durum/puan rozetleri artık posterin köşesine **tam yapışık** durur (`TopEndCornerShape` / `BottomEndCornerShape`: dış köşe düz, iç köşeler yumuşak; posterin yuvarlak köşesi rozeti doğal biçimde keser). Böylece rozet görselin üzerinde yüzüyor gibi durmaz ve görseli eskisinden az kaplar.
- **Kompaktlaştırma:** Rozet yazısı 9sp, iç boşluk daraltıldı, genişlik `widthIn(max = 108.dp)` + tek satır üç nokta ile sınırlandı. Uzun durum adları taşmaz.
- **Favori ve puan:** Favori yıldızı sol üst köşeye, puan sağ alt köşeye tam yapışık; kaynak logosu sol alt köşede kalır. Dört köşe aynı dili konuşur.
- **Küçük posterlerde kaynak logosu:** Satır düzenlerinde (kompakt/minimalist) 50–76dp posterlerde kaynak rozeti 16–18dp'ye küçültüldü; posterin yarısını kaplaması engellendi.

---

## 🇬🇧 English

### 🗑️ Activity Delete: Dropdown Panel Removed
- **One-tap delete (`ProfileActivityComponents.kt`):** The three-dot (⋮) button and its single-row "Delete" dropdown on your own AniList profile activity cards were removed. A direct red trash button now sits in the card's top-right corner.
- **Confirmation kept:** The button opens the existing `KitsugiConfirmDialog`, so accidental deletion is still prevented — only the redundant intermediate panel is gone.
- **Dependency cleanup:** `KitsugiDropdownMenu` / `KitsugiMenuItem` imports and the `showMenu` state were removed.

### 🌐 Automatic Translation for User Bios (wiki logic)
- **New shared infrastructure (`KitsugiAutoTranslation.kt`, `KitsugiHtmlTranslation.kt`):** Wiki-page translation rules now apply to profiles: when the setting is on, translation runs in-app, results are cached in memory (no repeat requests), the translate icon shows the same pulse/ring animation as the gallery button, and a compact "Original / Translation" chip that fits the narrow header row is offered.
- **HTML-safe translation:** Only **text nodes** inside the bio HTML are translated. `<br>`, `<a>`, `<img>`, colors and `href` targets are preserved verbatim; `<script>`, `<style>` and `<code>` contents are never touched. A node that fails keeps its original text; if every node fails, no translation is applied at all.
- **Coverage:** AniList "About" (own profile), AniList user profile "About", Kitsu bio, Shikimori bio, Simkl bio and MyAnimeList "About" card (plain text + expanded HTML view).
- **Live WebView updates (`KitsugiHtmlWebView.kt`):** When translation completes or "Original" is tapped, the new HTML is loaded without recreating the WebView. Previously `AndroidView` only ever showed the first payload, so translations never appeared.
- **Icon behaviour:** The translate icon stays identical to the wiki one — tapping it opens the text in the user's preferred external translator app; in-app translation only happens when "Automatic translation" is enabled.

### 👥 Follow Button Wording Fixed
- **`profile_following_button` → "Takiptesin":** On a followed AniList profile the button read "Takip Ediliyorsun" (passive: *you are being followed*), which reversed the meaning. The Turkish label is now "Takiptesin"; English stays "Following". The header's "Sizi takip ediyor" (they follow you) indicator is unchanged.

### 📐 My List Badges: Corner-Glued and Compact
- **Poster corner badges (`KitsugiMediaEntryCard.kt`):** Status/score badges in grid, large and comfortable layouts now sit flush against the poster corner (`TopEndCornerShape` / `BottomEndCornerShape`: flat outer corner, soft inner corners; the poster's rounded corner naturally clips the badge). They no longer look detached and cover less of the artwork.
- **Compact styling:** 9sp label text, tighter padding, width capped by `widthIn(max = 108.dp)` with single-line ellipsis, so long status labels never overflow.
- **Favorite and score:** The favorite star is glued to the top-left corner, the score to the bottom-right, and the source logo stays in the bottom-left. All four corners share one language.
- **Source logo on small posters:** In row layouts, the source badge shrinks to 16–18dp on 50–76dp posters so it no longer covers half the artwork.
