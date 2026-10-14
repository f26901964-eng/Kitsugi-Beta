# Kitsugi Beta v2.4.289

## 🇹🇷 Türkçe — Wiki sayfaları / okuyucu güvenilirliği

- **Eksik/olmayan sayfalar artık tıklanabilir kart değil:** Fandom'un `missing: ""` (eski API) ve `missing: true` (yeni API) biçimleri, geçersiz sayfalar ve sayfa kimliği olmayan sonuçlar ayıklanır. Kırmızı bağlantılar ve başka wiki'lere ait başlıklar ilgili sayfa listesine girmez. Tetsurō Araki gibi *gerçekten o wiki'de bulunmayan* maddeler için uygulama sahte içerik üretmez.
- **Var olan kart daha sağlam açılır:** Doğrulanmış kartın kanonik başlığı/özeti/görseli kısa süreli yeniden kullanılır; görsel/özet API'si hata verirse gerçek sayfa kimliğini doğrulayan yalın MediaWiki sorgusu denenir. Yönlendirmeler birden fazla adımda çözülür; protokol-göreli görsel URL'leri düzeltilir.
- **Fandom ve Wikipedia sorguları ayrıldı:** Fandom'da bulunmayan `prop=extracts` artık yalnızca Wikipedia için istenir. İsteğe bağlı MediaWiki modülleri 200/hata yanıtı verirse daha yalın sorguyla tekrar denenir. Eski olumsuz önbellek anahtarları yenilendi; kalıcı "bulunamadı" süresi 3 günden 1 güne indirildi.
- **Kısmi sayfa kaybolmaz:** İçindekiler yüklenemediğinde sayfanın mevcut özeti ve giriş bölümü görünür; içindekiler için gerçek ağ isteği yapan "Tekrar dene" sunulur, eksik sonuç 7 gün önbelleğe yazılmaz. Karakter/ekip okuyucusunda giriş bölümü de otomatik yüklenir. Sayfa hiç açılamazsa hata açıklaması, yeniden dene ve tarayıcıda aç seçenekleri vardır.
- **Testler:** Eski/yeni `missing` bayrakları, yönlendirmeler, Fandom/Wikipedia parametreleri, başka wiki/kırmızı bağlantı süzgeci için regresyon testleri eklendi.

> Fandom'da hiç yayımlanmamış bir kişiye uygulama wiki maddesi uyduramaz; böyle başlıklar artık yanıltıcı kart olarak gösterilmez. Üçüncü taraf hizmet kesintileri tamamen önlenemez; yeniden dene/tarayıcı yolu bu yüzden korunur.

---

## 🇬🇧 English — Wiki page/reader reliability

- Filter missing/invalid pages (both MediaWiki flag formats and absent page IDs), red links and cross-wiki links from related cards.
- Reuse verified previews when opening a card; fall back to a core page-ID query if optional preview modules fail. Handle multi-step redirects and normalize image URLs.
- Request TextExtracts only from Wikipedia, not Fandom; retry unsupported optional-module queries without those modules. Invalidate stale negative caches and shorten negative TTL to one day.
- Keep a valid article readable when its table of contents fails; allow a real retry without caching the incomplete TOC. Auto-load section 0 in character/staff readers and show a clear recovery path when a page cannot open.
- Add regression tests for the cases above. Articles not actually published by a wiki cannot be conjured by the app.
