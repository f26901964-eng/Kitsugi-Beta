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
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

@Composable
fun KitsugiShikimoriLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shikimoriBrandColor = Color(0xFF8E44AD)

    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, shikimoriBrandColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shikimoriBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, shikimoriBrandColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
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
                    text = "Shikimori OAuth2 entegrasyonu ile anime ve manga listenizi senkronize edin.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
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

                    // Step 1: Guide Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Adım: Shikimori'de Uygulama Açın",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = shikimoriBrandColor
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Redirect URI: urn:ietf:wg:oauth:2.0:oob\nKapsam (Scope): user_rates",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shikimori.one/oauth/applications"))
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
                                Text("Shikimori OAuth Sayfasını Aç", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Step 2: Client ID
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = {
                            clientId = it.trim()
                            errorMessage = null
                        },
                        label = { Text("Client ID (Uygulama Kimliği)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = shikimoriBrandColor)
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = shikimoriBrandColor,
                            focusedLabelColor = shikimoriBrandColor,
                            cursorColor = shikimoriBrandColor,
                            unfocusedBorderColor = KitsugiColors.Border
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Client Secret
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = {
                            clientSecret = it.trim()
                            errorMessage = null
                        },
                        label = { Text("Client Secret (Gizli Anahtar)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = shikimoriBrandColor)
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = shikimoriBrandColor,
                            focusedLabelColor = shikimoriBrandColor,
                            cursorColor = shikimoriBrandColor,
                            unfocusedBorderColor = KitsugiColors.Border
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Step 3: Authorization button
                    if (clientId.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                val authUrl = ShikimoriApiClient.buildAuthorizeUrl(clientId)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = shikimoriBrandColor.copy(alpha = 0.2f),
                                contentColor = shikimoriBrandColor
                            )
                        ) {
                            Icon(Icons.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yetkilendirme Kodunu Al (Tarayıcıda Onayla)", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Authorization Code
                    OutlinedTextField(
                        value = authCode,
                        onValueChange = {
                            authCode = it.trim()
                            errorMessage = null
                        },
                        label = { Text("Yetkilendirme Kodu (Code)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Password, contentDescription = null, tint = shikimoriBrandColor)
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                val text = clipboard.getText()?.text.orEmpty().trim()
                                if (text.isNotBlank()) {
                                    authCode = text
                                    errorMessage = null
                                }
                            }) {
                                Icon(
                                    Icons.Rounded.ContentPaste,
                                    contentDescription = "Yapıştır",
                                    tint = shikimoriBrandColor
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = shikimoriBrandColor,
                            focusedLabelColor = shikimoriBrandColor,
                            cursorColor = shikimoriBrandColor,
                            unfocusedBorderColor = KitsugiColors.Border
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                errorMessage = "Lütfen Client ID, Secret ve Kodu girin."
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
