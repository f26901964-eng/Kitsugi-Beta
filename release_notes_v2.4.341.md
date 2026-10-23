# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 📅 Listem Sıralaması Baştan Yazıldı — "Son Eklenen" Artık Gerçekten Son Eklenen
- **Gerçek eklenme tarihi:** Kayıtlara `addedAt` alanı eklendi (veritabanı v30). AniList, Kitsu ve Shikimori'de listenize eklediğiniz gerçek tarih kullanılır; önceden sıralama, içe aktarım sırasından ibaret olan yerel satır numarasına bakıyordu.
- **Sıralama artık kazanıyor:** Bir sıralama seçtiğinizde (Son eklenen, Puan, Ada göre…) liste tek akışta ve **global** sıralanır. İzleniyor/Tamamlandı/Planlandı bölümleri artık seçtiğiniz sırayı ezmiyor; bölümlü gezinme yalnızca "Varsayılan" sıralamada gösterilir.
- **Son eklenen'e özel görünüm:** Liste dönem başlıklarıyla açılır — Bugün, Dün, Bu Hafta, Bu Ay, Son 3 Ay, Bu Yıl, Daha Eski…
- **Her seçeneğe kendi mantığı:** İlerleme artık tamamlanma oranına (%92, %50…) göre; puansız ve tarihsiz kayıtlar her yönde sonda; "Ada göre" ekranda gördüğünüz (başlık diline göre) başlığı sıralar; Favoriler içinde en yeni favori üstte.
- **Zaman birimi düzeltmesi:** Bangumi'nin milisaniye, diğerlerinin saniye yazdığı güncelleme zamanları normalize edildi; Simkl kayıtları da artık son izleme zamanını taşıyor.
- Aynı düzeltmeler başka kullanıcıların liste ekranına ve TV kütüphanesine de yansıtıldı.
- **Not:** Güncellemeden sonra bir kez senkronizasyon yapmak eklenme tarihlerini kaynaklardaki gerçek değerlerle netleştirir. Görünümünüzün aniden değişmemesi için kayıtlı eski "Son eklenen" seçimi bir kerelik "Varsayılan"a alınır; istediğiniz an Filtre → Sıralama'dan yeniden seçebilirsiniz.

---

## 🇬🇧 English

### 📅 My List Sorting Rewritten — "Recently Added" Is Now Really Recently Added
- **Real add dates:** Entries now carry an `addedAt` timestamp (database v30). AniList, Kitsu, and Shikimori provide the actual date you added a title; sorting previously used the local row id, which is just import order.
- **Your sort wins:** Picking any sort (Recently added, Score, Title…) now produces one flat, globally ordered list. Status sections (Watching/Completed/Planning…) no longer override it; sectioned browsing remains under the new "Default" sort.
- **Dedicated Recently-added view:** The list is headed by period sections — Today, Yesterday, This Week, This Month, Last 3 Months, This Year, Earlier…
- **Per-option logic:** Progress now sorts by completion ratio; unrated/dateless entries always sink to the end; Title sorts by the displayed (title-language) name; within Favorites, newest first.
- **Timestamp normalization:** Millisecond (Bangumi) and second-based update times are unified; Simkl entries now carry their last-watched time.
- The same fixes apply to other users' list screens and the TV library.
- **Note:** Running one sync after updating backfills exact add dates from providers that expose them. To avoid a sudden layout change, a previously saved "Recently added" selection is moved to "Default" once; re-select it anytime under Filter → Sorting.
