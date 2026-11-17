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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.LocalReducedMotion
import dev.chrisbanes.haze.HazeState

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

@Composable
fun ErrorCard(error: ErrorInfo, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .padding(20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
        Text(error.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            error.whatHappened,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error.originalsSafe) {
            Text(
                "Your original files have not been changed.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        error.code?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            error.actions.forEach { a ->
                if (a.primary) Button(onClick = a.onClick) { Text(a.label) }
                else TextButton(onClick = a.onClick) { Text(a.label) }
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
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            body,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        primary?.let {
            Button(
                onClick = it.onClick,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .testTag("empty_primary_button"),
            ) { Text(it.label) }
        }
        secondary?.let { TextButton(onClick = it.onClick) { Text(it.label) } }
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
    glass: HazeState,
    op: OperationUi,
    onCancel: () -> Unit,
    onTogglePause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fraction = if (op.total == 0) 0f else (op.done.toFloat() / op.total).coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .glass(glass, RoundedCornerShape(28.dp), GlassKind.Sheet)
            .padding(20.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(op.title, style = MaterialTheme.typography.titleMedium)
        op.currentItem?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${op.done} of ${op.total}", style = MaterialTheme.typography.labelMedium)
            op.etaSeconds?.let { Text("${it}s left", style = MaterialTheme.typography.labelMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (op.canPause) TextButton(onClick = onTogglePause) { Text(if (op.paused) "Resume" else "Pause") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Glass toast with optional action (Undo / View / OK). Host it at the bottom above the tab bar. */
@Composable
fun GlassToast(
    glass: HazeState,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .glass(glass, RoundedCornerShape(24.dp), GlassKind.Chip)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.testTag("toast_action_button")) { Text(actionLabel) }
        }
    }
}
