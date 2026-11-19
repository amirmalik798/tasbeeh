package com.amirmalik.tasbeeh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmalik.tasbeeh.data.SavedSession
import com.amirmalik.tasbeeh.data.SessionEventType
import com.amirmalik.tasbeeh.data.SessionStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryInk = Color(0xFF090B0A)
private val HistoryPanel = Color(0xFF121714)
private val HistoryPanel2 = Color(0xFF19211C)
private val HistoryGold = Color(0xFFF0BF4E)
private val HistoryIvory = Color(0xFFFFF8EA)
private val HistoryMuted = Color(0xFF9A9B8D)
private val HistoryGreen = Color(0xFF74B48A)

@Composable
internal fun SessionHistoryScreen(
    sessions: List<SavedSession>,
    currentSessionId: String?,
    onBack: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessions) {
        while (sessions.any { it.status == SessionStatus.ACTIVE }) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val ordered = sessions.sortedByDescending { it.startedAt }
    Column(
        Modifier.fillMaxSize().background(HistoryInk).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(HistoryPanel2)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = HistoryGold)
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text("YOUR PRACTICE", color = HistoryGold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Session history", color = HistoryIvory, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (ordered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = HistoryGold, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("No sessions yet", color = HistoryIvory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your completed and in-progress sessions will appear here.", color = HistoryMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                ordered.forEach { session ->
                    SessionHistoryCard(
                        session = session,
                        isCurrent = session.id == currentSessionId,
                        now = now
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: SavedSession, isCurrent: Boolean, now: Long) {
    val active = session.status == SessionStatus.ACTIVE
    val tint = if (session.status == SessionStatus.COMPLETED) HistoryGreen else HistoryGold
    Surface(
        color = HistoryPanel,
        shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .34f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            isCurrent && active -> "CURRENT SESSION"
                            isCurrent && session.status == SessionStatus.PAUSED -> "PAUSED SESSION"
                            else -> "SESSION"
                        },
                        color = HistoryGold,
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(session.dhikr, color = HistoryIvory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(
                    when (session.status) {
                        SessionStatus.ACTIVE -> Icons.Default.PlayCircle
                        SessionStatus.PAUSED -> Icons.Default.PauseCircle
                        SessionStatus.COMPLETED -> Icons.Default.CheckCircle
                        SessionStatus.INCOMPLETE -> Icons.Default.Schedule
                    },
                    contentDescription = session.status.name,
                    tint = tint,
                    modifier = Modifier.size(27.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${session.count} / ${session.target}", color = HistoryIvory, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(formatSessionDuration(session.durationAt(now) ?: 0L), color = HistoryGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Active duration · excludes paused time",
                color = HistoryMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = Color(0xFF30382F))
            Spacer(Modifier.height(11.dp))
            Text("SESSION TIMELINE", color = HistoryGold, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            val timeline = session.events.ifEmpty {
                buildList {
                    if (session.startedAt > 0L) add(com.amirmalik.tasbeeh.data.SessionEvent(SessionEventType.STARTED, session.startedAt))
                    session.endedAt?.let { add(com.amirmalik.tasbeeh.data.SessionEvent(SessionEventType.ENDED, it)) }
                }
            }
            timeline.forEach { event ->
                val (label, icon) = when (event.type) {
                    SessionEventType.STARTED -> "Session started" to Icons.Default.PlayCircle
                    SessionEventType.PAUSED -> "Session paused" to Icons.Default.PauseCircle
                    SessionEventType.RESUMED -> "Session resumed" to Icons.Default.PlayCircle
                    SessionEventType.ENDED -> "Session ended" to Icons.Default.CheckCircle
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = HistoryGold, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(label, color = HistoryIvory, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(formatSessionDate(event.at), color = HistoryMuted, fontSize = 10.sp)
                }
            }
            if (active) {
                Text("Currently running", color = HistoryGreen, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

private fun formatSessionDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remainder = seconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${remainder}s"
        minutes > 0 -> "${minutes}m ${remainder}s"
        else -> "${remainder}s"
    }
}

private fun formatSessionDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Time unavailable"
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
}
