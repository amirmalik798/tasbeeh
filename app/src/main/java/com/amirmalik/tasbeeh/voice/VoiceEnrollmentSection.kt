package com.amirmalik.tasbeeh.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val EnrollmentPanel = Color(0xFF141A15)
private val EnrollmentGold = Color(0xFFFFD56A)
private val EnrollmentIvory = Color(0xFFFFF8EA)
private val EnrollmentMuted = Color(0xFF9A9B8D)

/**
 * Optional voice enrollment. The captured waveform exists only in memory and is
 * zeroed immediately after transcription. The reviewed transcript is kept in editor
 * memory only until save, then written to the app's no-backup voice-profile store.
 */
@Composable
internal fun VoiceEnrollmentSection(
    dhikrId: String,
    arabic: String,
    transliteration: String,
    savedProfileId: String,
    savedSourceHash: String,
    savedAt: Long,
    isProfileStaged: Boolean,
    onProfileChanged: (referenceText: String, profileId: String, sourceHash: String, createdAt: Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSourceHash = remember(arabic, transliteration) {
        voiceConfigurationHash(arabic, transliteration)
    }
    val hasProfileMetadata = savedProfileId.isNotBlank()
    val hasConfiguredProfile = hasProfileMetadata &&
        savedSourceHash.isNotBlank() && savedSourceHash == currentSourceHash && savedAt > 0L
    val hasStaleProfile = hasProfileMetadata && !hasConfiguredProfile

    var isWorking by remember(dhikrId) { mutableStateOf(false) }
    var showPermissionSettings by remember(dhikrId) { mutableStateOf(false) }
    var isRecording by remember(dhikrId) { mutableStateOf(false) }
    var statusText by remember(dhikrId) { mutableStateOf("") }
    var pendingTranscript by remember(dhikrId) { mutableStateOf("") }
    var pendingHash by remember(dhikrId) { mutableStateOf("") }
    var activeStopSignal by remember(dhikrId) { mutableStateOf<AtomicBoolean?>(null) }
    var enrollmentJob by remember(dhikrId) { mutableStateOf<Job?>(null) }

    fun beginEnrollment() {
        if (isWorking || isRecording) return
        enrollmentJob?.cancel()
        enrollmentJob = scope.launch {
            isWorking = true
            pendingTranscript = ""
            pendingHash = ""
            try {
                statusText = "Preparing the offline Arabic recognition model…"
                val modelFiles = VoiceModelRepository.ensureInstalled(context)
                statusText = "Ready. Speak one complete repetition at a natural pace, then press Stop."
                val stopSignal = AtomicBoolean(false)
                activeStopSignal = stopSignal
                isRecording = true
                val samples = try {
                    VoiceAudioRecorder.captureEnrollmentSample(context, stopSignal)
                } finally {
                    isRecording = false
                    activeStopSignal = null
                }
                statusText = "Checking your sample locally…"
                val transcript = try {
                    withContext(Dispatchers.Default) {
                        WhisperArabicRecognizer.create(modelFiles).use { recognizer ->
                            recognizer.transcribe(samples)
                        }
                    }
                } finally {
                    samples.fill(0f)
                }
                if (transcript.isBlank()) {
                    throw IllegalStateException("The sample could not be understood. Please try again in a quiet place.")
                }

                if (arabic.isNotBlank()) {
                    if (!isDhikrPhraseMatch(arabic, transcript)) {
                        throw IllegalStateException(
                            "The spoken sample did not match the Arabic Dhikr text. Check the text or try recording again."
                        )
                    }
                    onProfileChanged(
                        transcript,
                        UUID.randomUUID().toString(),
                        currentSourceHash,
                        System.currentTimeMillis()
                    )
                    statusText = "Voice phrase prepared. It will be kept privately on this device when you save the Dhikr."
                } else {
                    pendingTranscript = transcript
                    pendingHash = currentSourceHash
                    statusText = "Review the recognized Arabic phrase below. Confirm it is the Dhikr you intended."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: LinkageError) {
                statusText = "Offline speech recognition could not be loaded on this device. You can still save and count this Dhikr using taps."
            } catch (error: Exception) {
                statusText = error.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.take(220)
                    ?: "Voice enrollment could not be completed. Please try again."
            } finally {
                activeStopSignal?.set(true)
                activeStopSignal = null
                isRecording = false
                isWorking = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginEnrollment()
        } else {
            statusText = "Microphone access was not granted. You can still save and use this Dhikr with tap counting."
            showPermissionSettings = true
        }
    }

    fun requestEnrollment() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginEnrollment() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(dhikrId) {
        onDispose {
            activeStopSignal?.set(true)
            enrollmentJob?.cancel()
        }
    }

    LaunchedEffect(currentSourceHash, savedProfileId, savedSourceHash, savedAt, isProfileStaged, dhikrId) {
        when {
            hasStaleProfile -> {
                statusText = "The Dhikr text changed after this sample was recorded. Record a new sample before voice counting."
            }
            hasConfiguredProfile && isProfileStaged -> {
                statusText = "Voice sample ready. Save this Dhikr to store the phrase privately on this device."
            }
            hasConfiguredProfile -> {
                val exists = withContext(Dispatchers.IO) {
                    VoiceProfileRepository.load(context, savedProfileId, dhikrId, currentSourceHash) != null
                }
                statusText = if (exists) {
                    "Voice phrase is set up on this device."
                } else {
                    "The private voice sample is not present on this device. Record it again to enable voice counting."
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EnrollmentPanel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF3B4033))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "VOICE COUNTING",
                color = EnrollmentGold,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Record one sample of this Dhikr. Recognition runs on this device; the temporary recording is not saved.",
                color = EnrollmentMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            when {
                hasConfiguredProfile -> Text(
                    "VOICE PHRASE READY",
                    color = Color(0xFF83B994),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                hasStaleProfile -> Text(
                    "VOICE SAMPLE NEEDS UPDATING",
                    color = Color(0xFFE7C17C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                else -> Text(
                    "NOT SET UP",
                    color = EnrollmentMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (savedAt > 0L && hasConfiguredProfile && !isProfileStaged) {
                Text(
                    "Sample saved locally · ${java.text.DateFormat.getDateInstance().format(java.util.Date(savedAt))}",
                    color = EnrollmentMuted,
                    fontSize = 10.sp
                )
            }
            if (pendingTranscript.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0B100C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF514328))
                ) {
                    Text(
                        pendingTranscript,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = EnrollmentIvory,
                        fontSize = 18.sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.End
                    )
                }
                Button(
                    onClick = {
                        onProfileChanged(
                            pendingTranscript,
                            UUID.randomUUID().toString(),
                            pendingHash,
                            System.currentTimeMillis()
                        )
                        pendingTranscript = ""
                        pendingHash = ""
                        statusText = "Voice phrase prepared. It will be kept privately on this device when you save the Dhikr."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EnrollmentGold, contentColor = Color(0xFF17130A))
                ) {
                    Text("USE THIS VOICE SAMPLE", fontWeight = FontWeight.Bold)
                }
            }
            if (statusText.isNotBlank()) {
                Text(statusText, color = EnrollmentMuted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            if (showPermissionSettings) {
                TextButton(onClick = { openVoiceAppSettings(context) }) {
                    Text("OPEN MICROPHONE PERMISSION SETTINGS", color = EnrollmentGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRecording) {
                    Button(
                        onClick = { activeStopSignal?.set(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF733E33), contentColor = EnrollmentIvory)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Stop, contentDescription = null)
                        Text("STOP RECORDING", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = { requestEnrollment() },
                        modifier = Modifier.weight(1f),
                        enabled = !isWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = EnrollmentGold, contentColor = Color(0xFF17130A))
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                color = Color(0xFF17130A),
                                strokeWidth = 2.dp
                            )
                            Text("PREPARING…", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            androidx.compose.material3.Icon(Icons.Default.Mic, contentDescription = null)
                            Text(
                                if (hasConfiguredProfile || hasStaleProfile) "RECORD AGAIN" else "RECORD SAMPLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (savedProfileId.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onProfileChanged("", "", "", 0L)
                            pendingTranscript = ""
                            pendingHash = ""
                            statusText = "Voice sample removed. Tap counting remains available."
                        },
                        enabled = !isWorking && !isRecording
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove voice sample",
                            tint = Color(0xFFD49B86)
                        )
                    }
                }
            }
            if (isWorking && !isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(15.dp), color = EnrollmentGold, strokeWidth = 2.dp)
                    Text("Installing or checking the on-device model…", color = EnrollmentMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
