# PLAN / TASK — Detay üst çubuğu, haftalık takvim ve yükleme iskeletleri

Tarih: 2026-10-11
Dal: `arena/5dbbf6b1-kitsugi-beta`

## İstek ve kapsam

- Stüdyo/yapımcı detayında kayan başlık ile eylemler, özgün filtre/görünüm kontrolleri hâlâ görünürken açılmamalı; geçiş yumuşak olmalı.
- Kayan başlıkta, ana detay görünümündeki paylaşım işlevi bulunmalı.
- “Bu Hafta Yayında” ızgara/liste kartları “Tümünü Gör” görünümüne yaklaşmalı ve ekran genişliğine uyum sağlamalı.
- Veri yüklenirken boş ekran veya alakasız tam-ekran animasyon yerine ilgili alanın biçimine yakın yer tutucular gösterilmeli.

## Tamamlanan görevler

- [x] `StudioDetailPage`: kontrol satırının gerçek görünürlük/konumuna göre kayan başlığı aç; giriş/çıkış için fade sürelerini ayarla; aynı stüdyo paylaşım URL’sini kayan başlığa ekle.
- [x] Haftalık takvim ızgarasını ortak duyarlı kolon hesabına bağla; yatayda afiş + bilgi kartı, dikeyde afiş kartı kullan; liste afiş ve metin ölçülerini güncelle.
- [x] Takvim yükleme iskeletini seçili ızgara/liste görünümünün kolon, kart ve afiş ölçüleriyle eşleştir; ilk açılıştaki boş durum parlamasını engelle; yeniden denemede seçili kaynağı koru.
- [x] Stüdyo ayrıntısı için hero/hakkında/kontrol/yapım kartlarının yerleşimini izleyen yükleme iskeleti ekle.
- [x] Medya, karakter ve personel ayrıntılarının ilk yüklemesinde ortak hero/sekme/içerik iskeleti kullan.

## Doğrulama ve açık noktalar

- [x] `git diff --check` temiz.
- [x] Değişen Kotlin dosyaları sözdizimi ayrıştırıcısından hatasız geçti.
- [ ] Gradle/Android derleme ve cihaz üzerinde görsel-etkileşim testi: çalışma ortamında Java/JDK bulunmuyor.
- [ ] Uygulamadaki bu ekranlar dışındaki bütün yükleme durumlarının ayrı ayrı görsel denetimi; bu çalışma tüm uygulama için eksiksiz bir denetim değildir.

## Arşiv

ZIP içinde bu plan dahil kökteki `PLAN_*.md` ve `TASK_*.md` belgeleri ile bu işte değiştirilen kaynakların **tam dosya içerikleri** bulunur. Arşiv, Git deposunun `.git` dizinini veya bağımlılık/derleme çıktılarını içermez.
