package com.example.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.FarMotion
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.LocalReducedMotion
import dev.chrisbanes.haze.HazeState

enum class FarTab(
    val labelRes: Int,
    val outlined: ImageVector,
    val filled: ImageVector,
    val tag: String,
) {
    Photos(R.string.nav_photos, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary, "tab_photos"),
    Albums(R.string.nav_albums, Icons.Outlined.PhotoAlbum, Icons.Filled.PhotoAlbum, "tab_albums"),
    Search(R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search, "tab_search"),
    Files(R.string.nav_files, Icons.Outlined.Folder, Icons.Filled.Folder, "tab_files"),
}

/** Floating glass tab pill + detached circular camera button. Slides away when [visible] = false. */
@Composable
fun GlassBottomBar(
    glass: HazeState,
    selected: FarTab,
    onSelect: (FarTab) -> Unit,
    onCamera: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabPill(glass, selected, onSelect, Modifier.weight(1f))
            CameraButton(glass, onCamera)
        }
    }
}

/** Tablet / foldable navigation rail with detached camera action button. */
@Composable
fun GlassNavRail(
    glass: HazeState,
    selected: FarTab,
    onSelect: (FarTab) -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    Column(
        modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .width(80.dp)
                .glass(glass, RoundedCornerShape(28.dp), GlassKind.Bar)
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FarTab.entries.forEach { tab ->
                val isSel = tab == selected
                val label = stringResource(tab.labelRes)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
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
                        modifier = Modifier.size(22.dp),
                        tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        CameraButton(glass, onCamera)
    }
}

@Composable
private fun TabPill(glass: HazeState, selected: FarTab, onSelect: (FarTab) -> Unit, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val tabs = FarTab.entries
    BoxWithConstraints(
        modifier
            .height(64.dp)
            .glass(glass, RoundedCornerShape(32.dp))
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(tabWidth * tabs.indexOf(selected), FarMotion.spatial(reduced), label = "tabIndicator")
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(tabWidth)
                .height(56.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSel = tab == selected
                val label = stringResource(tab.labelRes)
                Column(
                    Modifier
                        .width(tabWidth)
                        .height(56.dp)
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
                        modifier = Modifier.size(22.dp),
                        tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraButton(glass: HazeState, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val desc = stringResource(R.string.cd_open_camera)
    Box(
        Modifier
            .size(64.dp)
            .glass(glass, CircleShape, GlassKind.Bar)
            .testTag("camera_shortcut_button")
            .semantics { role = Role.Button; contentDescription = desc }
            .clickable {
                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
