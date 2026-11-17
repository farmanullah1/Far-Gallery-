package com.example.feature.duplicates

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.media.MediaRow
import com.example.core.storage.Checksums
import com.example.ui.feedback.EmptyState
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
                resolver.openInputStream(row.uri)!!.use(Checksums::sha256)
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

/**
 * Review UI. Safety rules enforced here:
 * - only unprotected copies after the first are pre-ticked
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
    val marked = remember(groups) {
        mutableStateMapOf<Long, Boolean>().apply {
            groups.forEach { g ->
                g.items.filter { !it.protected }.drop(1).forEach { this[it.row.id] = true }
            }
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("duplicates_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.collection_duplicates), style = MaterialTheme.typography.displayLarge)
        }
        if (scanning != null) {
            LinearProgressIndicator(
                progress = { if (scanning.total == 0) 0f else scanning.hashed.toFloat() / scanning.total },
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            Text("Checking ${scanning.hashed} of ${scanning.total} files…", style = MaterialTheme.typography.labelMedium)
        }
        if (scanning == null && groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(
                    Icons.Filled.ContentCopy,
                    "No exact duplicates",
                    "Every photo and video in your library has unique contents.",
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                items(groups, key = { it.hash }) { g ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${g.items.size} identical copies", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(g.items, key = { it.row.id }) { d ->
                                val ticked = marked[d.row.id] == true
                                val othersKept = g.items.count { it.row.id != d.row.id && marked[it.row.id] != true }
                                Column(Modifier.size(width = 124.dp, height = 196.dp)) {
                                    Box(Modifier.size(120.dp).clip(RoundedCornerShape(10.dp))) {
                                        AsyncImage(d.row.uri, d.row.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    Text(d.row.name, maxLines = 1, style = MaterialTheme.typography.labelMedium)
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
                                            Text("Delete", style = MaterialTheme.typography.labelMedium)
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
                modifier = Modifier.fillMaxWidth().testTag("duplicates_trash_button"),
            ) {
                Text("Move ${chosen.size} to Recently Deleted")
            }
        }
    }
}
