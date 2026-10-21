# Kitsugi Beta v2.4.337

## 🇹🇷 Türkçe

### 🧩 Tüm Kaynaklarda Ortak Tür / Etiket / Stüdyo–Yapımcı–Yayıncı Birleşimi
- **Artık “birini kullan, diğerini at” yok — gerçek birleşim (`KitsugiTaxonomyMerge`):**
  - Detay sayfası hangi kaynaktan açılırsa açılsın (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu, Simkl, TMDB) türler, etiketler ve kurumlar **tüm kaynaklardan toplanıp birleştiriliyor**. Eskiden birincil kaynakta dolu olan alan ikincil kaynağın verisini tamamen eziyordu (`if (dolu) bunu else diğerini`); bu yüzden birçok yapımda tür/etiket ve stüdyo bilgisi gözden kaçıyordu.
  - Birleştirme **diller arası kanonik anahtar** ile tekilleştiriyor: `Action` · `Экшен` · `アクション` · `动作` · `Aksiyon` artık **tek çip**. `Повседневность` ≡ `Slice of Life` ≡ `日常` ≡ `Günlük Yaşam`.
- **Rusça (Shikimori) tür ve etiket sözlüğü (`RussianTaxonomyDictionary`, 114 grup):**
  - Shikimori'nin **79 resmî türünün tamamı** + yaygın Rusça etiketler TR/EN karşılıklarıyla eşlendi; MAL tür kimlikleri de saklanıyor, beklenmedik yazımlar kimlikten çözülüyor.
  - Sözlükte olmayan Kiril metni **otomatik Latin harflerine çevriliyor** (`Повседневность` → `Povsednevnost`). **Ekranda Rusça görünmüyor.**
- **Çince/Japonca sızıntısı kapatıldı:** sözlükte karşılığı olmayan Çince/Japonca etiket artık çip listesine **alınmıyor** (modelde korunuyor, arama bozulmuyor).
- **Çapraz kaynak toplayıcı (`KitsugiTaxonomySources`):**
  - AniList hafif sorgusuna `genres` ve `studios { edges { isMain node { id name } } }` eklendi; ayrıca MAL/Jikan'dan `genres`, `themes`, `demographics`, `studios`, `producers`, `licensors` alınıyor. Hepsi **paralel** ve **zaman tavanlı** (kaynak başına 4,5 sn, toplam 6 sn) — geciken kaynak atlanır, sayfa asıl veriyle açılır.
  - `realMalId` yalnızca gerçek MAL kimliği aralığında (`1..99.999.999`) kullanılıyor; Bangumi'nin 500M+ stable id'leri ve Shikimori/Kitsu kimlik uzayları karışmıyor.
- **Stüdyo / yapımcı / yayıncı / kanal / dergi eksiksiz toplama:**
  - Kurumlar Latin ada göre rol-farkında tekilleştiriliyor; aynı kurum birden fazla kaynakta geçiyorsa **kimliği olan (tıklanabilir)** kayıt tercih ediliyor.
  - Aynı şirket hem “Stüdyolar” hem “Yapımcılar” altında görünmüyor (`dropProducersThatAreStudios`).
  - Bangumi'nin infobox kurumları (`动画制作`, `製作`, `播放电视台`, `出版社`, `连载杂志`) MAL/AniList/TMDB kurumlarıyla **birlikte** korunuyor.
- **TR/EN dil dosyası uyumu:** 114 yeni `taxonomy_tag_*` kaynağı iki dilde üretildi; çeviri ekibi kodu değiştirmeden düzeltebilir (çalışma zamanında dil dosyası sözlükten öncelikli).
- **Yan bulgu — veri kaybettiren betik düzeltildi:** `sync_bangumi_tag_strings.py` işaretçiden sonraki tüm dosyayı silip yeniden yazıyordu (~8.800 satır: kurum kaynakları, wiki, stream). Artık bloğu **yerinde** yeniden yazar. Bu sayede daha önce hiç dil dosyasına yazılmamış **494 Bangumi etiketi** eklendi ve sözlükte olmayan artık kaynak (`bangumi_tag_chinese_speaking`) temizlendi.
- **Doğrulama:** yeni `scripts/check_taxonomy_master.py` (K1–K9 denetimi) + `diag/taxonomy_master/worklist.tsv` + `TaxonomyLabelsTest` (23 birim test). Dil dosyası eşitliği (`17.795 string`) ve iki üretecin idempotentliği bu turda yeşil.

---

## 🇬🇧 English

### 🧩 Unified Genres / Tags / Studios–Producers–Publishers Across All Sources
- **No more "use one, drop the other" — real merging (`KitsugiTaxonomyMerge`):**
  - Regardless of which source opens the detail page (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu, Simkl, TMDB), genres, tags and organisations are now **collected from every source and merged**. Previously a non-empty primary field completely replaced the secondary source's data, so many titles silently lost genres, tags and studio info.
  - Merging deduplicates via a **language-independent canonical key**: `Action` · `Экшен` · `アクション` · `动作` · `Aksiyon` collapse into a **single chip**.
- **Russian (Shikimori) genre & tag dictionary (`RussianTaxonomyDictionary`, 114 groups):**
  - Covers **all 79 official Shikimori genres** plus common Russian tags with TR/EN labels; MAL genre IDs are stored as a fallback resolution path.
  - Unknown Cyrillic labels are **transliterated to Latin automatically** — Russian text never reaches the screen.
- **Chinese/Japanese leakage closed:** labels that cannot be resolved are no longer rendered as chips (kept in the model so search still works).
- **Cross-source collector (`KitsugiTaxonomySources`):** AniList's lightweight query now also requests `genres` and `studios`; MAL/Jikan supplies `genres`, `themes`, `demographics`, `studios`, `producers`, `licensors`. Everything runs **in parallel** with a hard time budget (4.5 s per source, 6 s total).
- **Complete studio / producer / publisher / network collection:** role-aware deduplication by Latin name, preferring the entry **with an ID** (clickable studio page); a company no longer appears under both Studios and Producers.
- **TR/EN parity:** 114 new `taxonomy_tag_*` resources in both locales, editable without code changes.
- **Bonus fix — data-loss bug:** `sync_bangumi_tag_strings.py` used to truncate everything after its marker (~8,800 lines). It now rewrites the block in place, which also landed **494 previously unwritten Bangumi tags** and removed a stale orphan resource.
- **Verification:** new `scripts/check_taxonomy_master.py` (K1–K9), `diag/taxonomy_master/worklist.tsv`, and `TaxonomyLabelsTest` (23 unit tests).
