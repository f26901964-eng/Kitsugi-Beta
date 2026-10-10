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
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.AlertDialog
import com.kitsugi.animelist.ui.theme.gradient.Button
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kitsugi.animelist.data.account.AccountErrorFormatter
import com.kitsugi.animelist.data.account.KitsugiAccountClient
import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import com.kitsugi.animelist.data.account.LinkedAccountVault
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
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

    val vaultStatus by LinkedAccountVault.status.collectAsState()
    val vaultConflicts by LinkedAccountVault.conflicts.collectAsState()
    var passwordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var conflictChoice by remember { mutableStateOf<Boolean?>(null) }
    var loggedInEmail by remember { mutableStateOf(KitsugiAccountRepository.currentEmail()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val authSessionStatus by KitsugiAccountClient.client.auth.sessionStatus.collectAsState()

    // E-posta doğrulama deep link'i mevcut Activity'ye döndüğünde bu ekran oturumu da izler.
    LaunchedEffect(authSessionStatus) {
        when (val status = authSessionStatus) {
            is SessionStatus.Authenticated -> status.session.user?.email?.let { confirmedEmail ->
                loggedInEmail = confirmedEmail
                if (message?.startsWith("Doğrulama e-postası") == true) {
                    isError = false
                    message = "E-posta doğrulandı. Şifreni tekrar girerek güvenli kasayı aç."
                }
            }
            is SessionStatus.NotAuthenticated -> loggedInEmail = null
            else -> Unit
        }
    }

    fun showError(prefix: String, e: Throwable) {
        isError = true
        message = "$prefix: ${AccountErrorFormatter.userMessage(e)}"
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
        isError = false
        scope.launch {
            if (isRegister) {
                KitsugiAccountRepository.signUp(context, trimmed, password)
                    .onSuccess { hasSession ->
                        if (hasSession) {
                            loggedInEmail = KitsugiAccountRepository.currentEmail()
                            KitsugiAccountRepository.pullAndMergeSearchHistory(dao)
                                .onFailure { showError("Arama geçmişi eşitlenemedi", it) }
                            // Bildirim arşivi de eşitlenir (arka planda)
                            KitsugiAccountRepository.pullAndMergeNotificationArchive(context)
                            if (!isError) message = "Hesap oluşturuldu. Yedek durumunu aşağıdan kontrol edebilirsin."
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
                        // Bildirim arşivi de eşitlenir (arka planda)
                        KitsugiAccountRepository.pullAndMergeNotificationArchive(context)
                        if (!isError) {
                            isError = false
                            message = "Giriş yapıldı, arama geçmişi eşitlendi. Kasa durumu aşağıda."
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
        isError = false
        scope.launch {
            KitsugiAccountRepository.pullAndMergeSearchHistory(dao)
                .onSuccess { isError = false; message = "Eşitlendi." }
                .onFailure { showError("Eşitleme başarısız", it) }
            // Bildirim arşivi de eşitlenir (başarısızlık UI'ı bozmaz)
            KitsugiAccountRepository.pullAndMergeNotificationArchive(context)
            LinkedAccountVault.backupNow(context)
                .onFailure { showError("Şifreli yedek alınamadı", it) }
            if (!isError) message = "Arama geçmişi eşitlendi; bildirim arşivi ve bağlı hesaplar yedeklendi."
            busy = false
        }
    }

    fun signOut() {
        busy = true
        scope.launch {
            KitsugiAccountRepository.signOut(context)
                .onSuccess {
                    loggedInEmail = null
                    isError = false
                    message = "Çıkış yapıldı. Yerel veriler cihazda kalır."
                }
                .onFailure { showError("Çıkış başarısız", it) }
            busy = false
        }
    }

    if (passwordDialog) {
        fun closePasswordDialog() {
            passwordDialog = false
            currentPassword = ""; newPassword = ""; confirmPassword = ""
        }
        AlertDialog(
            onDismissRequest = { if (!busy) closePasswordDialog() },
            title = { Text("Kitsugi şifreni değiştir") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bağlı hesapların korunur. İşlem kesilirse aynı yeni şifreyle tekrar dene. Şifre sıfırlama bu işlemden farklıdır.")
                    OutlinedTextField(currentPassword, { currentPassword = it }, enabled = !busy,
                        label = { Text("Mevcut şifre") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(newPassword, { newPassword = it }, enabled = !busy,
                        label = { Text("Yeni şifre (en az 8 karakter)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(confirmPassword, { confirmPassword = it }, enabled = !busy,
                        label = { Text("Yeni şifreyi tekrar yaz") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && currentPassword.isNotEmpty() && newPassword.length >= 8 && newPassword == confirmPassword,
                    onClick = {
                        busy = true; isError = false; message = null
                        scope.launch {
                            try {
                                LinkedAccountVault.changePassword(context, currentPassword, newPassword)
                                    .onSuccess { message = "Şifre değiştirildi. Bağlı hesap yedeğin korundu." }
                                    .onFailure {
                                        showError("Şifre işlemi tamamlanamadı", it)
                                        message += " Ağ kesildiyse yeni şifreyle giriş yapmayı dene; eski şifre hâlâ geçerliyse aynı yeni şifreyle işlemi tekrar et."
                                    }
                            } finally { closePasswordDialog(); busy = false }
                        }
                    }) { Text("Şifreyi değiştir") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { closePasswordDialog() }) { Text("Vazgeç") } }
        )
    }

    conflictChoice?.let { keepLocal ->
        AlertDialog(onDismissRequest = { if (!busy) conflictChoice = null },
            title = { Text("Hesap yedeği çakışması") },
            text = { Text("${vaultConflicts.joinToString()} için " +
                (if (keepLocal) "bu cihazdaki" else "buluttaki") +
                " hesap bilgileri korunacak. Diğer servislerin değişiklikleri birleştirilir. Bulut değiştiyse işlem durdurulur.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; isError = false
                scope.launch {
                    try {
                        LinkedAccountVault.resolveConflict(context, keepLocal)
                            .onSuccess { message = "Seçimin uygulandı. Kasa durumunu kontrol et." }
                            .onFailure { showError("Çakışma çözülemedi", it) }
                    } finally { conflictChoice = null; busy = false }
                }
            }) { Text("Onayla") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { conflictChoice = null }) { Text("Vazgeç") } })
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
                        "Arama geçmişi, bağlı hesaplar ve taşınabilir ayarlar."
                    else
                        "Giriş yaparak verilerini cihazlar arasında eşitle.",
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (loggedInEmail != null) {
            Text(vaultStatus, style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            if (!LinkedAccountVault.hasLocalVault(context)) {
                Text(
                    "E-posta doğrulaması hesabı açtı. Şifreli yedeğini kullanmak için Kitsugi şifreni tekrar doğrula.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextSecondary
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    label = { Text("Kitsugi şifresi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val accountEmail = loggedInEmail ?: return@Button
                        busy = true
                        isError = false
                        message = null
                        scope.launch {
                            KitsugiAccountRepository.signIn(context, accountEmail, password)
                                .onSuccess {
                                    password = ""
                                    if (LinkedAccountVault.hasLocalVault(context)) {
                                        isError = false
                                        message = "Şifre doğrulandı; güvenli hesap yedeğin açıldı."
                                    } else {
                                        isError = true
                                        message = "Giriş doğrulandı ancak kasa açılamadı: ${LinkedAccountVault.status.value}"
                                    }
                                }
                                .onFailure { showError("Şifre doğrulanamadı", it) }
                            busy = false
                        }
                    },
                    enabled = !busy && password.length >= 6,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Şifreyi doğrula ve kasayı aç") }
            }
            Text("Şifre sıfırlanırsa eski şifreli kasa açılamaz. Yerel veriler çıkışta silinmez; farklı hesaba girişte bu cihazdaki veriler o hesaba yedeklenebilir.",
                style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            Button(
                onClick = { syncNow() },
                enabled = !busy && LinkedAccountVault.hasLocalVault(context),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Şimdi eşitle") }
            if (vaultConflicts.isNotEmpty()) {
                Text("Çakışan hesaplar: ${vaultConflicts.joinToString()}", color = KitsugiColors.AccentRed)
                OutlinedButton(enabled = !busy, onClick = { conflictChoice = true }) { Text("Bu cihazdaki hesapları koru") }
                OutlinedButton(enabled = !busy, onClick = { conflictChoice = false }) { Text("Buluttaki hesapları koru") }
            }
            OutlinedButton(enabled = !busy, onClick = { passwordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Şifreyi değiştir")
            }
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
            TextButton(enabled = !busy, onClick = { isRegister = !isRegister; message = null; isError = false }) {
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
