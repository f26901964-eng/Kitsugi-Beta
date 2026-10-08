package com.kitsugi.animelist.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Bangumi (bgm.tv) hesap bağlama diyaloğu.
 *
 * Shikimori diyaloğuyla ([KitsugiShikimoriLoginDialog]) aynı etkileşim deseni, ancak
 * **önemli bir farkla**: Bangumi'de paylaşılan bir varsayılan uygulama kaydı YOKTUR.
 * Bangumi'nin token ucu `client_secret` istediği için her uygulamanın kendi kaydını
 * https://bgm.tv/dev/app adresinden yapması gerekir (Aniyomi, Mihon, Komikku, Yokai ve
 * Suwayomi'nin her biri ayrı App ID/Secret kullanır). Bu yüzden kimlik bilgileri alanı
 * varsayılan olarak AÇIK gelir ve boşken "1-Tık Giriş" devre dışıdır.
 *
 * Akış:
 *  1. Kullanıcı App ID + App Secret girer → [BangumiAuthStore.saveCredentials]
 *  2. Tarayıcıda `bgm.tv/oauth/authorize` açılır → kullanıcı 允许 (İzin Ver) der
 *  3. `kitsugi://bangumi-auth?code=...` deep link'i uygulamaya döner
 *     ([ExternalAuthManager.handleAuthIntent] → [com.kitsugi.animelist.data.auth.BangumiAuthManager])
 *  4. Deep link açılmazsa kullanıcı adres çubuğundaki URL'yi 2. adıma yapıştırır
 */
@Composable
fun KitsugiBangumiLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, onComplete ->
        onComplete(false, "Bangumi giriş işleyicisi bağlı değil")
    }
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Bangumi marka rengi — Aniyomi'nin ic_tracker_bangumi rengiyle aynı: rgb(240,145,153)
    val bangumiBrandColor = Color(0xFFF09199)

    var clientId by remember { mutableStateOf(BangumiAuthStore.getClientId(context)) }
    var clientSecret by remember { mutableStateOf(BangumiAuthStore.getClientSecret(context)) }
    var authCode by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(!BangumiAuthStore.hasCredentials(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // Otomatik deep link ile giriş tamamlandığında diyaloğu kapat; hataları göster.
    LaunchedEffect(Unit) {
        ExternalAuthManager.authEvents.collect { event ->
            when (event) {
                is ExternalAuthManager.AuthEvent.Success ->
                    if (event.serviceName.equals("bangumi", ignoreCase = true)) {
                        isLoading = false
                        onDismiss()
                    }
                is ExternalAuthManager.AuthEvent.Error -> {
                    if (isLoading || event.message.contains("bangumi", ignoreCase = true)) {
                        isLoading = false
                        errorMessage = event.message
                    }
                }
                else -> Unit
            }
        }
    }

    val credentialsReady = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun persistCredentials() {
        BangumiAuthStore.saveCredentials(context, clientId, clientSecret)
    }

    fun openInBrowser(redirectUri: String) {
        errorMessage = null
        if (clientId.isBlank() || clientSecret.isBlank()) {
            errorMessage = "Önce App ID ve App Secret alanlarını doldurun (bkz. Özel API Anahtarları)."
            showAdvanced = true
            return
        }
        persistCredentials()
        BangumiAuthStore.savePendingRedirectUri(context, redirectUri)
        val authUrl = BangumiApiClient.buildAuthorizeUrl(
            clientId = clientId.trim(),
            redirectUri = redirectUri,
            state = java.util.UUID.randomUUID().toString().take(8)
        )
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
        }.onFailure {
            errorMessage = "Tarayıcı açılamadı: ${it.message}"
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, bangumiBrandColor.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Başlık
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bangumiBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, bangumiBrandColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "番", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = bangumiBrandColor)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Bangumi ile Bağlan",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )

                Text(
                    text = "bgm.tv koleksiyonunuzu (想看 / 在看 / 看过) Kitsugi ile eşitleyin.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Hata kutusu
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = KitsugiColors.AccentRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = KitsugiColors.AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KitsugiColors.AccentRed
                                )
                            }
                        }
                    }

                    // 0. Adım: Uygulama kaydı (App ID / App Secret)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, bangumiBrandColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = bangumiBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1. Adım: Bangumi Uygulama Anahtarları",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = bangumiBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Bangumi, her uygulamanın kendi kaydını yapmasını ister; " +
                                    "paylaşılan bir varsayılan anahtar yoktur. " +
                                    "bgm.tv hesabınızla oturum açıp 开发者平台 → 我的应用 → 创建应用 adımlarını izleyin. " +
                                    "\"回调地址\" (Redirect URI) alanını boş bırakın ya da birebir " +
                                    "${BangumiApiClient.DEEP_LINK_REDIRECT_URI} yazın. " +
                                    "Oluşan App ID ve App Secret'ı aşağıdaki \"Özel API Anahtarları\" bölümüne girin.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(BangumiApiClient.DEV_APP_URL))
                                        )
                                    }
                                }) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("bgm.tv/dev/app — Uygulama Kaydı", fontSize = 11.sp)
                                }
                                if (credentialsReady) {
                                    Text(
                                        text = "✓ Anahtarlar hazır",
                                        fontSize = 11.sp,
                                        color = KitsugiColors.AccentGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 1. Adım: Yetkilendirme
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, bangumiBrandColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = bangumiBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "2. Adım: Bangumi Hesabınızla Yetki Verin",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = bangumiBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tarayıcıda Bangumi oturumunuzla \"允许\" (İzin Ver) düğmesine bastığınızda " +
                                    "uygulama otomatik olarak bağlanır. Yetki kodu 60 saniye geçerlidir ve tek kullanımlıktır.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            KitsugiButton(
                                onClick = { openInBrowser(BangumiApiClient.DEEP_LINK_REDIRECT_URI) },
                                enabled = !isLoading && credentialsReady,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1-Tık Otomatik Giriş (Önerilen)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            KitsugiTonalButton(
                                onClick = { openInBrowser(BangumiApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI) },
                                enabled = !isLoading && credentialsReady,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = bangumiBrandColor.copy(alpha = 0.15f),
                                    contentColor = bangumiBrandColor
                                )
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Alternatif Şema (aniyomi://)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Tarayıcı \"sayfa açılamıyor\" derse endişelenmeyin: adres çubuğundaki tam URL'yi " +
                                    "(kitsugi://bangumi-auth?code=...) kopyalayıp 3. adıma yapıştırmanız yeterlidir.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }

                    // 2. Adım: Kod yapıştırma
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "3. Adım: Yetkilendirme Kodunu Yapıştırın",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KitsugiColors.TextPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Deep link çalışmadıysa izin verdikten sonra ekranda/adres çubuğunda görünen " +
                                    "kodu (veya tam adresi) buraya yapıştırın:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = authCode,
                                onValueChange = {
                                    authCode = BangumiApiClient.sanitizeAuthCode(it)
                                    errorMessage = null
                                },
                                label = { Text("Yetkilendirme Kodu (Code)") },
                                placeholder = { Text("Tarayıcıdan aldığınız kod") },
                                trailingIcon = {
                                    KitsugiTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                authCode = BangumiApiClient.sanitizeAuthCode(text)
                                                errorMessage = null
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Yapıştır", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Özel API anahtarları
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KitsugiColors.SurfaceElevated.copy(alpha = 0.3f))
                            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvanced = !showAdvanced }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Özel API Anahtarları (App ID / App Secret)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = KitsugiColors.TextSecondary
                                    )
                                )
                            }
                            Icon(
                                if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = KitsugiColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = clientId,
                                    onValueChange = {
                                        clientId = it.trim()
                                        errorMessage = null
                                    },
                                    label = { Text("App ID (client_id)") },
                                    placeholder = { Text("bgm...") },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = clientSecret,
                                    onValueChange = {
                                        clientSecret = it.trim()
                                        errorMessage = null
                                    },
                                    label = { Text("App Secret (client_secret)") },
                                    placeholder = { Text("32 karakterlik gizli anahtar") },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "Anahtarlar yalnızca bu cihazda saklanır ve doğrudan bgm.tv ile paylaşılır; " +
                                        "Kitsugi sunucusuna gönderilmez. Bir APK'yı dağıtacaksanız anahtarları " +
                                        "local.properties üzerinden derlemeye gömmek yerine kullanıcıdan istemek " +
                                        "daha güvenlidir (Bangumi PKCE desteklemediği için gömülen secret çıkarılabilir).",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                )

                                TextButton(
                                    onClick = {
                                        BangumiAuthStore.clearCredentials(context)
                                        clientId = ""
                                        clientSecret = ""
                                        errorMessage = null
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Kayıtlı Anahtarları Temizle", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Eylemler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KitsugiColors.TextSecondary
                        )
                    ) {
                        Text("İptal")
                    }

                    KitsugiButton(
                        onClick = {
                            val cleanCode = BangumiApiClient.sanitizeAuthCode(authCode)
                            when {
                                clientId.isBlank() || clientSecret.isBlank() -> {
                                    showAdvanced = true
                                    errorMessage = "App ID ve App Secret gereklidir (bgm.tv/dev/app)."
                                }
                                cleanCode.isBlank() -> {
                                    errorMessage = "Lütfen tarayıcıdan aldığınız yetkilendirme kodunu girin."
                                }
                                else -> {
                                    isLoading = true
                                    errorMessage = null
                                    persistCredentials()
                                    onLogin(clientId.trim(), clientSecret.trim(), cleanCode) { success, error ->
                                        isLoading = false
                                        if (success) onDismiss()
                                        else errorMessage = error ?: "Bangumi bağlantısı başarısız oldu."
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && authCode.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            KitsugiPlasmaLoader(size = 18.dp)
                        } else {
                            Text("Bağlan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
