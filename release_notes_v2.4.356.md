# Kitsugi Beta v2.4.356 — Listem Sıralaması: Kaynak Bazlı Düzeltme (2026-10-24)

## Listem / "Son eklenen"

- **Simkl** artık gerçek eklenme zamanını kullanıyor: `/sync/all-items`
  yanıtındaki `added_to_watchlist_at` okunuyor. Önceden `last_watched_at`
  vekil olduğu için bir bölüm izlemek diziyi "listeye yeni eklenmiş" gibi en
  üste taşıyordu.
- `addedAt` alanı boş kalmış satırlar Listem açılışında **onarılır**: eldeki
  tek zaman damgası (`updatedAt`) kullanılarak damgalanır ve satırlar
  "Eklenme Tarihi Bilinmiyor" kovasında birikmez. Düzeltilecek satır yoksa
  hiç yazım yapılmaz.
- "Son eklenen / İlk eklenen" karşılaştırıcısı yeniden yazıldı:
  - zamanı bilinmeyenler iki yönde de **sonda**,
  - bilinmeyenler kendi aralarında **ada göre** (yerel `id` karışımı bitti),
  - `updatedAt` artık sıralamayı bozan bir kırıcı değil.
- Sıralama ve dönem başlıkları **aynı** "etkin eklenme zamanı" çözümleyicisini
  kullanır: bir satır bir sekmede tarihli, "Tümü"de bilinmiyor olamaz.
- "Tümü" kartının zamanı da aynı çözümleyiciden gelir (kopyalar arasında en
  erken bilinen değer = listeye ilk giriş anı).

## Kaynak kaynak eklenme zamanı

| Kaynak | Alan | Nitelik |
|---|---|---|
| AniList | `createdAt` | gerçek |
| Kitsu | `attributes.createdAt` | gerçek |
| Shikimori | `created_at` | gerçek |
| Simkl | `added_to_watchlist_at` | gerçek (yeni) |
| MAL | API'de yok → ilk görülme (`updated_at`) | vekil |
| Bangumi | API'de yok → ilk görülme | vekil |
| Elle eklenen | ekleme anı | gerçek |

## Notlar

- DB şeması değişmedi (v30). Yeni göç gerekmez.
- Ayrıntılı kök neden analizi:
  `COZUM_RAPORU_LISTEM_SIRALAMA_KAYNAK_BAZLI_2026-10-24.md`
- Önceki düzeltme (v2.4.355): `COZUM_RAPORU_LISTEM_SIRALAMA_SON_EKLENEN_2026-10-23.md`
