# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🎬 Oynatıcıdan Otomatik Liste Takibi — 7 Kaynağın Tamamı
- **Hangi kaynaktan açarsanız açın** (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl,
  TMDB/eklentiler) içerik oynatılınca listenize **"İzleniyor"** olarak işlenir; listede yoksa
  otomatik eklenir.
- **Bölüm bitince izlenen bölüm sayısı kendiliğinden artar** (bölüm numarası yazılır, geriye
  sayılmaz). Film ve tek bölümlük içerik doğrudan **izlendi** olur; **son bölümde** kayıt
  **Tamamlandı**ya geçer ve ilerleme toplam bölüme tamamlanır.
- **Fragman ve jenerik muhasebesi:** kapanış jeneriğine ulaşmak (veya jeneriği atlamak) bölümü
  bitmiş sayar. Fragman ve 60 saniyeden kısa klipler **asla** takip edilmez.
- **Değişiklikler tüm bağlı kaynaklara yazılır** (kapatılabilir). Çevrimdışıyken yerel kayıt
  yapılır, bağlantı gelince kuyruk kendiliğinden gönderir.

### ⭐ Bitişte Hızlı Puanlama Çubuğu
- Bölüm/film bittiğinde **pencere açılmaz**: oynatıcının altında ince bir bant çıkar, 10 yıldızla
  kısayoldan puan verirsiniz, puan anında listeye yazılır, çubuk geri sayımla kaybolur.
- Kumanda (D-pad) ile tam uyumlu, PiP'te ve kilitli kontrollerde görünmez, kaynak rozeti gösterir.
- Aynı yıldıza ikinci dokunuş puanı temizler; mevcut puanınız çubukta görünür.

### 🧪 Deneysel Bölümü ve Onay Penceresi
- Otomatik takip artık **Ayarlar → Deneysel** sayfasında; **varsayılan olarak KAPALI**.
  Sen açmadan hiçbir liste/geçmiş kaydı yazılmaz, puanlama çubuğu da çıkmaz.
- Açmaya çalıştığında **riskleri anlatan bir onay penceresi** çıkar (listelerin değişeceği,
  ilerlemenin geri alınamayacağı, değişikliklerin tüm platformlara yayılacağı).
- Anahtar istediğin an kapatılabilir; kapatınca alt anahtarlar gizlenir ama yaptığın
  seçimler korunur. Alt anahtarlar: yeni ekleme, tüm kaynaklara yazma, "Bırakıldı"ya saygı,
  hızlı puanlama çubuğu.

### 🧩 Ek Düzeltmeler
- **Jenerik türleri düzeltildi:** `ed`/`mixed-ed`/`ending` aralıkları artık tanınıyor — "Bitişi
  Atla" ve bitişte "sonraki bölüm" davranışı ilk kez gerçekten çalışıyor.
- **Bölüm bitiminde izleme geçmişi silinmiyor:** konum sıfırlanır, "izlendi" bayrağı korunur;
  "Devam et" aynı bölümü tekrar önermez.
- Oynatıcıdan çıkarken son saniyelerdeki ilerleme kaybolmaz; konum hatırlama kapalı olsa bile
  izleme kaydı tutulur.
- Player → Gelişmiş → **Otomatik takip** bölümünden tüm davranışlar ayarlanabilir.

---

## 🇬🇧 English

### 🎬 Automatic List Tracking from the Player — All 7 Sources
- Play from **any source** (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl, TMDB/addons)
  and the title is marked **Watching**; missing entries are added automatically.
- **Watched-episode count increments when an episode finishes** (never decreases). Movies and
  single-episode titles are marked watched immediately; the **last episode** completes the entry
  and fills progress to the total.
- **Trailer & credits accounting:** crossing the ending-credits segment (or skipping it) counts as
  finishing; trailers and clips shorter than 60 s are never tracked.
- **Changes fan out to every connected source** (optional) and queue offline, syncing later.

### ⭐ Quick Rating Bar at the End
- No popup window: a slim bar appears at the bottom of the player with 10 stars, writes the score
  immediately and dismisses itself with a countdown. D-pad friendly, hidden in PiP/locked controls.

### 🧪 Experimental Section with a Consent Dialog
- Automatic tracking now lives in **Settings → Experimental** and is **OFF by default**:
  nothing is written and no rating bar appears until you enable it.
- Enabling shows a **consent dialog** explaining that your lists will change, progress cannot
  be undone, and changes fan out to every connected platform.
- The switch can be turned off at any time; sub-switches hide while off but keep your choices.

### 🧩 Additional fixes
- **Credits segment types fixed:** `ed`/`mixed-ed`/`ending` are now recognised — "Skip ending" and
  the post-play next-episode flow actually work for the first time.
- **Watch history is no longer deleted at episode end:** position resets, the watched flag stays.
- Final-seconds progress is captured when leaving the player, even with position memory disabled.
