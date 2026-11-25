package com.example.feature.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.app.DefaultApps
import com.example.app.DefaultRole
import com.example.app.DefaultStatus
import com.example.app.copyPlainTextToClipboard
import com.example.core.common.LocalDiagnostics
import com.example.core.database.OperationRecord
import com.example.core.database.QuarantineItemEntity
import com.example.feature.albums.AlbumSort
import com.example.ui.components.FarFilterPill
import com.example.ui.components.FarTopBar
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.ErrorAction
import com.example.ui.theme.FarPalette
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFont
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.text.DateFormat
import java.util.Date

import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore(
    name = "far_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.videoPosStore by preferencesDataStore(
    name = "far_video_positions",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class VideoPositionStore(private val ctx: Context) {
    private val key = stringPreferencesKey("positions_json")

    suspend fun get(uri: String): Long? = runCatching {
        val raw = ctx.videoPosStore.data.catch { emit(emptyPreferences()) }.first()[key] ?: return null
        val obj = org.json.JSONObject(raw)
        if (obj.has(uri)) obj.optLong(uri).takeIf { it > 1000L } else null
    }.getOrNull()

    suspend fun save(uri: String, positionMs: Long) {
        if (positionMs < 1000L) return
        runCatching {
            ctx.videoPosStore.edit { p ->
                val obj = p[key]?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()
                obj.put(uri, positionMs)
                if (obj.length() > 100) {
                    val keys = obj.keys()
                    if (keys.hasNext()) obj.remove(keys.next())
                }
                p[key] = obj.toString()
            }
        }
    }

    suspend fun clear() {
        runCatching { ctx.videoPosStore.edit { it.clear() } }
    }
}

enum class AppLanguage(val tag: String, val labelRes: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_en),
    Urdu("ur", R.string.language_ur),
}

@androidx.compose.runtime.Immutable
data class PinnedFolderShortcut(
    val id: String,
    val treeUri: String,
    val docId: String,
    val label: String,
)

data class FarSettings(
    val theme: ThemeMode = ThemeMode.System,
    val accentIndex: Int = 0,
    val dynamicColor: Boolean = false,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val haptics: Boolean = true,
    val gridColumns: Int = 3,
    val onboardingDone: Boolean = false,
    val trashDays: Int = 30,
    val resumeVideo: Boolean = true,
    val stripLocationOnShare: Boolean = true,
    val volumeKeyShutter: Boolean = true,
    val mirrorSelfie: Boolean = true,
    val cameraGrid: Boolean = false,
    val ocrEnabled: Boolean = true,
    val memoriesEnabled: Boolean = true,
    val languageTag: String = "",
    val albumSort: AlbumSort = AlbumSort.Pinned,
    val pinnedFolders: List<PinnedFolderShortcut> = emptyList(),
)

class SettingsStore(private val ctx: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val dyn = booleanPreferencesKey("dyn")
        val rm = booleanPreferencesKey("rm")
        val rt = booleanPreferencesKey("rt")
        val hap = booleanPreferencesKey("hap")
        val cols = intPreferencesKey("cols")
        val onb = booleanPreferencesKey("onb")
        val trash = intPreferencesKey("trash")
        val resume = booleanPreferencesKey("resume")
        val strip = booleanPreferencesKey("strip")
        val vol = booleanPreferencesKey("vol")
        val mirror = booleanPreferencesKey("mirror")
        val grid = booleanPreferencesKey("grid")
        val ocr = booleanPreferencesKey("ocr")
        val mem = booleanPreferencesKey("mem")
        val lang = stringPreferencesKey("lang")
        val albumSort = stringPreferencesKey("albumSort")
        val pinnedFolders = stringPreferencesKey("pinnedFolders")
    }

    val flow: Flow<FarSettings> = ctx.store.data
        .catch { err ->
            LocalDiagnostics.recordEvent(ctx, "FG-PREFS", "settings_read", err.javaClass.simpleName)
            emit(emptyPreferences())
        }
        .map { p -> runCatching { read(p) }.getOrDefault(FarSettings()) }

    suspend fun update(t: (FarSettings) -> FarSettings) {
        runCatching {
            ctx.store.edit { p: MutablePreferences ->
                val s = t(read(p))
                p[K.theme] = s.theme.name
                p[K.accent] = s.accentIndex
                p[K.dyn] = s.dynamicColor
                p[K.rm] = s.reduceMotion
                p[K.rt] = s.reduceTransparency
                p[K.hap] = s.haptics
                p[K.cols] = s.gridColumns
                p[K.onb] = s.onboardingDone
                p[K.trash] = s.trashDays
                p[K.resume] = s.resumeVideo
                p[K.strip] = s.stripLocationOnShare
                p[K.vol] = s.volumeKeyShutter
                p[K.mirror] = s.mirrorSelfie
                p[K.grid] = s.cameraGrid
                p[K.ocr] = s.ocrEnabled
                p[K.mem] = s.memoriesEnabled
                p[K.lang] = s.languageTag
                p[K.albumSort] = s.albumSort.name
                p[K.pinnedFolders] = encodePinned(s.pinnedFolders)
            }
        }.onFailure { err ->
            LocalDiagnostics.recordEvent(ctx, "FG-PREFS", "settings_save", err.javaClass.simpleName)
        }
    }

    private fun read(p: Preferences) = FarSettings(
        theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
        accentIndex = p[K.accent] ?: 0,
        dynamicColor = p[K.dyn] ?: false,
        reduceMotion = p[K.rm] ?: false,
        reduceTransparency = p[K.rt] ?: false,
        haptics = p[K.hap] ?: true,
        gridColumns = p[K.cols] ?: 3,
        onboardingDone = p[K.onb] ?: false,
        trashDays = p[K.trash] ?: 30,
        resumeVideo = p[K.resume] ?: true,
        stripLocationOnShare = p[K.strip] ?: true,
        volumeKeyShutter = p[K.vol] ?: true,
        mirrorSelfie = p[K.mirror] ?: true,
        cameraGrid = p[K.grid] ?: false,
        ocrEnabled = p[K.ocr] ?: true,
        memoriesEnabled = p[K.mem] ?: true,
        languageTag = p[K.lang] ?: "",
        albumSort = p[K.albumSort]?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() } ?: AlbumSort.Pinned,
        pinnedFolders = decodePinned(p[K.pinnedFolders]),
    )

    private fun encodePinned(list: List<PinnedFolderShortcut>): String {
        val arr = org.json.JSONArray()
        list.forEach { item ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", item.id)
                    put("tree", item.treeUri)
                    put("doc", item.docId)
                    put("label", item.label)
                }
            )
        }
        return arr.toString()
    }

    private fun decodePinned(raw: String?): List<PinnedFolderShortcut> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val tree = o.optString("tree")
                    val doc = o.optString("doc")
                    val label = o.optString("label")
                    if (id.isNotBlank() && tree.isNotBlank() && doc.isNotBlank() && label.isNotBlank()) {
                        add(PinnedFolderShortcut(id, tree, doc, label))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    s: FarSettings,
    ocrCount: Int,
    safeMode: Boolean,
    onLeaveSafeMode: () -> Unit,
    onChange: ((FarSettings) -> FarSettings) -> Unit,
    onOpenSystemDefaults: () -> Unit,
    onFixDefault: (DefaultRole, DefaultStatus) -> Unit,
    onClearCache: () -> Unit,
    onDeleteOcrIndex: () -> Unit,
    onManageAccess: () -> Unit,
    onOpenStorage: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onExportMetadataBackup: () -> Unit = {},
    onImportMetadataBackup: () -> Unit = {},
    quarantineItems: List<QuarantineItemEntity> = emptyList(),
    onDismissQuarantine: (String) -> Unit = {},
    onClearQuarantine: () -> Unit = {},
    recentOperations: List<OperationRecord> = emptyList(),
    onDismissOperation: (String) -> Unit = {},
    onClearOperations: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var diagRefresh by remember { mutableIntStateOf(0) }
    var diagStatus by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var settingsQuery by remember { mutableStateOf("") }
    val lastCrash = remember(diagRefresh) { LocalDiagnostics.readLastCrash(ctx) }
    val diagEvents = remember(diagRefresh) { LocalDiagnostics.readEvents(ctx) }
    BackHandler(onBack = onBack)

    val q = settingsQuery.trim().lowercase()
    fun matchesSetting(vararg keywords: String): Boolean {
        if (q.isEmpty()) return true
        return keywords.any { it.contains(q, ignoreCase = true) }
    }

    val showRecovery = (safeMode || lastCrash != null) && matchesSetting("recovery", "safe mode", "crash")
    val showDefaults = matchesSetting("default", "gallery", "photos", "videos", "camera", "viewer")
    val showAppearance = matchesSetting("appearance", "theme", "dark", "light", "amoled", "contrast", "accent", "color", "colour", "material you", "motion", "transparency", "haptic")
    val showLanguage = matchesSetting("language", "english", "urdu", "rtl", "locale")
    val showPhotos = matchesSetting("photos", "memories", "resume", "video", "exif", "gps", "strip", "access")
    val showCamera = matchesSetting("camera", "grid", "level", "mirror", "selfie", "volume", "shutter")
    val showPrivacy = matchesSetting("privacy", "data", "ocr", "text", "vault", "biometric", "pin", "identity", "id", "storage", "backup", "export", "import", "cache")
    val showActivity = matchesSetting("activity", "history", "operations", "batch", "resize", "rename", "collage", "pdf", "transfer")
    val showDiagnostics = matchesSetting("diagnostics", "recovery", "logs", "crash", "report")
    val showQuarantine = quarantineItems.isNotEmpty() && matchesSetting("quarantine", "unreadable", "corrupt", "skip")
    val showAbout = matchesSetting("about", "offline", "vault", "keystore", "internet", "version")
    val anyVisible = showRecovery || showDefaults || showAppearance || showLanguage || showPhotos ||
        showCamera || showPrivacy || showActivity || showDiagnostics || showQuarantine || showAbout

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        FarTopBar(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            backTestTag = "settings_back_button",
        )
        OutlinedTextField(
            value = settingsQuery,
            onValueChange = { settingsQuery = it },
            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (settingsQuery.isNotEmpty()) {
                    IconButton(onClick = { settingsQuery = "" }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.files_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FarSpacing.screenPadding, vertical = 6.dp)
                .testTag("settings_search_input"),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        if (!anyVisible) {
            item {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.settings_search_empty_title),
                    body = stringResource(R.string.settings_search_empty_body, settingsQuery.trim()),
                    primary = ErrorAction(stringResource(R.string.files_clear_search), true) { settingsQuery = "" },
                )
            }
        }

        if (showRecovery) {
            item {
                Section("Recovery note") {
                    if (safeMode) {
                        Text(
                            "Far Gallery started in safe mode after repeated startup crashes. Background indexing is paused.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = onLeaveSafeMode, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Exit safe mode") }
                    }
                    lastCrash?.let {
                        Text(
                            "Last crash log (local only): $it",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                LocalDiagnostics.dismissLastCrash(ctx)
                                diagRefresh++
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) { Text("Dismiss note") }
                    }
                }
            }
        }

        if (showDefaults) {
        item {
            Section(stringResource(R.string.settings_defaults)) {
                Text(
                    "Android doesn't have one switch for this. Pick Far Gallery when asked, choose “Always”, or clear the other app's default.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DefaultRole.entries.forEach { role ->
                    val st = DefaultApps.status(ctx, role)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onFixDefault(role, st) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(role.label, color = MaterialTheme.colorScheme.onSurface)
                            Text(role.hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            when (st) {
                                DefaultStatus.Mine -> "✓ Far Gallery"
                                DefaultStatus.AskEveryTime -> "Ask each time"
                                is DefaultStatus.Other -> st.label
                                DefaultStatus.NoHandler -> "Unavailable"
                            },
                            color = if (st == DefaultStatus.Mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onOpenSystemDefaults, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text("Open system default-app settings")
                }
            }
        }
        }

        if (showAppearance) {
        item {
            Section(stringResource(R.string.settings_appearance)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ThemeMode.entries) { _, m ->
                        FarFilterPill(
                            label = stringResource(m.labelRes()),
                            selected = s.theme == m,
                            onClick = { onChange { it.copy(theme = m) } },
                            modifier = Modifier.testTag("theme_${m.name.lowercase()}"),
                        )
                    }
                }
                Text("Accent", color = MaterialTheme.colorScheme.onSurface)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FarPalette.curated.forEachIndexed { idx, col ->
                        val active = s.accentIndex == idx && !s.dynamicColor
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onChange { it.copy(accentIndex = idx, dynamicColor = false) } }
                                .testTag("accent_$idx"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (active) Icon(Icons.Filled.Check, "Selected accent", Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }
                }
                Toggle("Use system colours (Material You)", s.dynamicColor) { v -> onChange { it.copy(dynamicColor = v) } }
                Toggle("Reduce motion", s.reduceMotion) { v -> onChange { it.copy(reduceMotion = v) } }
                Toggle("Reduce transparency (opaque bars)", s.reduceTransparency) { v -> onChange { it.copy(reduceTransparency = v) } }
                Toggle("Haptic feedback", s.haptics) { v -> onChange { it.copy(haptics = v) } }
            }
        }
        }

        if (showLanguage) {
        item {
            Section(stringResource(R.string.settings_language)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(AppLanguage.entries) { _, lang ->
                        FarFilterPill(
                            label = stringResource(lang.labelRes),
                            selected = s.languageTag == lang.tag,
                            onClick = {
                                onChange { it.copy(languageTag = lang.tag) }
                                val list = if (lang.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                else LocaleListCompat.forLanguageTags(lang.tag)
                                AppCompatDelegate.setApplicationLocales(list)
                            },
                            modifier = Modifier.testTag("lang_${lang.name.lowercase()}"),
                        )
                    }
                }
            }
        }
        }

        if (showPhotos) {
        item {
            Section(stringResource(R.string.settings_photos)) {
                Toggle("Memories (On this day)", s.memoriesEnabled) { v -> onChange { it.copy(memoriesEnabled = v) } }
                Toggle("Resume videos where you left off", s.resumeVideo) { v -> onChange { it.copy(resumeVideo = v) } }
                Toggle("Strip GPS & EXIF on shares and edits", s.stripLocationOnShare) { v -> onChange { it.copy(stripLocationOnShare = v) } }
                TextButton(onManageAccess, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Review photo access") }
            }
        }
        }

        if (showCamera) {
        item {
            Section(stringResource(R.string.settings_camera)) {
                Toggle("Grid and level", s.cameraGrid) { v -> onChange { it.copy(cameraGrid = v) } }
                Toggle("Mirror front camera", s.mirrorSelfie) { v -> onChange { it.copy(mirrorSelfie = v) } }
                Toggle("Volume keys take a photo", s.volumeKeyShutter) { v -> onChange { it.copy(volumeKeyShutter = v) } }
            }
        }
        }

        if (showPrivacy) {
        item {
            Section(stringResource(R.string.settings_privacy_data)) {
                Toggle("Find text in photos (on-device OCR)", s.ocrEnabled) { v -> onChange { it.copy(ocrEnabled = v) } }
                Text(
                    "$ocrCount photos currently indexed offline in Room.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onOpenVault,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_open_vault"),
                ) { Text(stringResource(R.string.vault_settings_button)) }
                Text(
                    stringResource(R.string.vault_settings_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onOpenStorage, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Storage breakdown & large files") }
                TextButton(
                    onClick = onExportMetadataBackup,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_export_backup"),
                ) { Text(stringResource(R.string.settings_backup_export)) }
                TextButton(
                    onClick = onImportMetadataBackup,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_import_backup"),
                ) { Text(stringResource(R.string.settings_backup_import)) }
                TextButton(onDeleteOcrIndex, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Delete text index") }
                TextButton(onClearCache, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Clear thumbnail cache (your photos are not deleted)") }
            }
        }
        }

        if (showActivity) {
            item {
                Section(stringResource(R.string.settings_activity_title)) {
                    if (recentOperations.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_activity_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                        recentOperations.take(10).forEach { op ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${op.type} · ${op.status}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when (op.status) {
                                            "Completed" -> semantic.success
                                            "Failed", "Interrupted" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${op.done}/${op.total} · ${fmt.format(Date(op.updatedAt))}" +
                                            (op.errorCode?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(
                                    onClick = { onDismissOperation(op.id) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                ) { Text(stringResource(R.string.action_dismiss)) }
                            }
                        }
                        TextButton(
                            onClick = onClearOperations,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_clear_activity"),
                        ) { Text(stringResource(R.string.settings_activity_clear)) }
                    }
                }
            }
        }

        if (showDiagnostics) {
        item {
            Section(stringResource(R.string.settings_diagnostics)) {
                if (diagEvents.isEmpty() && lastCrash == null) {
                    Text(
                        stringResource(R.string.diagnostics_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantic.success,
                    )
                } else {
                    diagEvents.take(8).forEach { entry ->
                        Text(
                            entry,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                diagStatus?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = semantic.info)
                }
                val copiedLabel = stringResource(R.string.diagnostics_copied)
                val clearedLabel = stringResource(R.string.diagnostics_cleared)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            copyPlainTextToClipboard(ctx, "Far Diagnostics", LocalDiagnostics.buildReport(ctx))
                            diagStatus = copiedLabel
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_copy_diagnostics"),
                    ) { Text(stringResource(R.string.diagnostics_copy_report)) }
                    TextButton(
                        onClick = {
                            LocalDiagnostics.clearAll(ctx)
                            diagRefresh++
                            diagStatus = clearedLabel
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_clear_diagnostics"),
                    ) { Text(stringResource(R.string.diagnostics_clear_logs)) }
                }
            }
        }
        }

        if (showQuarantine) {
            item {
                Section(stringResource(R.string.settings_quarantine_title, quarantineItems.size)) {
                    Text(
                        stringResource(R.string.settings_quarantine_body),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    quarantineItems.take(8).forEach { qItem ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    qItem.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${qItem.errorCode}: ${qItem.reason}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(
                                onClick = { onDismissQuarantine(qItem.sourceUri) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.action_dismiss)) }
                        }
                    }
                    TextButton(
                        onClick = onClearQuarantine,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("settings_clear_quarantine"),
                    ) { Text(stringResource(R.string.settings_quarantine_clear)) }
                }
            }
        }

        if (showAbout) {
        item {
            Section(stringResource(R.string.settings_about)) {
                Text(
                    stringResource(R.string.about_offline_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val semantic = LocalSemanticColors.current
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(semantic.elevatedSurface, RoundedCornerShape(22.dp))
                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(value, onChange)
    }
}
