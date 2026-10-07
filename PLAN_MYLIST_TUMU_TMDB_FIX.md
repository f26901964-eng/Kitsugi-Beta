# Task Plan — Listem "Tümü" Sırası, Varsayılan Kaynak ve Simkl → TMDB Detay

**Tarih:** 2026-10-07
**Kapsam:** Listem ekranı sekme sırası, birleşik görünümde temsilci kaynak seçimi ve Simkl kayıtlarının ayrıntı sayfasının TMDB üzerinden açılması.

## Kullanıcı bildirimleri

1. "Tümü sayfası Listem sayfasında ilk kısım olmalı. En başta görünüyor ama sola doğru kaydıramıyorum; sanki Shikimori kaynağının en sağındaymış gibi duruyor liste komple."
2. "Neden Tümü'de varsayılan kaynak Simkl oldu? Dizi ve filmler için tamam ama varsayılan kaynak AniList olmalı, geri kalanı otomatik olur zaten."
3. "Simkl Tümü'de ve kendisinde, Simkl liste sayfasında ayrıntı sayfası TMDB üzerinden açılmalı. Simkl genel olarak çok yavaş çünkü veriler çok geç geliyor."

## Kök nedenler

- **Sekme sırası tutarsızlığı:** Hap butonlar `displayPlatforms = listOf(allPlatform) + platforms` ile "Tümü"yü ilk sırada çiziyordu, ancak `MY_LIST_ALL_TAB_INDEX = 5` olduğu için pager'da "Tümü" **en son sayfaydı** (Shikimori'nin hemen sağı). Bu yüzden Tümü sayfasından sola kaydırma (sonraki sayfaya geçiş) çalışmıyordu.
- **Temsilci kaydın yanlış seçilmesi:** `groupMyListEntries()` aynı başlığın kopyalarını birleştirirken temsilciyi "en yeni eklenen kayıt" (`entry.id > representative.id`) olarak seçiyordu. Simkl kayıtları genelde en son eklendiği için birleşik listede varsayılan kaynak Simkl'e dönüyordu.
- **Simkl ayrıntı zinciri:** `KitsugiDetailClient` içinde `"simkl"` dalı önce Jikan (MAL), yalnızca başarısız olursa TMDB'yi deniyordu; Simkl API'si gecikmeli yanıt verdiği için ayrıntı sayfası yavaş açılıyordu.

## Tamamlanan işler

- [x] Sekme sırası yeniden numaralandırıldı: **Tümü = 0**, AniList = 1, MAL = 2, Simkl = 3, Kitsu = 4, Shikimori = 5 (`MY_LIST_*_TAB_INDEX`, `MY_LIST_TAB_COUNT`).
- [x] `MyListTabBar` platform index'leri, `MyListScreen` pager filtreleri, giriş/senkron eşleşmeleri ve `MyListEmptyState` metinleri numara yerine bu sabitleri kullanacak şekilde güncellendi (`myListSourceMatchesTab`, `defaultMyListSourceForTab`, `myListSourceDisplayName` yardımcıları eklendi).
- [x] Kayıtlı sekme tercihi için geriye dönük geçiş: eski 5 → 0, eski 0..4 → +1 (`migrateLegacyMyListTabIndex`, `AppViewModel.loadFilters` içinde bir kez uygulanır ve prefs'e yazılır).
- [x] Yeni kurulum varsayılanı: hesap bağlıysa Tümü, hiç hesap yoksa bağlantı çağrısı görünsün diye AniList sekmesi.
- [x] Birleşik görünümde temsilci kayıt önceliği: **AniList > MAL > Kitsu > Shikimori > Simkl > TMDB** (eşitlikte en yeni kayıt). Dizi/filmler AniList'te bulunmadığı için bu türlerde doğal olarak Simkl temsilci kalır.
- [x] Tümü sekmesinde manuel kayıt varsayılanı AniList (başlık da "Manuel AniList Kaydı").
- [x] `KitsugiDetailClient` "simkl" dalı TMDB öncelikli yeniden yazıldı (`fetchSimklDetailViaTmdb`):
  1. `entry.tmdbId` ile doğrudan TMDB (Simkl import'u `ids.tmdb` alanını zaten kaydediyor),
  2. yoksa MAL ID → `KitsugiIdResolver` (ARM) ile TMDB ID çözümü,
  3. yoksa başlıkla TMDB araması (film/dizi/anime tür sırası),
  4. hepsi başarısızsa anime için Jikan (MAL) → son çare Simkl.
- [x] Özet (synopsis) yolunda da Simkl için önbellekteki gerçek ID'lerle TMDB önceliklendirildi.

## Doğrulama

- `git diff --check`: başarılı.
- Bu çalışma ortamında Android SDK/JDK bulunmadığı için derleme yapılamadı; değişen 6 dosya gerçek Kotlin grameri (tree-sitter-kotlin) ile **sözdizimi** açısından hatasız doğrulandı (0 ERROR/MISSING düğümü).
- Sekme index'i kullanan tüm çağrı yerleri `grep` ile tarandı; `tab_index` tercihine başka erişen kod bulunmadı.

## Arşiv içeriği

Bu arşiv; değişen Kotlin dosyalarını orijinal repo dizin yapılarıyla, uygulanabilir bir `changes.patch` dosyasını, bu plan/task kaydını ve repodaki diğer plan dosyalarını içerir.
