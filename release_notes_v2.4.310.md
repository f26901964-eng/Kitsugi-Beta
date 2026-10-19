# Kitsugi Beta v2.4.310

## 🇹🇷 Türkçe

### 🚫 Ölü Link & Sahte Kaynak Koruması (`KitsugiCdnJwExtractor` & `CsStreamRunner`)
- **Bayat `contentUrl` Fallback Sarmalı Kırıldı (`KitsugiCdnJwExtractor`):** Embed sayfalarında şifreli `dc_` bloğu bulunup deşifre edilemediğinde (yeni script paketlemeleri, bad base-64 vb.), sistem artık sayfadaki eski ve CDN'de silinmiş olan bayat `contentUrl` adresini sahte kaynak olarak üretmiyor. Yalnızca açık `.m3u8` varsa denenir; aksi halde ölü kaynak üretilmeyip `crypted block çözülemedi` uyarısı `CsTrace` ve `HDCH`/`Kekik_CloseLoad`/`Rapidrame` loglarına aktarılır.
- **URL-Safe Base64 & Padding Desteği:** JWPlayer şifreli yükleri için URL-safe Base64 (`-` ve `_`) karakter desteği ve eksik dolgu (`padding`) otomatik onarımı eklendi.
- **Kesinleşmiş 404 Ölü Link Koruması (`CsStreamRunner`):** Hem HTTP HEAD 404 hem de HTTP GET 404 döndüğü kesinleşen (`trulyDead`) akışlar artık "false positive olabilir" denilerek oynatıcıya zorla gönderilmez. Oynatıcı sahte kaynakla açılıp MPV 9001 hatası vermez; kaynak temiz şekilde elenerek durum Tanı Raporu'na yansıtılır. Ağ zaman aşımı ve belirsiz durumlardaki iyimser fallback korundu.

---

## 🇬🇧 English

### 🚫 Dead Link & Ghost Source Guard (`KitsugiCdnJwExtractor` & `CsStreamRunner`)
- **Broke Stale `contentUrl` Fallback Loop (`KitsugiCdnJwExtractor`):** When an encrypted `dc_` JWPlayer payload is present but fails to decrypt (e.g. newer eval obfuscation or malformed base64), the native extractor no longer falls back to stale JSON-LD `contentUrl` links that have been decommissioned by the CDN. Only plain exposed `.m3u8` links are attempted; otherwise, no phantom stream is emitted, and a clear `crypted block could not be decrypted` alert is routed to diagnostic logs and tags.
- **URL-Safe Base64 & Missing Padding Recovery:** Added automatic handling for URL-safe Base64 characters (`-` and `_`) and automatic padding restoration for encrypted payloads.
- **Hard 404 Dead Stream Discard (`CsStreamRunner`):** Streams verified as conclusively dead (failing both HEAD 404 and GET 404) are no longer re-admitted under false-positive recovery heuristics. This prevents dead links from triggering MPV 9001 playback errors.
