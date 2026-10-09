# 📋 PLAN / TASK — Dikey Mod Kenar Taşması (Peek) + Uzun Metin Çeviri Sessiz Başarısızlığı

**Durum:** Kod tarafı TAMAMLANDI ✅ · Derleme/cihaz doğrulaması BEKLİYOR ⏳ (ortamda Android SDK yok)
**Dal:** `arena/1c502c21-kitsugi-beta` · **Tarih:** 2026-10-09
**Kullanıcı talebi:**
1. Dikey modda detay sayfalarının sağ/sol kenarlarından diğer sekmelerin köşeleri görünüyor (resimlerle işaretlendi).
2. Otomatik çeviri AÇIK olmasına rağmen çok uzun (>2000 karakter) karakter biyografisi çevrilmedi — sessiz hata şüphesi. Öneri: belli bir noktalama işaretine kadar çevir, kalanını sırayla çevir.

---

## 🎯 Sorun 1 — Dikey modda komşu sekmelerin kenarlardan görünmesi

### Kök neden
`DetailPageScaffold.kt` içindeki dikey mod `HorizontalPager`'ında
`contentPadding = PaddingValues(horizontal = 16.dp)` vardı. Compose Pager'da bu, sayfaları
ekranın tamamına değil iki yandan 16'şar dp içeride boyutlandırır; komşu sayfalar bu
boşluklardan görünür (klasik "peek" davranışı). Karakter/Personel detay sayfaları bu sorunu
yaşamıyordu çünkü orada dolgu sayfa başına uygulanıyordu.

### Çözüm (DetailPageScaffold.kt)
- Pager'dan `contentPadding` **kaldırıldı** → sayfalar tam ekran genişliğinde; komşu sekmeler
  `clipToBounds` sayesinde tamamen ekran dışında.
- 16dp yatay dolgu sayfa içine taşındı: `Box.padding(horizontal = 16.dp)` → görünüm birebir
  korundu, kenarlardan taşma kalktı.
- `pageHeights` ölçümü (`onGloballyPositioned`) Column üzerinde kaldığı için yükseklik
  interpolasyonu davranışı değişmedi.
- Kapsam: `ApiResultDetailPage` + `MediaEntryDetailPage` (ikisi de paylaşılan Scaffold'u kullanır).

---

## 🎯 Sorun 2 — Uzun metin (>2000 kr) çevrilmiyordu (sessiz başarısızlık)

### Kök neden (kodda doğrulandı)
`TranslationManager.splitIntoChunks` yalnızca **paragraf (`\n\n`) ve satır (`\n`)** sınırlarından
bölebiliyordu. Karakter biyografileri HTML'den düz metne çevrilirken sık sık **satır sonu
içermeyen tek dev paragraf** olarak geliyor → 3000-7000 karakterlik metin TE parça olarak
Google gtx ucuna gönderiliyor → uç reddediyor (4xx) → `fetchSingleChunk` orijinal metni
döndürüyordu → `translated == trimmed` → **çeviri yok, hata da yok** (tam kullanıcının gördüğü
belirti). Ek zayıflık: 429/5xx sonrası uygulama seviyesinde retry yoktu; kısmî başarı
ayırt edilemiyordu.

### Çözüm (TranslationManager.kt — kullanıcının noktalama önerisi uygulandı)
1. **Cümle sonundan bölme (yeni `splitOversizedLine`):** Sınırı aşan tek satır önce cümle
   sonlarından (`. ! ? … 。！？；`) bölünür. Cümle sonu sayılması için noktalamadan sonra
   boşluk/satır sonu şartı eklendi → `3.14`, `Mr.` gibi kısaltmalar bölünmez.
   Tek cümle bile sınırdan uzunsa kelimeden, kelime devasaysa sert kesilir.
2. **4 kademeli bölme önceliği:** paragraf → satır → cümle → kelime. Hiçbir parça 2000 kr'yi aşmaz.
3. **Başarısızlık artık tespit edilebilir:** `fetchSingleChunk` hata durumunda **null** döner;
   HTTP kodu ve parça uzunluğu loglanır (sessizlik bitti).
4. **Uygulama seviyesinde retry:** `fetchSingleChunkWithRetry` — parça başına 3 deneme,
   600ms/1200ms geri çekilme (OkHttp `RetryInterceptor` 429/5xx'i zaten deniyor; bu ek
   katman kalıcılaşan geçici hatalar için).
5. **Kademeli çeviri akışı (kullanıcı talebi):** `translateTo(..., onPartial)` — her parça
   çevrildikçe UI'ya "çevrilen kısım + henüz çevrilmemiş özgün kuyruk" gönderilir; metin
   büyüyüp küçülmez, dil kademeli dönüşür.
6. **Kısmî sonuç korunur:** Bir parça kalıcı başarısızsa çevrilen kısımlar ekranda kalır
   (karışık dil), tam metin dönerken "çevrilemedi" sayılır.
7. **Önbellek hijyeni:** Room ve DetailCache'e **yalnızca tam başarı** yazılır — yarım çeviri
   asla kalıcılaşmaz, sonraki açılışta tam çeviri yeniden denenir.

### ViewModel bağlantıları (onPartial akışı)
- `CharacterDetailViewModel` — biyografi (otomatik + manuel Çevir düğmesi)
- `StaffDetailViewModel` — biyografi (otomatik + manuel)
- `ApiResultDetailViewModel` — özet (otomatik + manuel)
- `MediaEntryDetailViewModel` — özet (otomatik + manuel)

Diğer çağıranlar (`translateToTurkish` tek argüman) varsayılan parametreyle uyumlu, değişiklik gerektirmez.

### Doğrulama (ortamda Android SDK olmadığından)
- Bölme algoritması Python'a birebir taşınıp 8 uç durumla test edildi: kısa metin, çok
  paragraf, **7000 kr tek paragraf (kullanıcı vakası → 4 parça, hepsi cümle başında)**,
  ondalık/kısaltma korunumu, 4100 kr tek cümle (kelime bölmesi), aralıksız CJK 3000 kr
  (sert kes), paragraf+satır karışımı, Japonca noktalama → **TÜM TESTLER GEÇTİ**, içerik
  kaybı yok, parça limiti hiçbirinde aşılmadı.

---

## 🔍 Bilinen sınırlar / notlar
- Çok uzun tek paragraflar çeviri sonrası parça sınırlarında paragraf arası boşlukla
  görünebilir (parçalar `\n\n` ile birleştiriliyor) — okunabilirlik açısından kabul edildi.
- Markdown link maskesi (`⟦L_n_START⟧`) cümle sınırına denk gelirse kademeli gösterim
  sırasında kısa süre kaba görünür; nihai sonuç düzgün (unmasker yedek yolu var).
- Ortam kaynaklı dosya senkronizasyon takılmaları nedeniyle tüm düzenlemeler `git diff`
  ile parça parça doğrulandı; paralel aynı-dosya düzenlemeleri yarışa yol açtığından
  eksik kalanlar Python (deterministik) ile yeniden uygulandı.

## 📁 Değişen dosyalar
| Dosya | Değişiklik |
|---|---|
| `ui/screens/detail/DetailPageScaffold.kt` | contentPadding → sayfa içi padding (peek fix) |
| `data/local/TranslationManager.kt` | cümle bölmesi, null-hata, retry, onPartial, önbellek hijyeni |
| `ui/screens/detail/CharacterDetailViewModel.kt` | biyografi kademeli akış (2 nokta) |
| `ui/screens/detail/StaffDetailViewModel.kt` | biyografi kademeli akış (2 nokta) |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | özet kademeli akış (2 nokta) |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | özet kademeli akış (2 nokta) |

## ✅ Cihazda test listesi
- [ ] Dikey modda medya detay sayfası: kenarlardan komşu sekme görünmüyor, yatay kaydırma çalışıyor
- [ ] Dikey modda liste detay sayfası: aynı kontrol
- [ ] Yatay mod: sol panel + sağ sekmeler düzeni değişmedi
- [ ] Uzun biyografili karakter (ör. AniList/MAL kaynaklı, >2000 kr): otomatik çeviri akarak geliyor
- [ ] Çeviri başarısızken (uçak modu): özgün metin kalıyor, sonraki açılışta tekrar deneniyor
- [ ] Kısa metinler: davranış değişmedi (Room önbelleğiyle anında)
