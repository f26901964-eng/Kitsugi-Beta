package com.kitsugi.animelist.core.music

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.kitsugi.animelist.data.settings.AppSettings

/**
 * 🚦 Otomatik çalma kapısı
 *
 * Okuyucu açıldığında ortam sesinin kendiliğinden başlaması, kullanıcının
 * beklemediği bir anda hoparlörden ses çıkması demek olabilir. Bu yüzden
 * otomatik başlatma iki koşula bağlanabilir:
 *
 *  - `musicAutoPlayHeadphonesOnly` → yalnızca kablolu/Bluetooth/USB
 *    kulaklık takılıyken başlat
 *  - `musicAutoPlayMuteOnSilentDevice` → cihaz sessiz/titreşim modundayken
 *    başlatma
 *
 * ⚠️ Bu iki ayar `AppSettings` içinde **tanımlıydı ama hiçbir yerde
 * okunmuyordu**: kullanıcı anahtarı açıp kapatıyor, hiçbir şey
 * değişmiyordu. Artık gerçekten uygulanıyor.
 */
object AutoplayGate {

    /**
     * Otomatik çalma şu anda başlatılabilir mi?
     *
     * @param settings okuyucunun anlık ayarları
     * @param force `true` ise kapı atlanır (kullanıcı elle "çal" dediğinde
     *        kullanılmaz; yalnızca otomatik başlatma için geçerlidir)
     */
    fun canAutoplay(context: Context, settings: AppSettings, force: Boolean = false): Boolean {
        if (force) return true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return true

        if (settings.musicAutoPlayMuteOnSilentDevice &&
            audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        ) {
            return false
        }

        if (settings.musicAutoPlayHeadphonesOnly && !hasPrivateOutput(audioManager)) {
            return false
        }

        return true
    }

    /**
     * Kullanıcının sesi **yalnızca kendisinin** duyacağı bir çıkış var mı?
     *
     * Kablolu kulaklık, kulaklık+ mikrofon, Bluetooth A2DP/SCO, BLE, USB
     * kulaklık ve işitme cihazı çıkışları "özel" sayılır. Hoparlör,
     * televizyon ve dock çıkışları sayılmaz.
     */
    fun hasPrivateOutput(audioManager: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())

        return devices.any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_HEARING_AID -> true
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
                else -> false
            }
        }
    }
}
