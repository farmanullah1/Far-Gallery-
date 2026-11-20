package com.example.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.Formatter
import android.widget.RemoteViews
import com.example.CameraActivity
import com.example.MainActivity
import com.example.R
import com.example.core.common.FarResult
import com.example.core.database.FarDatabase
import com.example.core.media.MediaFilter
import com.example.core.media.MediaRepository
import com.example.core.media.MediaRow
import com.example.feature.settings.SettingsStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LibraryStats(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val favoriteCount: Int = 0,
    val totalBytes: Long = 0L,
    val updatedAtMs: Long = 0L,
)

object LibraryStatsBridge {
    private const val PREFS = "far_widget_stats"
    private const val K_PHOTOS = "photos"
    private const val K_VIDEOS = "videos"
    private const val K_FAVS = "favs"
    private const val K_BYTES = "bytes"
    private const val K_UPDATED = "updated"

    fun read(ctx: Context): LibraryStats {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LibraryStats(
            photoCount = p.getInt(K_PHOTOS, 0),
            videoCount = p.getInt(K_VIDEOS, 0),
            favoriteCount = p.getInt(K_FAVS, 0),
            totalBytes = p.getLong(K_BYTES, 0L),
            updatedAtMs = p.getLong(K_UPDATED, 0L),
        )
    }

    fun syncFromRows(ctx: Context, rows: List<MediaRow>, favoriteCount: Int) {
        runCatching {
            var photos = 0
            var videos = 0
            var bytes = 0L
            rows.forEach { r ->
                if (r.isVideo) videos++ else photos++
                bytes += r.sizeBytes.coerceAtLeast(0L)
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(K_PHOTOS, photos)
                .putInt(K_VIDEOS, videos)
                .putInt(K_FAVS, favoriteCount)
                .putLong(K_BYTES, bytes)
                .putLong(K_UPDATED, System.currentTimeMillis())
                .apply()
            notifyWidgets(ctx)
        }
    }

    suspend fun refreshFromSystem(ctx: Context) {
        val repo = MediaRepository(ctx.contentResolver)
        val db = FarDatabase.get(ctx)
        val excluded = runCatching { db.folders().excluded().first().map { it.relativePath } }.getOrDefault(emptyList())
        val favs = runCatching { db.favorites().ids().first().size }.getOrDefault(0)
        val rows = (repo.queryAll(MediaFilter(excludedPaths = excluded), limit = 10000) as? FarResult.Ok)?.value ?: emptyList()
        syncFromRows(ctx, rows, favs)
    }

    fun notifyWidgets(ctx: Context) {
        runCatching {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, LibraryStatsWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                LibraryStatsWidgetProvider.updateWidgets(ctx, mgr, ids)
            }
        }
    }
}

class LibraryStatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                LibraryStatsBridge.refreshFromSystem(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateWidgets(ctx: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
            val stats = LibraryStatsBridge.read(ctx)
            val sizeLabel = Formatter.formatShortFileSize(ctx, stats.totalBytes)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val openGallery = PendingIntent.getActivity(
                ctx,
                101,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags,
            )
            val openCamera = PendingIntent.getActivity(
                ctx,
                102,
                Intent(ctx, CameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            )
            val openSearch = PendingIntent.getActivity(
                ctx,
                103,
                Intent(Intent.ACTION_VIEW, Uri.parse("farGallery://search"), ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags,
            )

            appWidgetIds.forEach { id ->
                val views = RemoteViews(ctx.packageName, R.layout.widget_library_stats).apply {
                    setTextViewText(R.id.widget_photos_count, stats.photoCount.toString())
                    setTextViewText(R.id.widget_videos_count, stats.videoCount.toString())
                    setTextViewText(R.id.widget_favs_count, stats.favoriteCount.toString())
                    setTextViewText(R.id.widget_total_size, sizeLabel)
                    setOnClickPendingIntent(R.id.widget_root, openGallery)
                    setOnClickPendingIntent(R.id.widget_btn_gallery, openGallery)
                    setOnClickPendingIntent(R.id.widget_btn_camera, openCamera)
                    setOnClickPendingIntent(R.id.widget_btn_search, openSearch)
                }
                mgr.updateAppWidget(id, views)
            }
        }
    }
}

class ThemeTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            val current = runCatching { SettingsStore(applicationContext).flow.first().theme }.getOrDefault(ThemeMode.System)
            applyTileUi(current)
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val store = SettingsStore(applicationContext)
            var nextMode = ThemeMode.System
            store.update { s ->
                nextMode = s.theme.next()
                s.copy(theme = nextMode)
            }
            applyTileUi(nextMode)
        }
    }

    private fun applyTileUi(mode: ThemeMode) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.qs_tile_theme, getString(mode.labelRes()))
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(R.string.app_name)
        }
        tile.state = if (mode == ThemeMode.System) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    companion object {
        fun syncTile(ctx: Context) {
            runCatching {
                requestListeningState(ctx, android.content.ComponentName(ctx, ThemeTileService::class.java))
            }
        }
    }
}

fun copyPlainTextToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
