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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.app.DefaultApps
import com.example.app.DefaultRole
import com.example.app.DefaultStatus
import com.example.core.common.LocalDiagnostics
import com.example.ui.theme.FarPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("far_settings")

enum class AppLanguage(val tag: String, val labelRes: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_en),
    Urdu("ur", R.string.language_ur),
}

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
    }

    val flow: Flow<FarSettings> = ctx.store.data.map(::read)

    suspend fun update(t: (FarSettings) -> FarSettings) {
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
    )
}

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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lastCrash = remember { LocalDiagnostics.readLastCrash(ctx) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                }
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displayLarge)
            }
        }

        if (safeMode || lastCrash != null) {
            item {
                Section("Recovery note") {
                    if (safeMode) {
                        Text(
                            "Far Gallery started in safe mode after repeated startup crashes. Background indexing is paused.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onLeaveSafeMode) { Text("Exit safe mode") }
                    }
                    lastCrash?.let {
                        Text("Last crash log (local only): $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { LocalDiagnostics.dismissLastCrash(ctx) }) { Text("Dismiss note") }
                    }
                }
            }
        }

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
                            .clickable { onFixDefault(role, st) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(role.label)
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
                TextButton(onOpenSystemDefaults) { Text("Open system default-app settings") }
            }
        }

        item {
            Section(stringResource(R.string.settings_appearance)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ThemeMode.entries) { _, m ->
                        FilterChip(
                            selected = s.theme == m,
                            onClick = { onChange { it.copy(theme = m) } },
                            label = { Text(if (m == ThemeMode.Amoled) "AMOLED" else m.name) },
                            modifier = Modifier.testTag("theme_${m.name.lowercase()}"),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Accent", Modifier.weight(1f))
                    FarPalette.curated.forEachIndexed { idx, col ->
                        val active = s.accentIndex == idx && !s.dynamicColor
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(col)
                                .then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { onChange { it.copy(accentIndex = idx, dynamicColor = false) } }
                                .testTag("accent_$idx"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) Icon(Icons.Filled.Check, "Selected accent", Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
                Toggle("Use system colours (Material You)", s.dynamicColor) { v -> onChange { it.copy(dynamicColor = v) } }
                Toggle("Reduce motion", s.reduceMotion) { v -> onChange { it.copy(reduceMotion = v) } }
                Toggle("Reduce transparency (opaque bars)", s.reduceTransparency) { v -> onChange { it.copy(reduceTransparency = v) } }
                Toggle("Haptic feedback", s.haptics) { v -> onChange { it.copy(haptics = v) } }
            }
        }

        item {
            Section(stringResource(R.string.settings_language)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(AppLanguage.entries) { _, lang ->
                        FilterChip(
                            selected = s.languageTag == lang.tag,
                            onClick = {
                                onChange { it.copy(languageTag = lang.tag) }
                                val list = if (lang.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                else LocaleListCompat.forLanguageTags(lang.tag)
                                AppCompatDelegate.setApplicationLocales(list)
                            },
                            label = { Text(stringResource(lang.labelRes)) },
                            modifier = Modifier.testTag("lang_${lang.name.lowercase()}"),
                        )
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_photos)) {
                Toggle("Memories (On this day)", s.memoriesEnabled) { v -> onChange { it.copy(memoriesEnabled = v) } }
                Toggle("Resume videos where you left off", s.resumeVideo) { v -> onChange { it.copy(resumeVideo = v) } }
                Toggle("Strip metadata when editing copies", s.stripLocationOnShare) { v -> onChange { it.copy(stripLocationOnShare = v) } }
                TextButton(onManageAccess) { Text("Review photo access") }
            }
        }

        item {
            Section(stringResource(R.string.settings_camera)) {
                Toggle("Grid and level", s.cameraGrid) { v -> onChange { it.copy(cameraGrid = v) } }
                Toggle("Mirror front camera", s.mirrorSelfie) { v -> onChange { it.copy(mirrorSelfie = v) } }
                Toggle("Volume keys take a photo", s.volumeKeyShutter) { v -> onChange { it.copy(volumeKeyShutter = v) } }
            }
        }

        item {
            Section(stringResource(R.string.settings_privacy_data)) {
                Toggle("Find text in photos (on-device OCR)", s.ocrEnabled) { v -> onChange { it.copy(ocrEnabled = v) } }
                Text(
                    "$ocrCount photos currently indexed offline in Room.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onDeleteOcrIndex) { Text("Delete text index") }
                TextButton(onClearCache) { Text("Clear thumbnail cache (your photos are not deleted)") }
            }
        }

        item {
            Section(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.about_offline_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
