# Kitsugi Beta v2.4.312

## 🇹🇷 Türkçe

### 🔁 Liste Ekleme/Güncelleme ve Altı Kaynak (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) Çapraz Senkron Düzeltmesi

Bu sürüm, bir kaynaktan (örn. Bangumi) eklenen/düzenlenen kaydın seçilen durum (örn. **İzleniyor**), puan, tarih ve bölüm değerleriyle hem kendi kaynağına hem de bağlı tüm kaynaklara aynı anda yazılmaması sorununu kökünden giderir. Ayrıntılar: `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md`.

- **Bangumi ekleme/düzenleme artık diğer kaynaklarla aynı hattı kullanıyor:** Eski akış önce Bangumi'ye yazıyor, yazma başarısız olursa (token/kapsam/ağ) kaydı telefona bile kaydetmiyor ve hiçbir kaynağa gitmiyordu. Artık kayıt önce yerele yazılır; canlı eşitleme Bangumi dahil altı kaynağa birden yayar, başarısız yazımlar bekleyen senkron kuyruğunda yeniden denenir. Ekran anında kapanır, sonuç mesajla bildirilir.
- **Bangumi kökenli kayıtların diğer kaynaklara yayılamamasının ana nedeni giderildi:** Kimlik doğrulamasında satırın özgün Japonca başlığı da artık dikkate alınıyor. Bangumi satırlarında görünen başlık Çince olduğu için MAL/AniList doğrulaması neredeyse her zaman düşüyor ve yayılım sessizce atlanıyordu.
- **Çapraz kimlik katmanının çözdüğü AniList kimliği artık AniList yazmasında kullanılıyor:** Daha önce çözülen kimlik atılıp yazma sırasında başlıkla yeniden çözümleme yapılıyordu; bu özellikle Bangumi kökenli kayıtlarda başarısız oluyordu.
- **Bangumi manga/kitap kayıtları artık yayılabiliyor:** Kimlik çözümleyicide manga erken dönüşü Bangumi köprüsünden önce geliyordu; Bangumi kitapları MAL/AniList kimliği hiç çözemiyordu.
- **Simkl'e seçilen bölüm ve puan artık gerçekten gidiyor:** Simkl kimliği yalnızca "hiç kimlik yoksa" aranıyordu (koşul tersine çalışıyordu); kimlik varken bölüm/puan adımları sessizce atlanıyor, yalnızca durum yazılıyordu. Ayrıca canlı hat artık toplu hatla aynı güvenli yolu kullanır: anime bölümleri 1..N işaretlenir, puan yazılır ve seçilen durum **en son** uygulanır (eski `S01E<N>` uydurması kaldırıldı). Simkl kimliği geri yazımı, satırın kendi kaynak kimliğini ezmiyor.
- **Shikimori kendi satırını MAL kimliği çözülemese bile günceller:** Shikimori içe aktarmasının yazdığı ham kimlikler artık yanlışlıkla MAL kimliği sayılmıyor; Shikimori yazması kendi `target_id` değeriyle yapılabiliyor.
- **Ham kimlikler (Shikimori/Bangumi/Kitsu) artık MAL sanılmıyor:** Çapraz kimlik çözümleyici ve önbellek anahtarı satırın kaynağını dikkate alıyor; yanlış yapımın kimliğinin başka satıra yapışması engellendi. Bangumi film/dizi satırlarının subject kimliği de korunuyor, böylece Simkl yayılımı çalışıyor.
- **AniList tarihleri artık sessizce silinmiyor:** Yerel satırda tarih boşken yapılan her eşitleme AniList'teki başlangıç/bitiş tarihlerini temizliyordu; boş tarih artık gönderilmiyor.
- **MAL puanı normalize edildi:** Eski 0..100 ölçekli puanlar tüm MAL güncellemesini 400 hatasıyla düşürüyordu.
- **Kaynak sekmeleri arasında tutarlılık:** Bir kaynaktaki kaydı düzenlediğinizde aynı yapımın diğer kaynak sekmelerindeki ve "Tümü" görünümündeki kopyalar da anında aynı durum/puan/bölüm/tarih değerlerini gösterir; silme işlemi de tüm yerel kopyaları kaldırır.

### 🧪 Birim Testleri
- `CrossProviderIdentityTest`: ham Shikimori/Bangumi/Kitsu kimliklerinin MAL sayılmaması, Bangumi film subject kimliği, kaynak uzaylı önbellek anahtarları, kardeş satır kimlik kesişimi (TMDB sezon koruması dahil), Bangumi video satırı yayılım kapısı.
- `CrossSyncIdentityGuardTest`: özgün Japonca başlığın kimlik eşdeğerliğinde kullanılması.

### ⚠️ Platform Sınırları (değişmedi, bilgi amaçlı)
- Bangumi API'sinde tarih alanı yoktur; editörde seçilen tarihler Bangumi'ye yazılamaz (diğer kaynaklara yazılır).
- Bangumi'de bölüm ilerlemesi azaltılırsa işaretli bölümler geri alınmaz (API'nin güvenilir geri alma yolu doğrulanamadı).
- Dizi (TV) bölüm ilerlemesi sezon kataloğu olmadan Simkl'e gönderilmez; durum ve puan gönderilir.

---

## 🇬🇧 English

### 🔁 List Add/Update & Six-Source (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) Cross-Sync Fix

Fixes the root causes behind entries added/edited from one source (e.g. Bangumi) not being written to that source — and not fanning out to the other connected sources — with the exact status (e.g. **Watching**), score, dates and episode progress selected. Bangumi adds/edits now use the same local-first pipeline as every other source (no more losing the edit when the Bangumi write fails); the native Japanese title now participates in identity verification (the main reason Bangumi-originated entries silently failed to fan out); the cross-identity layer's resolved AniList ID is now actually used; Bangumi manga/books can resolve cross IDs; Simkl now receives progress (episodes 1..N, batch contract), rating and — last — the selected status; Shikimori rows update with their own ID; raw provider IDs are no longer mistaken for MAL IDs; AniList dates are no longer wiped by empty local dates; MAL scores are normalized; and sibling rows across source tabs (including the "All" view) reflect edits immediately.
