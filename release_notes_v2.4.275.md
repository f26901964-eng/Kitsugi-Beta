# Kitsugi-Beta v2.4.275 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.275)

### 🔤 Wiki Çeviri Butonları Detay Sayfasıyla Birebir Aynı Oldu
- **Tek Mantık, Üç Yerde:** Karakter/kişi wiki sekmesi (Wiki özeti, Bilgi kutusu, bölümler) ve yapım wiki sekmesi (seri özeti, çip/arama kartı) çeviri butonlarının tamamı, detay sayfasındaki **Açıklama** kartının çeviri butonuyla aynı bileşene (`WikiTranslateButton`) geçti.
- **Buton Artık Kaybolmuyor:** Çeviri tamamlandığında "Orijinali göster" yazı düğmesine dönüşmüyor; ikon her zaman yerinde kalıyor — tıpkı detay sayfasındaki gibi.
- **Harici Çeviri Uygulaması:** İkona dokununca metin, ayarlardaki tercihe göre (Google Translate / DeepL / TranslateYou) **harici çeviri uygulamasında** açılıyor; uygulama kurulu değilse Google Translate web'e düşüyor.
- **Yüklenme Animasyonu:** Uygulama içi (otomatik) çeviri sürerken ikon nabız gibi yanıp sönüyor ve etrafında dönen halka görünüyor; buton o süre dokunulamıyor. Animasyon hem wiki çevirisi hem sayfanın açıklama/biyografi çevirisi sürerken çalışıyor.
- **Parça Parça Akan Çeviri:** Metinler çeviri tamamlanmasını beklemeden geldikçe ekranda tamamlanıyor (detay sayfası açıklamasıyla aynı akış).

### ⚙️ Otomatik Çeviri Wiki'de Uçtan Uca Çalışıyor
- **Ayar Açıkken:** Sayfa çözümlenir çözülmez özet ve **bilgi kutusu** kendiliğinden çevriliyor; bölümler açıldıkça çeviriliyor; medya wiki kartı açıklamaları da otomatik çevriliyor.
- **Anında Tepki:** Çeviri sekmesi açıkken ayar açılırsa yüklü metinler hemen çevriliyor, kapanırsa orijinale dönülüyor, **hedef dil** değiştirilirse tüm çeviriler yeni dile tazeleniyor.
- **Gizlilik Koruması:** Bilgi kutusu harici çeviriye gönderilirken spoiler (gizli) alanların değerleri **gönderilmiyor**.

---

## 🇬🇧 English (v2.4.275)

### 🔤 Wiki Translate Buttons Now Match the Detail Page Exactly
- **One Behavior, Everywhere:** All wiki translate buttons — character/staff wiki tab (summary, infobox, sections) and media wiki tab (series summary, chip/search card) — now use the very same component (`WikiTranslateButton`) as the detail page's **Description** translate button.
- **The Button Never Disappears:** Once a text is translated, the icon no longer swaps to a "Show original" text button; it stays in place exactly like the detail page.
- **External Translator App:** Tapping the icon opens the text in an **external translator app** (Google Translate / DeepL / TranslateYou depending on your setting), falling back to Google Translate web when no app is installed.
- **Loading Animation:** While in-app (automatic) translation runs, the icon pulses and a spinning ring appears around it; the button is disabled meanwhile. The animation also triggers while the page's own description/biography translation is running.
- **Streaming Translation:** Text fills in progressively as translation chunks arrive — the same streaming behavior as the detail page description.

### ⚙️ End-to-End Automatic Translation for Wikis
- **When the Setting Is On:** The summary and **infobox** are translated as soon as the page resolves, sections are translated as they are expanded, and media wiki card blurbs are auto-translated too.
- **Instant Reaction:** Toggling the setting while the tab is open translates the loaded texts immediately, disabling it reverts to the original, and changing the **target language** refreshes every translation.
- **Privacy Guard:** Spoiler (hidden) infobox values are **never** sent to the external translator.
