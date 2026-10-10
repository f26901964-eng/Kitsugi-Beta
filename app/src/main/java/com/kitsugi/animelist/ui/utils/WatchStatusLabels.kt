package com.kitsugi.animelist.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.model.WatchStatus

/**
 * İzleme durumu etiketini UYGULAMA DİLİNE göre çözümler.
 * Etiketler strings.xml (values / values-en) üzerinden gelir.
 */
@Composable
@ReadOnlyComposable
fun WatchStatus.localizedLabel(): String = stringResource(labelRes)

/**
 * Composable olmayan bağlamlar (filtre predicate'leri, ViewModel metinleri,
 * collection map lambdaları) için aynı kaynağı (strings.xml) okuyan sürüm.
 */
fun WatchStatus.plainLabel(): String =
    com.kitsugi.animelist.KitsugiApplication.getInstance()?.getString(labelRes) ?: name
