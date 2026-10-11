# Sürüm Notları — v2.4.239-beta

## 🔐 Cloudflare Sabır Bütçesi (90–120 sn) ve Otomatik Zincir

Kullanıcı akışı artık tek otomatik hareket: **çıplak isim araması → sonuç kartı → detay sayfası →
üst sezon barı → alt bölüm listesi → oynatma**. Cloudflare/WAF çözümü bu zincirin dışında,
arama ÖNCESİNDE ("CF gate") gerçekleşir ve süre bütçeleri kullanıcıya yansımaz.

### Yenilikler
- **CF Gate (arama öncesi clearance):** çerez yoksa gizli WebView meydan okumayı ≤90 sn'de çözer;
  çerezler tüm aşamalar (arama/load/sezon/bölüm/loadLinks) tarafından yeniden kullanılır.
  Sağlıklı siteler "temiz sayfa" yoklamasıyla gate'i milisaniyeler içinde geçer (gecikme yok).
- **Sabır kuralı:** sert kanıt (cf_clearance/__ddg*) anında; kanıt yoksa WebView 90 sn açık kalır,
  90 sn'den sonra son çare kabul, 120 sn'de sert kapanış. Eski 10 sn erken vazgeçme kaldırıldı.
- **CF farkında aşama bütçeleri:** challenge görülen hostlarda safeSearch/safeLoad/loadLinks
  15/25 sn yerine 120 sn; provider bütçesi CF şüphelilerde 240 sn; arama kutusu 60 sn.
- **Üst sezon barı doğrudan gezinmesi:** detay URL'sindeki sezon jetonu (sezon-2/season-2//s2/)
  hedef sezonla değiştirilip sayfa doğrudan yüklenir — sezon sekmesine tıklamanın karşılığı.
  Yüklenen sayfanın sezon barı (sunulan sezonlar) artık loglanır.
- Gate başarısında WebView anında sökülür (eski davranış çözüm sonrası da açık tutuyordu).

### Değişen dosyalar
- `core/network (lagradost) CloudflareKiller.kt`
- `data/cloudstream/CsCfWarmupManager.kt`
- `data/cloudstream/CsStreamRunner.kt`

Ayrıntı: `PLAN_TASK_CF_SABIR_VE_SEZON_MIMARISI_2026-10-11.md`
