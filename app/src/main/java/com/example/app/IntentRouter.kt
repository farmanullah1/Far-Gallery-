package com.example.app

import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import java.io.FileNotFoundException

/** What an incoming Intent is asking Far Gallery to do. Classification does NO I/O. */
sealed interface IncomingRoute {
    data object None : IncomingRoute
    data class ViewOrReceive(val intent: Intent, val shared: Boolean) : IncomingRoute
    data class Pick(val multiple: Boolean, val maxItems: Int, val mimeTypes: List<String>) : IncomingRoute
    data class Capture(val video: Boolean, val output: Uri?, val maxDurationSec: Int?, val maxBytes: Long?) : IncomingRoute
    data class Edit(val uri: Uri) : IncomingRoute
    data class OpenFolder(val uri: Uri) : IncomingRoute
    data object ShortcutCamera : IncomingRoute
    data object ShortcutFavorites : IncomingRoute
    data object ShortcutSearch : IncomingRoute
}

object IntentRouter {
    fun classify(intent: Intent?): IncomingRoute {
        intent ?: return IncomingRoute.None
        val data = intent.data
        if (data?.scheme.equals("farGallery", ignoreCase = true)) {
            return when (data?.host?.lowercase()) {
                "camera" -> IncomingRoute.ShortcutCamera
                "favorites" -> IncomingRoute.ShortcutFavorites
                "search" -> IncomingRoute.ShortcutSearch
                else -> IncomingRoute.None
            }
        }
        val type = intent.type.orEmpty()
        return when (intent.action) {
            Intent.ACTION_VIEW -> when {
                type == "vnd.android.document/directory" || type == "resource/folder" || type == "inode/directory" ->
                    data?.let(IncomingRoute::OpenFolder) ?: IncomingRoute.None
                data != null -> IncomingRoute.ViewOrReceive(intent, shared = false)
                else -> IncomingRoute.None
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> IncomingRoute.ViewOrReceive(intent, shared = true)
            Intent.ACTION_EDIT -> data?.let(IncomingRoute::Edit) ?: IncomingRoute.None
            Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK, MediaStore.ACTION_PICK_IMAGES -> IncomingRoute.Pick(
                multiple = runCatching {
                    intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) ||
                        intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 1) > 1
                }.getOrDefault(false),
                maxItems = runCatching { intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100) }.getOrDefault(100).coerceIn(1, 500),
                mimeTypes = runCatching { intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() }.getOrNull()
                    ?: listOf(type.ifEmpty { "*/*" }),
            )
            MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA -> IncomingRoute.Capture(
                video = false,
                output = outputUri(intent),
                maxDurationSec = null,
                maxBytes = null,
            )
            MediaStore.ACTION_VIDEO_CAPTURE, MediaStore.INTENT_ACTION_VIDEO_CAMERA -> IncomingRoute.Capture(
                video = true,
                output = outputUri(intent),
                maxDurationSec = runCatching { intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0) }.getOrDefault(0).takeIf { it > 0 },
                maxBytes = runCatching { intent.getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0L) }.getOrDefault(0L).takeIf { it > 0 },
            )
            else -> IncomingRoute.None
        }
    }

    private fun outputUri(i: Intent): Uri? =
        runCatching { IntentCompat.getParcelableExtra(i, MediaStore.EXTRA_OUTPUT, Uri::class.java) }.getOrNull()
}

/** Result helpers so callers always get a correct, permission-granted reply. */
object IntentResults {
    fun pick(activity: Activity, uris: List<Uri>) {
        if (uris.isEmpty()) {
            activity.setResult(Activity.RESULT_CANCELED)
            activity.finish()
            return
        }
        val data = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        data.data = uris.first()
        val clip = ClipData.newRawUri("media", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        data.clipData = clip
        activity.setResult(Activity.RESULT_OK, data)
        activity.finish()
    }

    fun capture(activity: Activity, saved: Uri, request: IncomingRoute.Capture): FarResult<Unit> = farCatching {
        val r: ContentResolver = activity.contentResolver
        val out = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (request.output != null) {
            val input = r.openInputStream(saved) ?: throw FileNotFoundException("captured stream missing")
            input.use { src ->
                val target = r.openOutputStream(request.output, "w") ?: throw FileNotFoundException("output stream missing")
                target.use { dst -> src.copyTo(dst) }
            }
            r.delete(saved, null, null)
        } else {
            out.data = saved
            if (!request.video) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                r.openInputStream(saved)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?.let { out.putExtra("data", it) }
            }
        }
        activity.setResult(Activity.RESULT_OK, out)
        activity.finish()
    }
}

/* ---------------------- Making Far Gallery the phone's default ---------------------- */
enum class DefaultRole(val label: String, val hint: String) {
    Gallery("Open photos", "Tapping a photo in any app opens it in Far Gallery"),
    Videos("Open videos", "Tapping a video opens it in Far Gallery"),
    Camera("Camera requests", "Apps that ask for a photo or video use Far Camera"),
    Picker("Photo picking", "Apps that ask you to choose a photo show Far Gallery"),
    Folders("Open folders", "Folder links from other apps open in Far Files"),
}

sealed interface DefaultStatus {
    data object Mine : DefaultStatus
    data object AskEveryTime : DefaultStatus
    data class Other(val label: String, val packageName: String) : DefaultStatus
    data object NoHandler : DefaultStatus
}

object DefaultApps {
    private val sampleImage = Uri.parse("content://media/external/images/media/1")
    private val sampleVideo = Uri.parse("content://media/external/video/media/1")

    fun probe(role: DefaultRole): Intent = when (role) {
        DefaultRole.Gallery -> Intent(Intent.ACTION_VIEW).setDataAndType(sampleImage, "image/jpeg")
        DefaultRole.Videos -> Intent(Intent.ACTION_VIEW).setDataAndType(sampleVideo, "video/mp4")
        DefaultRole.Camera -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        DefaultRole.Picker -> Intent(Intent.ACTION_PICK).setType("image/*")
        DefaultRole.Folders -> Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"),
            "vnd.android.document/directory",
        )
    }

    fun status(ctx: Context, role: DefaultRole): DefaultStatus = runCatching {
        val pm = ctx.packageManager
        val info = pm.resolveActivity(probe(role), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return@runCatching DefaultStatus.NoHandler
        when (info.packageName) {
            ctx.packageName -> DefaultStatus.Mine
            "android", "com.android.intentresolver" -> DefaultStatus.AskEveryTime
            else -> DefaultStatus.Other(info.loadLabel(pm).toString(), info.packageName)
        }
    }.getOrDefault(DefaultStatus.NoHandler)

    fun appDetails(pkg: String): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    fun systemDefaults(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
