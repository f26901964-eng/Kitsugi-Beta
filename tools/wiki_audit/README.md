# Wiki keşif denetimi

`audit.py`, uygulamanın Fandom wiki keşif mantığının (Wikidata P4073/P6262, karakter oylaması,
ilişki yürüyüşü, isim yolu + karakter parmak izi) Python karşılığını CANLI Fandom / Wikidata / AniList
üzerinde çalıştırır. Kotlin kodunun birebir kopyası değildir; politika değişikliklerini canlı veriyle
ölçmek içindir. Her politika değişikliği Kotlin tarafında birim testiyle de kilitlenir.

- Çalıştırma: Actions → "Fandom wiki denetimi (canlı)" (sandbox'ta ağ yok). `audit.py`/workflow
  değişince `arena/**` dallarında otomatik çalışır; sonuçlar `results/` ve `run.log` olarak dala işlenir.
- Çıktı: `results/report.md` (özet + vaka tablosu + başarısız vakaların izleri), `results/results.json`.
- Modlar: `ids` (AniList/MAL/TMDB kimliği var) ve `title-only` (eklenti manga: yalnızca başlık).
- `UNVERIFIED-*`: beklenen wiki bilinmeyen vakalar; elle doğrulanmalıdır.
