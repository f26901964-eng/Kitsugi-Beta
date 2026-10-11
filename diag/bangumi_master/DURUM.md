# DURUM — Bangumi MASTER entegrasyonu (devir notu)

**Tarih:** 2026-10-11 · **Dal:** `arena/dadf086f-kitsugi-beta` · **Sürüm:** `2.4.245-beta`
**Kapsam:** Bangumi stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi + etiket adlarının TR & EN
yerelleştirmesi (Faz 1-4).

---

## 1) Biten işler

| Faz | Ne yapıldı | Kanıt |
|---|---|---|
| 1 — Etiketler | `BangumiTagDictionary`: **907 grup** (78 yeni anahtar, 68 mevcut gruba **95 yeni etiket**). §7.4 ekran görüntüsü boşlukları kapandı: `催淚`/`催泪`/`催涙` → `tearjerker`, `京都` → `kyoto_animation`, `AS`/`After Story` → `after_story` (`ASOBI` yakalanmaz). | doğrulayıcı: 374 etiket çözümlemesi |
| 2 — Şirket adları | `BangumiOrgDictionary` (**1013 eşleme**, 365 kayıt: 133 stüdyo / 90 yapımcı / 96 ağ / 24 yayımcı / 21 dergi) + `toLatinStudioName()` org-öncelikli + `toLocalizedOrgName()`. Dil dosyalarına **TR 436 · EN 436** kayıt eklendi. | verifier: GERÇEK rol çakışması 0 |
| 3 — Detay sayfaları | Stüdyo/yapımcı/ağ başlıkları (hero + üst çubuk), erişilebilirlik etiketi, avatar monogramı ve paylaşım metni yerelleştirildi. | `StudioDetailComponents.kt` 8 nokta, `StudioDetailPage.kt` 3 nokta |
| 4 — Doğrulama | `BangumiMasterDictionaryTest` (JUnit, CI) + `verify_bangumi_master.py` + **gerçek `kotlinc` derleme harness'ı** | `✅ TÜM KONTROLLER GEÇTİ (0 hata)` |

## 2) Bu turda düzeltilen 3 hata (ikisi kritikti)

1. **DERLEME HATASI (kritik):** yeni `g(...)` bloğu listenin son elemanı `instrumental`'ın hemen
   ardına eklendiği için arada virgül yoktu → sözlük **hiç derlenmiyordu**. Üretici artık ekleme
   öncesi önceki elemanın sonuna virgülü otomatik koyar. Bunu yalnızca `kotlinc` yakaladı;
   Python doğrulayıcı sözdizimini görmez → **her üretimden sonra harness çalıştırılmalı**.
2. **EN dosyasına TR değer (kritik):** `bangumi_tag_*` kayıtları iki dile de TR metniyle
   yazılmıştı (`Kanada`, `Çekya`, `Kültürel Etki`). Üretici düzeltildi (`values-en/` artık
   `tags.tsv` **EN** sütununu okur) → **58 değer** güncellendi. Doğrulayıcıya "dil dosyası değer
   eşitliği" kontrolü eklendi (436 kayıt TSV ile karşılaştırılıyor).
3. **Yazım:** `华语` TR değeri `Çinçe` → **`Çince` Konuşulan Bölge** (TSV + dil dosyası + sürüm notları).

## 3) Devam edilecek kısım (sırayla)

1. **Faz 4 — gerçek cihaz/CI testi.** Sözlük testini CI'da koştur (GitHub Actions; `./gradlew test`
   ya da en azından `diag/bangumi_master/harness/compile_check.sh`). Cihazda şu senaryolar:
   Clannad ~After Story~ (etiket çipleri), bir Kyoto Animation / BONES / MAPPA yapımı, TR ve EN
   arayüzle stüdyo detay sayfası başlığı.
2. **7 ⚠ `pending` ismin doğrulanması** (kaynakta görülmeden dil dosyasına **girmez**):
   Studio Gohan · Studio Nagomu · Mouse · Revootoot · Signpost · Staple Entertainment ·
   Nippon Eiga-sha. Doğrulanan satırın `status` alanı `aktif` yapılır → `gen_...py` yeniden çalışır.
3. **Commit + push + PR** (henüz yapılmadı): tüm değişiklikler bu dalda çalışma ağacında duruyor.

## 4) Bilinçli kararlar (tekrar tartışılmasın)

* **`onStudioClick` ham adla gider.** Ad, detay sayfasında kimlik doğrulaması için kullanılıyor
  (`StudioDetailViewModel.matchesExpectedStudio`); yerelleştirme yalnızca **gösterim** katmanında.
  Arama/dedup daima Latin adla yürür.
* **Uydurma çeviri yok.** Marka adlarında TR = EN; üretim komiteleri (`PROJECT-XXX`,
  `光坂高校演劇部`) hiç çevrilmez, sözlüğe alınmaz.
* **Bilinmeyen CJK etiket** olduğu gibi gösterilir (liste sonuna sıralanır) — mevcut davranış korunur.
* **Çip çiziminde kaynak taraması yok:** arama `bangumiTagResourceCache` üzerinden önbellekli.

## 5) Komutlar

```bash
python3 diag/bangumi_master/gen_bangumi_master.py            # üret (idempotent)
python3 diag/bangumi_master/gen_bangumi_master.py --check    # yazmadan denetle
python3 diag/bangumi_master/verify_bangumi_master.py         # gölgelenme/simetri/değer eşitliği
KOTLINC=/yol/kotlinc bash diag/bangumi_master/harness/compile_check.sh   # gerçek derleme
```

## 6) Bilinen sınırlar

* Bangumi DB dump'ı bu ortamdan indirilemedi; liste mevcut sözlük + resmî meta etiketler + wiki
  alan tanımları + elle derleme ile oluşturuldu. Nadir kullanıcı etiketi görüldükçe `tags.tsv`'ye
  eklenip üretici yeniden çalıştırılır.
* Sözlükte olmayan (⚠ bekleyen) CJK ad → Latin ad yoksa **olduğu gibi** kalır; bu beklenen davranış.
* `strings.xml` içindeki MASTER bloğu **üreticinin yönettiği** alandır: elle düzenleme yerine
  `orgs.tsv` / `tags.tsv` güncellenip üretici çalıştırılmalıdır (üretici artık değer değişikliğini
  de senkronlar).
