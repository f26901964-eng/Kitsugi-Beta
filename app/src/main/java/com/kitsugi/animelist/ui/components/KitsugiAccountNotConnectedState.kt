package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text

/** Shared disconnected-account card used in both Profile and My List. */
@Composable
fun KitsugiAccountNotConnectedState(
    platformName: String,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    actionTextOverride: String? = null
) {
    val title = titleOverride ?: stringResource(
        R.string.inline_1_s_account_not_connected_086498f,
        platformName
    )
    val subtitle = subtitleOverride ?: stringResource(
        R.string.profil_istatistiklerinizi_favorilerinizi_ve_sosyal_aktivitelerin
    )
    val actionText = actionTextOverride ?: stringResource(R.string.hesabi_bagla)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KitsugiPlatformLogo(
                    platformId = platformName,
                    size = 56.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                KitsugiButton(onClick = onConnectClick) {
                    Text(
                        text = actionText,
                        color = KitsugiColors.Background,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
