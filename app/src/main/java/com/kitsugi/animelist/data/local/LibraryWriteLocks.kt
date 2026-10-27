package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaIdentity
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * KAYNAK BAŞINA YAZMA KİLİDİ — kitaplığa eşzamanlı yazan yolları sıraya sokar.
 *
 * ## Neden gerekli
 * Kütüphaneye aynı anda birden fazla yol yazabiliyor:
 *
 *  • açılıştaki arka plan liste yenilemesi ([MediaEntryRepository.replaceSourcePreservingAdultFlags],
 *    kaynağı siler + baştan yazar),
 *  • hesap ekranından/oturum açılışından gelen içe aktarım ([MediaEntryRepository.smartImport],
 *    önce mevcut satırları OKUR, sonra tek işlemde yazar),
 *  • çapraz eşitleme yazmaları ve çift kayıt onarımı.
 *
 * `smartImport` ile sil-yeniden-yaz döngüsü iç içe girdiğinde içe aktarım "satır yok" görüp
 * AYNI kaydı ikinci kez ekliyordu. Sonuç: tek kaynakta aynı yapım için iki satır, Listem'de
 * iki kart. Kilit, kaynak başına bu iki yolu sıraya sokar; farklı kaynaklar birbirini beklemez.
 *
 * Kilit **nesne düzeyinde** (repository örneğinden bağımsız) tutulur: repository birden fazla
 * yerde (ViewModel, Application, TV ekranı) ayrı örneklerle oluşturuluyor.
 */
internal object LibraryWriteLocks {

    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forSource(source: String): Mutex =
        locks.getOrPut(MediaIdentity.canonicalSource(source)) { Mutex() }
}
