package com.kitsugi.animelist.ui.components

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Uygulama içi OAuth WebView dialog'u — MAL, AniList, Simkl için.
 *
 * Eski davranış: startAuthentication() doğrudan harici tarayıcıya (Intent.ACTION_VIEW)
 * atıyordu. Harici tarayıcıda MAL'in /v1/oauth2/authorize ucu bazen HTTP 401 Basic Auth
 * challenge döndürüyor (Cloudflare / hatalı client_id / Brave kalkanları) ve tarayıcı
 * \"Oturum açın\" popup'ı gösteriyor. Kullanıcı MAL kullanıcı adı/şifresini oraya yazsa bile
 * giriş başarısız oluyor çünkü beklenen HTTP Basic değil, HTML form login.
 *
 * Yeni davranış (eski haline restore):
 *  - Uygulama içinde WebView açılır, User-Agent Chrome Mobile olarak ayarlanır.
 *  - CookieManager ile üçüncü parti çerezlere izin verilir.
 *  - onReceivedHttpAuthRequest override edilerek Basic Auth popup'ı engellenir
 *    (kullanıcıya HTML login formu gösterilir).
 *  - malapp://auth redirect'i yakalanıp ExternalAuthManager.handleAuthIntent() ile token takas edilir.
 *  - Başarılı olunca dialog kapanır ve snackbar ile bilgi verilir.
 */
@Composable
fun KitsugiOAuthWebViewDialog(
    serviceName: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current

    var authUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serviceName) {
        try {
            authUrl = ExternalAuthManager.getAuthUrlAndPrepare(context, serviceName)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Auth URL oluşturulamadı"
            onError(errorMessage ?: "Hata")
        }
    }

    val title = when (serviceName.lowercase()) {
        "mal", "myanimelist" -> "MyAnimeList Girişi"
        "anilist" -> "AniList Girişi"
        "simkl" -> "Simkl Girişi"
        else -> "Hesap Girişi"
    }

    Dialog(
        onDismissRequest = {
            CookieManager.getInstance().flush()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (serviceName.lowercase()) {
                                "mal" -> "MAL hesabınızla giriş yapın ve izin verin"
                                "anilist" -> "AniList hesabınızla giriş yapın"
                                else -> "Hesabınızla giriş yapın"
                            },
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.yenile), tint = KitsugiColors.TextSecondary)
                    }
                    IconButton(onClick = {
                        CookieManager.getInstance().flush()
                        onDismiss()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.kapat), tint = KitsugiColors.TextPrimary)
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor,
                        trackColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.1f)
                    )
                } else {
                    HorizontalDivider(color = KitsugiColors.Border)
                }

                // Error banner
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KitsugiColors.AccentRed.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // WebView Container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (authUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            if (url != null) {
                                                // OAuth redirect yakala
                                                if (url.startsWith("malapp://auth")) {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    ExternalAuthManager.handleAuthIntent(
                                                        ctx,
                                                        intent,
                                                        onSuccess = { svc ->
                                                            android.widget.Toast.makeText(
                                                                ctx,
                                                                "${svc.uppercase()} bağlantısı başarılı",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                            onSuccess(svc)
                                                            onDismiss()
                                                        },
                                                        onError = { err ->
                                                            errorMessage = err
                                                            onError(err)
                                                        }
                                                    )
                                                    return
                                                }
                                            }
                                            isLoading = true
                                            progress = 0
                                        }

                                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                            isLoading = false
                                            // Cookie sync for Cloudflare bypass
                                            val finalUrl = pageUrl ?: authUrl ?: ""
                                            try {
                                                CookieManager.getInstance().flush()
                                                // Sync to OkHttp if possible
                                                val nh = try {
                                                    uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java)
                                                } catch (_: Exception) {
                                                    eu.kanade.tachiyomi.network.NetworkHelper(ctx)
                                                }
                                                nh.cookieJar.syncFromWebView(finalUrl)
                                            } catch (_: Exception) {}
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            if (url != null && url.startsWith("malapp://auth")) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                ExternalAuthManager.handleAuthIntent(
                                                    ctx,
                                                    intent,
                                                    onSuccess = { svc ->
                                                        android.widget.Toast.makeText(
                                                            ctx,
                                                            "${svc.uppercase()} bağlantısı başarılı",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                        onSuccess(svc)
                                                        onDismiss()
                                                    },
                                                    onError = { err ->
                                                        errorMessage = err
                                                        onError(err)
                                                    }
                                                )
                                                return true
                                            }
                                            return false
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val requestUrl = request?.url?.toString()
                                            if (requestUrl != null && requestUrl.startsWith("malapp://auth")) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                                ExternalAuthManager.handleAuthIntent(
                                                    ctx,
                                                    intent,
                                                    onSuccess = { svc ->
                                                        android.widget.Toast.makeText(
                                                            ctx,
                                                            "${svc.uppercase()} bağlantısı başarılı",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                        onSuccess(svc)
                                                        onDismiss()
                                                    },
                                                    onError = { err ->
                                                        errorMessage = err
                                                        onError(err)
                                                    }
                                                )
                                                return true
                                            }
                                            return false
                                        }

                                        // FIX: MAL'in bazen döndürdüğü HTTP 401 Basic Auth challenge'ını engelle
                                        // Eski tarayıcı popup'ı \"Oturum açın\" diye çıkıyordu ve kullanıcı doğru bilgileri
                                        // girse bile giriş başarısız oluyordu. WebView'da bu callback'i override edip
                                        // iptal ederek HTML login formunun gösterilmesini sağlıyoruz.
                                        override fun onReceivedHttpAuthRequest(
                                            view: WebView?,
                                            handler: android.webkit.HttpAuthHandler?,
                                            host: String?,
                                            realm: String?
                                        ) {
                                            // Basic Auth popup'ını gösterme, iptal et ve kullanıcıya bilgi ver
                                            handler?.cancel()
                                            errorMessage = "MAL temel kimlik doğrulama istedi (beklenmeyen). Lütfen sayfayı yenileyin veya tarayıcı çerezlerini temizleyin. Host: $host"
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: android.webkit.WebResourceError?
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                errorMessage = "Sayfa yüklenemedi: ${error?.description}"
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress
                                            if (newProgress >= 100) {
                                                isLoading = false
                                            }
                                        }
                                    }
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        // Modern Chrome Mobile UA — Cloudflare ve MAL WAF'ı için kritik
                                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    loadUrl(authUrl!!)
                                    webViewInstance = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (errorMessage == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Güvenli bağlantı • ${when(serviceName.lowercase()){ "mal"->"myanimelist.net"; "anilist"->"anilist.co"; else->"simkl.com" }}",
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (errorMessage != null) {
                            Text(
                                text = "Sorun mu var? Tarayıcıda açmayı deneyin",
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (errorMessage != null) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val extUrl = ExternalAuthManager.getAuthUrlAndPrepare(context, serviceName)
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(extUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Tarayıcıda Aç", color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(
                            onClick = {
                                CookieManager.getInstance().flush()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(stringResource(R.string.kapat), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
