package com.example.feature.vault

import android.app.Activity
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.app.copyPlainTextToClipboard
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.core.common.userTitle
import com.example.core.database.FarDatabase
import com.example.core.database.IdentityDocEntity
import com.example.core.database.VaultItemEntity
import com.example.core.media.MediaRepository
import com.example.core.vault.DecryptedIdentityDoc
import com.example.core.vault.DocExpiryStatus
import com.example.core.vault.IdentityDocType
import com.example.core.vault.PinVerifyResult
import com.example.core.vault.VaultPinStore
import com.example.core.vault.VaultRepository
import com.example.core.vault.VaultSecurityState
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.GlassToast
import com.example.ui.glass.FarGlassState
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class VaultTab { Media, IdentityDocs }

@Composable
fun VaultScreen(
    activity: Activity,
    db: FarDatabase,
    repo: MediaRepository,
    glass: FarGlassState,
    onMediaRestored: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val pinStore = remember(ctx) { VaultPinStore(ctx) }
    val vaultRepo = remember { VaultRepository() }

    val secState by pinStore.stateFlow.collectAsState(initial = VaultSecurityState())
    val vaultItems by remember(db) {
        db.vaultItems().all().catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    val identityEntities by remember(db) {
        db.identityDocs().all().catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())

    var unlocked by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(VaultTab.Media) }
    var toast by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var previewItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var deleteItemTarget by remember { mutableStateOf<VaultItemEntity?>(null) }
    var deleteOriginalsOffer by remember { mutableStateOf<List<Uri>?>(null) }

    var viewingDoc by remember { mutableStateOf<DecryptedIdentityDoc?>(null) }
    var editingDoc by remember { mutableStateOf<DecryptedIdentityDoc?>(null) }
    var creatingDoc by remember { mutableStateOf(false) }
    var deleteDocTarget by remember { mutableStateOf<IdentityDocEntity?>(null) }
    var attachmentPreview by remember { mutableStateOf<Pair<String, String>?>(null) }

    var securitySheetOpen by remember { mutableStateOf(false) }
    var changingPinOpen by remember { mutableStateOf(false) }

    // Strict FLAG_SECURE while on the Vault screen
    DisposableEffect(activity) {
        val window = activity.window
        val hadSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (!hadSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!hadSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Immediate auto-lock when app backgrounds / screen turns off
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false
                previewItem = null
                viewingDoc = null
                attachmentPreview = null
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    BackHandler {
        when {
            attachmentPreview != null -> attachmentPreview = null
            previewItem != null -> previewItem = null
            viewingDoc != null -> viewingDoc = null
            else -> onBack()
        }
    }

    val deleteOriginalsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            toast = "Original unencrypted copies removed"
            onMediaRestored()
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            when (val res = vaultRepo.importMedia(ctx, db, uris)) {
                is FarResult.Ok -> {
                    busy = false
                    deleteOriginalsOffer = uris
                }
                is FarResult.Err -> {
                    busy = false
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "vault_import", res.error.userTitle())
                    toast = "Import failed (${res.error.code})"
                }
            }
        }
    }

    val biometricAvailable = remember(ctx) {
        val bm = BiometricManager.from(ctx)
        bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchBiometricPrompt(onSuccess: () -> Unit) {
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null || !biometricAvailable) {
            toast = "Biometrics unavailable on this device"
            return
        }
        val executor = ContextCompat.getMainExecutor(ctx)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch { pinStore.recordBiometricUnlock() }
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        toast = errString.toString()
                    }
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(ctx.getString(R.string.vault_unlock_title))
            .setSubtitle(ctx.getString(R.string.vault_unlock_body))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText(ctx.getString(R.string.vault_use_pin))
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3200)
            toast = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (unlocked) {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == VaultTab.Media) {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        } else {
                            creatingDoc = true
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("vault_primary_fab"),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(
                            if (activeTab == VaultTab.Media) R.string.vault_import_media
                            else R.string.vault_add_id
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).testTag("vault_back_button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.cd_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.vault_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (unlocked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { securitySheetOpen = true },
                                modifier = Modifier.size(48.dp).testTag("vault_security_button"),
                            ) {
                                Icon(
                                    Icons.Filled.Security,
                                    stringResource(R.string.vault_security_settings),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = {
                                    unlocked = false
                                    previewItem = null
                                    viewingDoc = null
                                },
                                modifier = Modifier.size(48.dp).testTag("vault_lock_now_button"),
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    stringResource(R.string.vault_lock_now),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                if (!unlocked) {
                    VaultLockGate(
                        securityState = secState,
                        biometricAvailable = biometricAvailable && secState.biometricEnabled,
                        onRequestBiometric = { launchBiometricPrompt { unlocked = true } },
                        onSetupPin = { pin ->
                            scope.launch {
                                when (val res = pinStore.setupPin(pin)) {
                                    is FarResult.Ok -> unlocked = true
                                    is FarResult.Err -> toast = "Could not set PIN (${res.error.code})"
                                }
                            }
                        },
                        onVerifyPin = { pin, onFeedback ->
                            scope.launch {
                                when (val res = pinStore.verifyPin(pin)) {
                                    PinVerifyResult.Verified -> unlocked = true
                                    PinVerifyResult.NotConfigured -> onFeedback(ctx.getString(R.string.vault_setup_title))
                                    is PinVerifyResult.Invalid -> onFeedback(
                                        ctx.getString(R.string.vault_pin_wrong, res.failedAttempts)
                                    )
                                    is PinVerifyResult.Cooldown -> {
                                        val rem = ((res.lockedUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
                                        onFeedback(ctx.getString(R.string.vault_pin_cooldown, rem.toInt()))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val decryptedDocs = remember(identityEntities) {
                        identityEntities.map { vaultRepo.decryptIdentityDoc(it) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FarFilterPill(
                            label = "${stringResource(R.string.vault_tab_media)} (${vaultItems.size})",
                            selected = activeTab == VaultTab.Media,
                            leadingIcon = Icons.Filled.PhotoLibrary,
                            onClick = { activeTab = VaultTab.Media },
                            modifier = Modifier.testTag("vault_tab_media"),
                        )
                        FarFilterPill(
                            label = "${stringResource(R.string.vault_tab_ids)} (${decryptedDocs.size})",
                            selected = activeTab == VaultTab.IdentityDocs,
                            leadingIcon = Icons.Filled.Badge,
                            onClick = { activeTab = VaultTab.IdentityDocs },
                            modifier = Modifier.testTag("vault_tab_ids"),
                        )
                    }

                    if (busy) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = FarSpacing.screenPadding, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "Encrypting locally with Android Keystore…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    when (activeTab) {
                        VaultTab.Media -> {
                            if (vaultItems.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Filled.Lock,
                                    title = stringResource(R.string.vault_media_empty_title),
                                    body = stringResource(R.string.vault_media_empty_body),
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(112.dp),
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(
                                        start = FarSpacing.screenPadding,
                                        end = FarSpacing.screenPadding,
                                        top = 8.dp,
                                        bottom = 96.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(vaultItems, key = { it.id }) { item ->
                                        VaultMediaTile(
                                            item = item,
                                            vaultRepo = vaultRepo,
                                            onClick = { previewItem = item },
                                        )
                                    }
                                }
                            }
                        }

                        VaultTab.IdentityDocs -> {
                            if (decryptedDocs.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Filled.Badge,
                                    title = stringResource(R.string.vault_ids_empty_title),
                                    body = stringResource(R.string.vault_ids_empty_body),
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(
                                        start = FarSpacing.screenPadding,
                                        end = FarSpacing.screenPadding,
                                        top = 8.dp,
                                        bottom = 96.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(decryptedDocs, key = { it.entity.id }) { doc ->
                                        IdentityDocCard(
                                            doc = doc,
                                            onClick = { viewingDoc = doc },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            toast?.let { msg ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    GlassToast(glass, msg, stringResource(R.string.action_ok), onAction = { toast = null })
                }
            }
        }
    }

    // Full-screen in-memory Vault media viewer
    previewItem?.let { item ->
        VaultMediaPreviewDialog(
            item = item,
            vaultRepo = vaultRepo,
            onExportGallery = {
                scope.launch {
                    when (val res = vaultRepo.exportToGallery(ctx, item)) {
                        is FarResult.Ok -> {
                            toast = ctx.getString(R.string.vault_exported_ok)
                            onMediaRestored()
                        }
                        is FarResult.Err -> {
                            LocalDiagnostics.recordEvent(ctx, res.error.code, "vault_export", res.error.userTitle())
                            toast = "Export failed (${res.error.code})"
                        }
                    }
                }
            },
            onDelete = { deleteItemTarget = item },
            onDismiss = { previewItem = null },
        )
    }

    // Confirm delete vault item
    deleteItemTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteItemTarget = null },
            title = { Text(stringResource(R.string.vault_delete_item_title)) },
            text = { Text(stringResource(R.string.vault_delete_item_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            vaultRepo.deleteVaultItem(ctx, db, target)
                            if (previewItem?.id == target.id) previewItem = null
                            deleteItemTarget = null
                            toast = "Deleted from Private Vault"
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItemTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Optional offer to remove unencrypted originals via Android MediaStore consent dialog
    deleteOriginalsOffer?.let { uris ->
        AlertDialog(
            onDismissRequest = { deleteOriginalsOffer = null },
            title = { Text(stringResource(R.string.vault_remove_originals_title)) },
            text = { Text(stringResource(R.string.vault_remove_originals_body, uris.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOriginalsOffer = null
                        val contentUris = uris.filter { it.scheme == "content" }
                        if (contentUris.isNotEmpty()) {
                            runCatching {
                                val sender: IntentSender = repo.deleteRequest(contentUris)
                                deleteOriginalsLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }.onFailure {
                                toast = "Use your system Gallery/Photos app if this source cannot be removed automatically"
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.vault_delete_originals), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOriginalsOffer = null }) {
                    Text(stringResource(R.string.vault_keep_originals))
                }
            },
        )
    }

    // View Identity Doc Details
    viewingDoc?.let { doc ->
        IdentityDocDetailDialog(
            doc = doc,
            vaultRepo = vaultRepo,
            onCopyNumber = {
                copyPlainTextToClipboard(ctx, doc.entity.title, doc.docNumber)
                toast = ctx.getString(R.string.vault_doc_copied)
            },
            onOpenAttachment = { name, iv -> attachmentPreview = name to iv },
            onEdit = {
                viewingDoc = null
                editingDoc = doc
            },
            onDelete = { deleteDocTarget = doc.entity },
            onDismiss = { viewingDoc = null },
        )
    }

    // Attachment full-screen viewer
    attachmentPreview?.let { (fileName, ivHex) ->
        Dialog(
            onDismissRequest = { attachmentPreview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            var bmp by remember(fileName) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(fileName) {
                bmp = (vaultRepo.decodeSideAttachmentBitmap(ctx, fileName, ivHex) as? FarResult.Ok)?.value
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { attachmentPreview = null },
                contentAlignment = Alignment.Center,
            ) {
                bmp?.let { b ->
                    Image(b.asImageBitmap(), "Document image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } ?: CircularProgressIndicator(color = Color.White)
                IconButton(
                    onClick = { attachmentPreview = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(0.55f), CircleShape),
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Color.White)
                }
            }
        }
    }

    // Add / Edit Identity Document dialog
    if (creatingDoc || editingDoc != null) {
        IdentityDocEditorDialog(
            initial = editingDoc,
            onDismiss = {
                creatingDoc = false
                editingDoc = null
            },
            onSave = { docType, title, holder, number, issuer, expiryMs, notes, frontUri, backUri, clearFront, clearBack ->
                scope.launch {
                    val res = vaultRepo.saveIdentityDoc(
                        ctx = ctx,
                        db = db,
                        existingId = editingDoc?.entity?.id,
                        docType = docType,
                        title = title,
                        holderName = holder,
                        docNumber = number,
                        issuer = issuer,
                        issuedDateMs = editingDoc?.entity?.issuedDateMs,
                        expiryDateMs = expiryMs,
                        notes = notes,
                        newFrontUri = frontUri,
                        newBackUri = backUri,
                        clearFront = clearFront,
                        clearBack = clearBack,
                    )
                    when (res) {
                        is FarResult.Ok -> {
                            creatingDoc = false
                            editingDoc = null
                            toast = "Identity document saved cleanly"
                        }
                        is FarResult.Err -> {
                            LocalDiagnostics.recordEvent(ctx, res.error.code, "id_save", res.error.userTitle())
                            toast = "Could not save (${res.error.code})"
                        }
                    }
                }
            },
        )
    }

    deleteDocTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteDocTarget = null },
            title = { Text("Delete “${target.title}”?") },
            text = { Text("The encrypted record and attached images will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            vaultRepo.deleteIdentityDoc(ctx, db, target)
                            if (viewingDoc?.entity?.id == target.id) viewingDoc = null
                            deleteDocTarget = null
                            toast = "Document deleted"
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDocTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Security settings dialog
    if (securitySheetOpen) {
        AlertDialog(
            onDismissRequest = { securitySheetOpen = false },
            title = { Text(stringResource(R.string.vault_security_settings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (biometricAvailable) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.vault_biometric_toggle),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = secState.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { pinStore.setBiometricEnabled(enabled) }
                                },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            securitySheetOpen = false
                            changingPinOpen = true
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(R.string.vault_change_pin))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { securitySheetOpen = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (changingPinOpen) {
        ChangePinDialog(
            onDismiss = { changingPinOpen = false },
            onSubmit = { oldPin, newPin, onError ->
                scope.launch {
                    when (val res = pinStore.changePin(oldPin, newPin)) {
                        is FarResult.Ok -> {
                            if (res.value) {
                                changingPinOpen = false
                                toast = ctx.getString(R.string.vault_pin_changed)
                            } else {
                                onError("Current PIN is incorrect")
                            }
                        }
                        is FarResult.Err -> onError("PIN must be 4–8 digits")
                    }
                }
            },
        )
    }
}

@Composable
private fun VaultLockGate(
    securityState: VaultSecurityState,
    biometricAvailable: Boolean,
    onRequestBiometric: () -> Unit,
    onSetupPin: (String) -> Unit,
    onVerifyPin: (String, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var entered by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(securityState.lockedUntilMs) {
        if (securityState.lockedUntilMs > System.currentTimeMillis()) {
            while (System.currentTimeMillis() < securityState.lockedUntilMs) {
                nowMs = System.currentTimeMillis()
                delay(500)
            }
            nowMs = System.currentTimeMillis()
            errorMsg = null
        }
    }

    // Trigger biometric prompt automatically on lock screen entry when enabled
    LaunchedEffect(securityState.isPinConfigured, biometricAvailable) {
        if (securityState.isPinConfigured && biometricAvailable && securityState.lockedUntilMs <= System.currentTimeMillis()) {
            onRequestBiometric()
        }
    }

    val cooldownSec = ((securityState.lockedUntilMs - nowMs) / 1000L).coerceAtLeast(0L)
    val inCooldown = cooldownSec > 0L

    val title = when {
        !securityState.isPinConfigured && firstSetupPin == null -> stringResource(R.string.vault_setup_title)
        !securityState.isPinConfigured -> stringResource(R.string.vault_setup_confirm_title)
        else -> stringResource(R.string.vault_unlock_title)
    }
    val subtitle = when {
        !securityState.isPinConfigured -> stringResource(R.string.vault_setup_body)
        inCooldown -> stringResource(R.string.vault_pin_cooldown, cooldownSec.toInt())
        else -> stringResource(R.string.vault_unlock_body)
    }

    fun submitDigit(d: Char) {
        if (inCooldown || entered.length >= 8) return
        errorMsg = null
        entered += d
    }

    fun submitPin() {
        if (inCooldown || entered.length < 4) return
        val candidate = entered
        if (!securityState.isPinConfigured) {
            val first = firstSetupPin
            if (first == null) {
                firstSetupPin = candidate
                entered = ""
            } else if (first == candidate) {
                entered = ""
                onSetupPin(candidate)
            } else {
                firstSetupPin = null
                entered = ""
                errorMsg = ctx.getString(R.string.vault_pin_mismatch)
            }
        } else {
            entered = ""
            onVerifyPin(candidate) { err -> errorMsg = err }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (inCooldown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // PIN dots
            Row(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val slots = entered.length.coerceAtLeast(4).coerceAtMost(8)
                for (i in 0 until slots) {
                    val filled = i < entered.length
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            )
                    )
                }
            }

            errorMsg?.let { err ->
                Text(err, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }

            // Keypad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { digit ->
                            KeypadCircleButton(
                                label = digit,
                                enabled = !inCooldown,
                                onClick = { submitDigit(digit[0]) },
                                modifier = Modifier.testTag("vault_pin_key_$digit"),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (securityState.isPinConfigured && biometricAvailable) {
                        IconButton(
                            onClick = onRequestBiometric,
                            enabled = !inCooldown,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("vault_biometric_button"),
                        ) {
                            Icon(
                                Icons.Filled.Fingerprint,
                                stringResource(R.string.vault_use_biometrics),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace")
                        }
                    }

                    KeypadCircleButton(
                        label = "0",
                        enabled = !inCooldown,
                        onClick = { submitDigit('0') },
                        modifier = Modifier.testTag("vault_pin_key_0"),
                    )

                    if (securityState.isPinConfigured && biometricAvailable) {
                        IconButton(
                            onClick = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace")
                        }
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }
                }
            }

            Button(
                onClick = { submitPin() },
                enabled = entered.length >= 4 && !inCooldown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("vault_pin_submit"),
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_ok))
            }
        }
    }
}

@Composable
private fun KeypadCircleButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VaultMediaTile(
    item: VaultItemEntity,
    vaultRepo: VaultRepository,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var thumb by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id) {
        thumb = (vaultRepo.decodeInMemoryBitmap(ctx, item, 360) as? FarResult.Ok)?.value
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(semantic.elevatedSurface)
            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("vault_media_tile_${item.id}"),
        contentAlignment = Alignment.Center,
    ) {
        val b = thumb
        if (b != null) {
            Image(b.asImageBitmap(), item.originalName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                if (item.isVideo) Icons.Filled.PlayCircleOutline else Icons.Filled.Lock,
                item.originalName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.isVideo) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(0.6f), CircleShape)
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.PlayCircleOutline, null, Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun VaultMediaPreviewDialog(
    item: VaultItemEntity,
    vaultRepo: VaultRepository,
    onExportGallery: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        failed = false
        when (val res = vaultRepo.decodeInMemoryBitmap(ctx, item, 2048)) {
            is FarResult.Ok -> bitmap = res.value
            is FarResult.Err -> failed = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                bitmap != null -> bitmap?.let { b ->
                    Image(
                        b.asImageBitmap(),
                        item.originalName,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                failed -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text(
                        item.originalName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (item.isVideo) "Encrypted video ready to export back to Gallery."
                        else "Unable to preview encrypted media.",
                        color = Color.White.copy(0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(0.55f), CircleShape),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onExportGallery,
                        modifier = Modifier.size(48.dp).background(Color.Black.copy(0.55f), CircleShape).testTag("vault_export_button"),
                    ) {
                        Icon(Icons.Filled.Output, stringResource(R.string.vault_export_gallery), tint = Color.White)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp).background(Color.Black.copy(0.55f), CircleShape).testTag("vault_delete_item_button"),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, stringResource(R.string.action_delete), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityDocCard(
    doc: DecryptedIdentityDoc,
    onClick: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    var reveal by remember { mutableStateOf(false) }
    val maskedNum = remember(doc.docNumber, reveal) {
        if (reveal || doc.docNumber.length <= 4) doc.docNumber
        else "•".repeat((doc.docNumber.length - 4).coerceAtMost(8)) + doc.docNumber.takeLast(4)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(semantic.elevatedSurface)
            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("identity_doc_card_${doc.entity.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Badge, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    doc.entity.title.ifBlank { doc.entity.docType },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (doc.entity.holderName.isNotBlank()) {
                Text(
                    doc.entity.holderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                maskedNum,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurface,
            )
            ExpiryBadge(doc.expiryStatus)
        }
        IconButton(
            onClick = { reveal = !reveal },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                if (reveal) "Hide number" else "Reveal number",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpiryBadge(status: DocExpiryStatus) {
    val semantic = LocalSemanticColors.current
    val (label, color) = when (status) {
        DocExpiryStatus.NoExpiry -> return
        is DocExpiryStatus.Expired -> stringResource(R.string.vault_doc_expired, status.daysAgo) to MaterialTheme.colorScheme.error
        is DocExpiryStatus.ExpiringSoon -> stringResource(R.string.vault_doc_expiring, status.daysLeft) to semantic.warning
        is DocExpiryStatus.Valid -> stringResource(R.string.vault_doc_valid, status.daysLeft) to semantic.success
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
    )
}

@Composable
private fun IdentityDocDetailDialog(
    doc: DecryptedIdentityDoc,
    vaultRepo: VaultRepository,
    onCopyNumber: () -> Unit,
    onOpenAttachment: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var frontBmp by remember(doc.entity.frontFileName) { mutableStateOf<Bitmap?>(null) }
    var backBmp by remember(doc.entity.backFileName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(doc.entity.id) {
        val fName = doc.entity.frontFileName
        val fIv = doc.entity.frontIvHex
        if (fName != null && fIv != null) {
            frontBmp = (vaultRepo.decodeSideAttachmentBitmap(ctx, fName, fIv, 600) as? FarResult.Ok)?.value
        }
        val bName = doc.entity.backFileName
        val bIv = doc.entity.backIvHex
        if (bName != null && bIv != null) {
            backBmp = (vaultRepo.decodeSideAttachmentBitmap(ctx, bName, bIv, 600) as? FarResult.Ok)?.value
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(doc.entity.docType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(doc.entity.title.ifBlank { doc.entity.docType })
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (doc.entity.holderName.isNotBlank()) {
                    DetailLine(stringResource(R.string.vault_doc_holder_label), doc.entity.holderName)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.vault_doc_number_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(doc.docNumber, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFont))
                    }
                    IconButton(onClick = onCopyNumber, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.ContentCopy, stringResource(R.string.vault_doc_copy_number))
                    }
                }
                if (doc.entity.issuer.isNotBlank()) {
                    DetailLine(stringResource(R.string.vault_doc_issuer_label), doc.entity.issuer)
                }
                doc.entity.expiryDateMs?.let { ms ->
                    val dateStr = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    DetailLine("Expiry", dateStr)
                    ExpiryBadge(doc.expiryStatus)
                }
                if (doc.notes.isNotBlank()) {
                    DetailLine("Notes", doc.notes)
                }
                if (frontBmp != null || backBmp != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fn = doc.entity.frontFileName
                        val fi = doc.entity.frontIvHex
                        frontBmp?.let { fb ->
                            if (fn != null && fi != null) {
                                Image(
                                    fb.asImageBitmap(),
                                    stringResource(R.string.vault_doc_front_photo),
                                    Modifier
                                        .weight(1f)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenAttachment(fn, fi) },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        val bn = doc.entity.backFileName
                        val bi = doc.entity.backIvHex
                        backBmp?.let { bb ->
                            if (bn != null && bi != null) {
                                Image(
                                    bb.asImageBitmap(),
                                    stringResource(R.string.vault_doc_back_photo),
                                    Modifier
                                        .weight(1f)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenAttachment(bn, bi) },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityDocEditorDialog(
    initial: DecryptedIdentityDoc?,
    onDismiss: () -> Unit,
    onSave: (
        docType: IdentityDocType,
        title: String,
        holder: String,
        number: String,
        issuer: String,
        expiryMs: Long?,
        notes: String,
        frontUri: Uri?,
        backUri: Uri?,
        clearFront: Boolean,
        clearBack: Boolean,
    ) -> Unit,
) {
    var docType by remember { mutableStateOf(initial?.let { IdentityDocType.fromWire(it.entity.docType) } ?: IdentityDocType.Passport) }
    var title by remember { mutableStateOf(initial?.entity?.title ?: "") }
    var holder by remember { mutableStateOf(initial?.entity?.holderName ?: "") }
    var number by remember { mutableStateOf(initial?.docNumber ?: "") }
    var issuer by remember { mutableStateOf(initial?.entity?.issuer ?: "") }
    var expiryText by remember {
        mutableStateOf(
            initial?.entity?.expiryDateMs?.let { ms ->
                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            } ?: ""
        )
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var frontUri by remember { mutableStateOf<Uri?>(null) }
    var backUri by remember { mutableStateOf<Uri?>(null) }
    var clearFront by remember { mutableStateOf(false) }
    var clearBack by remember { mutableStateOf(false) }

    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            frontUri = uri
            clearFront = false
        }
    }
    val backPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            backUri = uri
            clearBack = false
        }
    }

    val parsedExpiry = remember(expiryText) {
        val trimmed = expiryText.trim()
        if (trimmed.isEmpty()) null to false
        else runCatching {
            val ld = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
            ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to false
        }.getOrElse { null to true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.vault_add_id else R.string.vault_edit_id))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.vault_doc_type), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IdentityDocType.entries.forEach { t ->
                        FarFilterPill(
                            label = t.label,
                            selected = docType == t,
                            onClick = { docType = t },
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.vault_doc_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("id_doc_title_input"),
                )
                OutlinedTextField(
                    value = holder,
                    onValueChange = { holder = it },
                    label = { Text(stringResource(R.string.vault_doc_holder_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("id_doc_holder_input"),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.vault_doc_number_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("id_doc_number_input"),
                )
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text(stringResource(R.string.vault_doc_issuer_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = expiryText,
                    onValueChange = { expiryText = it },
                    label = { Text(stringResource(R.string.vault_doc_expiry_label)) },
                    placeholder = { Text("2030-12-31") },
                    isError = parsedExpiry.second,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.vault_doc_notes_label)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { frontPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    ) {
                        val hasFront = frontUri != null || (!clearFront && initial?.entity?.frontFileName != null)
                        Text(
                            if (hasFront) "${stringResource(R.string.vault_doc_front_photo)} ✓"
                            else stringResource(R.string.vault_doc_front_photo),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { backPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    ) {
                        val hasBack = backUri != null || (!clearBack && initial?.entity?.backFileName != null)
                        Text(
                            if (hasBack) "${stringResource(R.string.vault_doc_back_photo)} ✓"
                            else stringResource(R.string.vault_doc_back_photo),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.isNotBlank() && !parsedExpiry.second,
                onClick = {
                    onSave(
                        docType,
                        title.ifBlank { docType.label },
                        holder,
                        number,
                        issuer,
                        parsedExpiry.first,
                        notes,
                        frontUri,
                        backUri,
                        clearFront,
                        clearBack,
                    )
                },
                modifier = Modifier.testTag("id_doc_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSubmit: (oldPin: String, newPin: String, onError: (String) -> Unit) -> Unit,
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_change_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) oldPin = it },
                    label = { Text("Current PIN") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("New PIN (4–8 digits)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirm new PIN") },
                    singleLine = true,
                )
                err?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = oldPin.length >= 4 && newPin.length >= 4 && confirmPin == newPin,
                onClick = { onSubmit(oldPin, newPin) { err = it } },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
