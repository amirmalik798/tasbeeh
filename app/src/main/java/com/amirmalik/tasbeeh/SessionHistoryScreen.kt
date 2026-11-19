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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmalik.tasbeeh.data.SavedSession
import com.amirmalik.tasbeeh.data.SessionEvent
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
    session: SavedSession?,
    onBack: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.id, session?.status) {
        now = System.currentTimeMillis()
        if (session?.status == SessionStatus.ACTIVE) {
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HistoryInk).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF171B17))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFFFD56A))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("YOUR PRACTICE", color = HistoryGold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (session == null || session.status == SessionStatus.ACTIVE || session.status == SessionStatus.PAUSED) {
                        "Current session"
                    } else {
                        "Latest session"
                    },
                    color = HistoryIvory,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (session == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = HistoryGold, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No current session", color = HistoryIvory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your current session and its pause/resume timeline will appear here.", color = HistoryMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SessionHistoryCard(
                    session = session,
                    now = now
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: SavedSession, now: Long) {
    val active = session.status == SessionStatus.ACTIVE
    val duration = session.durationAt(now)
    val tint = if (session.status == SessionStatus.COMPLETED) HistoryGreen else HistoryGold
    Surface(
        color = HistoryPanel,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .32f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session.dhikr,
                            color = HistoryIvory,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    val startedAtLabel = remember(session.startedAt) { formatSessionDate(session.startedAt) }
                    Text(startedAtLabel, color = HistoryMuted, fontSize = 11.sp)
                }
                Surface(color = tint.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
                    Text(session.status.name, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${session.count} / ${session.target}", color = HistoryIvory, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(duration?.let(::formatSessionDuration) ?: "Duration unavailable", color = HistoryGold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF30382F))
            Spacer(Modifier.height(10.dp))
            Text("SESSION TIMELINE", color = HistoryGold, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val events = remember(session.events, session.status, session.startedAt, session.endedAt) {
                val base = if (session.events.isNotEmpty()) session.events else buildList {
                    if (session.startedAt > 0L) add(SessionEvent(SessionEventType.STARTED, session.startedAt))
                    session.endedAt?.let { add(SessionEvent(SessionEventType.ENDED, it)) }
                }
                if (session.startedAt > 0L && base.none { it.type == SessionEventType.STARTED }) {
                    listOf(SessionEvent(SessionEventType.STARTED, session.startedAt)) + base
                } else base
            }
            val displayEvents = remember(events) {
                events.map { event -> event to formatSessionDate(event.at) }
            }
            displayEvents.forEach { (event, eventTimestampLabel) ->
                val (label, icon) = when (event.type) {
                    SessionEventType.STARTED -> "Session started" to Icons.Default.PlayCircle
                    SessionEventType.PAUSED -> "Session paused" to Icons.Default.PauseCircle
                    SessionEventType.RESUMED -> "Session resumed" to Icons.Default.PlayCircle
                    SessionEventType.ENDED -> "Session ended" to Icons.Default.CheckCircle
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(icon, contentDescription = null, tint = HistoryGold, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(label, color = HistoryIvory, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(eventTimestampLabel, color = HistoryMuted, fontSize = 10.sp, textAlign = TextAlign.End)
                }
            }
            if (active || session.status == SessionStatus.PAUSED) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = HistoryMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (active) "Session is still running" else "Waiting to resume",
                        color = HistoryMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Active duration excludes all time between each pause and resume.",
                color = HistoryMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

private fun formatSessionDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000L
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${remainder}s"
        minutes > 0 -> "${minutes}m ${remainder}s"
        else -> "${remainder}s"
    }
}

private fun formatSessionDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Time unavailable"
    return SimpleDateFormat("MMM d, yyyy · h:mm:ss a", Locale.getDefault()).format(Date(timestamp))
}
