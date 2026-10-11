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
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

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
                    message = KitsugiStrings.get(R.string.e_posta_dogrulandi_sifreni_tekrar_girerek_guvenli_kasayi_ac)
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
            message = KitsugiStrings.get(R.string.gecerli_bir_e_posta_ve_en_az_6_karakterli_sifre_gir)
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
                            if (!isError) message = KitsugiStrings.get(R.string.hesap_olusturuldu_yedek_durumunu_asagidan_kontrol_edebilirsin)
                        } else {
                            isError = false
                            isRegister = false
                            message = KitsugiStrings.get(R.string.dogrulama_e_postasi_gonderildi_onayladiktan_sonra_giris_yap)
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
                            message = KitsugiStrings.get(R.string.giris_yapildi_arama_gecmisi_esitlendi_kasa_durumu_asagida)
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
                .onSuccess { isError = false; message = KitsugiStrings.get(R.string.esitlendi) }
                .onFailure { showError(KitsugiStrings.get(R.string.esitleme_basarisiz), it) }
            // Bildirim arşivi de eşitlenir (başarısızlık UI'ı bozmaz)
            KitsugiAccountRepository.pullAndMergeNotificationArchive(context)
            LinkedAccountVault.backupNow(context)
                .onFailure { showError("Şifreli yedek alınamadı", it) }
            if (!isError) message = KitsugiStrings.get(R.string.arama_gecmisi_esitlendi_bildirim_arsivi_ve_bagli_hesaplar_yedekl)
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
                    message = KitsugiStrings.get(R.string.cikis_yapildi_yerel_veriler_cihazda_kalir)
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
            title = { Text(KitsugiStrings.get(R.string.inline_change_your_kitsugi_password_d2623b3)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(KitsugiStrings.get(R.string.inline_linked_accounts_are_preserved_if_9ef2bcf))
                    OutlinedTextField(currentPassword, { currentPassword = it }, enabled = !busy,
                        label = { Text(KitsugiStrings.get(R.string.inline_current_password_52929cf)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(newPassword, { newPassword = it }, enabled = !busy,
                        label = { Text(KitsugiStrings.get(R.string.inline_new_password_at_least_8_f9a9801)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(confirmPassword, { confirmPassword = it }, enabled = !busy,
                        label = { Text(KitsugiStrings.get(R.string.inline_re_enter_new_password_5319ba5)) }, singleLine = true,
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
                                    .onSuccess { message = KitsugiStrings.get(R.string.sifre_degistirildi_bagli_hesap_yedegin_korundu) }
                                    .onFailure {
                                        showError("Şifre işlemi tamamlanamadı", it)
                                        message += " Ağ kesildiyse yeni şifreyle giriş yapmayı dene; eski şifre hâlâ geçerliyse aynı yeni şifreyle işlemi tekrar et."
                                    }
                            } finally { closePasswordDialog(); busy = false }
                        }
                    }) { Text(stringResource(R.string.sifreyi_degistir)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { closePasswordDialog() }) { Text(stringResource(R.string.vazgec)) } }
        )
    }

    conflictChoice?.let { keepLocal ->
        AlertDialog(onDismissRequest = { if (!busy) conflictChoice = null },
            title = { Text(KitsugiStrings.get(R.string.inline_account_backup_conflict_d3c74ac)) },
            text = { Text(KitsugiStrings.get(R.string.inline_1_s_for_d58aa9f, vaultConflicts.joinToString()) +
                (if (keepLocal) KitsugiStrings.get(R.string.bu_cihazdaki) else "buluttaki") +
                " hesap bilgileri korunacak. Diğer servislerin değişiklikleri birleştirilir. Bulut değiştiyse işlem durdurulur.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; isError = false
                scope.launch {
                    try {
                        LinkedAccountVault.resolveConflict(context, keepLocal)
                            .onSuccess { message = KitsugiStrings.get(R.string.secimin_uygulandi_kasa_durumunu_kontrol_et) }
                            .onFailure { showError(KitsugiStrings.get(R.string.cakisma_cozulemedi), it) }
                    } finally { conflictChoice = null; busy = false }
                }
            }) { Text(KitsugiStrings.get(R.string.onayla)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { conflictChoice = null }) { Text(stringResource(R.string.vazgec)) } })
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
                    text = loggedInEmail ?: KitsugiStrings.get(R.string.ui_account_signed_out),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (loggedInEmail != null)
                        KitsugiStrings.get(R.string.ui_account_subtitle_signed_in)
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
                    KitsugiStrings.get(R.string.e_posta_dogrulamasi_hesabi_acti_sifreli_yedegini_kullanmak_icin),
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextSecondary
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    label = { Text(KitsugiStrings.get(R.string.kitsugi_sifresi)) },
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
                                        message = KitsugiStrings.get(R.string.sifre_dogrulandi_guvenli_hesap_yedegin_acildi)
                                    } else {
                                        isError = true
                                        message = KitsugiStrings.get(R.string.giris_dogrulandi_ancak_kasa_acilamadi_x, LinkedAccountVault.status.value)
                                    }
                                }
                                .onFailure { showError("Şifre doğrulanamadı", it) }
                            busy = false
                        }
                    },
                    enabled = !busy && password.length >= 6,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(KitsugiStrings.get(R.string.sifreyi_dogrula_ve_kasayi_ac)) }
            }
            Text(KitsugiStrings.get(R.string.inline_if_your_password_is_reset_a9614b3),
                style = MaterialTheme.typography.bodySmall, color = KitsugiColors.TextSecondary)
            Button(
                onClick = { syncNow() },
                enabled = !busy && LinkedAccountVault.hasLocalVault(context),
                modifier = Modifier.fillMaxWidth()
            ) { Text(KitsugiStrings.get(R.string.inline_sync_now_2f993db)) }
            if (vaultConflicts.isNotEmpty()) {
                Text(KitsugiStrings.get(R.string.inline_conflicting_accounts_1_s_0c4c670, vaultConflicts.joinToString()), color = KitsugiColors.AccentRed)
                OutlinedButton(enabled = !busy, onClick = { conflictChoice = true }) { Text(KitsugiStrings.get(R.string.inline_keep_accounts_on_this_device_fd2ebcf)) }
                OutlinedButton(enabled = !busy, onClick = { conflictChoice = false }) { Text(KitsugiStrings.get(R.string.inline_keep_cloud_accounts_47e2ee5)) }
            }
            OutlinedButton(enabled = !busy, onClick = { passwordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sifreyi_degistir))
            }
            OutlinedButton(
                onClick = { signOut() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(KitsugiStrings.get(R.string.inline_sign_out_8d1ab12)) }
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
                label = { Text(stringResource(R.string.sifre)) },
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
                Text(if (isRegister) KitsugiStrings.get(R.string.ui_account_sign_up) else KitsugiStrings.get(R.string.ui_action_sign_in))
            }
            TextButton(enabled = !busy, onClick = { isRegister = !isRegister; message = null; isError = false }) {
                Text(
                    if (isRegister) KitsugiStrings.get(R.string.ui_account_prompt_sign_in)
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
