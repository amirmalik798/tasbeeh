package com.amirmalik.tasbeeh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Flag
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
    sessions: List<SavedSession>,
    openSession: SavedSession?,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var confirmFinish by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val totalSessions = sessions.size + (if (openSession != null) 1 else 0)
    val completed = remember(sessions) { sessions.filter { it.status == SessionStatus.COMPLETED } }
    val trackedCompleted = remember(completed) { completed.filter { it.activeDurationMs != null } }
    val totalCount = remember(sessions, openSession) {
        sessions.sumOf { it.count.toLong() } + (openSession?.count ?: 0).toLong()
    }
    val totalActiveMs = trackedCompleted.let { tracked ->
        val archivedDuration = sessions
            .filter { it.activeDurationMs != null }
            .sumOf { it.activeDurationMs ?: 0L }
        archivedDuration + (openSession?.activeDurationAt(now) ?: 0L)
    }
    val hasTrackedTime = sessions.any { it.activeDurationMs != null } || openSession != null
    val filtered = remember(sessions, selectedFilter) {
        sessions.filter {
            when (selectedFilter) {
                "Completed" -> it.status == SessionStatus.COMPLETED
                "Incomplete" -> it.status == SessionStatus.INCOMPLETE
                else -> true
            }
        }.sortedByDescending { it.endedAt ?: it.completedAt ?: it.startedAt }
    }

    LaunchedEffect(openSession?.id, openSession?.status, openSession?.lastActivityAt) {
        if (openSession != null && openSession.status == SessionStatus.ACTIVE) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(HistoryInk).statusBarsPadding().navigationBarsPadding()
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
                Text("YOUR JOURNEY", color = HistoryGold, fontSize = 10.sp, letterSpacing = 2.1.sp, fontWeight = FontWeight.Bold)
                Text("Session history", color = HistoryIvory, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(
                color = Color(0xFF252014),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, HistoryGold.copy(alpha = .35f))
            ) {
                Text(
                    "$totalSessions SESSIONS",
                    color = Color(0xFFFFD875),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Text("YOUR PRACTICE", color = HistoryMuted, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HistoryStatCard(
                    modifier = Modifier.weight(1f),
                    value = completed.size.toString(),
                    label = "COMPLETED",
                    icon = { Icon(Icons.Default.CheckCircle, null, tint = HistoryGreen, modifier = Modifier.size(15.dp)) }
                )
                HistoryStatCard(
                    modifier = Modifier.weight(1f),
                    value = totalCount.toString(),
                    label = "TOTAL DHIKR",
                    icon = { Icon(Icons.Default.Timeline, null, tint = HistoryGold, modifier = Modifier.size(15.dp)) }
                )
                HistoryStatCard(
                    modifier = Modifier.weight(1f),
                    value = if (!hasTrackedTime) "—" else formatSessionDuration(totalActiveMs),
                    label = "ACTIVE TIME",
                    icon = { Icon(Icons.Default.Timer, null, tint = HistoryGold, modifier = Modifier.size(15.dp)) }
                )
            }

            if (openSession != null) {
                Spacer(Modifier.height(19.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).background(HistoryGold, CircleShape))
                    Text("CURRENT SESSION", color = HistoryGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OpenSessionCard(
                    session = openSession,
                    now = now,
                    onResume = onResume,
                    onFinish = { confirmFinish = true }
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAST SESSIONS", color = HistoryIvory, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("A record of each completed or unfinished round", color = HistoryMuted, fontSize = 11.sp)
                }
                Text("${filtered.size}", color = HistoryGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(11.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("All", "Completed", "Incomplete").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HistoryGold,
                            selectedLabelColor = Color(0xFF17130A),
                            containerColor = HistoryPanel2,
                            labelColor = Color(0xFFC9C2B3)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = Color(0xFF343A31),
                            selectedBorderColor = HistoryGold
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(58.dp).clip(CircleShape).background(HistoryPanel2),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timeline, null, tint = HistoryGold, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.height(13.dp))
                        Text(
                            if (selectedFilter == "All") "Your journey starts here" else "No ${selectedFilter.lowercase()} sessions yet",
                            color = HistoryIvory,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Your saved dhikr sessions will appear here.",
                            color = HistoryMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(filtered, key = { it.id }) { session ->
                        PastSessionCard(session)
                    }
                }
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = HistoryPanel,
            title = { Text("Finish this session?", color = HistoryIvory) },
            text = {
                Text(
                    "Your current progress will be saved as an incomplete session. You can still review it in your history.",
                    color = HistoryMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    onFinish()
                }) {
                    Text("FINISH SESSION", color = Color(0xFFFFA18A))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) {
                    Text("KEEP SESSION", color = HistoryGold)
                }
            }
        )
    }
}

@Composable
private fun HistoryStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = HistoryPanel,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30382F))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            icon()
            Spacer(Modifier.height(9.dp))
            Text(value, color = HistoryIvory, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(label, color = HistoryMuted, fontSize = 9.sp, letterSpacing = .7.sp, maxLines = 1)
        }
    }
}

@Composable
private fun OpenSessionCard(
    session: SavedSession,
    now: Long,
    onResume: () -> Unit,
    onFinish: () -> Unit
) {
    val paused = session.status == SessionStatus.PAUSED
    val activeMs = session.activeDurationAt(now)
    val progress = (session.count.toFloat() / session.target.coerceAtLeast(1)).coerceIn(0f, 1f)
    Surface(
        color = HistoryPanel,
        shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HistoryGold.copy(alpha = .62f)),
        shadowElevation = 4.dp
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF211D12), Color(0xFF151B15), Color(0xFF121714)))
            ).padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.dhikr, color = HistoryIvory, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Started ${formatSessionDate(session.startedAt)}",
                        color = HistoryMuted,
                        fontSize = 11.sp
                    )
                }
                StatusPill(if (paused) "PAUSED" else "IN PROGRESS", paused)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(session.count.toString(), color = HistoryIvory, fontSize = 31.sp, fontWeight = FontWeight.Light)
                Text("/ ${session.target}", color = HistoryMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
                Spacer(Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", color = HistoryGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF303329))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF99743A), HistoryGold))))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SessionMetric("ACTIVE TIME", formatSessionDuration(activeMs))
                SessionMetric("ELAPSED", formatSessionDuration(session.totalElapsedAt(now)))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HistoryGold, contentColor = Color(0xFF17130A))
                ) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.CheckCircle, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (paused) "RESUME" else "RETURN TO COUNTER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HistoryMuted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF46483C))
                ) {
                    Text("FINISH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PastSessionCard(session: SavedSession) {
    val completed = session.status == SessionStatus.COMPLETED
    Surface(
        color = HistoryPanel,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2F362E))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.dhikr, color = HistoryIvory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatSessionDate(session.endedAt ?: session.completedAt ?: session.startedAt),
                        color = HistoryMuted,
                        fontSize = 11.sp
                    )
                }
                StatusPill(if (completed) "COMPLETED" else "INCOMPLETE", !completed)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${session.count}", color = HistoryIvory, fontSize = 23.sp, fontWeight = FontWeight.Light)
                Text(" / ${session.target}", color = HistoryMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Flag, null, tint = if (completed) HistoryGreen else HistoryMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (completed) "Goal reached" else "Ended early", color = if (completed) HistoryGreen else HistoryMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionMetric(
                    "ACTIVE TIME",
                    session.activeDurationMs?.let(::formatSessionDuration) ?: "Not tracked"
                )
                SessionMetric(
                    "TOTAL ELAPSED",
                    if (session.startedAt > 0L) {
                        formatSessionDuration(session.totalElapsedAt(session.endedAt ?: session.completedAt ?: session.startedAt))
                    } else "Not tracked"
                )
            }
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: String) {
    Column {
        Text(label, color = HistoryMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = HistoryGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(label: String, pausedOrIncomplete: Boolean) {
    val color = if (pausedOrIncomplete) HistoryGold else HistoryGreen
    Surface(
        color = color.copy(alpha = .12f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .35f))
    ) {
        Text(
            label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .55.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

private fun formatSessionDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatSessionDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Previously saved"
    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(timestamp))
}
