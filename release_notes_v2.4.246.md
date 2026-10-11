# Kitsugi-Beta v2.4.246 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🏢 1. Bangumi Master Şirket & Etiket Yerelleştirmesi (TR / EN)
- **Kapsamlı Şirket Sözlüğü (`BangumiOrgDictionary.kt`):** 1013 eşleme ve 365 kayıt ile stüdyo (133), yapımcı (90), yayıncı ağ (96), yayımcı (24) ve dergi (21) adları için küresel Latin ve yerelleştirilmiş kaynak eşlemeleri eklendi.
- **Genişletilmiş Etiket Sözlüğü (`BangumiTagDictionary.kt`):** 907 gruba ulaşıldı (78 yeni anahtar, 95 yeni etiket varyantı). `催淚`/`催泪` (tearjerker), `京都` (Kyoto Animation), `AS`/`After Story` gibi özel etiket boşlukları tamamen kapatıldı.
- **Detay Sayfaları & Çip Entegrasyonu:** Stüdyo, yapımcı ve yayıncı ağ adları detay sayfası bilgi kartlarında, filtre çiplerinde ve stüdyo profil sayfalarında (`toLocalizedOrgName()`) arayüz diline (TR/EN) uygun gösterilir. Monogram avatar, erişilebilirlik etiketi, paylaşım metni ve üst çubuk yerelleştirildi.
- **Dil Dosyaları & Doğrulama:** `strings.xml` ve `values-en/strings.xml` içine 436 yeni simetrik kaynak eklendi. İngilizce dosyadaki 58 hatalı Türkçe değer düzeltildi ve "Çinçe" yazımı "Çince Konuşulan Bölge" olarak güncellendi.
- **Doğrulama & Test:** `BangumiMasterDictionaryTest` birim testleri ve `verify_bangumi_master.py` doğrulama altyapısı eklendi (0 rol çakışması, tam TSV uyumu).

---

## 🇬🇧 English

### 🏢 1. Bangumi Master Org & Tag Localization (TR / EN)
- **Comprehensive Org Dictionary (`BangumiOrgDictionary.kt`):** Implemented 1013 mapping lines across 365 distinct organizations: studios (133), producers (90), broadcast networks (96), publishers (24), and magazines (21) with standardized global Latin names.
- **Expanded Tag Dictionary (`BangumiTagDictionary.kt`):** Scaled to 907 canonical tag groups (78 new keys, 95 new variant aliases). Resolved missing Chinese/Japanese aliases such as `催淚`/`催泪` (tearjerker), `京都` (Kyoto Animation), and `AS`/`After Story`.
- **Detail Pages & Chip Integration:** Organization names in anime details, chips, and dedicated studio detail pages now render through `toLocalizedOrgName()` matching the UI language (TR/EN). Monogram initials, accessibility tags, share descriptions, and top bars are fully localized.
- **Resource Symmetry & Cleanups:** Added 436 symmetric string entries across both `values/strings.xml` and `values-en/strings.xml`. Fixed 58 Turkish entries that previously leaked into English resources, and corrected "Çinçe" to "Çince Konuşulan Bölge".
- **Harness & Verification:** Added `BangumiMasterDictionaryTest` JUnit suite and `verify_bangumi_master.py` validator ensuring zero ambiguous role conflicts and 1:1 TSV parity.
