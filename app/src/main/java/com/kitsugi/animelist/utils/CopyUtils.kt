package com.kitsugi.animelist.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Çift tıklandığında [text] değerini panoya kopyalayan Modifier extension'ı.
 * Tüm uygulama genelinde kullanılır.
 */
fun Modifier.copyOnDoubleTap(
    context: Context,
    text: String,
    onSingleTap: (() -> Unit)? = null
): Modifier = this.pointerInput(text, onSingleTap) {
    val copyText = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Kitsugi_copy", text))
        Toast.makeText(context, KitsugiStrings.get(R.string.kopyalandi_x, text), Toast.LENGTH_SHORT).show()
    }

    if (onSingleTap == null) {
        detectTapGestures(onDoubleTap = { copyText() })
    } else {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = { copyText() }
        )
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Kitsugi_copy") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, KitsugiStrings.get(R.string.kopyalandi_x, text), Toast.LENGTH_SHORT).show()
}
