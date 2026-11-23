package com.example.feature.storage

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.MediaRow
import com.example.ui.components.FarFilterPill
import com.example.ui.components.FarTopBar
import com.example.ui.feedback.EmptyState
import com.example.ui.theme.FarPalette
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFont

enum class StorageCategoryFilter(val labelRes: Int) {
    All(R.string.storage_filter_all),
    Videos(R.string.storage_filter_videos),
    Camera(R.string.storage_filter_camera),
    Screenshots(R.string.storage_filter_screenshots),
    Other(R.string.storage_filter_other),
}

@Immutable
data class StorageBucket(
    val label: String,
    val bytes: Long,
    val count: Int,
    val color: Color,
)

@Immutable
data class StorageSummary(
    val totalBytes: Long,
    val totalCount: Int,
    val buckets: List<StorageBucket>,
    val largeFiles: List<MediaRow>,
)

object StorageAnalyzer {
    fun categoryOf(row: MediaRow): StorageCategoryFilter = when {
        row.isVideo -> StorageCategoryFilter.Videos
        row.relativePath?.contains("Screenshots", ignoreCase = true) == true -> StorageCategoryFilter.Screenshots
        row.relativePath?.contains("DCIM/Camera", ignoreCase = true) == true -> StorageCategoryFilter.Camera
        else -> StorageCategoryFilter.Other
    }

    fun summarize(
        rows: List<MediaRow>,
        minLargeBytes: Long = 10L * 1024 * 1024,
        categoryFilter: StorageCategoryFilter = StorageCategoryFilter.All,
    ): StorageSummary {
        var videoBytes = 0L
        var videoCount = 0
        var camBytes = 0L
        var camCount = 0
        var shotBytes = 0L
        var shotCount = 0
        var otherBytes = 0L
        var otherCount = 0

        for (r in rows) {
            val sz = r.sizeBytes.coerceAtLeast(0L)
            when (categoryOf(r)) {
                StorageCategoryFilter.Videos -> {
                    videoBytes += sz
                    videoCount++
                }
                StorageCategoryFilter.Screenshots -> {
                    shotBytes += sz
                    shotCount++
                }
                StorageCategoryFilter.Camera -> {
                    camBytes += sz
                    camCount++
                }
                StorageCategoryFilter.Other, StorageCategoryFilter.All -> {
                    otherBytes += sz
                    otherCount++
                }
            }
        }

        val buckets = listOf(
            StorageBucket("Videos", videoBytes, videoCount, FarPalette.Purple),
            StorageBucket("Camera Photos", camBytes, camCount, FarPalette.Blue),
            StorageBucket("Screenshots", shotBytes, shotCount, FarPalette.Orange),
            StorageBucket("Other Photos", otherBytes, otherCount, FarPalette.Green),
        ).filter { it.count > 0 || it.bytes > 0L }

        val total = buckets.sumOf { it.bytes }
        val largest = rows
            .filter { it.sizeBytes >= minLargeBytes }
            .filter { categoryFilter == StorageCategoryFilter.All || categoryOf(it) == categoryFilter }
            .sortedByDescending { it.sizeBytes }
        return StorageSummary(total, rows.size, buckets, largest)
    }
}

@Composable
fun StorageAnalyzerScreen(
    rows: List<MediaRow>,
    onBack: () -> Unit,
    onPreview: (MediaRow) -> Unit,
    onTrash: (List<Uri>) -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var thresholdBytes by remember { mutableLongStateOf(10L * 1024 * 1024) }
    var category by remember { mutableStateOf(StorageCategoryFilter.All) }
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }
    val summary = remember(rows, thresholdBytes, category) {
        StorageAnalyzer.summarize(rows, thresholdBytes, category)
    }

    val semantic = LocalSemanticColors.current
    val chosenUris = summary.largeFiles.filter { selectedIds[it.id] == true }.map { it.uri }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            FarTopBar(
                title = stringResource(R.string.storage_title),
                subtitle = "${summary.totalCount} items · ${Formatter.formatFileSize(ctx, summary.totalBytes)} indexed",
                onBack = onBack,
                backTestTag = "storage_back_button",
            )
        },
        bottomBar = {
            if (chosenUris.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    Button(
                        onClick = {
                            onTrash(chosenUris)
                            selectedIds.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("storage_trash_selected_button"),
                    ) {
                        Text(stringResource(R.string.storage_trash_selected, chosenUris.size))
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(semantic.elevatedSurface, RoundedCornerShape(22.dp))
                            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(22.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.storage_breakdown),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (summary.totalBytes > 0L) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                summary.buckets.forEach { b ->
                                    val weight = (b.bytes.toFloat() / summary.totalBytes.toFloat()).coerceAtLeast(0.03f)
                                    Box(
                                        Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(b.color)
                                    )
                                }
                            }
                        }
                        summary.buckets.forEach { b ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(10.dp).background(b.color, CircleShape))
                                    Text("${b.label} (${b.count})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    Formatter.formatFileSize(ctx, b.bytes),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.storage_large_files),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val thresholds = listOf(
                            "All > 1 MB" to 1L * 1024 * 1024,
                            "> 10 MB" to 10L * 1024 * 1024,
                            "> 25 MB" to 25L * 1024 * 1024,
                            "> 100 MB" to 100L * 1024 * 1024,
                        )
                        items(thresholds) { (label, minB) ->
                            FarFilterPill(
                                label = label,
                                selected = thresholdBytes == minB,
                                onClick = { thresholdBytes = minB },
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StorageCategoryFilter.entries) { cat ->
                            FarFilterPill(
                                label = stringResource(cat.labelRes),
                                selected = category == cat,
                                onClick = { category = cat },
                                modifier = Modifier.testTag("storage_cat_${cat.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            if (summary.largeFiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.CleaningServices,
                        title = "No large files match",
                        body = "Try lowering the minimum size chip above to inspect smaller photos or videos.",
                        modifier = Modifier.height(220.dp),
                    )
                }
            } else {
                items(summary.largeFiles, key = { it.id }) { row ->
                    val checked = selectedIds[row.id] == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else semantic.elevatedSurface)
                            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                            .clickable { onPreview(row) }
                            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked -> selectedIds[row.id] = isChecked },
                            modifier = Modifier.testTag("storage_select_${row.id}"),
                        )
                        AsyncImage(
                            model = row.uri,
                            contentDescription = row.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${Formatter.formatFileSize(ctx, row.sizeBytes)} · ${row.relativePath?.trimEnd('/') ?: "Media"}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onTrash(listOf(row.uri)) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.DeleteOutline, "Move to trash", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
