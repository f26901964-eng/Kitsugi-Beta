package com.kitsugi.animelist.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import com.kitsugi.animelist.data.account.LinkedAccountVault
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch

/**
 * Kitsugi hesabı ekranı: e-posta/şifre ile kayıt ve giriş, oturum bilgisi, eşitleme ve çıkış.
 */
@Composable
fun KitsugiAccountContent() {
    val context = LocalContext.current.applicationContext
    val dao = remember { KitsugiDatabase.getDatabase(context).searchHistoryDao() }
    val scope = rememberCoroutineScope()
    val accent = LocalKitsugiAccent.current

    var loggedInEmail by remember { mutableStateOf(KitsugiAccountRepository.currentEmail()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun showError(prefix: String, e: Throwable) {
        isError = true
        message = "$prefix: ${e.message ?: "bilinmeyen hata"}"
    }

    fun submit() {
        val trimmed = email.trim()
        if (!trimmed.contains("@") || password.length < 6) {
            isError = true
            message = "Geçerli bir e-posta ve en az 6 karakterli şifre gir."
            return
        }
        busy = true
        message = null
        scope.launch {
            if (isRegister) {
                KitsugiAccountRepository.signUp(context, trimmed, password)
                    .onSuccess { hasSession ->
                        if (hasSession) {
                            loggedInEmail = KitsugiAccountRepository.currentEmail()
                            KitsugiAccountRepository.pullAndMergeSearchHistory(dao)
                            isError = false
                            message = "Hesap oluşturuldu, giriş yapıldı."
                        } else {
                            isError = false
                            isRegister = false
                            message = "Doğrulama e-postası gönderildi. Onayladıktan sonra giriş yap."
                        }
                    }
                    .onFailure { showError("Kayıt başarısız", it) }
            } else {
                KitsugiAccountRepository.signIn(context, trimmed, password)
                    .onSuccess {
                        loggedInEmail = KitsugiAccountRepository.currentEmail()
                        KitsugiAccountRepository.pullAndMergeSearchHistory(dao)
                            .onFailure { e -> showError("Eşitleme başarısız", e) }
                        if (!isError) {
                            isError = false
                            message = "Giriş yapıldı, veriler eşitlendi."
                        }
                    }
                    .onFailure { showError("Giriş başarısız", it) }
            }
            password = ""
            busy = false
        }
    }

    fun syncNow() {
        busy = true
        message = null
        scope.launch {
            KitsugiAccountRepository.pullAndMergeSearchHistory(dao)
                .onSuccess { isError = false; message = "Eşitlendi." }
                .onFailure { showError("Eşitleme başarısız", it) }
            busy = false
        }
    }

    fun signOut() {
        busy = true
        scope.launch {
            KitsugiAccountRepository.signOut(context)
            loggedInEmail = null
            isError = false
            message = "Çıkış yapıldı. Yerel veriler cihazda kalır."
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profil başlığı
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(48.dp)
            )
            Column {
                Text(
                    text = loggedInEmail ?: "Giriş yapılmadı",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (loggedInEmail != null)
                        "Arama geçmişin bu hesapla eşitleniyor."
                    else
                        "Giriş yaparak verilerini cihazlar arasında eşitle.",
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (loggedInEmail != null) {
            Button(
                onClick = { syncNow() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Şimdi eşitle") }
            OutlinedButton(
                onClick = { signOut() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Çıkış yap") }
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-posta") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegister) "Kayıt ol" else "Giriş yap")
            }
            TextButton(onClick = { isRegister = !isRegister; message = null }) {
                Text(
                    if (isRegister) "Zaten hesabın var mı? Giriş yap"
                    else "Hesabın yok mu? Kayıt ol"
                )
            }
        }

        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        message?.let {
            Text(
                text = it,
                color = if (isError) KitsugiColors.AccentRed else KitsugiColors.AccentGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KitsugiColors.SurfaceSoft, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}
