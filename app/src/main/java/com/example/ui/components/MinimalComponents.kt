package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.glass.FarGlassState
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.FarMotion
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.LocalSemanticColors

/**
 * Standardized minimal header shared across sub-screens and utility flows.
 * Guarantees uniform status-bar clearance, 16dp horizontal margins, and 48dp touch targets.
 */
@Composable
fun FarTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String = "top_bar_back",
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(backTestTag),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }
    }
}

/**
 * Compact minimal filter pill with guaranteed 48dp minimum interactive height and crisp Light/Dark/AMOLED contrast.
 */
@Composable
fun FarFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onDarkSurface: Boolean = false,
) {
    val semantic = LocalSemanticColors.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (onDarkSurface) 0.30f else 0.18f)
        onDarkSurface -> Color.White.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val fg = when {
        selected && onDarkSurface -> Color.White
        selected -> MaterialTheme.colorScheme.primary
        onDarkSurface -> Color.White.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        onDarkSurface -> Color.White.copy(alpha = 0.22f)
        else -> semantic.cardBorder
    }

    Box(
        modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .border(0.75.dp, borderColor, RoundedCornerShape(17.dp))
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable {
                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(14.dp), tint = fg)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Icon(trailingIcon, null, Modifier.size(14.dp), tint = fg)
            }
        }
    }
}

/**
 * Reusable collapsible drawer for secondary filter, sort, and density controls
 * paired with a 1-row quick filter pill strip.
 */
@Composable
fun FarFilterDrawer(
    visible: Boolean,
    glass: FarGlassState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val semantic = LocalSemanticColors.current
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(FarMotion.spatial(reduced)) + fadeIn(FarMotion.effects(reduced)),
        exit = shrinkVertically(FarMotion.spatial(reduced)) + fadeOut(FarMotion.effects(reduced)),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(glass, RoundedCornerShape(20.dp), GlassKind.Sheet)
                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * Compact glass recovery strip for non-blocking warnings/errors (limited access, muted mic, interrupted jobs).
 */
@Composable
fun RecoveryBanner(
    glass: FarGlassState,
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val semantic = LocalSemanticColors.current
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier
                .fillMaxWidth()
                .glass(glass, RoundedCornerShape(18.dp), GlassKind.Chip)
                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(semantic.warningContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = semantic.warning)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onAction,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(actionLabel, maxLines = 1)
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        stringResource(R.string.action_dismiss),
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
