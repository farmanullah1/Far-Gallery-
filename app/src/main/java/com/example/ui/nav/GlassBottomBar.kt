package com.example.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.glass.FarGlassState
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.FarIcons
import com.example.ui.theme.FarMotion
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.LocalIsDark
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.LocalSemanticColors

enum class FarTab(
    val labelRes: Int,
    val outlined: ImageVector,
    val filled: ImageVector,
    val tag: String,
) {
    Photos(R.string.nav_photos, FarIcons.PhotosOutlined, FarIcons.PhotosFilled, "tab_photos"),
    Camera(R.string.nav_camera, FarIcons.CameraOutlined, FarIcons.CameraFilled, "tab_camera"),
    Files(R.string.nav_files, FarIcons.FilesOutlined, FarIcons.FilesFilled, "tab_files"),
}

enum class PhotosSubTab(val labelRes: Int, val tag: String) {
    Library(R.string.photos_sub_library, "photos_sub_library"),
    Albums(R.string.photos_sub_albums, "photos_sub_albums"),
    Search(R.string.photos_sub_search, "photos_sub_search"),
}

/** Top segmented glass switcher inside the Photos tab (Library / Albums / Search). */
@Composable
fun PhotosSubNavBar(
    glass: FarGlassState,
    selected: PhotosSubTab,
    onSelect: (PhotosSubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(glass, RoundedCornerShape(22.dp), GlassKind.Chip)
            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(22.dp))
            .padding(3.dp),
    ) {
        PhotosSubTab.entries.forEach { sub ->
            val sel = sub == selected
            val label = stringResource(sub.labelRes)
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable {
                        if (!sel) {
                            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(sub)
                        }
                    }
                    .padding(vertical = 8.dp, horizontal = 6.dp)
                    .testTag(sub.tag)
                    .semantics {
                        role = Role.Tab
                        this.selected = sel
                        contentDescription = label
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Floating 3-destination glass bottom bar (Photos / Camera / Files). */
@Composable
fun GlassBottomBar(
    glass: FarGlassState,
    selected: FarTab,
    onSelect: (FarTab) -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(FarMotion.spatial(reduced)) { it } + fadeIn(FarMotion.effects(reduced)),
        exit = slideOutVertically(FarMotion.spatial(reduced)) { it } + fadeOut(FarMotion.effects(reduced)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabPill(glass, selected, onSelect, Modifier.fillMaxWidth())
        }
    }
}

/** Tablet / foldable 3-destination navigation rail. */
@Composable
fun GlassNavRail(
    glass: FarGlassState,
    selected: FarTab,
    onSelect: (FarTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val isDark = LocalIsDark.current
    val semantic = LocalSemanticColors.current
    Column(
        modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier
                .width(74.dp)
                .glass(glass, RoundedCornerShape(26.dp), GlassKind.Bar)
                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(26.dp))
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FarTab.entries.forEach { tab ->
                val isSel = tab == selected
                val label = stringResource(tab.labelRes)
                val fg = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.14f)
                            else Color.Transparent
                        )
                        .testTag(tab.tag)
                        .semantics { role = Role.Tab; this.selected = isSel; contentDescription = label }
                        .clickable {
                            if (!isSel) {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isSel) tab.filled else tab.outlined,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = fg,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = fg,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPill(glass: FarGlassState, selected: FarTab, onSelect: (FarTab) -> Unit, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val isDark = LocalIsDark.current
    val semantic = LocalSemanticColors.current
    val tabs = FarTab.entries
    val selectedColor = if (!isDark && MaterialTheme.colorScheme.primary.luminance() > 0.45f) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
    BoxWithConstraints(
        modifier
            .height(56.dp)
            .glass(glass, RoundedCornerShape(28.dp))
            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(28.dp))
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(tabWidth * tabs.indexOf(selected), FarMotion.spatial(reduced), label = "tabIndicator")
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(tabWidth)
                .height(48.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.14f),
                    RoundedCornerShape(24.dp),
                )
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSel = tab == selected
                val label = stringResource(tab.labelRes)
                val fg = if (isSel) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .width(tabWidth)
                        .height(48.dp)
                        .testTag(tab.tag)
                        .semantics { role = Role.Tab; this.selected = isSel; contentDescription = label }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!isSel) {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isSel) tab.filled else tab.outlined,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = fg,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = fg,
                    )
                }
            }
        }
    }
}
