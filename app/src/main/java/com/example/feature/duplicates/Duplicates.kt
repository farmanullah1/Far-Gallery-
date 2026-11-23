package com.example.feature.duplicates

import android.content.ContentResolver
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class DupItem(val row: MediaRow, val isFavorite: Boolean, val inUserAlbum: Boolean) {
    val protected get() = isFavorite || inUserAlbum
}

data class DupGroup(val hash: String, val items: List<DupItem>)

sealed interface DupScan {
    data class Progress(val hashed: Int, val total: Int) : DupScan
    data class Done(val groups: List<DupGroup>) : DupScan
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
 * - the last remaining copy in any group can never be ticked
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
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    val marked = remember(groups) {
        mutableStateMapOf<Long, Boolean>().apply {
            DuplicateLogic.smartSelectExtraCopies(groups).forEach { this[it] = true }
        }
    }
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
                                        Box(Modifier.size(120.dp).clip(RoundedCornerShape(10.dp))) {
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
}
