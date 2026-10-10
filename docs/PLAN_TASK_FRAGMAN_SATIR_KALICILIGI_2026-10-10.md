# PLAN / TASK — Fragman (Ön İzleme) Satır Kalıcılığı — Oynatıcı Açıkken Üstteki Kart Görünür Kalsın

**Tarih:** 2026-10-10
**Dal:** `arena/04d731c2-kitsugi-beta`
**Commit:** `db27fa8` — "Fragman kartı: oynatıcı açıkken üstteki satır kartı da görünür (açılış/kapanış satırlarıyla aynı davranış)"
**Durum:** Uygulandı (derleme yapılmadı — ortamda Java/Android SDK yok; sözdizimi ve parantez dengesi statik doğrulandı)

---

## İstek

Açılış/kapanış müzikleri bölümünde bir video açıldığında, şarkı satırı kartı
(ikon + başlık + "Uygulama içinde oynat") oynatıcının **üstünde görünür kalıyor**.

Ancak **Ön İzleme** kartında "Fragmanı İzle" satırı, fragman oynatıcısı açılınca
**kayboluyordu** — geriye yalnızca "Ön izleme" başlığı ve boş bir alan kalıyordu.

**İstenen davranış:** Fragman oynatıcısı açıldığında da, açılış/kapanış
müziklerindeki satır kartının görünümü ile **aynı şekilde** bir satır kartı
oynatıcının üstünde görünsün. Bu davranış **tüm detay sayfalarında** geçerli olsun.

---

## Yapılan

### `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`

`KitsugiTrailerCard` yeniden düzenlendi — satır artık koşulsuz olarak gösterilir
(`KitsugiThemesList`'teki tema satırı düzeniyle birebir aynı yapı):

1. **Satır kartı her zaman görünür** (eski kodda `if (!showPlayer)` içindeydi):
   - **Kapalıyken:** dolu turuncu dairede ▶ oynat ikonu, "Fragmanı İzle" +
     "Uygulama içinde oynat" (eski görünüm aynen korundu).
   - **Açıkken (aktif):** dolu turuncu dairede yukarı ok (chevron),
     "Fragmanı İzle" başlığı turuncu vurgu renginde, satır arka planı
     `accent %15` — temalardaki aktif satır kartıyla aynı görünüm.
2. **Satıra dokunma = aç/kapat (toggle):** satır oynatıcıyı açar; açıkken
   tekrar dokununca kapatır (tema satırlarındaki davranışın aynısı).
3. **Oynatıcı satırın altında** açılır (`AnimatedVisibility` ile) — müzik
   videolarındaki düzenin aynısı: satır (üstte) → oynatıcı → "Kapat" düğmesi.
4. "Kapat" düğmesi oynatıcının altında korundu.

### Kapsam — tüm detay sayfaları

`KitsugiTrailerCard` **ortak bileşendir**; kullanım yerleri:

- `ApiDetailTabContents.kt` → API/arama kaynaklı detay sayfası (anime + manga)
- `EntryDetailTabContents.kt` → listeden açılan medya detay sayfası (anime + manga)

Her iki detay sayfası tipi de aynı bileşeni kullandığı için değişiklik
**otomatik olarak tüm detay sayfalarında geçerlidir**.

---

## Doğrulama

- Kotlin kaynak dosyasında parantez/sözdizimi dengesi statik olarak doğrulandı
  (110 `{` / 110 `}`).
- Gradle derlemesi bu ortamda çalıştırılamadı (Java + Android SDK yok);
  değişiklik yalnızca mevcut composable gövdesinin yeniden düzenlenmesidir,
  yeni import gerekmez (tüm ikonlar/importlar zaten mevcuttu).
