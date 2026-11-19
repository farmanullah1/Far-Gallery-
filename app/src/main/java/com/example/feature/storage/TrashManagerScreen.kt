package com.example.feature.storage

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.MediaRow
import com.example.ui.components.FarTopBar
import com.example.ui.feedback.EmptyState
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashManagerScreen(
    rows: List<MediaRow>,
    loading: Boolean,
    onOpen: (MediaRow) -> Unit,
    onRestore: (List<Uri>) -> Unit,
    onPermanentDelete: (List<Uri>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var selection by remember(rows) { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmEmpty by remember { mutableStateOf<List<MediaRow>?>(null) }

    BackHandler {
        if (selection.isNotEmpty()) selection = emptySet() else onBack()
    }

    val totalBytes = remember(rows) { rows.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    val selectedRows = remember(rows, selection) { rows.filter { it.id in selection } }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
        ) {
        FarTopBar(
            title = stringResource(R.string.trash_title),
            subtitle = "${rows.size} item(s) · ${Formatter.formatShortFileSize(ctx, totalBytes)}",
            onBack = onBack,
            backTestTag = "trash_back_button",
            actions = {
                if (rows.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selection = if (selection.size == rows.size) emptySet() else rows.map { it.id }.toSet()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("trash_select_all"),
                    ) {
                        Text(stringResource(R.string.batch_all))
                    }
                }
            },
        )

        if (rows.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FarSpacing.screenPadding, vertical = 6.dp)
                    .background(semantic.elevatedSurface, RoundedCornerShape(18.dp))
                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val targetRows = selectedRows.ifEmpty { rows }
                OutlinedButton(
                    onClick = { onRestore(targetRows.map { it.uri }) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("trash_restore_button"),
                ) {
                    Icon(Icons.Filled.RestoreFromTrash, null, Modifier.size(16.dp))
                    Text(
                        if (selectedRows.isNotEmpty()) " ${stringResource(R.string.trash_restore_selected, selectedRows.size)}"
                        else " ${stringResource(R.string.trash_restore_all)}",
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = { confirmEmpty = targetRows },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("trash_empty_button"),
                ) {
                    Icon(Icons.Filled.DeleteForever, null, Modifier.size(16.dp))
                    Text(" ${stringResource(R.string.trash_empty_all)}", maxLines = 1)
                }
            }
        }

        when {
            loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            rows.isEmpty() -> EmptyState(
                icon = Icons.Outlined.DeleteSweep,
                title = stringResource(R.string.trash_empty_title),
                body = stringResource(R.string.trash_empty_body),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(FarSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(rows, key = { it.id }) { row ->
                    val isSel = row.id in selection
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    if (selection.isNotEmpty()) {
                                        selection = if (isSel) selection - row.id else selection + row.id
                                    } else {
                                        onOpen(row)
                                    }
                                },
                                onLongClick = {
                                    selection = if (isSel) selection - row.id else selection + row.id
                                },
                            )
                            .testTag("trash_item_${row.id}"),
                    ) {
                        AsyncImage(
                            model = row.uri,
                            contentDescription = row.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (row.isVideo) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                null,
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(18.dp),
                                tint = Color.White,
                            )
                        }
                        Text(
                            Formatter.formatShortFileSize(ctx, row.sizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                        if (selection.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Black.copy(0.4f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSel) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    confirmEmpty?.let { targets ->
        AlertDialog(
            onDismissRequest = { confirmEmpty = null },
            title = { Text(stringResource(R.string.trash_empty_confirm_title, targets.size)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPermanentDelete(targets.map { it.uri })
                        confirmEmpty = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmEmpty = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
