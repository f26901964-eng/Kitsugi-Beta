# TASK — Galeri İndirme: Tekrar İndirmeyi Önleme + İçerik Adına Göre Gruplama

**Tarih:** 2026-10-08 · **Dal:** `arena/b3cbf78d-kitsugi-beta`
**Plan:** `PLAN_GALERI_INDIRME_DUPLIKASYON_FIX.md`

---

## Görev

Kullanıcının isteği (ekran görüntüsü: yatay galeri, sağ panel "İndirilen Resimler"):

1. [x] İndirilenler galerisinde (yerel dosyalar) **indirme butonu gösterilmeyecek**
2. [x] Daha önce indirilmiş resim **tekrar indirilemeyecek** (index mantığı)
3. [x] İndirilen resimler **içerik adına göre (anime/dizi/film) gruplanacak**
4. [x] **TMDB / Shikimori / Fanart.tv** kaynaklarının galeri resimlerini düzgün
       sağlayıp sağlamadığı kontrol edilecek

## Değişen Dosyalar

| # | Dosya | Değişiklik |
|---|---|---|
| 1 | `app/src/main/java/com/kitsugi/animelist/utils/KitsugiImageDownloadHelper.kt` | İndirme index'i (JSON, filesDir), `downloadedUrls` StateFlow, `isImageDownloaded`/`markImageDownloaded`/`unmarkImageDownloadedByFileName`/`refreshDownloadedUrls`, `downloadImage` korumaları (yerel dosya / zaten indirilmiş / in-flight), Unicode-safe başlık, başarılı kayıtta index'e işleme |
| 2 | `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt` | `allowDownload` parametresi, yerel dosyada indirme butonunu gizleme, "zaten indirilmiş" yeşil tik durumu, `onDownload` korumaları, StateFlow dinleme |
| 3 | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt` | İçerik adına göre gruplama (başlık + FlowRow), grup-odaklı galeri (`allowDownload = false`), silmede index temizliği, kart `modifier` parametresi, başlık ayrıştırma iyileştirmesi |

## Kaynak Kontrolü (Görev 4) — Sonuç

- **TMDB** ✅ — `/3/{type}/{id}/images`, tür fallback'i, API anahtarı (kullanıcı → yerleşik yedek), önbellek.
- **Shikimori** ✅ — `/api/animes/{id}/screenshots`, resilient GET (429/5xx tekrar), göreli URL çözümü, eksik-görsel filtresi, önbellek.
- **Fanart.tv** ✅ — Film: TMDB ID; TV/Anime: TVDB ID (Room → TMDB external_ids → animeapi.my.id → ARM Kitsu zinciri), film↔TV uçtan uca fallback, tüm kategoriler.
- Üçü de iki detay ViewModel'inde paralel çekilip `distinctBy { url }` ile birleştiriliyor.
- **Arıza bulunmadı — düzeltme gerekmiyor.**

## Doğrulama

- [x] Sözdizimi: 3 dosyada parantez/küme/köşeli parantez dengesi elle kontrol edildi (dengeli).
- [x] Import'lar: `CheckCircle`, `lazy.item`, `KitsugiImageDownloadHelper`, `MutableStateFlow`/`StateFlow`, `JSONObject` eklendi; kullanılmayan grid import'ları kaldırıldı.
- [ ] Cihaz/emülatörde derleme ve elle UI testi (sandbox'ta Java/Android SDK yok)

## Test Senaryoları (cihazda yapılacak)

1. Detay sayfası galerisi → resim indir → buton yeşil tik; tekrar dokun →
   "Bu resim zaten indirilmiş."
2. İndirmeler → Resimler → gruplara ayrık başlıklar; gruba dokun → galeride
   indirme butonu yok, başlık içerik adı.
3. İndirmeler → Resimler → resim sil → aynı resim tekrar indirilebilir.
4. Çift dokunuş → "Bu resim zaten indiriliyor."
5. Türkçe/Japonca içerik adları grup başlığında düzgün görünmeli.

## Not

Kullanıcıya zip paketi Google Drive üzerinden iletilecek (değişen dosyalar +
PLAN + TASK).
