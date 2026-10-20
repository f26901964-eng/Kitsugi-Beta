# Bangumi VAULT — Exhaustive (2026-10-20)

Bu klasör, **Bangumi (bgm.tv) + AniList + MAL + Kitsu + Shikimori** kaynaklarından
gelecek **TÜM** stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi / komite / kişi / etiket / tür
değerlerinin **tek gerçek kaynağıdır (single source of truth)**.

Her değer burada **bir kez** kaydedilir, Google Drive'daki
`Kitsugi-Bangumi-VAULT-EXHAUSTIVE-2026-10-20` klasörüyle birebir senkron tutulur,
ve uygulamanın `values/strings.xml` (TR) + `values-en/strings.xml` (EN) dil dosyalarına
otomatik senkron edilir.

## Neden VAULT?

`diag/bangumi_master/` hattı elle derleme + sınırlı TSV (365 şirket) içeriyordu.
Kullanıcı talebi: **eksik tek bir etiket/yapımcı bile kalmamalı**.
Bu VAULT, **exhaustive harvesting** ile o boşluğu kapatır.

## Yapı

```
diag/bangumi_vault/
  README.md
  manifest.json          # sürüm, istatistik, son hasat tarihi, Drive klasör ID
  vault.json             # TEK DOSYADA TÜM KAYITLAR (uygulama bu dosyayı okumaz; arşiv)
  studios.json           #  stüdyolar
  producers.json         #  yapımcılar (şirket + komite + kişi)
  networks.json          #  yayıncı ağlar
  publishers.json        #  yayımcılar
  magazines.json         #  dergiler
  tags.json              #  etiketler (Bangumi + AniList birleşik)
  genres.json            #  türler (AniList/MAL Jikan)
  committees.json        #  yapım komiteleri (PROJECT-xxx, 光坂高校演劇部 vb.)
  raw_harvest/
    harvest_2026-10-20.json   # ham hasat dökümü (subject_id → infobox + tags)
  scripts/
    harvest_bangumi_exhaustive.py
    vault_to_strings.py
    sync_to_drive.py
```

## Drive Senkronu

Drive klasörü: https://drive.google.com/drive/folders/1pZAOYsAnMjveNK6grdXjqV9IDM1TFaJp

Yerel `diag/bangumi_vault/*.json` dosyaları Drive'a şu script ile itilir:

```bash
python3 diag/bangumi_vault/scripts/sync_to_drive.py --push
python3 diag/bangumi_vault/scripts/sync_to_drive.py --pull
```

CI, `vault.json` ile `values*/strings.xml` anahtar kümelerinin simetrik olduğunu doğrular.

## Dil Dosyası Sözleşmesi

Her VAULT kaydı:

```json
{
  "key": "bangumi_studio_kyoto_animation",
  "role": "studio",
  "tr": "Kyoto Animation",
  "en": "Kyoto Animation",
  "labels": ["京都アニメーション", "京アニ", "京都動画", "KyoAni"],
  "source": "bangumi",
  "first_seen_subject": 154,
  "count": 1247,
  "status": "active"
}
```

- `tr` / `en`: gösterim değeri (şirket adlarında TR=EN, etiketlerde TR≠EN)
- `labels`: CJK/JP varyantlar + takma adlar + Latin adlar (normalize eşleşme için)
- `status`: `active` | `pending` (doğrulanmadı, dil dosyasına girmez) | `deprecated`
- `labels` ilk yazım kazanır (gölgelenme yasak) — `verify_vault.py` denetler.

Yeni değer görüldüğünde:
1. Harvester `raw_harvest/`'a yazar
2. `vault.json` güncellenir (idempotent)
3. `vault_to_strings.py` → `values*/strings.xml` yaması
4. `sync_to_drive.py --push` → Drive'a yüklenir
5. Çeviri ekibi Drive'daki `translations_pending.tsv` üzerinden TR/EN düzeltir

## Harvest

```bash
# Tekil subject
python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --subject 154

# Toplu (1..400000 aralığı, rate-limited)
python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --range 1 5000 --out raw_harvest/harvest_2026-10-20.json

# Mevcut MASTER + ekran görüntüsü farklarını ekle
python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --ingest-screenshots
```

## Eksiksizlik Garantisi

- **Komiteler dahil**: `光坂高校演劇部`, `攻殻機動隊製作委員会`, `PROJECT-xxx` gibi değerler
  `committees.json`'da ayrı tutulur, ekranda olduğu gibi gösterilir (çeviri yok).
- **Kişiler dahil**: `田中渉`, `大澤雅彦`, `Rika Davis` gibi yapımcı kişi adları
  `producers.json`'da `role: person` ile tutulur.
- **Yayıncı ağ belirsizlikleri**: `MBS TRUTH` gibi birleşik etiketler ayrıştırılıp ikisi de kaydedilir.
- **Etiketler birleşik**: Bangumi + AniList + MAL tag havuzu tek `tags.json`'da, yüzdeli
  AniList tagleri (`98% Aile Yaşamı`) de normalize edilir (`aile_yaşamı`).
