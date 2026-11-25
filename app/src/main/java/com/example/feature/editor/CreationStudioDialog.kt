package com.example.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.FarFilterPill
import com.example.ui.theme.LocalSemanticColors

sealed interface CreationRequest {
    data class Collage(val config: CollageConfig) : CreationRequest
    data class MultiPagePdf(val mode: PdfExporter.PdfLayoutMode) : CreationRequest
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreationStudioDialog(
    photoCount: Int,
    onCreate: (CreationRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    var modeIndex by remember { mutableIntStateOf(0) } // 0 = Collage, 1 = Multi-page PDF
    var aspect by remember { mutableStateOf(CollageAspect.Square) }
    var spacingPx by remember { mutableIntStateOf(20) }
    var background by remember { mutableStateOf(CollageBackground.White) }
    var pdfMode by remember { mutableStateOf(PdfExporter.PdfLayoutMode.PhotoBook) }

    val collageCount = photoCount.coerceIn(CollageEngine.MIN_PHOTOS, CollageEngine.MAX_PHOTOS)
    val pdfCount = photoCount.coerceAtMost(24)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.creation_studio_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarFilterPill(
                        label = stringResource(R.string.creation_mode_collage),
                        selected = modeIndex == 0,
                        onClick = { modeIndex = 0 },
                        modifier = Modifier.testTag("creation_mode_collage"),
                    )
                    FarFilterPill(
                        label = stringResource(R.string.creation_mode_pdf),
                        selected = modeIndex == 1,
                        onClick = { modeIndex = 1 },
                        modifier = Modifier.testTag("creation_mode_pdf"),
                    )
                }

                if (modeIndex == 0) {
                    if (photoCount > CollageEngine.MAX_PHOTOS) {
                        Text(
                            stringResource(R.string.creation_collage_cap_note, CollageEngine.MAX_PHOTOS, photoCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.creation_collage_count, collageCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Live geometric grid wireframe preview
                    val bgColor = Color(background.colorInt)
                    val cellColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    val cellBorder = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val previewW = 140.dp
                        val previewH = (140f / aspect.ratio).coerceIn(72f, 180f).dp
                        Canvas(
                            Modifier
                                .width(previewW)
                                .height(previewH)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(10.dp)),
                        ) {
                            val w = size.width.toInt().coerceAtLeast(32)
                            val h = size.height.toInt().coerceAtLeast(32)
                            val gap = (spacingPx / 5).coerceAtLeast(0)
                            val cells = CollageEngine.computeCells(collageCount, w, h, gap)
                            val corner = if (gap > 0) 4.dp.toPx() else 0f
                            cells.forEach { r ->
                                drawRoundRect(
                                    color = cellColor,
                                    topLeft = Offset(r.left, r.top),
                                    size = Size(r.width(), r.height()),
                                    cornerRadius = CornerRadius(corner, corner),
                                )
                                drawRoundRect(
                                    color = cellBorder,
                                    topLeft = Offset(r.left, r.top),
                                    size = Size(r.width(), r.height()),
                                    cornerRadius = CornerRadius(corner, corner),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.creation_aspect_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CollageAspect.entries.forEach { a ->
                            FarFilterPill(
                                label = a.label,
                                selected = aspect == a,
                                onClick = { aspect = a },
                                modifier = Modifier.testTag("collage_aspect_${a.name.lowercase()}"),
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.creation_spacing_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            0 to stringResource(R.string.creation_spacing_none),
                            12 to stringResource(R.string.creation_spacing_tight),
                            28 to stringResource(R.string.creation_spacing_classic),
                        ).forEach { (px, label) ->
                            FarFilterPill(
                                label = label,
                                selected = spacingPx == px,
                                onClick = { spacingPx = px },
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.creation_bg_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CollageBackground.entries.forEach { bg ->
                            FarFilterPill(
                                label = bg.label,
                                selected = background == bg,
                                onClick = { background = bg },
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.creation_pdf_count, pdfCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FarFilterPill(
                            label = stringResource(R.string.creation_pdf_book),
                            selected = pdfMode == PdfExporter.PdfLayoutMode.PhotoBook,
                            onClick = { pdfMode = PdfExporter.PdfLayoutMode.PhotoBook },
                            modifier = Modifier.testTag("pdf_mode_book"),
                        )
                        FarFilterPill(
                            label = stringResource(R.string.creation_pdf_sheet),
                            selected = pdfMode == PdfExporter.PdfLayoutMode.ContactSheet,
                            onClick = { pdfMode = PdfExporter.PdfLayoutMode.ContactSheet },
                            modifier = Modifier.testTag("pdf_mode_sheet"),
                        )
                    }
                    Text(
                        stringResource(
                            if (pdfMode == PdfExporter.PdfLayoutMode.PhotoBook) R.string.creation_pdf_book_hint
                            else R.string.creation_pdf_sheet_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val req = if (modeIndex == 0) {
                        CreationRequest.Collage(
                            CollageConfig(
                                aspect = aspect,
                                spacingPx = spacingPx,
                                background = background,
                            )
                        )
                    } else {
                        CreationRequest.MultiPagePdf(pdfMode)
                    }
                    onCreate(req)
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("creation_confirm_button"),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
