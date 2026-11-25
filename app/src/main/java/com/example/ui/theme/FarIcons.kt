package com.example.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized Material 3 icon tokens.
 * - Uses filled variants for active/primary actions, outlined for inactive/secondary controls.
 * - Uses `Icons.AutoMirrored` for all directional icons to support RTL layouts natively.
 */
object FarIcons {
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val Forward: ImageVector get() = Icons.AutoMirrored.Filled.ArrowForward
    val Next: ImageVector get() = Icons.AutoMirrored.Filled.NavigateNext
    val OpenExternal: ImageVector get() = Icons.AutoMirrored.Filled.OpenInNew
    val ShareSend: ImageVector get() = Icons.AutoMirrored.Filled.Send
    val Sort: ImageVector get() = Icons.AutoMirrored.Filled.Sort

    val PhotosFilled: ImageVector get() = Icons.Filled.PhotoLibrary
    val PhotosOutlined: ImageVector get() = Icons.Outlined.PhotoLibrary
    val AlbumsFilled: ImageVector get() = Icons.Filled.PhotoAlbum
    val AlbumsOutlined: ImageVector get() = Icons.Outlined.PhotoAlbum
    val SearchFilled: ImageVector get() = Icons.Filled.Search
    val SearchOutlined: ImageVector get() = Icons.Outlined.Search
    val CameraFilled: ImageVector get() = Icons.Filled.PhotoCamera
    val CameraOutlined: ImageVector get() = Icons.Outlined.PhotoCamera
    val FilesFilled: ImageVector get() = Icons.Filled.Folder
    val FilesOutlined: ImageVector get() = Icons.Outlined.Folder

    val Settings: ImageVector get() = Icons.Outlined.Settings
    val LightMode: ImageVector get() = Icons.Outlined.LightMode
    val DarkMode: ImageVector get() = Icons.Outlined.DarkMode
    val Add: ImageVector get() = Icons.Filled.Add
    val Close: ImageVector get() = Icons.Filled.Close
    val Check: ImageVector get() = Icons.Filled.Check
    val FilterTune: ImageVector get() = Icons.Filled.Tune

    val VaultLocked: ImageVector get() = Icons.Filled.Lock
    val VaultOutlined: ImageVector get() = Icons.Outlined.Lock
    val Biometric: ImageVector get() = Icons.Filled.Fingerprint
    val IdentityDoc: ImageVector get() = Icons.Outlined.Badge
}
