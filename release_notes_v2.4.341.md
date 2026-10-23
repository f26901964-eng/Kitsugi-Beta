# Kitsugi Beta v2.4.341 — Arama ortak taksonomi kataloğu

**Tarih:** 2026-10-21 · **Dal:** `arena/dac8a4b1-kitsugi-beta`

## Türkçe

- Arama tür/etiket sayfasındaki sabit 24 tür ve ~170 etiket kaldırıldı. Ortak
  katalogdan **114 tür, 1.325 etiket** yükleniyor; listeler tembel çiziliyor.
  `aksiyon`, `action` ve `экшен` aynı `Action` çipini bulur. Seçili sayaç ve
  **Tümünü temizle** eklendi.
- **9.457 Latin kurum adı** için rol bazlı seçici: stüdyo, yapımcı, yayıncı,
  kanal ve dergi. Takma adlar (CJK dâhil) yalnızca arama girdisinde kullanılır;
  çipler TR/EN'de aynı Latin adla çizilir. Son seçimler yerel olarak hatırlanır.
- `SearchFilters.organizations` varsayılan boş; önceki filtre ayarları korunur.
  Kurum seçimi **henüz sonuçları filtrelemez** — kaynak bazlı eşleme sonraki
  sürümün T55–T60 işidir. Arayüz beklemeyi görünür biçimde bildirir; yalnızca
  kurum seçerek boş arama başlatılmaz.
- Üretici: `scripts/gen_taxonomy_catalog.py` (`--check`),
  `scripts/check_search_catalog.py --check`, Python regresyon testleri ve CI.
  v2.4.340'ın tamamlanmış ad haritası mevcut Bangumi VAULT TSV mimarisiyle
  eşitlendi; kurum/etiket senkronu artık Kotlin içi dev listelere bağlı değil.
- Ham JSON **1.200.090 bayt**, gzip **272.692 bayt** (APK sıkıştırması cihazda
  ayrıca doğrulanmalı). Cihaz derlemesi bu sandbox'ta JDK/Gradle olmadığı için
  yapılmadı; Kotlin sözdizimi tree-sitter ve kaynak/parite denetimleriyle geçti.

## English

- The search facet now reads **114 genres and 1,325 tags** from one generated,
  lazily displayed taxonomy catalog instead of hardcoded lists. Turkish,
  English, and Russian aliases resolve to the same chip. Selected counts and
  Clear all are available.
- A role-based picker searches **9,457 Latin organization names**. Original
  source spellings are search-only aliases; proper names stay identical in
  Turkish and English. Recent selections are remembered locally.
- `SearchFilters.organizations` defaults to empty. **Provider-side organization
  filtering is not active yet** (T55–T60); the UI explicitly says so and does
  not launch a query for an organization selection alone.
- Catalog generation and read-only CI checks added; the existing Bangumi VAULT
  TSV sources were reconciled with the completed v2.4.340 Latin mappings.
  1,200,090 raw JSON bytes compress to 272,692 gzip bytes. Android build and
  device behavior remain unverified without JDK/Gradle in this environment.

**Checks:** `gen_taxonomy_catalog.py --check`, `check_search_catalog.py --check`,
`test_gen_taxonomy_catalog.py`, TR/EN parity, taxonomy master, 35 fields × 7
sources audit, both tag sync `--check` commands, Latin-org sync `--check`, CJK
UI check, tree-sitter Kotlin parse. Provider E2E tests are pending T55–T60.
