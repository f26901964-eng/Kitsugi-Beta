package com.kitsugi.animelist.ui.components
import com.kitsugi.animelist.ui.components.KitsugiTonalButton
import com.kitsugi.animelist.ui.components.KitsugiButton

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.ui.theme.KitsugiColors

@Composable
fun KitsugiShikimoriLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shikimoriBrandColor = Color(0xFF8E44AD)

    var clientId by remember {
        mutableStateOf(ExternalAuthManager.getShikimoriClientId(context))
    }
    var clientSecret by remember {
        mutableStateOf(ExternalAuthManager.getShikimoriClientSecret(context))
    }
    var authCode by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // Otomatik deep-link ile giriş tamamlandığında diyaloğu otomatik kapat;
    // deep-link sırasında oluşan hataları da kullanıcıya göster.
    LaunchedEffect(Unit) {
        ExternalAuthManager.authEvents.collect { event ->
            when (event) {
                is ExternalAuthManager.AuthEvent.Success ->
                    if (event.serviceName.equals("shikimori", ignoreCase = true)) onDismiss()
                is ExternalAuthManager.AuthEvent.Error -> {
                    // Yalnızca Shikimori akışıyla ilgili hataları burada göster.
                    if (isLoading || event.message.contains("shikimori", ignoreCase = true)) {
                        isLoading = false
                        errorMessage = event.message
                    }
                }
                else -> Unit
            }
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
                .border(1.dp, shikimoriBrandColor.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shikimoriBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, shikimoriBrandColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🌸", fontSize = 26.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Shikimori ile Bağlan",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )

                Text(
                    text = "Shikimori hesabınızdaki izleme listelerini Kitsugi ile doğrudan eşitleyin.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    // 1. Adım: Tarayıcıda Oturum Aç ve Yetki Ver
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, shikimoriBrandColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = shikimoriBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1. Adım: Shikimori Hesabınızla Yetki Verin",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = shikimoriBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Aşağıdaki '1-Tık Otomatik Giriş' butonuna dokunun. Tarayıcıda Shikimori hesabınızla oturum açıp 'Разрешить (İzin Ver)' butonuna bastığınızda uygulama otomatik olarak bağlanır.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // 1-Tık Otomatik Giriş (Deep Link)
                            KitsugiButton(
                                onClick = {
                                    val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                    errorMessage = null
                                    // Token adımı authorize adımındaki redirect_uri ile eşleşmek zorunda.
                                    ExternalAuthManager.saveShikimoriPendingRedirectUri(context, ShikimoriApiClient.DEEP_LINK_REDIRECT_URI)
                                    val authUrl = ShikimoriApiClient.buildAuthorizeUrl(targetId, ShikimoriApiClient.DEEP_LINK_REDIRECT_URI)
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                    context.startActivity(intent)
                                },
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

                            // Alternatif: Kod Gösterimi (oob)
                            KitsugiTonalButton(
                                onClick = {
                                    val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                    errorMessage = null
                                    ExternalAuthManager.saveShikimoriPendingRedirectUri(context, ShikimoriApiClient.DEFAULT_REDIRECT_URI)
                                    val authUrl = ShikimoriApiClient.buildAuthorizeUrl(targetId, ShikimoriApiClient.DEFAULT_REDIRECT_URI)
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = shikimoriBrandColor.copy(alpha = 0.15f),
                                    contentColor = shikimoriBrandColor
                                )
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tarayıcıda Aç (Kodu Manuel Al)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Redirect URI uyuşmazlığında ne yapılacağını açıkla
                            Text(
                                text = "Tarayıcıda \"The requested redirect uri is malformed or doesn't match client redirect URI\" hatası görürseniz: " +
                                    "Shikimori'deki OAuth uygulamanızın Redirect URI listesinde ${ShikimoriApiClient.DEEP_LINK_REDIRECT_URI} kayıtlı değil demektir. " +
                                    "Uygulama ayarlarından ekleyin ya da aşağıdaki aniyomi:// alternatifini / manuel kodu kullanın.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(ShikimoriApiClient.APPLICATIONS_URL))
                                            )
                                        }
                                    }
                                ) {
                                    Text("Shikimori OAuth Uygulamalarım", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                        errorMessage = null
                                        ExternalAuthManager.saveShikimoriPendingRedirectUri(context, ShikimoriApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI)
                                        val authUrl = ShikimoriApiClient.buildAuthorizeUrl(targetId, ShikimoriApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI)
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))) }
                                    }
                                ) {
                                    Text("Alternatif Tek Tık (aniyomi://)", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 2. Adım: Verilen Kodu Yapıştırın
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
                                    text = "2. Adım: Yetkilendirme Kodunu Yapıştırın",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KitsugiColors.TextPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "İzin verdikten sonra ekranda görünen kodu (veya tarayıcıdaki tam adresi) buraya yapıştırın:",
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
                                    authCode = ShikimoriApiClient.sanitizeAuthCode(it)
                                    errorMessage = null
                                },
                                label = { Text("Yetkilendirme Kodu (Code)") },
                                placeholder = { Text("Tarayıcıdan aldığınız kod") },
                                trailingIcon = {
                                    KitsugiTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                authCode = ShikimoriApiClient.sanitizeAuthCode(text)
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

                    // Gelişmiş Ayarlar (İsteğe Bağlı)
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
                                    text = "Özel API Anahtarları (İsteğe Bağlı)",
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
                                Text(
                                    text = "Varsayılan olarak hazır API anahtarı kullanılır. Sadece kendi Shikimori OAuth uygulamanız varsa doldurun.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )

                                OutlinedTextField(
                                    value = clientId,
                                    onValueChange = { clientId = it.trim() },
                                    label = { Text("Özel Client ID") },
                                    placeholder = { Text(ShikimoriApiClient.DEFAULT_CLIENT_ID) },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = clientSecret,
                                    onValueChange = { clientSecret = it.trim() },
                                    label = { Text("Özel Client Secret") },
                                    placeholder = { Text(ShikimoriApiClient.DEFAULT_CLIENT_SECRET) },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                TextButton(
                                    onClick = {
                                        ExternalAuthManager.resetShikimoriCredentials(context)
                                        clientId = ShikimoriApiClient.DEFAULT_CLIENT_ID
                                        clientSecret = ShikimoriApiClient.DEFAULT_CLIENT_SECRET
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Varsayılan Anahtarları Geri Yükle", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
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
                            val cleanCode = ShikimoriApiClient.sanitizeAuthCode(authCode)
                            if (cleanCode.isBlank()) {
                                errorMessage = "Lütfen tarayıcıdan aldığınız yetkilendirme kodunu girin."
                            } else {
                                isLoading = true
                                errorMessage = null
                                val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                val targetSecret = clientSecret.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_SECRET }

                                onLogin(targetId, targetSecret, cleanCode) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        onDismiss()
                                    } else {
                                        errorMessage = error ?: "Shikimori bağlantısı başarısız oldu."
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
