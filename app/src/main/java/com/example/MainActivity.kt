package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.app.FarApp
import com.example.app.IncomingRoute
import com.example.app.IntentRouter
import com.example.core.common.IncomingMediaResolver
import com.example.core.common.IncomingResult
import com.example.core.common.LocalDiagnostics
import com.example.core.database.FarDatabase
import com.example.feature.settings.FarSettings
import com.example.feature.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FarApplication : Application(), ImageLoaderFactory {
    private val appExceptionHandler = CoroutineExceptionHandler { _, e ->
        LocalDiagnostics.recordEvent(this, "FG-APP-INIT", "app_scope", e.javaClass.simpleName)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + appExceptionHandler)
    private val startMs = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build()
            )
        }
        installCrashNote()
        appScope.launch {
            runCatching {
                cacheDir.listFiles { f -> f.isFile && f.name.startsWith("trim_") && f.name.endsWith(".mp4") }
                    ?.forEach { it.delete() }
            }
            try {
                FarDatabase.get(this@FarApplication).operations().markInterrupted(System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LocalDiagnostics.recordEvent(this@FarApplication, "FG-DB-INIT", "mark_interrupted", e.javaClass.simpleName)
            }
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "thumb_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(160)
            .build()

    /** Privacy-safe local crash note: class name, API level, startup flag. No file names, URIs, GPS, or OCR text. */
    private fun installCrashNote() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                File(filesDir, "last_crash.txt").writeText(
                    "${System.currentTimeMillis()}|${e.javaClass.name}|api=${Build.VERSION.SDK_INT}|startup=${System.currentTimeMillis() - startMs < 8000}"
                )
                val f = File(filesDir, "crash_count.txt")
                f.writeText(((f.takeIf { it.exists() }?.readText()?.toIntOrNull() ?: 0) + 1).toString())
            }
            prev?.uncaughtException(t, e)
        }
    }
}

enum class Access { Full, Selected, None }

object Permissions {
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Always recompute from the system; users can change access in Settings at any time. */
    fun access(c: Context): Access = when {
        Build.VERSION.SDK_INT >= 33 &&
            (granted(c, Manifest.permission.READ_MEDIA_IMAGES) || granted(c, Manifest.permission.READ_MEDIA_VIDEO)) -> Access.Full
        Build.VERSION.SDK_INT >= 34 && granted(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Selected
        Build.VERSION.SDK_INT < 33 && granted(c, Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.Full
        else -> Access.None
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    val settings: StateFlow<FarSettings?> = store.flow
        .catch { err ->
            LocalDiagnostics.recordEvent(getApplication(), "FG-PREFS", "vm_settings", err.javaClass.simpleName)
            emit(FarSettings())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _route = MutableStateFlow<IncomingRoute>(IncomingRoute.None)
    val route: StateFlow<IncomingRoute> = _route.asStateFlow()
    private val _incoming = MutableStateFlow<IncomingResult?>(null)
    val incoming: StateFlow<IncomingResult?> = _incoming.asStateFlow()
    private val _access = MutableStateFlow(runCatching { Permissions.access(app) }.getOrDefault(Access.None))
    val access: StateFlow<Access> = _access.asStateFlow()
    private val _safeMode = MutableStateFlow(false)
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    init {
        _safeMode.value = runCatching {
            val f = File(app.filesDir, "crash_count.txt")
            (f.takeIf { it.exists() }?.readText()?.toIntOrNull() ?: 0) >= 3
        }.getOrDefault(false)
    }

    fun leaveSafeMode() {
        runCatching { File(getApplication<Application>().filesDir, "crash_count.txt").delete() }
        _safeMode.value = false
    }

    fun markHealthyStart() {
        runCatching { File(getApplication<Application>().filesDir, "crash_count.txt").delete() }
    }

    fun refreshAccess() {
        _access.value = runCatching { Permissions.access(getApplication()) }.getOrDefault(Access.None)
    }

    fun update(t: (FarSettings) -> FarSettings) = viewModelScope.launch { store.update(t) }

    fun handleIntent(intent: Intent?) {
        val r = runCatching { IntentRouter.classify(intent) }
            .onFailure { err ->
                LocalDiagnostics.recordEvent(getApplication(), "FG-INTENT", "classify", err.javaClass.simpleName)
            }
            .getOrDefault(IncomingRoute.None)
        _route.value = r
        if (r is IncomingRoute.ViewOrReceive) {
            viewModelScope.launch(Dispatchers.IO) {
                _incoming.value = runCatching {
                    IncomingMediaResolver(getApplication<Application>().contentResolver).resolve(r.intent)
                }.onFailure { err ->
                    LocalDiagnostics.recordEvent(getApplication(), "FG-INCOMING", "resolve", err.javaClass.simpleName)
                }.getOrNull()
            }
        }
    }

    @Volatile
    var volumeShutterHandler: (() -> Unit)? = null

    fun consumeRoute() {
        _route.value = IncomingRoute.None
        _incoming.value = null
    }
}

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = runCatching { installSplashScreen() }.getOrNull()
        if (splash == null) {
            setTheme(R.style.Theme_FarGallery)
        }
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
        splash?.setKeepOnScreenCondition { vm.settings.value == null }
        if (savedInstanceState == null) vm.handleIntent(intent)
        val startFiles = runCatching {
            intent?.component?.className?.endsWith("FilesLauncher") == true
        }.getOrDefault(false)
        setContent { FarApp(vm, activity = this, startOnFiles = startFiles) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
        vm.markHealthyStart()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val handler = vm.volumeShutterHandler
        val volEnabled = vm.settings.value?.volumeKeyShutter ?: true
        if (volEnabled && handler != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && event?.repeatCount == 0) {
            handler.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
