package com.kitsugi.animelist.ui.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

@Composable
fun KitsugiKitsuLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accentColor = LocalKitsugiAccent.current
    val focusManager = LocalFocusManager.current
    val kitsuBrandColor = Color(0xFFFD755C)

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, kitsuBrandColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon & Title
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(kitsuBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, kitsuBrandColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🦊", fontSize = 28.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Kitsu ile Bağlan",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )

                Text(
                    text = "Kitsu kullanıcı adı veya e-postanız ile şifrenizi girerek kütüphanenizi Kitsugi ile senkronize edin.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )

                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
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

                // Email / Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("E-posta veya Kullanıcı Adı") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = kitsuBrandColor)
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = kitsuBrandColor,
                        focusedLabelColor = kitsuBrandColor,
                        cursorColor = kitsuBrandColor,
                        unfocusedBorderColor = KitsugiColors.Border
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Şifre") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = kitsuBrandColor)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Şifreyi Göster",
                                tint = KitsugiColors.TextSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = kitsuBrandColor,
                        focusedLabelColor = kitsuBrandColor,
                        cursorColor = kitsuBrandColor,
                        unfocusedBorderColor = KitsugiColors.Border
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
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
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Lütfen tüm alanları doldurun."
                            } else {
                                isLoading = true
                                errorMessage = null
                                onLogin(username, password) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        onDismiss()
                                    } else {
                                        errorMessage = error ?: "Giriş yapılamadı"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            KitsugiPlasmaLoader(size = 18.dp)
                        } else {
                            Text("Giriş Yap", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
