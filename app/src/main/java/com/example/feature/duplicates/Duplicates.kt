package com.example.feature.duplicates

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.media.MediaRow
import com.example.core.storage.Checksums
import com.example.ui.components.FarFilterPill
import com.example.ui.components.FarTopBar
import com.example.ui.feedback.EmptyState
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.max

data class DupItem(val row: MediaRow, val isFavorite: Boolean, val inUserAlbum: Boolean) {
    val protected get() = isFavorite || inUserAlbum
}

data class DupGroup(val hash: String, val items: List<DupItem>)

sealed interface DupScan {
    data class Progress(val hashed: Int, val total: Int) : DupScan
    data class Done(val groups: List<DupGroup>) : DupScan
}

enum class DupReviewMode(val labelRes: Int) {
    Exact(R.string.dup_tab_exact),
    Bursts(R.string.dup_tab_bursts),
    Blurry(R.string.dup_tab_blurry),
}

data class BurstGroup(
    val id: String,
    val folderLabel: String,
    val bestFrameId: Long,
    val items: List<DupItem>,
)

data class BlurCandidate(
    val item: DupItem,
    val sharpnessScore: Double,
    val resolutionLabel: String,
)

sealed interface BlurScan {
    data class Progress(val checked: Int, val total: Int) : BlurScan
    data class Done(val blurry: List<BlurCandidate>) : BlurScan
}

/** Exact duplicates: group by size (cheap), then stream SHA-256 only for same-size candidates. Cancellable; unreadable files skipped. */
class DuplicateFinder(private val resolver: ContentResolver) {
    fun scan(all: List<MediaRow>, favorites: Set<Long>, inAlbums: Set<Long>): Flow<DupScan> = flow {
        val candidates = all.groupBy { it.sizeBytes }.filter { it.key > 0 && it.value.size > 1 }.values.flatten()
        val byHash = HashMap<String, MutableList<MediaRow>>()
        candidates.forEachIndexed { i, row ->
            currentCoroutineContext().ensureActive()
            emit(DupScan.Progress(i, candidates.size))
            val h = (farCatching {
                val input = resolver.openInputStream(row.uri) ?: throw java.io.IOException("openInputStream returned null")
                input.use(Checksums::sha256)
            } as? FarResult.Ok)?.value ?: return@forEachIndexed
            byHash.getOrPut(h) { ArrayList() } += row
        }
        emit(
            DupScan.Done(
                byHash.filter { it.value.size > 1 }.map { (h, rows) ->
                    DupGroup(h, rows.map { DupItem(it, it.id in favorites, it.id in inAlbums) })
                }
            )
        )
    }.flowOn(Dispatchers.IO)
}

/** Groups rapid consecutive photos in the same folder and ranks a suggested Best Frame. */
object BurstDetector {
    fun findBursts(
        rows: List<MediaRow>,
        favorites: Set<Long>,
        inAlbums: Set<Long>,
        maxGapMs: Long = 8_000L,
    ): List<BurstGroup> {
        val photosByFolder = rows
            .filter { !it.isVideo && it.takenMs > 0L }
            .groupBy { it.relativePath?.trimEnd('/') ?: "Media" }

        val out = ArrayList<BurstGroup>()
        photosByFolder.forEach { (folder, folderPhotos) ->
            val sorted = folderPhotos.sortedBy { it.takenMs }
            if (sorted.size < 2) return@forEach
            val currentRun = ArrayList<MediaRow>()
            currentRun += sorted.first()
            fun flushRun() {
                if (currentRun.size >= 2) {
                    val items = currentRun.map { DupItem(it, it.id in favorites, it.id in inAlbums) }
                    val best = items.maxWithOrNull(
                        compareBy<DupItem> { it.protected }
                            .thenBy { it.row.width.toLong() * it.row.height.toLong() }
                            .thenBy { it.row.sizeBytes }
                    )?.row?.id ?: items.first().row.id
                    out += BurstGroup(
                        id = "${folder}_${currentRun.first().id}",
                        folderLabel = folder,
                        bestFrameId = best,
                        items = items,
                    )
                }
                currentRun.clear()
            }
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val curr = sorted[i]
                if ((curr.takenMs - prev.takenMs) <= maxGapMs) {
                    currentRun += curr
                } else {
                    flushRun()
                    currentRun += curr
                }
            }
            flushRun()
        }
        return out.sortedByDescending { g -> g.items.maxOfOrNull { it.row.takenMs } ?: 0L }
    }
}

/** Pure offline 3x3 Laplacian variance sharpness estimator. Never deletes automatically. */
object BlurDetector {
    const val DEFAULT_BLUR_THRESHOLD = 110.0

    fun laplacianVariance(luma: FloatArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || luma.size < width * height) return 0.0
        val count = (width - 2) * (height - 2)
        val responses = DoubleArray(count)
        var idx = 0
        var sum = 0.0
        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val c = luma[rowOffset + x]
                val top = luma[(y - 1) * width + x]
                val bottom = luma[(y + 1) * width + x]
                val left = luma[rowOffset + x - 1]
                val right = luma[rowOffset + x + 1]
                val lap = (4.0 * c) - top - bottom - left - right
                responses[idx++] = lap
                sum += lap
            }
        }
        val mean = sum / count.toDouble()
        var sqDiff = 0.0
        for (i in 0 until count) {
            val d = responses[i] - mean
            sqDiff += d * d
        }
        return sqDiff / count.toDouble()
    }

    fun scoreBitmap(bitmap: Bitmap): Double {
        val targetMax = 192
        val maxDim = max(bitmap.width, bitmap.height)
        val working = if (maxDim > targetMax && bitmap.width > 0 && bitmap.height > 0) {
            val scale = targetMax.toFloat() / maxDim.toFloat()
            val w = max(3, (bitmap.width * scale).toInt())
            val h = max(3, (bitmap.height * scale).toInt())
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }
        return try {
            val w = working.width
            val h = working.height
            if (w < 3 || h < 3) return 0.0
            val pixels = IntArray(w * h)
            working.getPixels(pixels, 0, w, 0, 0, w, h)
            val luma = FloatArray(w * h)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                luma[i] = (0.299f * r) + (0.587f * g) + (0.114f * b)
            }
            laplacianVariance(luma, w, h)
        } finally {
            if (working !== bitmap) {
                working.recycle()
            }
        }
    }

    fun scanPhotos(
        resolver: ContentResolver,
        rows: List<MediaRow>,
        favorites: Set<Long>,
        inAlbums: Set<Long>,
        threshold: Double = DEFAULT_BLUR_THRESHOLD,
    ): Flow<BlurScan> = flow {
        val photos = rows.filter { !it.isVideo }
        val blurry = ArrayList<BlurCandidate>()
        photos.forEachIndexed { index, row ->
            currentCoroutineContext().ensureActive()
            emit(BlurScan.Progress(index, photos.size))
            val score = (farCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(row.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val longest = max(bounds.outWidth, bounds.outHeight)
                val sample = if (longest > 256) (longest / 256).coerceAtLeast(1) else 1
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = resolver.openInputStream(row.uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                    ?: return@farCatching null
                try {
                    scoreBitmap(bmp)
                } finally {
                    bmp.recycle()
                }
            } as? FarResult.Ok)?.value ?: return@forEachIndexed

            if (score < threshold) {
                val res = if (row.width > 0 && row.height > 0) "${row.width}×${row.height}" else "Photo"
                blurry += BlurCandidate(
                    item = DupItem(row, row.id in favorites, row.id in inAlbums),
                    sharpnessScore = score,
                    resolutionLabel = res,
                )
            }
        }
        emit(BlurScan.Done(blurry.sortedBy { it.sharpnessScore }))
    }.flowOn(Dispatchers.IO)
}

object DuplicateLogic {
    /**
     * Marks unprotected duplicate copies while guaranteeing at least one copy (ideally protected,
     * oldest/highest quality kept) remains unticked in every group.
     */
    fun smartSelectExtraCopies(groups: List<DupGroup>): Set<Long> = buildSet {
        groups.forEach { g ->
            val protectedCount = g.items.count { it.protected }
            val unprotected = g.items.filter { !it.protected }
            val toMark = if (protectedCount > 0) unprotected else unprotected.drop(1)
            toMark.forEach { add(it.row.id) }
        }
    }

    fun reclaimableBytes(groups: List<DupGroup>, markedIds: Set<Long>): Long =
        groups.sumOf { g ->
            g.items.filter { it.row.id in markedIds }.sumOf { it.row.sizeBytes.coerceAtLeast(0L) }
        }
}

/**
 * Review UI. Safety rules enforced here:
 * - only unprotected extra copies are pre-ticked
 * - the last remaining copy in any duplicate/burst group can never be ticked
 * - protected items (favorite / in a user album) stay unticked and labeled
 * - deletion goes through Android system Trash consent
 */
@Composable
fun DuplicatesScreen(
    scanning: DupScan.Progress?,
    groups: List<DupGroup>,
    onBack: () -> Unit,
    onTrash: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    blurScanning: BlurScan.Progress? = null,
    blurryItems: List<BlurCandidate> = emptyList(),
    burstGroups: List<BurstGroup> = emptyList(),
    onStartBlurScan: (() -> Unit)? = null,
    onPreview: ((MediaRow) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var mode by remember { mutableStateOf(DupReviewMode.Exact) }

    val marked = remember(groups) {
        mutableStateMapOf<Long, Boolean>().apply {
            DuplicateLogic.smartSelectExtraCopies(groups).forEach { this[it] = true }
        }
    }
    val burstMarked = remember(burstGroups) { mutableStateMapOf<Long, Boolean>() }
    val blurMarked = remember(blurryItems) { mutableStateMapOf<Long, Boolean>() }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        FarTopBar(
            title = stringResource(R.string.collection_duplicates),
            onBack = onBack,
            backTestTag = "duplicates_back_button",
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DupReviewMode.entries) { m ->
                FarFilterPill(
                    label = stringResource(m.labelRes),
                    selected = mode == m,
                    onClick = {
                        mode = m
                        if (m == DupReviewMode.Blurry) {
                            onStartBlurScan?.invoke()
                        }
                    },
                    modifier = Modifier.testTag("dup_tab_${m.name.lowercase()}"),
                )
            }
        }

        when (mode) {
            DupReviewMode.Exact -> {
                if (scanning != null) {
                    Column(Modifier.padding(horizontal = FarSpacing.screenPadding)) {
                        LinearProgressIndicator(
                            progress = { if (scanning.total == 0) 0f else scanning.hashed.toFloat() / scanning.total },
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                        Text("Checking ${scanning.hashed} of ${scanning.total} files…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (scanning == null && groups.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EmptyState(
                            Icons.Filled.ContentCopy,
                            stringResource(R.string.dup_no_title),
                            stringResource(R.string.dup_no_body),
                        )
                    }
                } else {
                    val activeMarkedIds = marked.filterValues { it }.keys
                    val reclaimable = DuplicateLogic.reclaimableBytes(groups, activeMarkedIds)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 6.dp)
                            .background(semantic.elevatedSurface, RoundedCornerShape(18.dp))
                            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.dup_reclaimable_summary,
                                groups.size,
                                Formatter.formatFileSize(ctx, reclaimable),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FarFilterPill(
                                label = stringResource(R.string.dup_smart_select),
                                selected = activeMarkedIds.isNotEmpty(),
                                onClick = {
                                    marked.clear()
                                    DuplicateLogic.smartSelectExtraCopies(groups).forEach { marked[it] = true }
                                },
                                modifier = Modifier.testTag("dup_smart_select_pill"),
                            )
                            FarFilterPill(
                                label = stringResource(R.string.dup_clear_marks),
                                selected = activeMarkedIds.isEmpty(),
                                onClick = { marked.clear() },
                                modifier = Modifier.testTag("dup_clear_marks_pill"),
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(groups, key = { it.hash }) { g ->
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(semantic.elevatedSurface, RoundedCornerShape(20.dp))
                                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(20.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.dup_identical_copies, g.items.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(g.items, key = { it.row.id }) { d ->
                                            val ticked = marked[d.row.id] == true
                                            val othersKept = g.items.count { it.row.id != d.row.id && marked[it.row.id] != true }
                                            Column(Modifier.size(width = 124.dp, height = 196.dp)) {
                                                Box(
                                                    Modifier
                                                        .size(120.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable(enabled = onPreview != null) { onPreview?.invoke(d.row) }
                                                ) {
                                                    AsyncImage(d.row.uri, d.row.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                                Text(d.row.name, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                                if (d.protected) {
                                                    Text(
                                                        if (d.isFavorite) "Favorite — kept" else "In album — kept",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                } else {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = ticked,
                                                            onCheckedChange = { want -> if (!want || othersKept > 0) marked[d.row.id] = want },
                                                            enabled = ticked || othersKept > 0,
                                                        )
                                                        Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val chosen = groups.flatMap { it.items }.filter { marked[it.row.id] == true }
                    Button(
                        onClick = { onTrash(chosen.map { it.row.uri }) },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("duplicates_trash_button"),
                    ) {
                        Text("Move ${chosen.size} to Recently Deleted")
                    }
                }
            }

            DupReviewMode.Bursts -> {
                if (burstGroups.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EmptyState(
                            Icons.Filled.BurstMode,
                            stringResource(R.string.dup_burst_empty_title),
                            stringResource(R.string.dup_burst_empty_body),
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(burstGroups, key = { it.id }) { burst ->
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(semantic.elevatedSurface, RoundedCornerShape(20.dp))
                                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(20.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.dup_burst_count, burst.items.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(burst.items, key = { it.row.id }) { d ->
                                            val isBest = d.row.id == burst.bestFrameId
                                            val ticked = burstMarked[d.row.id] == true
                                            val othersKept = burst.items.count { it.row.id != d.row.id && burstMarked[it.row.id] != true }
                                            Column(Modifier.size(width = 132.dp, height = 216.dp)) {
                                                Box(
                                                    Modifier
                                                        .size(124.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(
                                                            width = if (isBest) 2.dp else 0.dp,
                                                            color = if (isBest) MaterialTheme.colorScheme.primary else semantic.cardBorder,
                                                            shape = RoundedCornerShape(10.dp),
                                                        )
                                                        .clickable(enabled = onPreview != null) { onPreview?.invoke(d.row) }
                                                ) {
                                                    AsyncImage(d.row.uri, d.row.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    if (isBest) {
                                                        Text(
                                                            stringResource(R.string.dup_burst_best),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(4.dp)
                                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                                Text(d.row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                                Text(
                                                    Formatter.formatShortFileSize(ctx, d.row.sizeBytes),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (d.protected) {
                                                    Text(
                                                        if (d.isFavorite) "Favorite — kept" else "In album — kept",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                } else {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = ticked,
                                                            onCheckedChange = { want -> if (!want || othersKept > 0) burstMarked[d.row.id] = want },
                                                            enabled = ticked || othersKept > 0,
                                                        )
                                                        Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val chosen = burstGroups.flatMap { it.items }.filter { burstMarked[it.row.id] == true }
                    Button(
                        onClick = { onTrash(chosen.map { it.row.uri }) },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("bursts_trash_button"),
                    ) {
                        Text("Move ${chosen.size} to Recently Deleted")
                    }
                }
            }

            DupReviewMode.Blurry -> {
                if (blurScanning != null) {
                    Column(Modifier.padding(horizontal = FarSpacing.screenPadding)) {
                        LinearProgressIndicator(
                            progress = { if (blurScanning.total == 0) 0f else blurScanning.checked.toFloat() / blurScanning.total },
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                        Text(
                            stringResource(R.string.dup_blurry_scanning, blurScanning.checked, blurScanning.total),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (blurScanning == null && blurryItems.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EmptyState(
                            Icons.Filled.BlurOn,
                            stringResource(R.string.dup_blurry_empty_title),
                            stringResource(R.string.dup_blurry_empty_body),
                        )
                    }
                } else {
                    val markedBytes = blurryItems.filter { blurMarked[it.item.row.id] == true }
                        .sumOf { it.item.row.sizeBytes.coerceAtLeast(0L) }
                    Text(
                        stringResource(
                            R.string.dup_blurry_summary,
                            blurryItems.size,
                            Formatter.formatFileSize(ctx, markedBytes),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = FarSpacing.screenPadding, vertical = 8.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(blurryItems, key = { it.item.row.id }) { candidate ->
                            val r = candidate.item.row
                            val ticked = blurMarked[r.id] == true
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(semantic.elevatedSurface)
                                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                                    .clickable(enabled = onPreview != null) { onPreview?.invoke(r) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(
                                    model = r.uri,
                                    contentDescription = r.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${candidate.resolutionLabel} · ${Formatter.formatShortFileSize(ctx, r.sizeBytes)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(R.string.dup_blurry_score, candidate.sharpnessScore),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    if (candidate.item.protected) {
                                        Text(
                                            if (candidate.item.isFavorite) "Favorite — kept" else "In album — kept",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (!candidate.item.protected) {
                                    Checkbox(
                                        checked = ticked,
                                        onCheckedChange = { blurMarked[r.id] = it },
                                        modifier = Modifier.testTag("blur_select_${r.id}"),
                                    )
                                }
                            }
                        }
                    }
                    val chosen = blurryItems.map { it.item }.filter { blurMarked[it.row.id] == true }
                    Button(
                        onClick = { onTrash(chosen.map { it.row.uri }) },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("blurry_trash_button"),
                    ) {
                        Text("Move ${chosen.size} to Recently Deleted")
                    }
                }
            }
        }
    }
}
