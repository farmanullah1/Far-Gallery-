package com.example.ui.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.FarError
import com.example.core.common.LocalDiagnostics
import com.example.core.common.resolveSummary
import com.example.core.common.resolveTitle
import com.example.ui.glass.FarGlassState
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.LocalSemanticColors

/** Every screen exposes one of these. Errors are NEVER rendered as an empty state. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T, val stale: Boolean = false) : UiState<T>
    data class Empty(val reason: EmptyReason) : UiState<Nothing>
    data class Failure(val error: ErrorInfo, val cached: Any? = null) : UiState<Nothing>
}

enum class EmptyReason { NoMedia, NoPermission, NoResults, NotIndexedYet, EmptyAlbum, EmptyFolder }

@Immutable
data class ErrorAction(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/** What happened -> is my file safe -> what can I do. */
@Immutable
data class ErrorInfo(
    val title: String,
    val whatHappened: String,
    val originalsSafe: Boolean = true,
    val code: String? = null,
    val actions: List<ErrorAction> = emptyList(),
)

fun FarError.toErrorInfo(
    ctx: android.content.Context,
    actions: List<ErrorAction> = emptyList(),
    originalsSafe: Boolean = true,
): ErrorInfo = ErrorInfo(
    title = resolveTitle(ctx),
    whatHappened = resolveSummary(ctx),
    originalsSafe = originalsSafe,
    code = code,
    actions = actions,
)

/**
 * Defensive screen boundary for route state/fallback handling.
 * Renders a localized recoverable ErrorCard whenever [faultCode] is present or an empty/invalid payload triggers fallback.
 */
@Composable
fun FarErrorBoundary(
    faultCode: String? = null,
    onRecover: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    var recoveryNonce by remember { mutableIntStateOf(0) }
    if (faultCode != null) {
        LocalDiagnostics.recordEvent(ctx, faultCode, "error_boundary", "recovered_ui")
        Box(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            ErrorCard(
                ErrorInfo(
                    title = stringResource(R.string.error_boundary_title),
                    whatHappened = stringResource(R.string.error_boundary_body),
                    originalsSafe = true,
                    code = faultCode,
                    actions = buildList {
                        add(ErrorAction(stringResource(R.string.error_boundary_reset), primary = true) {
                            recoveryNonce++
                            onRecover()
                        })
                        if (onBack != null) {
                            add(ErrorAction(stringResource(R.string.error_boundary_back), primary = false, onClick = onBack))
                        }
                    },
                ),
                modifier = Modifier.testTag("global_error_boundary_card"),
            )
        }
    } else {
        androidx.compose.runtime.key(recoveryNonce) {
            content()
        }
    }
}

@Composable
fun ErrorCard(error: ErrorInfo, modifier: Modifier = Modifier) {
    val semantic = LocalSemanticColors.current
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(semantic.elevatedSurface)
                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
            Text(
                error.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                error.whatHappened,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error.originalsSafe) {
                Text(
                    stringResource(R.string.error_originals_safe),
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.success,
                    textAlign = TextAlign.Center,
                )
            }
            error.code?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error.actions.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    error.actions.forEach { a ->
                        if (a.primary) {
                            Button(onClick = a.onClick, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(a.label) }
                        } else {
                            TextButton(onClick = a.onClick, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(a.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primary: ErrorAction? = null,
    secondary: ErrorAction? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            title,
            Modifier.widthIn(max = 420.dp).padding(top = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            Modifier.widthIn(max = 420.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        primary?.let {
            Button(
                onClick = it.onClick,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("empty_primary_button"),
            ) { Text(it.label) }
        }
        secondary?.let {
            TextButton(onClick = it.onClick, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(it.label) }
        }
    }
}

/** Shimmer-less pulse placeholder; static when reduced motion is on. */
@Composable
fun Modifier.skeleton(): Modifier {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a")
        a
    }
    return this.alpha(alpha).background(MaterialTheme.colorScheme.surfaceVariant)
}

/** Bottom glass progress sheet for batch operations (copy, delete, export). */
@Immutable
data class OperationUi(
    val title: String,
    val currentItem: String?,
    val done: Int,
    val total: Int,
    val bytesPerSecond: Long? = null,
    val etaSeconds: Int? = null,
    val canPause: Boolean = false,
    val paused: Boolean = false,
)

@Composable
fun OperationProgressSheet(
    glass: FarGlassState,
    op: OperationUi,
    onCancel: () -> Unit,
    onTogglePause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fraction = if (op.total == 0) 0f else (op.done.toFloat() / op.total).coerceIn(0f, 1f)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .glass(glass, RoundedCornerShape(24.dp), GlassKind.Sheet)
                .padding(16.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(op.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            op.currentItem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.progress_count_of, op.done, op.total), style = MaterialTheme.typography.labelMedium)
                op.etaSeconds?.let {
                    Text(stringResource(R.string.progress_eta_sec, it), style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (op.canPause) {
                    TextButton(onClick = onTogglePause, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(if (op.paused) R.string.action_resume else R.string.action_pause))
                    }
                }
                TextButton(onClick = onCancel, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/** Glass toast with optional action (Undo / View / OK). Host it at the bottom above the tab bar. */
@Composable
fun GlassToast(
    glass: FarGlassState,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier
                .widthIn(max = 460.dp)
                .padding(horizontal = 16.dp)
                .glass(glass, RoundedCornerShape(24.dp), GlassKind.Chip)
                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = if (actionLabel != null) 8.dp else 16.dp, top = 6.dp, bottom = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("toast_action_button"),
                ) { Text(actionLabel) }
            }
        }
    }
}
