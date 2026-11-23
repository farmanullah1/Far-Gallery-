package com.example.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.BatchMediaOps
import com.example.core.media.BatchResizeConfig
import com.example.core.media.MediaRow
import com.example.core.media.QuickSharePreset
import com.example.core.media.ResizePreset
import com.example.ui.components.FarFilterPill

@Composable
fun BatchResizeDialog(
    count: Int,
    stripExifDefault: Boolean,
    onConfirm: (BatchResizeConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var preset by remember { mutableStateOf(ResizePreset.Edge1600) }
    var useWebp by remember { mutableStateOf(false) }
    var quality by remember { mutableIntStateOf(88) }
    var stripExif by remember { mutableStateOf(stripExifDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_resize_title) + " ($count)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ResizePreset.entries) { p ->
                        FarFilterPill(
                            label = p.label,
                            selected = preset == p,
                            onClick = { preset = p },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarFilterPill(label = "JPEG", selected = !useWebp, onClick = { useWebp = false })
                    FarFilterPill(label = "WebP", selected = useWebp, onClick = { useWebp = true })
                }
                Column {
                    Text(
                        stringResource(R.string.batch_quality, quality),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 50f..100f,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.batch_strip_exif),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(checked = stripExif, onCheckedChange = { stripExif = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(BatchResizeConfig(preset, useWebp, quality, stripExif)) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("batch_resize_confirm"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun BatchRenameDialog(
    sampleNames: List<String>,
    onConfirm: (prefix: String, startNumber: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var prefix by remember { mutableStateOf("Trip") }
    var startStr by remember { mutableStateOf("1") }
    val startNum = startStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val preview = BatchMediaOps.buildRenamedName(sampleNames.firstOrNull() ?: "IMG_001.jpg", prefix, startNum)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_rename_title, sampleNames.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text(stringResource(R.string.batch_rename_prefix)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_rename_prefix"),
                )
                OutlinedTextField(
                    value = startStr,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 5) startStr = it },
                    label = { Text(stringResource(R.string.batch_rename_start)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("batch_rename_start"),
                )
                Text(
                    stringResource(R.string.batch_rename_preview, preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(prefix, startNum) },
                enabled = prefix.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("batch_rename_confirm"),
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun CompareSecondPickerDialog(
    excludeId: Long,
    candidates: List<MediaRow>,
    onPick: (MediaRow) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(candidates, excludeId) {
        candidates.filter { it.id != excludeId && !it.isVideo }.take(60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_pick_second)) },
        text = {
            if (choices.isEmpty()) {
                Text(stringResource(R.string.gallery_empty_with_access))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(choices, key = { it.id }) { row ->
                        AsyncImage(
                            model = row.uri,
                            contentDescription = row.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(row) }
                                .testTag("compare_pick_${row.id}"),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun QuickSharePresetDialog(
    count: Int,
    defaultPreset: QuickSharePreset,
    onPickPreset: (QuickSharePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(defaultPreset) { mutableStateOf(defaultPreset) }
    val options = listOf(
        Triple(QuickSharePreset.Original, R.string.share_preset_original, R.string.share_preset_original_sub),
        Triple(QuickSharePreset.PrivacyClean, R.string.share_preset_clean, R.string.share_preset_clean_sub),
        Triple(QuickSharePreset.Compact1600, R.string.share_preset_compact, R.string.share_preset_compact_sub),
        Triple(QuickSharePreset.Messaging1080, R.string.share_preset_messaging, R.string.share_preset_messaging_sub),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.share_presets_title)} ($count)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (preset, titleRes, subRes) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selected = preset }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("share_preset_${preset.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected == preset, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(titleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(subRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected == preset) {
                            Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPickPreset(selected) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("share_preset_confirm"),
            ) {
                Text(stringResource(R.string.action_share))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
