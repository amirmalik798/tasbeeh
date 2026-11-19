package com.amirmalik.tasbeeh

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import com.amirmalik.tasbeeh.data.CountOutcome
import com.amirmalik.tasbeeh.data.canUndoCount
import com.amirmalik.tasbeeh.data.CustomDhikr
import com.amirmalik.tasbeeh.data.SessionStatus
import com.amirmalik.tasbeeh.data.TasbeehState
import com.amirmalik.tasbeeh.data.TasbeehStore
import com.amirmalik.tasbeeh.data.sessionSnapshotForDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Ink = Color(0xFF090B0A)
private val Panel = Color(0xFF121714)
private val Panel2 = Color(0xFF19211C)
private val Gold = Color(0xFFF0BF4E)
private val GoldLight = Color(0xFFFFE6A0)
private val Ivory = Color(0xFFFFF8EA)
private val Muted = Color(0xFF9A9B8D)

internal const val RECITATION_QUEUE_REPLACE = 0
internal const val RECITATION_QUEUE_APPEND = 1

internal fun arabicSpeechQueueMode(manualPlayback: Boolean): Int =
    if (manualPlayback) RECITATION_QUEUE_REPLACE else RECITATION_QUEUE_APPEND

internal fun arabicPlaybackControlEnabled(hasArabicText: Boolean, isSpeaking: Boolean): Boolean =
    hasArabicText || isSpeaking

internal fun recitationPlaybackFailureMessage(
    error: Throwable? = null,
    mediaPlayerWhat: Int? = null,
    mediaPlayerExtra: Int? = null
): String {
    val codes = setOfNotNull(mediaPlayerWhat, mediaPlayerExtra)
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val causeMessage = when {
        android.media.MediaPlayer.MEDIA_ERROR_IO in codes ->
            "Android could not read the recorded recitation stream. The connection may have dropped or the server may have interrupted the audio."
        android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT in codes ||
            causes.any { it is java.net.SocketTimeoutException } ->
            "The connection to the recitation provider timed out before the recitation could load. Check your internet connection and try again."
        causes.any { it is java.net.UnknownHostException } ->
            "Android could not resolve the recitation provider. Check your internet connection or DNS settings."
        causes.any { it is java.net.ConnectException } ->
            "Android could not connect to the recitation provider. The server may be unavailable or your network may be blocking it."
        causes.any { it is java.io.FileNotFoundException } ->
            "The requested recitation audio file was not found on the audio provider."
        android.media.MediaPlayer.MEDIA_ERROR_MALFORMED in codes ->
            "The audio stream returned by the recording is damaged or incomplete, so Android cannot decode it."
        android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED in codes ->
            "The audio format returned by the recording format is not supported by this Android media player."
        android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED in codes ->
            "Android's media playback service stopped unexpectedly. Try playing the recitation again."
        android.media.MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK in codes ->
            "Android cannot stream this audio file in its current format."
        causes.any { it is java.io.IOException } ->
            "The network or audio stream was interrupted while Android was reading the recorded recitation."
        else -> error?.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Android reported an unknown error while loading the recorded recitation."
    }
    val diagnostics = buildList {
        mediaPlayerWhat?.let { add("MediaPlayer code: $it") }
        mediaPlayerExtra?.let { add("detail: $it") }
    }
    return if (diagnostics.isEmpty()) {
        causeMessage
    } else {
        "$causeMessage\n\nDiagnostic: ${diagnostics.joinToString(", ")}."
    }
}

class MainActivity : ComponentActivity() {
    private var speechPlaying by mutableStateOf(false)
    private val pendingRecitationUrls = ArrayDeque<String>()
    private var recitationMediaPlayer: MediaPlayer? = null

    private fun refreshSpeechPlayingState() {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            speechPlaying = recitationMediaPlayer != null || pendingRecitationUrls.isNotEmpty()
        }
    }

    private fun stopRecitationAudio() {
        pendingRecitationUrls.clear()
        val player = recitationMediaPlayer
        recitationMediaPlayer = null
        if (player != null) {
            runCatching { player.setOnPreparedListener(null) }
            runCatching { player.setOnCompletionListener(null) }
            runCatching { player.setOnErrorListener(null) }
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        speechPlaying = false
    }

    private fun showRecitationPlaybackError(message: String) {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            runCatching {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Recitation unavailable")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }.onFailure { dialogError ->
                Log.w("Noor-e-Dhikr", "Could not display recitation playback error dialog", dialogError)
                runCatching {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun failCurrentRecitation(
        error: Throwable? = null,
        mediaPlayerWhat: Int? = null,
        mediaPlayerExtra: Int? = null
    ) {
        val player = recitationMediaPlayer
        recitationMediaPlayer = null
        pendingRecitationUrls.clear()
        if (player != null) {
            runCatching { player.setOnPreparedListener(null) }
            runCatching { player.setOnCompletionListener(null) }
            runCatching { player.setOnErrorListener(null) }
            runCatching { player.reset() }
            runCatching { player.release() }
        }

        val message = recitationPlaybackFailureMessage(error, mediaPlayerWhat, mediaPlayerExtra)
        Log.w("Noor-e-Dhikr", "recitation stream failed: $message", error)
        showRecitationPlaybackError(message)
        refreshSpeechPlayingState()
    }

    private fun startNextRecitationAudio() {
        if (isFinishing || isDestroyed || recitationMediaPlayer != null) return
        if (pendingRecitationUrls.isEmpty()) {
            refreshSpeechPlayingState()
            return
        }

        val url = pendingRecitationUrls.removeFirst()
        val player = MediaPlayer()
        recitationMediaPlayer = player

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setOnPreparedListener { prepared ->
                if (recitationMediaPlayer !== player || isFinishing || isDestroyed) {
                    runCatching { prepared.release() }
                    return@setOnPreparedListener
                }
                runCatching { prepared.start() }
                    .onFailure { failCurrentRecitation(it) }
            }
            player.setOnCompletionListener { completed ->
                if (recitationMediaPlayer === player) {
                    recitationMediaPlayer = null
                    runCatching { completed.release() }
                    startNextRecitationAudio()
                } else {
                    runCatching { completed.release() }
                }
            }
            player.setOnErrorListener { _, what, extra ->
                if (recitationMediaPlayer === player) {
                    failCurrentRecitation(mediaPlayerWhat = what, mediaPlayerExtra = extra)
                } else {
                    runCatching { player.release() }
                }
                true
            }
            player.setDataSource(url)
            player.prepareAsync()
            refreshSpeechPlayingState()
        } catch (error: Exception) {
            failCurrentRecitation(error)
        }
    }

    private fun isTrustedRecitationUrl(url: String): Boolean =
        url.startsWith("https://verses.quran.foundation/") ||
                url.startsWith("https://audio.opendua.org/")

    private fun playRecitations(urls: List<String>, queueMode: Int) {
        if (isFinishing || isDestroyed) return
        val safeUrls = urls.map { it.trim() }.filter { isTrustedRecitationUrl(it) }
        if (safeUrls.isEmpty()) {
            if (urls.isNotEmpty()) {
                showRecitationPlaybackError("The selected recitation URL was not from a supported HTTPS audio host.")
            } else {
                runCatching {
                    Toast.makeText(this, "A recorded recitation is not available for this entry.", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        if (queueMode == RECITATION_QUEUE_REPLACE) stopRecitationAudio()
        safeUrls.forEach { pendingRecitationUrls.addLast(it) }
        refreshSpeechPlayingState()
        startNextRecitationAudio()
    }

    private fun stopRecitationPlayback() {
        stopRecitationAudio()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 10)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 10)
        val store = TasbeehStore(applicationContext)
        setContent {
            val openDuaRepository = remember { OpenDuaCatalogRepository(applicationContext) }
            val quranicDuaRepository = remember { QuranicDuaRepository(applicationContext) }
            val cachedOpenDua = remember(openDuaRepository) { openDuaRepository.loadCachedSnapshot() }
            var openDuaItems by remember { mutableStateOf(cachedOpenDua.items) }
            var openDuaCategories by remember { mutableStateOf(cachedOpenDua.categories) }
            var openDuaIsRefreshing by remember { mutableStateOf(true) }
            var openDuaRefreshError by remember { mutableStateOf<String?>(null) }
            var openDuaCacheWriteFailed by remember { mutableStateOf(false) }
            var openDuaRetryCount by remember { mutableIntStateOf(0) }
            var extendedQuranicItems by remember { mutableStateOf(quranicDuaRepository.loadCachedItems()) }

            LaunchedEffect(openDuaRepository, openDuaRetryCount) {
                openDuaIsRefreshing = true
                openDuaRefreshError = null
                openDuaCacheWriteFailed = false
                try {
                    val snapshot = openDuaRepository.refresh()
                    if (snapshot.items.isEmpty() || snapshot.categories.isEmpty()) {
                        throw IllegalStateException("OpenDua returned an empty catalogue.")
                    }
                    openDuaItems = snapshot.items
                    openDuaCategories = snapshot.categories
                    openDuaCacheWriteFailed = !snapshot.cachePersisted
                    if (!snapshot.cachePersisted) {
                        Log.e("Noor-e-Dhikr", "OpenDua catalogue loaded but could not be committed to offline storage")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("Noor-e-Dhikr", "OpenDua catalogue refresh failed; retaining the last verified local copy", error)
                    openDuaRefreshError = error.localizedMessage
                        ?.takeIf { it.isNotBlank() }
                        ?: "The catalogue could not be downloaded. Check your connection and try again."
                } finally {
                    openDuaIsRefreshing = false
                }
            }

            LaunchedEffect(quranicDuaRepository) {
                runCatching { extendedQuranicItems = quranicDuaRepository.refresh() }
                    .onFailure { Log.w("Noor-e-Dhikr", "Qur'anic text refresh failed; using cached references", it) }
            }
            val catalogItems = remember(openDuaItems, extendedQuranicItems) {
                (extendedQuranicItems + openDuaItems).distinctBy { it.id }
            }
            var storageRetryCount by remember { mutableIntStateOf(0) }
            var storageReadError by remember { mutableStateOf(false) }
            val savedStateFlow = remember(store, storageRetryCount) {
                store.state
                    .onStart {
                        storageReadError = false
                        store.migrateToSingleSession()
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        // Do not replace a failed read with default preferences:
                        // that could make existing user data appear to be erased.
                        Log.e("Noor-e-Dhikr", "Unable to load saved state", error)
                        storageReadError = true
                    }
            }
            val savedState by savedStateFlow.collectAsState(initial = null)
            val saved = savedState
            when {
                storageReadError -> TasbeehStorageRecoveryScreen(
                    onRetry = { storageRetryCount++ }
                )
                saved == null -> TasbeehStartupScreen()
                else -> TasbeehApp(
                    state = saved,
                    store = store,
                    isSpeaking = speechPlaying,
                    catalogItems = catalogItems,
                    catalogCategories = openDuaCategories,
                    openDuaIsRefreshing = openDuaIsRefreshing,
                    openDuaRefreshError = openDuaRefreshError,
                    openDuaCacheWriteFailed = openDuaCacheWriteFailed,
                    onRetryOpenDuaCatalog = { openDuaRetryCount++ },
                    onPlayRecitation = { urls, queueMode -> playRecitations(urls, queueMode) },
                    onStopSpeech = { stopRecitationPlayback() }
                ) {
                    if (saved.hapticsEnabled) {
                        runCatching {
                            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopRecitationAudio()
        super.onDestroy()
    }
}

@Composable
private fun TasbeehStartupScreen() {
    Box(
        Modifier.fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(58.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Panel2)
                    .border(1.dp, Gold.copy(alpha = .4f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center
            ) {
                KaabaMark(Modifier.size(29.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("NOOR-E-DHIKR", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
            Spacer(Modifier.height(7.dp))
            Text("Preparing your remembrance", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(23.dp),
                color = Gold,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun TasbeehStorageRecoveryScreen(onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Saved data is unavailable",
                color = Ivory,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The app could not read its saved data. Nothing has been reset. Check available device storage, then try again.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
            ) {
                Text("RETRY", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TasbeehApp(
    state: TasbeehState,
    store: TasbeehStore,
    isSpeaking: Boolean,
    catalogItems: List<DhikrItem>,
    catalogCategories: List<String>,
    openDuaIsRefreshing: Boolean,
    openDuaRefreshError: String?,
    openDuaCacheWriteFailed: Boolean,
    onRetryOpenDuaCatalog: () -> Unit,
    onPlayRecitation: (List<String>, Int) -> Unit,
    onStopSpeech: () -> Unit,
    onTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun launchStoreOperation(
        onSuccess: () -> Unit = {},
        operation: suspend () -> Unit
    ) {
        scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Noor-e-Dhikr", "A saved-data operation failed", error)
                Toast.makeText(
                    context,
                    "The action could not be completed. Your saved data has not been intentionally reset. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            onSuccess()
        }
    }

    var showHistoryScreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showTarget by remember { mutableStateOf(false) }
    var showDhikrScreen by remember { mutableStateOf(false) }
    var showCustomDhikrEditor by remember { mutableStateOf(false) }
    var editingCustomDhikr by remember { mutableStateOf<CustomDhikr?>(null) }
    val selectedCustomDhikr = state.customDhikrs.firstOrNull { it.id == state.selectedCustomDhikrId }
    LaunchedEffect(catalogItems, state.dhikr, state.selectedCustomDhikrId, state.customDhikrs, state.currentSession?.status) {
        val customSelectionExists = state.selectedCustomDhikrId?.let { selectedId ->
            state.customDhikrs.any { it.id == selectedId }
        } ?: false
        val builtInSelectionExists = state.selectedCustomDhikrId == null &&
            catalogItems.any { it.value == state.dhikr }
        val sessionIsOpenForSelection = state.currentSession?.status == SessionStatus.ACTIVE ||
            state.currentSession?.status == SessionStatus.PAUSED
        if (!customSelectionExists && !builtInSelectionExists && !sessionIsOpenForSelection) {
            val fallback = catalogItems.firstOrNull { it.category == "Quran" } ?: catalogItems.firstOrNull()
            if (fallback != null) {
                store.selectBuiltInDhikr(fallback.value, fallback.arabic, System.currentTimeMillis())
            }
        }
    }
    val selectedDhikr = catalogItems.firstOrNull { it.value == state.dhikr }
    // Keep the completed session's captured content visible until the user
    // starts the next round. The next count still uses the currently selected
    // Dhikr, so editing a custom entry affects future sessions only.
    val sessionIsOpen = state.currentSession?.status == SessionStatus.ACTIVE ||
        state.currentSession?.status == SessionStatus.PAUSED
    val activeSession = state.currentSession?.takeIf { sessionIsOpen }
    val completedRoundStillDisplayed =
        state.currentSession?.status == SessionStatus.COMPLETED &&
            state.count == state.currentSession.count
    val sessionForDisplay = sessionSnapshotForDisplay(state.currentSession, state.count)

    val selectedDhikrTitle = selectedCustomDhikr?.title ?: selectedDhikr?.title ?: state.dhikr
    val selectedDhikrArabic = selectedCustomDhikr?.arabic?.takeIf { it.isNotBlank() } ?: selectedDhikr?.arabic
    val selectedDhikrTransliteration = selectedCustomDhikr?.transliteration?.takeIf { it.isNotBlank() }
        ?: selectedDhikr?.transliteration
    val selectedDhikrMeaning = selectedCustomDhikr?.meaning.orEmpty().ifBlank { selectedDhikr?.meaning.orEmpty() }

    val activeDhikrTitle = sessionForDisplay?.dhikr ?: selectedDhikrTitle
    val activeDhikrArabic = if (sessionForDisplay != null) {
        sessionForDisplay.arabic.takeIf { it.isNotBlank() }
    } else {
        selectedDhikrArabic
    }
    val activeDhikrTransliteration = if (sessionForDisplay != null) {
        sessionForDisplay.transliteration.takeIf { it.isNotBlank() }
    } else {
        selectedDhikrTransliteration
    }
    val activeDhikrMeaning = sessionForDisplay?.meaning ?: selectedDhikrMeaning

    // Counting after completion starts a new session from the current selection,
    // not from the historical snapshot still shown on screen.
    val countDhikrTitle = activeSession?.dhikr ?: selectedDhikrTitle
    val countDhikrArabic = if (activeSession != null) {
        activeSession.arabic.takeIf { it.isNotBlank() }
    } else {
        selectedDhikrArabic
    }
    val countDhikrTransliteration = if (activeSession != null) {
        activeSession.transliteration.takeIf { it.isNotBlank() }
    } else {
        selectedDhikrTransliteration
    }
    val countDhikrMeaning = activeSession?.meaning ?: selectedDhikrMeaning
    val activeQuranAyahKeys = if (state.selectedCustomDhikrId == null) {
        catalogItems.firstOrNull { it.value == activeDhikrTitle }?.quranAyahKeys.orEmpty()
    } else {
        emptyList()
    }
    val countQuranAyahKeys = if (activeSession == null && state.selectedCustomDhikrId == null) {
        catalogItems.firstOrNull { it.value == countDhikrTitle }?.quranAyahKeys.orEmpty()
    } else {
        emptyList()
    }

    fun recitationUrlsFor(title: String, quranKeys: List<String>, isCustom: Boolean): List<String> {
        if (isCustom) return emptyList()
        val item = catalogItems.firstOrNull { it.value == title }
        val resolvedAyahKeys = quranKeys.ifEmpty { item?.quranAyahKeys.orEmpty() }
        return item?.recitationAudioUrls?.takeIf { it.isNotEmpty() }
            ?: QuranRecitationAudio.audioUrlsForAyahs(resolvedAyahKeys)
    }
    val activeRecitationUrls = recitationUrlsFor(
        activeDhikrTitle, activeQuranAyahKeys, state.selectedCustomDhikrId != null
    )
    val countRecitationUrls = recitationUrlsFor(
        countDhikrTitle, countQuranAyahKeys, state.selectedCustomDhikrId != null
    )
    val effectiveTarget = sessionForDisplay?.target ?: state.target
    val sessionPaused = state.currentSession?.status == SessionStatus.PAUSED
    val canUndo = canUndoCount(state.count, state.currentSession?.status)

    // The editor owns its own BackHandler so it can ask before discarding edits.
    BackHandler(enabled = showDhikrScreen || showHistoryScreen || showSettings) {
        if (showDhikrScreen) {
            showDhikrScreen = false
        } else if (showHistoryScreen) {
            showHistoryScreen = false
        } else {
            showSettings = false
        }
    }

    fun increment() {
        val now = System.currentTimeMillis()
        launchStoreOperation {
            val outcome = store.countDhikr(
                dhikr = countDhikrTitle,
                arabic = countDhikrArabic.orEmpty(),
                transliteration = countDhikrTransliteration.orEmpty(),
                meaning = countDhikrMeaning,
                fallbackTarget = effectiveTarget,
                now = now
            )
            if (outcome == CountOutcome.COUNTED) {
                if (state.soundEnabled && countRecitationUrls.isNotEmpty()) {
                    onPlayRecitation(countRecitationUrls, arabicSpeechQueueMode(manualPlayback = false))
                }
                onTap()
            }
        }
    }

    val systemDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = systemDensity.density,
            fontScale = systemDensity.fontScale * state.fontScale
        )
    ) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Gold, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Ivory, secondary = Color(0xFF16714B))) {
        when {
            showCustomDhikrEditor -> {
                CustomDhikrEditorScreen(
                    existing = editingCustomDhikr,
                    existingTitles = catalogItems.map { it.title } + state.customDhikrs
                        .filterNot { it.id == editingCustomDhikr?.id }
                        .map { it.title },
                    onBack = {
                        showCustomDhikrEditor = false
                        editingCustomDhikr = null
                    },
                    onSave = { custom ->
                        launchStoreOperation(
                            operation = { store.saveCustomDhikr(custom) },
                            onSuccess = {
                                showCustomDhikrEditor = false
                                editingCustomDhikr = null
                            }
                        )
                    }
                )
            }
            showHistoryScreen -> {
                SessionHistoryScreen(
                    session = state.currentSession,
                    onBack = { showHistoryScreen = false }
                )
            }
            showDhikrScreen -> {
                DhikrSelectionScreen(
                    catalogItems = catalogItems,
                    openDuaCategories = catalogCategories,
                    openDuaIsRefreshing = openDuaIsRefreshing,
                    openDuaRefreshError = openDuaRefreshError,
                    openDuaCacheWriteFailed = openDuaCacheWriteFailed,
                    onRetryOpenDuaCatalog = onRetryOpenDuaCatalog,
                    currentDhikr = state.dhikr,
                    currentCustomDhikrId = state.selectedCustomDhikrId,
                    customDhikrs = state.customDhikrs,
                    onBack = { showDhikrScreen = false },
                    onSelect = { item ->
                        when {
                            item.customId != null && item.customId != state.selectedCustomDhikrId -> {
                                val custom = state.customDhikrs.firstOrNull { it.id == item.customId }
                                if (custom != null) launchStoreOperation(
                                    onSuccess = { showDhikrScreen = false }
                                ) {
                                    store.selectCustomDhikr(custom, System.currentTimeMillis())
                                }
                            }
                            item.customId == null &&
                                (state.selectedCustomDhikrId != null || item.value != state.dhikr) -> {
                                val builtIn = catalogItems.firstOrNull { it.value == item.value }
                                launchStoreOperation(
                                    onSuccess = { showDhikrScreen = false }
                                ) {
                                    store.selectBuiltInDhikr(
                                        item.value,
                                        builtIn?.arabic.orEmpty(),
                                        System.currentTimeMillis()
                                    )
                                }
                            }
                            else -> showDhikrScreen = false
                        }
                    },
                    onCreateCustom = {
                        editingCustomDhikr = null
                        showCustomDhikrEditor = true
                    },
                    onEditCustom = { custom ->
                        editingCustomDhikr = custom
                        showCustomDhikrEditor = true
                    },
                    onDeleteCustom = { custom, onDeleted ->
                        launchStoreOperation(onSuccess = onDeleted) {
                            store.deleteCustomDhikr(custom.id, System.currentTimeMillis())
                        }
                    }
                )
            }
            else -> {
            BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
            val compactLayout = maxHeight < 760.dp ||
                systemDensity.fontScale * state.fontScale >= 1.2f
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF3A2A0F).copy(alpha = .38f), Color.Transparent), Offset(size.width * .5f, size.height * .30f), size.width * .8f),
                    size.width * .8f, Offset(size.width * .5f, size.height * .30f)
                )
                val step = 34.dp.toPx()
                for (x in -size.height.toInt()..size.width.toInt() step step.toInt()) {
                    drawLine(Color(0xFFB99450).copy(alpha = .035f), Offset(x.toFloat(), 0f), Offset(x + size.height, size.height), 1.dp.toPx())
                }
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(
                    onHistory = { showHistoryScreen = true },
                    onSettings = { showSettings = true }
                )
                Spacer(Modifier.height(if (compactLayout) 7.dp else 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(Gold, CircleShape))
                    Text("A MOMENT OF REMEMBRANCE", color = Muted, fontSize = 11.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(if (compactLayout) 5.dp else 10.dp))
                Text(
                    activeDhikrTitle,
                    color = Ivory,
                    fontSize = if (activeDhikrTitle.length > 22) 19.sp else 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val dhikrSubtitle = activeDhikrArabic ?: activeDhikrTransliteration
                if (!dhikrSubtitle.isNullOrBlank()) {
                    Text(
                        dhikrSubtitle,
                        color = GoldLight.copy(alpha = .92f),
                        fontSize = if (dhikrSubtitle.length > 55) 14.sp else 19.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp).fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = state.count >= effectiveTarget,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Surface(
                        color = Color(0xFF243528),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Gold.copy(alpha = .45f)
                        )
                    ) {
                        Text(
                            "✦  Target reached — MashaAllah  ✦",
                            color = GoldLight,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }

                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    // Let the counter use the available width and the remaining
                    // vertical space, while naturally shrinking on short screens.
                    val preferredDiameter = if (compactLayout) maxWidth * .86f else maxWidth
                    val diameter = min(maxWidth, min(maxHeight, preferredDiameter))
                    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                        TasbeehStrand(count = state.count, onCount = { increment() }, enabled = !sessionPaused, modifier = Modifier.fillMaxSize())
                        AnimatedContent(
                            targetState = state.count,
                            transitionSpec = {
                                (slideInVertically { it / 3 } + fadeIn(tween(130))) togetherWith
                                    (slideOutVertically { -it / 3 } + fadeOut(tween(130)))
                            },
                            label = "bead-counter",
                            modifier = Modifier.align(Alignment.Center).offset(y = diameter * -.075f)
                        ) { count ->
                            Text(count.toString(), color = Ivory, fontSize = if (compactLayout) 38.sp else 46.sp,
                                fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                Spacer(Modifier.height(if (compactLayout) 1.dp else 4.dp))
                Text("${(effectiveTarget - state.count.coerceAtMost(effectiveTarget)).coerceAtLeast(0)} remaining",
                    color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(if (compactLayout) 7.dp else 12.dp))
                ProgressTrack(state.count, effectiveTarget)
                Spacer(Modifier.height(if (compactLayout) 8.dp else 15.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallAction(Icons.Default.Undo, "UNDO", canUndo, Modifier.weight(1f)) {
                        launchStoreOperation { store.undoCount(System.currentTimeMillis()) }
                    }
                    if (sessionIsOpen) {
                        SmallAction(
                            if (sessionPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            if (sessionPaused) "RESUME" else "PAUSE",
                            true,
                            Modifier.weight(1f)
                        ) {
                            launchStoreOperation {
                                if (sessionPaused) store.resumeCurrentSession()
                                else store.pauseCurrentSession()
                            }
                        }
                    }
                    SmallAction(
                        Icons.Default.Refresh,
                        "RESET",
                        state.count > 0 || sessionIsOpen,
                        Modifier.weight(1f)
                    ) { showReset = true }
                }
                Spacer(Modifier.height(if (compactLayout) 7.dp else 11.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickChip(
                        if (sessionIsOpen || completedRoundStillDisplayed) {
                            "Next target · ${state.target}"
                        } else {
                            "Target · ${state.target}"
                        },
                        Modifier.weight(1f)
                    ) { showTarget = true }
                    QuickChip("Change dhikr", Modifier.weight(1f)) { showDhikrScreen = true }
                }
                Text(
                    "Developed by: Amir Malik (2026)",
                    color = Muted.copy(alpha = .72f),
                    fontSize = 9.sp,
                    letterSpacing = .55.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 7.dp)
                )
            }
        }

        }
        }

        if (!showDhikrScreen && !showHistoryScreen && showReset) AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = Panel,
            title = { Text("Reset counter?", color = Ivory) },
            text = {
                Column {
                    Text(
                        if (sessionIsOpen) {
                            "Your current session will be saved as incomplete, then the counter will return to zero."
                        } else {
                            "Your count will return to zero."
                        },
                        color = Muted
                    )
                    if (sessionIsOpen) {
                        TextButton(
                            onClick = {
                                launchStoreOperation(
                                    operation = { store.resetCounter(saveSession = false, now = System.currentTimeMillis()) },
                                    onSuccess = { showReset = false }
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                        ) {
                            Text("Discard this session instead", color = Color(0xFFFF9B82), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    launchStoreOperation(
                        operation = { store.resetCounter(saveSession = true, now = System.currentTimeMillis()) },
                        onSuccess = { showReset = false }
                    )
                }) {
                    Text(if (sessionIsOpen) "SAVE & RESET" else "RESET COUNTER", color = Gold)
                }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("CANCEL", color = Muted) } }
        )
        if (!showDhikrScreen && !showHistoryScreen && showTarget) TargetDialog(
            current = state.target,
            onDismiss = { showTarget = false },
            hasOpenSession = sessionIsOpen,
            isNextTarget = sessionIsOpen || completedRoundStillDisplayed
        ) { target ->
            launchStoreOperation(
                operation = { store.update(target = target) },
                onSuccess = { showTarget = false }
            )
        }
        if (!showDhikrScreen && !showHistoryScreen && showSettings) SettingsDialog(
            state = state,
            quranAyahKeys = activeQuranAyahKeys,
            activeAudioUrls = activeRecitationUrls,
            isSpeaking = isSpeaking,
            onPlayRecitation = {
                onPlayRecitation(activeRecitationUrls, arabicSpeechQueueMode(manualPlayback = true))
            },
            onStopSpeech = onStopSpeech,
            onDismiss = { showSettings = false },
            onSave = { sound, haptics, fontScale ->
                launchStoreOperation(onSuccess = { showSettings = false }) {
                    store.update(soundEnabled = sound, hapticsEnabled = haptics, fontScale = fontScale)
                }
            }
        )
        }
    }
}

@Composable
private fun Header(
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF252B20), Color(0xFF111510))))
                    .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { KaabaMark(Modifier.size(23.dp)) }
            Column {
                Text(
                    "NOOR-E-DHIKR",
                    color = Ivory,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onHistory, modifier = Modifier.size(48.dp).background(Panel, CircleShape)) {
                Icon(Icons.Default.History, "Session history", tint = Gold, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).background(Panel, CircleShape)) {
                Icon(Icons.Default.Tune, "Settings", tint = Gold, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun KaabaMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF080A09), Offset(w * .12f, h * .16f), Size(w * .76f, h * .7f), androidx.compose.ui.geometry.CornerRadius(w * .08f))
        drawRect(Gold, Offset(w * .12f, h * .34f), Size(w * .76f, h * .10f))
        drawLine(GoldLight, Offset(w * .12f, h * .52f), Offset(w * .88f, h * .52f), w * .06f)
    }
}

internal fun isInsideTasbeehTapTarget(
    x: Float,
    y: Float,
    width: Float,
    height: Float
): Boolean {
    if (width <= 0f || height <= 0f) return false
    val radiusX = width * .355f
    val radiusY = height * .325f
    val normalizedX = (x - width * .5f) / radiusX
    val normalizedY = (y - height * .425f) / radiusY
    // 1.18 includes the outside edge of the beads while rejecting the
    // invisible corners of the square Canvas hit area.
    return normalizedX * normalizedX + normalizedY * normalizedY <= 1.18f
}

@Composable
private fun TasbeehStrand(
    count: Int,
    onCount: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val rotation = remember { Animatable(count.toFloat()) }
    val pressure = remember { Animatable(0f) }
    val tasselSway = remember { Animatable(0f) }
    var previousCount by remember { mutableIntStateOf(count) }

    LaunchedEffect(count) {
        val changedBy = count - previousCount
        previousCount = count
        if (changedBy != 0) {
            // Pull one bead through the counting point. The loop advances first,
            // then the cord tension and tassel settle with a short physical recoil.
            launch {
                pressure.snapTo(0f)
                pressure.animateTo(1f, tween(68, easing = FastOutSlowInEasing))
                pressure.animateTo(0f, spring(dampingRatio = .52f, stiffness = 360f))
            }
            launch {
                tasselSway.animateTo(
                    if (changedBy > 0) .82f else -.82f,
                    tween(125, easing = FastOutSlowInEasing)
                )
                tasselSway.animateTo(0f, spring(dampingRatio = .46f, stiffness = 215f))
            }

            if (kotlin.math.abs(changedBy) == 1) {
                rotation.animateTo(
                    count.toFloat(),
                    spring(dampingRatio = .72f, stiffness = 340f)
                )
            } else {
                rotation.snapTo(count.toFloat())
            }
        }
    }

    Canvas(
        modifier
            .alpha(if (enabled) 1f else .58f)
            .semantics {
                contentDescription = if (enabled) {
                    "Tasbeeh counter: $count repetitions. Tap inside the bead circle to count one."
                } else {
                    "Tasbeeh counter paused at $count repetitions. Resume the session to continue."
                }
                role = Role.Button
                if (enabled) {
                    onClick(label = "Count one dhikr") {
                        onCount()
                        true
                    }
                } else {
                    stateDescription = "Session paused"
                }
            }
            .then(
                if (enabled) Modifier.pointerInput(onCount) {
                    detectTapGestures { tap ->
                        if (isInsideTasbeehTapTarget(
                                x = tap.x,
                                y = tap.y,
                                width = size.width.toFloat(),
                                height = size.height.toFloat()
                            )
                        ) {
                            onCount()
                        }
                    }
                } else Modifier
            )
    ) {
        val minDimension = size.minDimension
        val cx = size.width * .5f
        val cy = size.height * .425f
        val radiusX = size.width * .355f
        val radiusY = size.height * .325f
        val beadCount = 33
        // Slightly smaller than the cord's bead-to-bead pitch, leaving a fine
        // dark seam so the individual polished beads read as separate objects.
        val beadRadius = minDimension * .0325f
        val pitch = 2.0 * PI / beadCount
        val activeIndex = ((count % beadCount) + beadCount) % beadCount
        val press = pressure.value

        // Warm light behind the strand separates the luminous gold beads
        // from the dark background without adding an artificial glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF946B27).copy(alpha = .18f),
                    Color(0xFF173C29).copy(alpha = .07f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radiusX * 1.48f
            ),
            radius = radiusX * 1.48f,
            center = Offset(cx, cy)
        )

        // The cord is dark braided silk with a subtle emerald thread and a fine
        // gold reflection. Both remain visible in the tiny gaps between beads.
        val cordTopLeft = Offset(cx - radiusX, cy - radiusY)
        val cordSize = Size(radiusX * 2f, radiusY * 2f)
        drawOval(
            color = Color(0xFF010202),
            topLeft = cordTopLeft,
            size = cordSize,
            style = Stroke(width = beadRadius * .25f)
        )
        drawOval(
            color = Color(0xFF16714B).copy(alpha = .72f),
            topLeft = Offset(cx - radiusX, cy - radiusY - beadRadius * .025f),
            size = cordSize,
            style = Stroke(width = beadRadius * .058f)
        )
        drawOval(
            color = Color(0xFFFFD875).copy(alpha = .48f),
            topLeft = Offset(cx - radiusX, cy - radiusY + beadRadius * .055f),
            size = cordSize,
            style = Stroke(width = beadRadius * .026f)
        )

        // Gold divider beads separate the traditional groups of eleven.
        listOf(11, 22).forEach { boundary ->
            val markerAngle = -PI / 2.0 + (boundary - .5 - rotation.value) * pitch
            val markerCenter = Offset(
                cx + cos(markerAngle).toFloat() * radiusX,
                cy + sin(markerAngle).toFloat() * radiusY
            )
            val markerRadius = beadRadius * .34f
            drawCircle(
                color = Color.Black.copy(alpha = .88f),
                radius = markerRadius * 1.28f,
                center = Offset(markerCenter.x + markerRadius * .12f, markerCenter.y + markerRadius * .25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFFF3C9),
                        .18f to Color(0xFFFFD66D),
                        .48f to Color(0xFFD99B2B),
                        .82f to Color(0xFF8E5A16),
                        1f to Color(0xFF3D250B)
                    ),
                    center = Offset(markerCenter.x - markerRadius * .34f, markerCenter.y - markerRadius * .42f),
                    radius = markerRadius * 1.58f
                ),
                radius = markerRadius,
                center = markerCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = .86f),
                radius = markerRadius * .11f,
                center = Offset(markerCenter.x - markerRadius * .34f, markerCenter.y - markerRadius * .42f)
            )
        }

        // All 33 beads share one continuous loop. The two beads nearest the
        // counting point ease inward together as the next bead is pulled through.
        val beadPositions = (0 until beadCount).map { index ->
            val angle = -PI / 2.0 + (index - rotation.value) * pitch
            val sine = sin(angle).toFloat()
            val cosine = cos(angle).toFloat()
            val depth = (sine + 1f) * .5f
            val wrapped = (angle + PI / 2.0 + PI) % (2.0 * PI)
            val positiveWrapped = if (wrapped < 0.0) wrapped + 2.0 * PI else wrapped
            val angularDistance = kotlin.math.abs(positiveWrapped - PI)
            val pinchDistance = (angularDistance / (pitch * 2.35)).toFloat()
            val pinch = (1f - pinchDistance).coerceIn(0f, 1f)
            val localPressure = press * pinch * pinch
            // Far-side beads sit a little smaller, with tiny deterministic
            // size differences to avoid a machine-perfect plastic appearance.
            val handFinishedVariation = 0.985f + 0.018f * (0.5f + 0.5f * cos(index * 2.399f)).toFloat()
            val radius = beadRadius * (.92f + .12f * depth) * handFinishedVariation
            val radialInset = beadRadius * .15f * localPressure
            val x = cx + cosine * (radiusX - radialInset)
            val y = cy + sine * (radiusY - radialInset) + localPressure * beadRadius * .07f
            Triple(index, Offset(x, y), radius)
        }.sortedBy { it.second.y }

        // Tiny emerald silk knots peek through the gaps and show that the
        // gold beads are threaded on a real cord rather than floating apart.
        for (index in 0 until beadCount) {
            val knotAngle = -PI / 2.0 + (index + .5 - rotation.value) * pitch
            val knotCenter = Offset(
                cx + cos(knotAngle).toFloat() * radiusX,
                cy + sin(knotAngle).toFloat() * radiusY
            )
            val knotRadius = beadRadius * .14f
            drawCircle(
                color = Color.Black.copy(alpha = .78f),
                radius = knotRadius * 1.65f,
                center = Offset(knotCenter.x + knotRadius * .22f, knotCenter.y + knotRadius * .34f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8BC49B), Color(0xFF26784E), Color(0xFF082C1B)),
                    center = Offset(knotCenter.x - knotRadius * .28f, knotCenter.y - knotRadius * .35f),
                    radius = knotRadius * 1.55f
                ),
                radius = knotRadius,
                center = knotCenter
            )
            drawLine(
                color = Color(0xFFB7D8B9).copy(alpha = .72f),
                start = Offset(knotCenter.x - knotRadius * .35f, knotCenter.y - knotRadius * .22f),
                end = Offset(knotCenter.x + knotRadius * .28f, knotCenter.y + knotRadius * .30f),
                strokeWidth = knotRadius * .18f,
                cap = StrokeCap.Round
            )
        }

        beadPositions.forEach { (index, center, radius) ->
            val x = center.x
            val y = center.y
            val isActive = index == activeIndex
            val depth = ((y - (cy - radiusY)) / (radiusY * 2f)).coerceIn(0f, 1f)
            val goldTone = when (index % 4) {
                0 -> Color(0xFFFFE6A0)
                1 -> Color(0xFFF8D477)
                2 -> Color(0xFFE9B94F)
                else -> Color(0xFFFFD982)
            }

            // Soft contact shadow and warm reflected light give the bead
            // weight against the dark braided cord.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = .78f),
                        Color(0xFF120B03).copy(alpha = .48f),
                        Color.Transparent
                    ),
                    center = Offset(x + radius * .20f, y + radius * .30f),
                    radius = radius * 1.34f
                ),
                radius = radius * 1.34f,
                center = Offset(x + radius * .12f, y + radius * .20f)
            )

            // Kiswah-inspired gold: pale champagne at the light source, rich
            // satin gold across the face, and antique bronze in the shadow.
            // The gradient makes each bead spherical rather than a flat yellow dot.
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFFF9E7),
                        .055f to Color(0xFFFFEFC1),
                        .16f to goldTone,
                        .34f to Color(0xFFE8B64D),
                        .56f to Color(0xFFC48725),
                        .78f to Color(0xFF855016),
                        .94f to Color(0xFF4A2B0B),
                        1f to Color(0xFF281605)
                    ),
                    center = Offset(x - radius * .38f, y - radius * .48f),
                    radius = radius * 1.48f
                ),
                radius = radius * (1f + if (isActive) press * .035f else 0f),
                center = center
            )

            // A dark bronze bevel and a fine champagne edge give the polished
            // bead a real material boundary without outlining it like an icon.
            drawCircle(
                color = Color(0xFF5C370D).copy(alpha = .56f),
                radius = radius * .965f,
                center = center,
                style = Stroke(width = radius * .055f)
            )
            drawCircle(
                color = Color(0xFFFFE9A9).copy(alpha = if (isActive) .72f else .42f),
                radius = radius * .925f,
                center = center,
                style = Stroke(width = radius * .028f)
            )

            // Soft rectangular studio reflection and a narrow curved glint
            // recreate the way polished metal catches a real light source.
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFBEA).copy(alpha = if (isActive) .88f else .72f),
                        Color(0xFFFFE6A0).copy(alpha = .34f),
                        Color.Transparent
                    ),
                    center = Offset(x - radius * .36f, y - radius * .50f),
                    radius = radius * .78f
                ),
                topLeft = Offset(x - radius * .57f, y - radius * .69f),
                size = Size(radius * 1.02f, radius * .64f)
            )
            drawArc(
                color = Color(0xFFFFF9E5).copy(alpha = if (isActive) .82f else .58f),
                startAngle = 202f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = Offset(x - radius * .61f, y - radius * .59f),
                size = Size(radius * 1.19f, radius * .88f),
                style = Stroke(width = radius * .052f)
            )
            drawCircle(
                color = Color.White.copy(alpha = if (isActive) .96f else .82f),
                radius = radius * .052f,
                center = Offset(x - radius * .38f, y - radius * .48f)
            )
            // A second, narrower studio reflection makes the polished surface
            // feel curved while keeping the lighting consistent across beads.
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF8E4).copy(alpha = .46f),
                        Color(0xFFFFE7A8).copy(alpha = .16f),
                        Color.Transparent
                    ),
                    center = Offset(x - radius * .18f, y - radius * .03f),
                    radius = radius * .40f
                ),
                topLeft = Offset(x - radius * .30f, y - radius * .18f),
                size = Size(radius * .25f, radius * .70f)
            )

            // Fine lathe-turned lines on the lower hemisphere catch the light
            // like a hand-polished decorative bead, not a flat icon.
            drawArc(
                color = Color(0xFF5B350A).copy(alpha = .42f),
                startAngle = 18f,
                sweepAngle = 145f,
                useCenter = false,
                topLeft = Offset(x - radius * .76f, y - radius * .76f),
                size = Size(radius * 1.52f, radius * 1.52f),
                style = Stroke(width = radius * .035f)
            )
            drawArc(
                color = Color(0xFFFFE7A0).copy(alpha = .38f),
                startAngle = 22f,
                sweepAngle = 138f,
                useCenter = false,
                topLeft = Offset(x - radius * .70f, y - radius * .70f),
                size = Size(radius * 1.40f, radius * 1.40f),
                style = Stroke(width = radius * .020f)
            )

            // A fine warm reflection on the lower edge reinforces the depth of
            // the front arc while keeping the gold material consistent.
            if (depth > .72f) {
                drawArc(
                    color = Color(0xFFFFD56A).copy(alpha = .24f),
                    startAngle = 24f,
                    sweepAngle = 62f,
                    useCenter = false,
                    topLeft = Offset(x - radius * .84f, y - radius * .84f),
                    size = Size(radius * 1.68f, radius * 1.68f),
                    style = Stroke(width = radius * .055f)
                )
            }

            // The selected bead gets a tiny moving champagne glint only.
            if (isActive) {
                drawArc(
                    color = Color(0xFFFFF0BF).copy(alpha = .96f),
                    startAngle = 208f,
                    sweepAngle = 44f,
                    useCenter = false,
                    topLeft = Offset(x - radius * 1.01f, y - radius * 1.01f),
                    size = Size(radius * 2.02f, radius * 2.02f),
                    style = Stroke(width = radius * .045f)
                )
            }
        }

        // A longer glossy imam bead with brighter gold collars and a narrow
        // emerald silk thread at the neck.
        val strandBottom = cy + radiusY
        val imamWidth = beadRadius * 1.12f
        val imamHeight = beadRadius * 2.30f
        val imamTop = strandBottom + beadRadius * .88f
        val imamBottom = imamTop + imamHeight
        val imamCenter = Offset(cx, (imamTop + imamBottom) * .5f)

        drawLine(
            color = Color(0xFF080909),
            start = Offset(cx, strandBottom - beadRadius * .04f),
            end = Offset(cx, imamTop + beadRadius * .16f),
            strokeWidth = beadRadius * .17f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF16714B).copy(alpha = .95f),
            start = Offset(cx - beadRadius * .028f, strandBottom + beadRadius * .24f),
            end = Offset(cx - beadRadius * .028f, imamTop + beadRadius * .10f),
            strokeWidth = beadRadius * .04f,
            cap = StrokeCap.Round
        )
        drawOval(
            color = Color.Black.copy(alpha = .84f),
            topLeft = Offset(cx - imamWidth * .60f, imamTop + beadRadius * .12f),
            size = Size(imamWidth * 1.20f, imamHeight * 1.05f)
        )
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFFF2C8),
                    .10f to Color(0xFFFFDF8A),
                    .26f to Color(0xFFE8B74C),
                    .52f to Color(0xFFB87920),
                    .78f to Color(0xFF71400F),
                    1f to Color(0xFF321B07)
                ),
                center = Offset(imamCenter.x - imamWidth * .28f, imamCenter.y - imamHeight * .30f),
                radius = imamHeight * .95f
            ),
            topLeft = Offset(cx - imamWidth * .5f, imamTop),
            size = Size(imamWidth, imamHeight)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF9E6).copy(alpha = .88f), Color(0xFFFFD875).copy(alpha = .22f), Color.Transparent),
                center = Offset(cx - imamWidth * .25f, imamTop + imamHeight * .22f),
                radius = imamWidth * .58f
            ),
            topLeft = Offset(cx - imamWidth * .37f, imamTop + imamHeight * .10f),
            size = Size(imamWidth * .48f, imamHeight * .58f)
        )
        listOf(.10f, .34f, .66f, .90f).forEachIndexed { index, fraction ->
            val bandY = imamTop + imamHeight * fraction
            val bandWidth = if (index == 0 || index == 3) .49f else .43f
            drawLine(
                color = Color(0xFF71400F).copy(alpha = .62f),
                start = Offset(cx - imamWidth * bandWidth, bandY + beadRadius * .025f),
                end = Offset(cx + imamWidth * bandWidth, bandY + beadRadius * .025f),
                strokeWidth = beadRadius * .105f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFD15C),
                start = Offset(cx - imamWidth * bandWidth, bandY),
                end = Offset(cx + imamWidth * bandWidth, bandY),
                strokeWidth = beadRadius * .065f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFF0C1).copy(alpha = .78f),
                start = Offset(cx - imamWidth * bandWidth * .76f, bandY - beadRadius * .045f),
                end = Offset(cx + imamWidth * bandWidth * .76f, bandY - beadRadius * .045f),
                strokeWidth = beadRadius * .022f,
                cap = StrokeCap.Round
            )
        }

        // Fine gold silk tassel threads have individual curves and a soft
        // follow-through as the strand moves.
        val tasselRoot = imamBottom - imamHeight * .035f
        val swayOffset = tasselSway.value * beadRadius * .78f
        for (strand in 0 until 19) {
            val spread = (strand - 9) / 9f
            val endX = cx + spread * beadRadius * 1.42f + swayOffset
            val endY = tasselRoot + beadRadius * (1.82f + .30f * (1f - kotlin.math.abs(spread)))
            val path = Path().apply {
                moveTo(cx + spread * beadRadius * .09f, tasselRoot)
                cubicTo(
                    cx + spread * beadRadius * .22f + swayOffset * .20f,
                    tasselRoot + beadRadius * .58f,
                    cx + spread * beadRadius * 1.05f + swayOffset * .60f,
                    endY - beadRadius * .42f,
                    endX,
                    endY
                )
            }
            drawPath(
                path = path,
                color = when {
                    strand % 5 == 0 -> Color(0xFFFFF0BE).copy(alpha = .94f)
                    strand % 2 == 0 -> Color(0xFFFFD875).copy(alpha = .90f)
                    else -> Color(0xFFB87D24).copy(alpha = .88f)
                },
                style = Stroke(width = beadRadius * .027f, cap = StrokeCap.Round)
            )
        }
        drawLine(
            color = Color(0xFFFFD56A),
            start = Offset(cx - beadRadius * .52f, tasselRoot + beadRadius * .12f),
            end = Offset(cx + beadRadius * .52f, tasselRoot + beadRadius * .12f),
            strokeWidth = beadRadius * .085f,
            cap = StrokeCap.Round
        )
    }
}
@Composable
private fun ProgressTrack(count: Int, target: Int) {
    val fraction = (count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(420), label = "progress")
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Session progress"
            stateDescription = "${(fraction * 100).toInt()} percent complete"
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SESSION PROGRESS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text("${(animated * 100).toInt()}%", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF2A2D25))) {
            Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF92713C), GoldLight))))
        }
    }
}

@Composable
private fun SessionControlButton(
    paused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (paused) Color(0xFF252014) else Color(0xFF14251B),
            contentColor = if (paused) GoldLight else Color(0xFF9BD0A6)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (paused) Gold.copy(alpha = .58f) else Color(0xFF427354)
        )
    ) {
        Icon(
            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            if (paused) "RESUME SESSION" else "PAUSE SESSION",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(62.dp).clip(RoundedCornerShape(19.dp)).background(Panel)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                if (!enabled) stateDescription = "Unavailable"
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Gold else Muted.copy(alpha = .35f), modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (enabled) Muted else Muted.copy(alpha = .35f), fontSize = 9.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun QuickChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick),
        color = Panel,
        shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3028))) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                color = Ivory,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TargetDialog(
    current: Int,
    onDismiss: () -> Unit,
    hasOpenSession: Boolean,
    isNextTarget: Boolean,
    onSave: (Int) -> Unit
) {
    var custom by remember { mutableStateOf(current.toString()) }
    val presets = listOf(7, 11, 33, 70, 99, 100, 500, 1000, 5000)
    val parsedCustom = custom.toIntOrNull()
    val customIsValid = parsedCustom != null && parsedCustom in 1..9999

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Text(
                if (isNextTarget) "Choose next target" else "Choose your target",
                color = Ivory
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasOpenSession) {
                    Surface(
                        color = Color(0xFF252014),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .28f))
                    ) {
                        Text(
                            "Your current session keeps its original target. This selection applies to your next session.",
                            color = GoldLight,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(11.dp)
                        )
                    }
                }
                Text(
                    "QUICK TARGETS",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp
                )
                presets.chunked(3).forEach { rowTargets ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        rowTargets.forEach { target ->
                            FilterChip(
                                selected = current == target,
                                onClick = { onSave(target) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        if (target >= 1000) String.format(Locale.US, "%,d", target) else target.toString(),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                shape = RoundedCornerShape(13.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold,
                                    selectedLabelColor = Ink,
                                    containerColor = Panel2,
                                    labelColor = Ivory
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = current == target,
                                    borderColor = Color(0xFF41463D),
                                    selectedBorderColor = Gold
                                )
                            )
                        }
                        repeat(3 - rowTargets.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF34392F))
                Text(
                    "OR SET A CUSTOM TARGET",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp
                )
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom target") },
                    isError = custom.isNotBlank() && !customIsValid,
                    supportingText = {
                        Text(
                            if (custom.isNotBlank() && !customIsValid) "Enter a number from 1 to 9,999."
                            else "Enter a number from 1 to 9,999"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    colors = darkFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = customIsValid,
                onClick = { parsedCustom?.let(onSave) }
            ) { Text("SAVE CUSTOM", color = if (customIsValid) Gold else Muted) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Muted) }
        }
    )
}

@Composable
private fun SettingsDialog(
    state: TasbeehState,
    quranAyahKeys: List<String>,
    activeAudioUrls: List<String>,
    isSpeaking: Boolean,
    onPlayRecitation: () -> Unit,
    onStopSpeech: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Boolean, Boolean, Float) -> Unit
) {
    var sound by remember(state.soundEnabled) { mutableStateOf(state.soundEnabled) }
    var haptics by remember(state.hapticsEnabled) { mutableStateOf(state.hapticsEnabled) }
    var fontScale by remember(state.fontScale) { mutableFloatStateOf(state.fontScale) }
    val fontSizes = listOf("Standard" to 1.0f, "Comfort" to 1.12f, "Large" to 1.25f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Preferences", color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingToggle(
                    "Recorded recitation on count",
                    "Play Quran Foundation or OpenDua recordings when available",
                    sound
                ) { sound = it }
                SettingToggle("Haptic feedback", "A gentle tactile response", haptics) { haptics = it }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Recorded recitation", color = Ivory, fontSize = 14.sp)
                        Text(
                            when {
                                activeAudioUrls.isEmpty() -> "No recorded recitation is available for this entry"
                                quranAyahKeys.isNotEmpty() -> "Qur'anic recitation · internet required"
                                else -> "OpenDua recitation · internet required"
                            },
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    FilledTonalButton(
                        onClick = { if (isSpeaking) onStopSpeech() else onPlayRecitation() },
                        enabled = arabicPlaybackControlEnabled(
                            hasArabicText = activeAudioUrls.isNotEmpty(),
                            isSpeaking = isSpeaking
                        ),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Panel2,
                            contentColor = GoldLight,
                            disabledContainerColor = Panel2.copy(alpha = .55f),
                            disabledContentColor = Muted
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSpeaking) "STOP" else "PLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (quranAyahKeys.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "RECITER: " + QuranRecitationAudio.RECITER_NAME.uppercase() + " · " +
                            QuranRecitationAudio.RECITATION_STYLE.uppercase(),
                        color = Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .7.sp
                    )
                    Text(
                        "Complete cited ayahs are streamed in sequence from Quran Foundation.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        "Audio: Quran.Foundation · " + QuranRecitationAudio.RECITER_NAME,
                        color = Color(0xFF83B994),
                        fontSize = 10.sp
                    )
                } else if (activeAudioUrls.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text("RECORDED AUDIO · OPENDUA", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Audio is streamed from the recording linked to this OpenDua entry.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(Modifier.height(3.dp))
                Text(
                    "TEXT SIZE",
                    color = Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text("Adjust text throughout the entire app.", color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fontSizes.forEach { (label, scale) ->
                        FilterChip(
                            selected = kotlin.math.abs(fontScale - scale) < .01f,
                            onClick = { fontScale = scale },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                    fontSize = 11.sp, maxLines = 1)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold,
                                selectedLabelColor = Ink,
                                containerColor = Panel2,
                                labelColor = Ivory
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = kotlin.math.abs(fontScale - scale) < .01f,
                                borderColor = Color(0xFF41463D),
                                selectedBorderColor = Gold
                            )
                        )
                    }
                }
                Text(
                    when {
                        fontScale >= 1.2f -> "Large text selected"
                        fontScale >= 1.1f -> "Comfortable text selected"
                        else -> "Standard text selected"
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sound, haptics, fontScale) }) {
                Text("SAVE SETTINGS", color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Muted) }
        }
    )
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ivory, fontSize = 14.sp)
            Text(description, color = Muted, fontSize = 12.sp)
        }
        Switch(checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold))
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ivory, unfocusedTextColor = Ivory, focusedBorderColor = Gold,
    unfocusedBorderColor = Color(0xFF45483F), focusedLabelColor = Gold,
    unfocusedLabelColor = Muted, cursorColor = Gold
)
