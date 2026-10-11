package com.kitsugi.animelist.ui.screens.detail.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * FP-18 – Component representing the episodes list section of detail pages.
 */
@Composable
fun EpisodesSection(
    episodeCount: Int,
    onEpisodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_episodes_1_s_b40a1e6, episodeCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (episodeCount == 0) {
            Text(
                text = stringResource(R.string.inline_episode_information_is_unavailable_452c7f0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 1..minOf(episodeCount, 4)) {
                    KitsugiButton(onClick = { onEpisodeClick(i) }) {
                        Text(stringResource(R.string.inline_episode_1_s_dc662be, i))
                    }
                }
            }
        }
    }
}
