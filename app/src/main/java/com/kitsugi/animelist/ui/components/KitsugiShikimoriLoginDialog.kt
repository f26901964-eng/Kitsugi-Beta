package com.kitsugi.animelist.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    var clientId by remember { mutableStateOf(ExternalAuthManager.getShikimoriClientId(context)) }
    var clientSecret by remember { mutableStateOf(ExternalAuthManager.getShikimoriClientSecret(context)) }
    var authCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, shikimoriBrandColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shikimoriBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, shikimoriBrandColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🌸", fontSize = 24.sp)
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

                // Scrollable steps
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.4f))
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

                    // Step 1: Open Shikimori to generate App credentials
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, shikimoriBrandColor.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Adım: Shikimori'de Uygulama Aç",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = shikimoriBrandColor
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Aşağıdaki butona dokunun, açılan sayfada form hazırdır. Sadece 'Создать (Kaydet)' butonuna basın.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val newAppUrl = ShikimoriApiClient.buildNewApplicationUrl()
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newAppUrl))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = shikimoriBrandColor,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shikimori Uygulama Sayfasını Aç", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Step 2: Paste Client ID & Client Secret
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "2. Adım: Verilen ID ve Secret'ı Yapıştırın",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KitsugiColors.TextPrimary
                                )
                            )

                            // Client ID field
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = {
                                    clientId = it.trim()
                                    errorMessage = null
                                },
                                label = { Text("Client ID (Uygulama Kimliği)") },
                                placeholder = { Text("Oluşturulan Client ID") },
                                trailingIcon = {
                                    FilledTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                clientId = text
                                                errorMessage = null
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("Yapıştır", fontSize = 10.sp)
                                    }
                                },
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Client Secret field
                            OutlinedTextField(
                                value = clientSecret,
                                onValueChange = {
                                    clientSecret = it.trim()
                                    errorMessage = null
                                },
                                label = { Text("Client Secret (Gizli Anahtar)") },
                                placeholder = { Text("Oluşturulan Secret") },
                                trailingIcon = {
                                    FilledTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                clientSecret = text
                                                errorMessage = null
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("Yapıştır", fontSize = 10.sp)
                                    }
                                },
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Step 3: Authorize & Get Code
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "3. Adım: İzin Ver ve Kodu Al",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KitsugiColors.TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ID'nizi yapıştırdıktan sonra aşağıdaki butona tıklayın, 'Разрешить (İzin Ver)' deyin ve açılan ekrandaki kodu kopyalayın.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    if (clientId.isBlank()) {
                                        errorMessage = "Lütfen önce yukarıdaki Client ID kutusunu doldurun."
                                        return@FilledTonalButton
                                    }
                                    val authUrl = ShikimoriApiClient.buildAuthorizeUrl(clientId)
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                    context.startActivity(intent)
                                },
                                enabled = clientId.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = shikimoriBrandColor.copy(alpha = 0.15f),
                                    contentColor = shikimoriBrandColor
                                )
                            ) {
                                Icon(Icons.Rounded.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tarayıcıda Onayla ve Kod Al", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Auth code input
                            OutlinedTextField(
                                value = authCode,
                                onValueChange = {
                                    authCode = it.trim()
                                    errorMessage = null
                                },
                                label = { Text("Yetkilendirme Kodu (Code)") },
                                placeholder = { Text("Tarayıcıdan aldığınız kod") },
                                trailingIcon = {
                                    FilledTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                authCode = text
                                                errorMessage = null
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("Yapıştır", fontSize = 10.sp)
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

                    Button(
                        onClick = {
                            if (clientId.isBlank() || clientSecret.isBlank() || authCode.isBlank()) {
                                errorMessage = "Lütfen Client ID, Secret ve Yetkilendirme Kodunu girin."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            onLogin(clientId, clientSecret, authCode) { success, error ->
                                isLoading = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = error ?: "Shikimori bağlantısı başarısız"
                                }
                            }
                        },
                        enabled = !isLoading && clientId.isNotBlank() && clientSecret.isNotBlank() && authCode.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = shikimoriBrandColor,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Bağlan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
