package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceAuthentication
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uses the source's real auth provider/preferences, with its website session as a fallback. */
@Composable
fun MangaSourceLoginDialog(
    source: MangaSource,
    onSessionUpdated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    var authentication by remember(source.stableSourceKey()) {
        mutableStateOf<MangaSourceAuthentication?>(null)
    }
    var values by remember(source.stableSourceKey()) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var authorized by remember(source.stableSourceKey()) { mutableStateOf<Boolean?>(null) }
    var username by remember(source.stableSourceKey()) { mutableStateOf<String?>(null) }
    var loading by remember(source.stableSourceKey()) { mutableStateOf(true) }
    var saving by remember(source.stableSourceKey()) { mutableStateOf(false) }
    var error by remember(source.stableSourceKey()) { mutableStateOf<String?>(null) }
    var webUrl by remember(source.stableSourceKey()) { mutableStateOf<String?>(null) }

    LaunchedEffect(source.stableSourceKey()) {
        authentication = try {
            withContext(Dispatchers.IO) { source.authentication() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message
            null
        }
        authorized = try {
            kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                withContext(Dispatchers.IO) { source.isAuthenticated() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
        username = if (authorized == true) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    withContext(Dispatchers.IO) { source.authenticatedUsername() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
        } else null
        values = authentication?.credentialFields.orEmpty().associate { it.key to it.value }
        loading = false
    }

    val loginUrl = authentication?.loginUrl ?: source.webSessionUrl
    if (webUrl != null) {
        KitsugiWebViewDialog(
            title = stringResource(R.string.manga_source_login_title, source.name),
            url = webUrl!!,
            cookieSyncUrls = listOfNotNull(source.webSessionUrl, authentication?.loginUrl),
            onDismiss = {
                webUrl = null
                onSessionUpdated()
                onDismiss()
            },
        )
        return
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountCircle, null, tint = accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.manga_source_login_title, source.name),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    stringResource(R.string.manga_source_login_description),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (authorized) {
                    true -> Text(
                        if (username.isNullOrBlank()) {
                            stringResource(R.string.manga_source_login_status_authorized)
                        } else {
                            stringResource(R.string.manga_source_login_status_user, username!!)
                        },
                        color = KitsugiColors.AccentGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    false -> Text(
                        stringResource(R.string.manga_source_login_status_unauthorized),
                        color = KitsugiColors.AccentRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    null -> Text(
                        stringResource(R.string.manga_source_login_status_unknown),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = accent)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(weight = 1f, fill = false)
                            .heightIn(max = 430.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val fields = authentication?.credentialFields.orEmpty()
                        if (fields.isNotEmpty()) {
                            Text(
                                stringResource(R.string.manga_source_login_credentials_description),
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            fields.forEach { field ->
                                OutlinedTextField(
                                    value = values[field.key].orEmpty(),
                                    onValueChange = { values = values + (field.key to it) },
                                    label = { Text(field.title) },
                                    visualTransformation = if (field.isSecret) {
                                        PasswordVisualTransformation()
                                    } else {
                                        androidx.compose.ui.text.input.VisualTransformation.None
                                    },
                                    singleLine = true,
                                    enabled = !saving,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (loginUrl != null) {
                            Text(
                                stringResource(R.string.manga_source_login_web_description),
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                error?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = KitsugiColors.AccentRed, style = MaterialTheme.typography.bodySmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loginUrl != null) {
                        OutlinedButton(
                            onClick = { webUrl = loginUrl },
                            enabled = !loading && !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.manga_source_login_open_site))
                        }
                    }
                    if (authentication?.credentialFields?.isNotEmpty() == true) {
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    error = null
                                    try {
                                        source.saveCredentials(values)
                                        onSessionUpdated()
                                        onDismiss()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        error = failure.message
                                            ?: failure.javaClass.simpleName
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(stringResource(R.string.manga_source_login_save_retry))
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.kapat))
                    }
                }
            }
        }
    }
}

@Composable
fun MangaSourceAuthBanner(
    source: MangaSource,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalKitsugiAccent.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.AccentRed.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = KitsugiColors.AccentRed, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        stringResource(R.string.manga_source_login_required_title),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.manga_source_login_required_message, source.name),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLogin) {
                    Text(stringResource(R.string.manga_source_login_action))
                }
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.downloads_manga_retry), color = accent)
                }
            }
        }
    }
}
