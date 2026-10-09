# Rapor — "Zaten listende var" ama listede görünmüyor (Listeye Ekle tutarsızlığı)

**Tarih:** 2026-10-09
**Kapsam:** Detay sayfası → "Listeye Ekle" → "zaten listende var" çelişkisi ve kayıt bulunamama şikâyeti (Bangumi kaynağı, CLANNAD 〜AFTER STORY〜 örneği).

## Kullanıcı bildirimi

Bangumi kaynağındaki içerik (CLANNAD 〜AFTER STORY〜, subject 876) detay sayfasında
"Listeye Ekle" butonu görünüyor; tıklanınca `"Clannad: After Story" zaten listende var.`
uyarısı çıkıyor. Kullanıcı Listem'e gidiyor ama kaydı bulamıyor.

Ekran görüntüsü iki şeyi kanıtlıyor:

1. Buton **"Listeye Ekle"** — yani sayfa, kaydın listede OLMADIĞI bilgisiyle çizilmiş.
2. Snackbar **"zaten listende var"** — yani ekleme akışı aynı anda kaydın VAR olduğunu söylüyor.

## Kök nedenler

1. **Çelişkili kimlik kontrolleri.** Buton durumu (`AppRootDetailPages` → `firstMatching(key.result)`)
   detay zenginleştirmesinden **ÖNCEKİ** sonuca bakıyor (realMalId/tmdbId henüz yok, başlık ham).
   Ekleme akışı (`AppViewModel.addApiSelectionToList`) ise **ZENGİNLEŞTİRİLMİŞ** `displayResult`'a
   bakıyor (MAL ID artık `5681`, başlık seçili dile göre "Clannad: After Story"). İkinci kontrol
   eşleşince "zaten" mesajı çıkarken buton hâlâ "Ekle" diyordu.
2. **İşe yaramaz uyarı mesajı.** `"${result.title}" zaten listende var` — GELEN başlığı söylüyordu;
   listede asıl bulunan kaydın başlığı ve hangi sekmede olduğu (AniList/MAL/Bangumi…) belirtilmiyordu.
   Kayıt başka sekmede (örn. Bangumi sayfasından eklenen kayıt `primarySource` kuralıyla AniList
   sekmesine yazılır) ya da farklı bir başlık varyantıyla saklandığı için kullanıcı bulamıyordu.
3. **Kırılgan başlık eşlemesi.** `MediaEntry.matches` yalnızca `result.title` ↔ `entry.title`
   karşılaştırıyordu. `result.title` detay sayfasında **görünen** (dile göre değişen) başlıktı;
   Bangumi'nin ayrı English/romaji/Japonca alanları yok sayılıyordu.
4. **Listem araması eksikti.** Arama yalnızca `entry.title`'a bakıyordu; İngilizce/Japonca
   alternatif başlıklar taranmıyor, "clannad after story" gibi noktalama farklı sorgular
   "CLANNAD 〜AFTER STORY〜" kaydını bulamıyordu.

## Yapılan değişiklikler

| Dosya | Değişiklik |
| --- | --- |
| `data/remote/KitsugiModels.kt` | `matches()` adım 4: tüm başlık varyantları (title/English/Japanese/romaji) `MediaIdentity.normalizedTitle` ile karşılaştırılır; yıl+tip koruması aynı. |
| `ui/app/AppViewModel.kt` | Tekrar kontrolü artık eşleşen KAYDI bulup onu isimlendirir. Kimlik çözümünden sonra (erken tıklama / tamamlanmamış zenginleştirme) İKİNCİ kontrol eklendi — çift kayıt engellenir. |
| `ui/screens/mylist/MyListLibraryGrouping.kt` | `duplicateListMessage(entry)` ortak üretici: `"…" zaten listende var (Bangumi sekmesi).` |
| `ui/screens/mylist/MyListScreen.kt` | Arama: English/Japonca başlıklar + noktalama/boşluk-insensitive normalleştirilmiş eşleşme. API arama diyaloğu tekrar uyarısı da ortak mesajı kullanır. |
| `ui/screens/detail/ApiResultDetailPage.kt` | `findExistingEntry` geri çağrısı: buton durumu, ekleme akışının kontrolüyle AYNI eşleme sonucundan beslenir. Kayıt varsa buton **"✎ Düzenle"** olur ve o kaydı açar. |
| `AppRootDetailPages.kt` | `findExistingEntry = { mediaEntries.firstOrNull { it.matches(r) } }` bağlandı. |
| `app/src/test/.../MediaEntryMatchesTest.kt` | Rapor senaryoları için birim testleri (MAL-ID çapraz eşleşme, başlık varyantı, sezon ayrımı, TMDB/stableId, negatif kontroller). |

## Yeni davranış

- Sayfada "Listeye Ekle" görünüyorsa ekleme ya **başarılı olur** ya da kimlik çözümü yeni
  bir eşleşme bulursa uyarı **bulunan kaydı** isimlendirir: `"Clannad: After Story" zaten
  listende var (AniList sekmesi).`
- Kayıt zaten listedeysе buton **"✎ Düzenle"** olur; tıklayınca doğrudan o kayıt açılır
  (kullanıcı "nerede?" sorusunun cevabını görür).
- Listem araması `"clannad after story"`, `"CLANNAD 〜AFTER STORY〜"`, `"クラナド"` vb.
  yazım farkları ve alternatif başlıklarla eşleşir.

## Doğrulama

- `git diff --check`: başarılı.
- 6 değişen + 1 yeni Kotlin dosyası parantez/blok dengesi ve söz dizimi açısından
  kontrol edildi (bu ortamda Android SDK/JDK yok; derleme yapılamadı).
- `MediaEntryMatchesTest` JUnit4 ile yazıldı; CI/yerel ortamda `:app:testDebugUnitTest`
  ile koşturulabilir.
