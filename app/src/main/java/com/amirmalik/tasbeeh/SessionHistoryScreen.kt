package com.amirmalik.tasbeeh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clock
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmalik.tasbeeh.data.SavedSession
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

    LaunchedEffect(session?.id, session?.status, session?.endedAt) {
        val isUnfinished = session?.status == SessionStatus.ACTIVE ||
            session?.status == SessionStatus.PAUSED
        if (isUnfinished) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        } else {
            now = System.currentTimeMillis()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(HistoryInk)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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
                Text(
                    "YOUR PRACTICE",
                    color = HistoryGold,
                    fontSize = 10.sp,
                    letterSpacing = 2.0.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Current session",
                    color = HistoryIvory,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (session != null) {
                SessionStatusPill(session.status)
            }
        }

        if (session == null) {
            EmptyCurrentSession()
        } else {
            val duration = session.durationAt(now)
            val progress = (session.count.toFloat() / session.target.coerceAtLeast(1)).coerceIn(0f, 1f)
            val isUnfinished = session.status == SessionStatus.ACTIVE || session.status == SessionStatus.PAUSED

            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = HistoryPanel,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (session.status == SessionStatus.COMPLETED) HistoryGreen.copy(alpha = .48f)
                        else HistoryGold.copy(alpha = .52f)
                    )
                ) {
                    Column(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(
                                listOf(Color(0xFF241F12), Color(0xFF172019), Color(0xFF111713))
                            )
                        ).padding(19.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isUnfinished) "SESSION IN PROGRESS" else "SESSION SUMMARY",
                                    color = HistoryGold,
                                    fontSize = 9.sp,
                                    letterSpacing = 1.6.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    session.dhikr,
                                    color = HistoryIvory,
                                    fontSize = 21.sp,
                                    lineHeight = 27.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                Modifier.size(43.dp).clip(CircleShape)
                                    .background(Color(0xFF302817))
                                    .border(1.dp, HistoryGold.copy(alpha = .25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (session.status) {
                                        SessionStatus.ACTIVE -> Icons.Default.PlayCircle
                                        SessionStatus.PAUSED -> Icons.Default.PauseCircle
                                        SessionStatus.COMPLETED -> Icons.Default.CheckCircle
                                        SessionStatus.INCOMPLETE -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = if (session.status == SessionStatus.COMPLETED) HistoryGreen else HistoryGold,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                session.count.toString(),
                                color = HistoryIvory,
                                fontSize = 44.sp,
                                lineHeight = 48.sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                " / ${session.target}",
                                color = HistoryMuted,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 5.dp, bottom = 6.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(progress * 100).toInt()}%",
                                color = HistoryGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 7.dp)
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier.fillMaxWidth().height(7.dp)
                                .clip(CircleShape).background(Color(0xFF30352C))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(progress).fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF99743A), HistoryGold)
                                        )
                                    )
                            )
                        }
                        Spacer(Modifier.height(11.dp))
                        Text(
                            when (session.status) {
                                SessionStatus.ACTIVE -> "Keep going. Your progress is saved automatically."
                                SessionStatus.PAUSED -> "This session is paused. Resume it from the main counter."
                                SessionStatus.COMPLETED -> "Your target was reached. MashaAllah."
                                SessionStatus.INCOMPLETE -> "This session ended before reaching its target."
                            },
                            color = HistoryMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    "SESSION TIMING",
                    color = HistoryGold,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                Surface(
                    color = HistoryPanel,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30382F))
                ) {
                    Column(Modifier.fillMaxWidth().padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimingIcon(Icons.Default.PlayCircle)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("STARTED", color = HistoryMuted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    formatSessionDate(session.startedAt),
                                    color = HistoryIvory,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Box(
                            Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
                                .width(1.dp).height(23.dp).background(Color(0xFF3A3D30))
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimingIcon(if (session.endedAt != null) Icons.Default.CheckCircle else Icons.Default.Clock)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ENDED", color = HistoryMuted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    session.endedAt?.let(::formatSessionDate) ?: "Not finished yet",
                                    color = if (session.endedAt != null) HistoryIvory else HistoryGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(Modifier.height(17.dp))
                        HorizontalDivider(color = Color(0xFF30382F))
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "TOTAL DURATION",
                                    color = HistoryMuted,
                                    fontSize = 9.sp,
                                    letterSpacing = 1.1.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    duration?.let(::formatSessionDuration) ?: "Not available",
                                    color = HistoryGold,
                                    fontSize = 29.sp,
                                    lineHeight = 34.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = HistoryGold.copy(alpha = .86f),
                                modifier = Modifier.size(27.dp)
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Measured from the session start time to its end time, including any pauses.",
                            color = HistoryMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun EmptyCurrentSession() {
    Box(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(25.dp))
                    .background(HistoryPanel2)
                    .border(1.dp, HistoryGold.copy(alpha = .28f), RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = HistoryGold, modifier = Modifier.size(33.dp))
            }
            Spacer(Modifier.height(19.dp))
            Text(
                "No current session",
                color = HistoryIvory,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Your next session will appear here automatically when you begin counting. We keep only the current session, not a long history.",
                color = HistoryMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TimingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(33.dp).clip(CircleShape).background(HistoryPanel2),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = HistoryGold, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SessionStatusPill(status: SessionStatus) {
    val (label, tint) = when (status) {
        SessionStatus.ACTIVE -> "ACTIVE" to HistoryGreen
        SessionStatus.PAUSED -> "PAUSED" to HistoryGold
        SessionStatus.COMPLETED -> "COMPLETED" to HistoryGreen
        SessionStatus.INCOMPLETE -> "INCOMPLETE" to HistoryMuted
    }
    Surface(
        color = tint.copy(alpha = .12f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .35f))
    ) {
        Text(
            label,
            color = tint,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .6.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
        )
    }
}

private fun formatSessionDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatSessionDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Start time unavailable for this earlier session"
    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(timestamp))
}
