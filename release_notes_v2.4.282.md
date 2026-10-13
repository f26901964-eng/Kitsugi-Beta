# Kitsugi Beta v2.4.282

## 🇹🇷 Türkçe

### Karakter / Ekip / Seslendiren "Wiki" sekmesi: "Tekrar dene" artık gerçekten yeniden çözümlüyor
- **Ölü "Tekrar dene" düğmesi düzeltildi.** Karakter, ekip ve seslendirme sanatçısı detayındaki
  Wiki sekmesinin "Tekrar dene" düğmesi yalnızca yeniden bağlanıyordu; karakter katmanındaki ve
  yapım wiki katmanındaki "bulunamadı" önbelleği (3 güne kadar) ile süreç içi "wiki yok" listesi
  (30 dk) aynı sonucu ağa hiç çıkmadan geri verdiği için düğme çalışmıyormuş gibi görünüyordu.
  Artık yeniden deneme; önbellekleri (olumsuz kayıtlar dahil) okumadan, zehir kayıtları temizleyip
  çözümlemeyi baştan yapıyor. Yapım wiki sekmesindeki davranışla aynı seviyeye getirildi.
- **Bayat "bulunamadı" kayıtları kısaltıldı:** 3 gün → 1 gün.
- **Geçici kesintiler artık kalıcı "bulunamadı" yazmıyor:**
  - HTTP 403/408 (Cloudflare/WMF bot koruması, zaman aşımı) geçici hata sayılıyor;
    404 (gerçekten olmayan wiki) sayılmıyor.
  - Kimlik köprüsü (AniList/Kitsu/Simkl) patlamaları geçici hata işaretliyor.
  - Wikidata karakter yedeğinde tek kimliğin ağ hatası tüm zinciri kırmıyor, sıradaki deneniyor.
- Sonuç: Fandom sayfası var olan karakterlerde (ör. Date A Live → Kotori Itsuka) geçici bir
  kesinti sonrası günlerce süren "Wiki bulunamadı" durumu ve çalışmayan yeniden deneme düğmesi
  giderildi; ilk sağlıklı istekte sekme yeniden doluyor.

## 🇬🇧 English

### Character / Staff / Voice-actor "Wiki" tab: "Retry" now truly re-resolves
- **Dead "Retry" button fixed.** On character, staff and voice-actor detail pages the Wiki tab's
  retry only re-bound the view model; the character-layer and media-wiki-layer "not found" caches
  (up to 3 days) plus the in-process "missing wiki" list (30 min) returned the same result without
  any network call, so the button felt broken. Retry now clears the poison entries and re-resolves
  with caches bypassed (negative entries included), matching the media wiki tab behaviour.
- **Stale negatives shortened:** 3 days → 1 day.
- **Transient outages no longer persist a "not found":** HTTP 403/408 counted as transient
  (404 still means "wiki really absent"), identity-bridge exceptions marked transient, and the
  Wikidata character fallback no longer aborts the whole chain when one identity lookup fails.
- Result: characters whose Fandom page exists (e.g. Date A Live → Kotori Itsuka) no longer stay
  hidden for days after a transient outage, and retry recovers them immediately.
