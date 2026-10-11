package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.diagnostics.PlatformErrorPlatform
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.KitsugiColors

/** Searchable, localized reference list for stable Manga Source and CloudStream error codes. */
@Composable
fun PlatformErrorCatalogScreen() {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPlatform by rememberSaveable { mutableStateOf("ALL") }

    val platform = when (selectedPlatform) {
        "MANGA" -> PlatformErrorPlatform.Manga
        "CLOUDSTREAM" -> PlatformErrorPlatform.CloudStream
        else -> null
    }
    val entries = remember(query, selectedPlatform, context) {
        PlatformErrorCode.all(platform)
            .filter { definition ->
                if (query.isBlank()) return@filter true
                val searchable = listOf(
                    definition.id,
                    definition.category,
                    context.getString(definition.titleRes),
                    context.getString(definition.causeRes),
                    context.getString(definition.actionRes),
                ).joinToString(" ").lowercase()
                searchable.contains(query.trim().lowercase())
            }
            .sortedBy { it.id }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = KitsugiColors.Background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.platform_error_catalog_count, entries.size),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text(stringResource(R.string.platform_error_catalog_search)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selectedPlatform == "ALL",
                    onClick = { selectedPlatform = "ALL" },
                    label = { Text(stringResource(R.string.platform_error_filter_all)) },
                )
                FilterChip(
                    selected = selectedPlatform == "MANGA",
                    onClick = { selectedPlatform = "MANGA" },
                    label = { Text(stringResource(R.string.platform_error_filter_manga)) },
                )
                FilterChip(
                    selected = selectedPlatform == "CLOUDSTREAM",
                    onClick = { selectedPlatform = "CLOUDSTREAM" },
                    label = { Text(stringResource(R.string.platform_error_filter_cloudstream)) },
                )
            }

            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.platform_error_catalog_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    color = KitsugiColors.TextMuted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.id }) { definition ->
                        PlatformErrorExplanationBlock(
                            code = definition.id,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
