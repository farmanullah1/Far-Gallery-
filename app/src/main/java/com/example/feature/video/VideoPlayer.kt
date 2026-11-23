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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.farCatching
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.FarSpacing
import com.example.ui.glass.FarGlassState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs

private val speeds = listOf(0.5f, 1f, 1.5f, 2f)

/** Media3 player + glass controls. Playback failure shows a card with Retry / Open with and never crashes the viewer. */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String,
    glass: FarGlassState,
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
    var seekFlashLabel by remember { mutableStateOf<String?>(null) }
    var seekFlashSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(seekFlashSeq) {
        if (seekFlashLabel != null) {
            delay(750)
            seekFlashLabel = null
        }
    }

    val player = remember(uri, attempt) {
        runCatching {
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                resumeMs?.let { seekTo(it) }
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        LocalDiagnostics.recordEvent(ctx, "FG-VID-${e.errorCodeName}", "video_player", e.errorCodeName)
                        error = ErrorInfo(
                            title = "Can't play this video",
                            whatHappened = "This video uses a codec or profile this device can't play.",
                            originalsSafe = true,
                            code = "FG-VID-${e.errorCodeName}",
                            actions = listOf(
                                ErrorAction(ctx.getString(R.string.action_retry), true) { error = null; attempt++ },
                                ErrorAction(ctx.getString(R.string.viewer_open_with), false, onOpenWith),
                            ),
                        )
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }
                })
            }
        }.onFailure { err ->
            LocalDiagnostics.recordEvent(ctx, "FG-VID-INIT", "video_player", err.javaClass.simpleName)
            error = ErrorInfo(
                title = "Can't play this video",
                whatHappened = "The video player could not initialize for this item.",
                originalsSafe = true,
                code = "FG-VID-INIT",
                actions = listOf(
                    ErrorAction(ctx.getString(R.string.action_retry), true) { error = null; attempt++ },
                    ErrorAction(ctx.getString(R.string.viewer_open_with), false, onOpenWith),
                ),
            )
        }.getOrNull()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val inPip = (ctx as? Activity)?.isInPictureInPictureMode == true
                if (!inPip && player?.isPlaying == true) {
                    runCatching { player.pause() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.let { p ->
                runCatching { onPosition(p.currentPosition) }
                runCatching { p.release() }
            }
        }
    }
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            runCatching {
                pos = player.currentPosition.coerceAtLeast(0L)
                dur = player.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (error != null || player == null) {
            ErrorCard(
                error ?: ErrorInfo(
                    title = "Can't play this video",
                    whatHappened = "The video player could not initialize.",
                    originalsSafe = true,
                    code = "FG-VID-NULL",
                    actions = listOf(ErrorAction(ctx.getString(R.string.viewer_open_with), true, onOpenWith)),
                ),
                Modifier.align(Alignment.Center),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(player, dur) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val deltaMs = if (offset.x < size.width / 2f) -10_000L else 10_000L
                                val maxDur = dur.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val target = (player.currentPosition + deltaMs).coerceIn(0L, maxDur)
                                runCatching { player.seekTo(target) }
                                seekFlashLabel = if (deltaMs < 0L) "-10s" else "+10s"
                                seekFlashSeq++
                            },
                        )
                    }
            ) {
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player; useController = false } },
                    modifier = Modifier.fillMaxSize(),
                )
                AnimatedVisibility(
                    visible = seekFlashLabel != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = seekFlashLabel.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 92.dp, start = FarSpacing.screenPadding, end = FarSpacing.screenPadding)
                    .glass(glass, RoundedCornerShape(24.dp), GlassKind.Overlay)
                    .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .padding(12.dp),
            ) {
                Slider(
                    value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onValueChange = { if (dur > 0) runCatching { player.seekTo((it * dur).toLong()) } },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${clock(pos)} / ${clock(dur)}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { runCatching { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) } },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Replay10, stringResource(R.string.video_back_10s), tint = Color.White)
                        }
                        IconButton(
                            onClick = { runCatching { if (player.isPlaying) player.pause() else player.play() } },
                            modifier = Modifier.size(48.dp).testTag("video_play_pause"),
                        ) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
                        }
                        IconButton(
                            onClick = { runCatching { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(dur.coerceAtLeast(0L))) } },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Forward10, stringResource(R.string.video_forward_10s), tint = Color.White)
                        }
                        TextButton(
                            onClick = {
                                speedIdx = (speedIdx + 1) % speeds.size
                                runCatching { player.setPlaybackSpeed(speeds[speedIdx]) }
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) { Text("${speeds[speedIdx]}×", color = Color.White) }
                        AudioTrackButton(player)
                        IconButton(
                            onClick = { onFrameGrab(pos) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, stringResource(R.string.video_save_frame), tint = Color.White)
                        }
                        IconButton(
                            onClick = { onTrim(dur) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.ContentCut, stringResource(R.string.video_trim_title), tint = Color.White)
                        }
                        if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                            IconButton(
                                onClick = {
                                    runCatching {
                                        (ctx as? Activity)?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                                    }.onFailure { err ->
                                        LocalDiagnostics.recordEvent(ctx, "FG-VID-PIP", "video_player", err.javaClass.simpleName)
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.PictureInPictureAlt, stringResource(R.string.video_pip), tint = Color.White)
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
    var groups by remember(player) {
        mutableStateOf(player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO })
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (groups.size < 2) return
    var idx by remember { mutableIntStateOf(0) }
    TextButton(
        onClick = {
            idx = (idx + 1) % groups.size
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(groups[idx].mediaTrackGroup, 0)).build()
        },
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
    ) { Text("Audio ${idx + 1}/${groups.size}", color = Color.White) }
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
    /** Re-exports [startMs, endMs] with optional speed/mute to a temp file, then publishes it via MediaStore. Cancellable; the source is never touched. */
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun trim(
        ctx: Context,
        source: Uri,
        startMs: Long,
        endMs: Long,
        speed: Float = 1f,
        muteAudio: Boolean = false,
        onProgress: (Float) -> Unit,
    ): FarResult<Uri> {
        if (endMs <= startMs) return FarResult.Err(FarError.InvalidInput("trim range"))
        val safeSpeed = speed.coerceIn(0.25f, 4f)
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
                        LocalDiagnostics.recordEvent(ctx, "FG-TRIM-${e.errorCodeName}", "video_trimmer", e.errorCodeName)
                        if (cont.isActive) cont.resume(FarResult.Err(FarError.Io(code = "FG-TRIM-${e.errorCodeName}")))
                    }
                }).build()

                val editedBuilder = EditedMediaItem.Builder(item).setRemoveAudio(muteAudio)
                if (abs(safeSpeed - 1f) > 0.05f) {
                    val audioProcessors = if (muteAudio) emptyList() else listOf(SonicAudioProcessor().apply { setSpeed(safeSpeed) })
                    editedBuilder.setEffects(Effects(audioProcessors, listOf(SpeedChangeEffect(safeSpeed))))
                }
                t.start(editedBuilder.build(), tmp.absolutePath)
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
    onSave: (startMs: Long, endMs: Long, speed: Float, muteAudio: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var range by remember(durationMs) { mutableStateOf(0f..1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muteAudio by remember { mutableStateOf(false) }
    val safeDur = durationMs.coerceAtLeast(1000L)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.video_trim_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            RangeSlider(value = range, onValueChange = { range = it }, enabled = !busy)
            Text(
                "${clock((range.start * safeDur).toLong())} – ${clock((range.endInclusive * safeDur).toLong())}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.video_trim_speed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(speeds) { sp ->
                    FarFilterPill(
                        label = "${sp}×",
                        selected = abs(speed - sp) < 0.05f,
                        onClick = { if (!busy) speed = sp },
                        modifier = Modifier.testTag("trim_speed_${sp}"),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.video_trim_mute),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = muteAudio,
                    onCheckedChange = { if (!busy) muteAudio = it },
                    modifier = Modifier.testTag("trim_mute_switch"),
                )
            }
            Text(
                stringResource(R.string.video_trim_untouched_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) LinearProgressIndicator(progress = { progress ?: 0f }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onCancel, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(if (busy) R.string.action_cancel else R.string.action_close))
                }
                if (!busy) {
                    TextButton(
                        onClick = {
                            onSave((range.start * safeDur).toLong(), (range.endInclusive * safeDur).toLong(), speed, muteAudio)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(R.string.editor_save_copy))
                    }
                }
            }
        }
    }
}
