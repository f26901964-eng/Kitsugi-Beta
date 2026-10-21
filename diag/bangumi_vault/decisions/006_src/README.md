# Chunk 006 — karar kaynakları (yeniden üretilebilirlik)

`scratch_006/` çalışma klasörü git dışında tutulur; **kararların kaynağı** buradadır.
`dec_006_<bant>.tsv` dosyaları kanon+map'i üreten **asıl** karar tablolarıdır;
`gen.py`/`auto.py`/`manual/*.py` bunların nasıl üretildiğini gösterir.

## İçerik

| Dosya | İşlev |
|---|---|
| `records_006_tags.json` | 20–99 bandındaki **4.027 kaydın dondurulmuş hâli** (key, count, orijinal ad, labels). Damgalama sonrası `tags.json`'daki `tr` çeviriye döndüğü için indeksler bu dosyaya sabitlenir. |
| `band_a.txt … band_d.txt` | bant başına `index  count  orijinal ad` (worklist sırası) |
| `auto.py` | mekanik sınıflar: yıl/dönem, puan/sayı, doujin etkinliği, raf/durum, işaret, tek karakter, gürültü, belirsiz kısaltma → SKIP; Latin adlar için normalize eşleşme / değer ikizi / as_is |
| `manual/a.py … manual/d.py` | elle kararlar (kanon/global/new/as_is gerekçeleriyle) |
| `gen.py` | `auto.py` + `manual/<bant>.py` → `dec_006_<bant>.tsv` |
| `dec_006_a.tsv … dec_006_d.tsv` | **üretilmiş karar tablosu** (`i`, `canon_id|SKIP`, `TR`, `EN`, `origin`, `not`) |

## Bantlar

| Bant | count | kayıt |
|---|---|---:|
| a | 60–99 | 625 |
| b | 40–59 | 884 |
| c | 30–39 | 913 |
| d | 20–29 | 1.605 |

## Yeniden üretim

```bash
python3 diag/bangumi_vault/decisions/006_src/gen.py a     # b, c, d
python3 diag/bangumi_vault/scripts/build_tag_chunk.py \
    --chunk 006_a --min-count 60 --max-count 99 \
    --decisions diag/bangumi_vault/decisions/006_src/dec_006_a.tsv \
    --snapshot diag/bangumi_vault/decisions/006_src/records_006_tags.json
```

`--snapshot` verilmezse `tags.json`'daki **kararsız** kayıtlar kullanılır; 006 damgalandığı
için o küme artık boştur. Bu yüzden snapshot zorunludur.

**Bilinen sınırlama:** `gen.py` bugün yeniden koşulduğunda `cid`, `TR`, `EN` **birebir
aynı** çıkar; ancak kanon ve `BangumiTagDictionary` artık 006'nın kendi satırlarını da
içerdiği için birkaç düzine Latin adın `origin` etiketi `as_is` yerine `existing`
görünebilir (değer aynıdır). Kanon/map'i **bayt bayt** yeniden üretmek için yukarıdaki
`dec_006_*.tsv` dosyalarını kullanın; onlar dondurulmuş karar tablolarıdır.
