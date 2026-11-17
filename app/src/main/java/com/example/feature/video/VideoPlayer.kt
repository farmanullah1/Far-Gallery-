package com.example.feature.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private val speeds = listOf(0.5f, 1f, 1.5f, 2f)

/** Media3 player + glass controls. Playback failure shows a card with Retry / Open with and never crashes the viewer. */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String,
    glass: HazeState,
    resumeMs: Long?,
    onPosition: (Long) -> Unit,
    onFrameGrab: (Long) -> Unit,
    onTrim: (Long) -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var error by remember(uri) { mutableStateOf<ErrorInfo?>(null) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var speedIdx by remember { mutableIntStateOf(1) }
    var attempt by remember(uri) { mutableIntStateOf(0) }

    val player = remember(uri, attempt) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            resumeMs?.let { seekTo(it) }
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    error = ErrorInfo(
                        "Can't play this video",
                        "This video uses a codec or profile this device can't play.",
                        originalsSafe = true,
                        code = "FG-VID-${e.errorCodeName}",
                        actions = listOf(
                            ErrorAction("Retry", true) { error = null; attempt++ },
                            ErrorAction("Open with…", false, onOpenWith),
                        ),
                    )
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            })
        }
    }
    DisposableEffect(player) {
        onDispose {
            onPosition(player.currentPosition)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0L)
            dur = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (error != null) {
            ErrorCard(error!!, Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; useController = false } },
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
                    .glass(glass, RoundedCornerShape(24.dp), GlassKind.Overlay)
                    .padding(12.dp),
            ) {
                Slider(
                    value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onValueChange = { if (dur > 0) player.seekTo((it * dur).toLong()) },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${clock(pos)} / ${clock(dur)}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                            Icon(Icons.Filled.Replay10, "Back 10s", tint = Color.White)
                        }
                        IconButton(
                            onClick = { if (player.isPlaying) player.pause() else player.play() },
                            modifier = Modifier.testTag("video_play_pause"),
                        ) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
                        }
                        IconButton(onClick = { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(dur.coerceAtLeast(0L))) }) {
                            Icon(Icons.Filled.Forward10, "Forward 10s", tint = Color.White)
                        }
                        TextButton(onClick = {
                            speedIdx = (speedIdx + 1) % speeds.size
                            player.setPlaybackSpeed(speeds[speedIdx])
                        }) { Text("${speeds[speedIdx]}×", color = Color.White) }
                        AudioTrackButton(player)
                        IconButton(onClick = { onFrameGrab(player.currentPosition) }) {
                            Icon(Icons.Filled.PhotoCamera, "Save frame", tint = Color.White)
                        }
                        IconButton(onClick = { onTrim(dur) }) {
                            Icon(Icons.Filled.ContentCut, "Trim", tint = Color.White)
                        }
                        if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                            IconButton(onClick = {
                                (ctx as? Activity)?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                            }) {
                                Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTrackButton(player: Player) {
    val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (groups.size < 2) return
    var idx by remember { mutableIntStateOf(0) }
    TextButton(onClick = {
        idx = (idx + 1) % groups.size
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(groups[idx].mediaTrackGroup, 0)).build()
    }) { Text("Audio ${idx + 1}/${groups.size}", color = Color.White) }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

/* ------------------------------ Frame grab ------------------------------ */
object FrameGrabber {
    /** Saves the frame as a NEW JPEG in Pictures/Far Gallery. The video is never modified. */
    suspend fun grab(ctx: Context, video: Uri, positionMs: Long): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val mmr = MediaMetadataRetriever()
            val bmp: Bitmap = try {
                mmr.setDataSource(ctx, video)
                mmr.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: throw java.io.IOException("no frame")
            } finally {
                mmr.release()
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "FRAME_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val out = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
            try {
                r.openOutputStream(out)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                r.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                r.delete(out, null, null)
                throw e
            } finally {
                bmp.recycle()
            }
            out
        }
    }
}

/* ------------------------------ Trim (save as copy) ------------------------------ */
object VideoTrimmer {
    /** Re-exports [startMs, endMs] to a temp file, then publishes it via MediaStore. Cancellable; the source is never touched. */
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun trim(
        ctx: Context,
        source: Uri,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit,
    ): FarResult<Uri> {
        if (endMs <= startMs) return FarResult.Err(FarError.InvalidInput("trim range"))
        val tmp = File(ctx.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
        val exported = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<FarResult<Unit>> { cont ->
                val item = MediaItem.Builder().setUri(source).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
                ).build()
                val t = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, r: ExportResult) {
                        onProgress(1f)
                        if (cont.isActive) cont.resume(FarResult.Ok(Unit))
                    }

                    override fun onError(c: Composition, r: ExportResult, e: ExportException) {
                        if (cont.isActive) cont.resume(FarResult.Err(FarError.Io(code = "FG-TRIM-${e.errorCodeName}")))
                    }
                }).build()
                t.start(EditedMediaItem.Builder(item).build(), tmp.absolutePath)
                cont.invokeOnCancellation {
                    t.cancel()
                    tmp.delete()
                }
            }
        }
        if (exported is FarResult.Err) {
            tmp.delete()
            return exported
        }
        return withContext(Dispatchers.IO) {
            farCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "TRIM_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Far Gallery")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val r = ctx.contentResolver
                val out = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
                try {
                    tmp.inputStream().use { i -> r.openOutputStream(out)!!.use { i.copyTo(it) } }
                    r.update(out, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    r.delete(out, null, null)
                    throw e
                } finally {
                    tmp.delete()
                }
                out
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimPanel(
    durationMs: Long,
    busy: Boolean,
    progress: Float?,
    onSave: (Long, Long) -> Unit,
    onCancel: () -> Unit,
) {
    var range by remember(durationMs) { mutableStateOf(0f..1f) }
    val safeDur = durationMs.coerceAtLeast(1000L)
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Trim video", style = MaterialTheme.typography.titleMedium)
        RangeSlider(value = range, onValueChange = { range = it }, enabled = !busy)
        Text(
            "${clock((range.start * safeDur).toLong())} – ${clock((range.endInclusive * safeDur).toLong())}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            "Saved as a new copy. The original stays untouched.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) LinearProgressIndicator(progress = { progress ?: 0f }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onCancel) { Text(if (busy) "Cancel export" else "Close") }
            if (!busy) {
                TextButton(onClick = { onSave((range.start * safeDur).toLong(), (range.endInclusive * safeDur).toLong()) }) {
                    Text("Save copy")
                }
            }
        }
    }
}
