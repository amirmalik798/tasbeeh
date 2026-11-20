package com.amirmalik.tasbeeh.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmalik.tasbeeh.data.CustomDhikr

private val VoiceInk = Color(0xFF090B0A)
private val VoicePanel = Color(0xFF121714)
private val VoiceGold = Color(0xFFF0BF4E)
private val VoiceIvory = Color(0xFFFFF8EA)
private val VoiceMuted = Color(0xFF9A9B8D)

@Composable
internal fun VoiceCountingScreen(
    dhikr: CustomDhikr,
    count: Int,
    target: Int,
    state: VoiceServiceState,
    message: String,
    transcript: String,
    canPause: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val isActive = state == VoiceServiceState.PREPARING ||
        state == VoiceServiceState.LISTENING ||
        state == VoiceServiceState.PROCESSING ||
        state == VoiceServiceState.PAUSING
    val isBusy = state == VoiceServiceState.PREPARING ||
        state == VoiceServiceState.PROCESSING ||
        state == VoiceServiceState.PAUSING
    val progress = (count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize()
            .background(VoiceInk)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VoiceGold)
                Text("BACK", color = VoiceGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("VOICE COUNT", color = VoiceGold, letterSpacing = 1.5.sp, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text(dhikr.title, color = VoiceIvory, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        if (dhikr.arabic.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                dhikr.arabic,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFE7AD),
                fontSize = 25.sp,
                lineHeight = 40.sp,
                textAlign = TextAlign.End
            )
        } else if (dhikr.transliteration.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(dhikr.transliteration, color = VoiceGold, fontSize = 14.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = VoicePanel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF30372F))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 25.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(78.dp).clip(CircleShape)
                        .background(if (isActive) Color(0xFF243528) else Color(0xFF242820)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp), color = VoiceGold, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isActive) Icons.Default.Mic else if (state == VoiceServiceState.TARGET_REACHED) Icons.Default.CheckCircle else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (isActive || state == VoiceServiceState.TARGET_REACHED) VoiceGold else VoiceMuted,
                            modifier = Modifier.size(31.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    when (state) {
                        VoiceServiceState.PREPARING -> "Preparing recognition"
                        VoiceServiceState.LISTENING -> "Listening on this device"
                        VoiceServiceState.PROCESSING -> "Checking the phrase"
                        VoiceServiceState.PAUSING -> "Pausing microphone"
                        VoiceServiceState.PAUSED -> "Listening paused"
                        VoiceServiceState.TARGET_REACHED -> "Target reached"
                        VoiceServiceState.ERROR -> "Voice count unavailable"
                        else -> "Voice counting stopped"
                    },
                    color = VoiceIvory,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    message,
                    color = VoiceMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
                if (transcript.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF0B100C),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF394236))
                    ) {
                        Text(
                            transcript,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = VoiceGold,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("COUNT", color = VoiceMuted, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        Text("$count / $target", color = VoiceIvory, fontSize = 31.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFF293128))) {
            Box(
                Modifier.fillMaxWidth(progress).height(8.dp)
                    .clip(CircleShape).background(VoiceGold)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text("${(target - count).coerceAtLeast(0)} remaining", color = VoiceMuted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        if (isActive) {
            Button(
                onClick = if (canPause) onPause else onStop,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF733E33), contentColor = VoiceIvory)
            ) {
                Icon(if (canPause) Icons.Default.Pause else Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (canPause) "PAUSE LISTENING" else "STOP LISTENING", fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            }
        } else if (state != VoiceServiceState.TARGET_REACHED) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                enabled = canStart,
                colors = ButtonDefaults.buttonColors(containerColor = VoiceGold, contentColor = VoiceInk)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        !canStart -> "COUNTER PAUSED"
                        state == VoiceServiceState.PAUSED -> "RESUME LISTENING"
                        else -> "START VOICE COUNT"
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp
                )
            }
        }
        if (state == VoiceServiceState.ERROR && message.contains("microphone permission", ignoreCase = true)) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN MICROPHONE SETTINGS", color = VoiceGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("RETURN TO TAP COUNTER", color = VoiceGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(13.dp))
        Text(
            "Phrase matching runs locally. Recognition is not speaker identity verification. You can always use the tap counter.",
            modifier = Modifier.fillMaxWidth(),
            color = VoiceMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Notifications are optional. Even when they are disabled, the app still speaks “TARGET REACHED”.",
            modifier = Modifier.fillMaxWidth(),
            color = VoiceMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}
