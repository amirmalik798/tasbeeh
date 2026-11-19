package com.amirmalik.tasbeeh

import android.os.Bundle
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amirmalik.tasbeeh.data.SavedSession
import com.amirmalik.tasbeeh.data.TasbeehState
import com.amirmalik.tasbeeh.data.TasbeehStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Ink = Color(0xFF090B0A)
private val Panel = Color(0xFF121714)
private val Panel2 = Color(0xFF19211C)
private val Gold = Color(0xFFD8B66B)
private val GoldLight = Color(0xFFF4DFA4)
private val Ivory = Color(0xFFF4F0E6)
private val Muted = Color(0xFF9A9B8D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 10)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 10)
        val store = TasbeehStore(applicationContext)
        setContent {
            val saved by store.state.collectAsState(initial = TasbeehState())
            TasbeehApp(saved, store) {
                if (saved.hapticsEnabled) window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}

@Composable
private fun TasbeehApp(state: TasbeehState, store: TasbeehStore, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showTarget by remember { mutableStateOf(false) }
    var showDhikr by remember { mutableStateOf(false) }
    var motionKey by remember { mutableIntStateOf(0) }
    var completionRecordedFor by remember { mutableIntStateOf(-1) }

    BackHandler(enabled = showHistory || showSettings) {
        showHistory = false
        showSettings = false
    }

    fun increment() {
        val next = state.count + 1
        val now = System.currentTimeMillis()
        scope.launch {
            store.update(count = next)
            if (next >= state.target && state.count < state.target && completionRecordedFor != state.target) {
                completionRecordedFor = state.target
                store.completeSession(SavedSession(now, state.dhikr, next, state.target, now))
            }
        }
        motionKey++
        onTap()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Ivory, secondary = Color(0xFF516D52))) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF24271B).copy(alpha = .42f), Color.Transparent), Offset(size.width * .5f, size.height * .30f), size.width * .8f),
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
                Header(onHistory = { showHistory = true }, onSettings = { showSettings = true })
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(Gold, CircleShape))
                    Text("A MOMENT OF REMEMBRANCE", color = Muted, fontSize = 10.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(10.dp))
                Text(state.dhikr, color = Ivory, fontSize = 25.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Text("سُبْحَانَ اللّٰهِ", color = GoldLight.copy(alpha = .85f), fontSize = 19.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))

                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                    val diameter = min(maxWidth, 352.dp)
                    TasbeehStrand(count = state.count, motionKey = motionKey, modifier = Modifier.size(diameter))
                }

                Spacer(Modifier.height(5.dp))
                Text("YOUR REMEMBRANCE", color = Muted, fontSize = 10.sp, letterSpacing = 2.4.sp)
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = state.count,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(tween(130))) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut(tween(130)))
                    },
                    label = "counter"
                ) { count ->
                    Text(count.toString(), color = Ivory, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                }
                Text("${(state.target - state.count.coerceAtMost(state.target)).coerceAtLeast(0)} remaining", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                ProgressTrack(state.count, state.target)
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallAction(Icons.Default.Undo, "UNDO", state.count > 0, Modifier.weight(1f)) {
                        scope.launch { store.update(count = (state.count - 1).coerceAtLeast(0)) }
                        motionKey++
                    }
                    Button(
                        onClick = { increment() },
                        modifier = Modifier.weight(1.7f).height(62.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("COUNT", fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, fontSize = 14.sp)
                    }
                    SmallAction(Icons.Default.Refresh, "RESET", true, Modifier.weight(1f)) { showReset = true }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickChip("Target · ${state.target}", Modifier.weight(1f)) { showTarget = true }
                    QuickChip("Change dhikr", Modifier.weight(1f)) { showDhikr = true }
                }
                Spacer(Modifier.height(14.dp))
            }
            AnimatedVisibility(
                visible = state.count >= state.target,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 82.dp)
            ) {
                Surface(color = Color(0xFF243528), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .45f))) {
                    Text("✦  Target reached — MashaAllah  ✦", color = GoldLight, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
        }

        if (showReset) AlertDialog(
            onDismissRequest = { showReset = false }, containerColor = Panel,
            title = { Text("Reset this session?", color = Ivory) },
            text = { Text("Your current count will return to zero. Completed sessions remain in history.", color = Muted) },
            confirmButton = { TextButton(onClick = { scope.launch { store.update(count = 0) }; completionRecordedFor = -1; showReset = false }) { Text("RESET COUNT", color = Gold) } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("CANCEL", color = Muted) } }
        )
        if (showTarget) TargetDialog(state.target, { showTarget = false }) { target -> scope.launch { store.update(target = target) }; showTarget = false }
        if (showDhikr) DhikrDialog(state.dhikr, { showDhikr = false }) { dhikr -> scope.launch { store.update(dhikr = dhikr) }; showDhikr = false }
        if (showSettings) SettingsDialog(state, { showSettings = false }) { sound, haptics -> scope.launch { store.update(soundEnabled = sound, hapticsEnabled = haptics) } }
        if (showHistory) HistoryDialog(state.sessions, { showHistory = false })
    }
}

@Composable
private fun Header(onHistory: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF252B20), Color(0xFF111510))))
                    .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { KaabaMark(Modifier.size(23.dp)) }
            Column {
                Text("TASBEEH", color = Ivory, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.1.sp)
                Text("THE ART OF DHIKR", color = Muted, fontSize = 8.sp, letterSpacing = 1.6.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onHistory, modifier = Modifier.size(42.dp).background(Panel, CircleShape)) {
                Icon(Icons.Default.History, "Session history", tint = Gold, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(42.dp).background(Panel, CircleShape)) {
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

@Composable
private fun TasbeehStrand(count: Int, motionKey: Int, modifier: Modifier = Modifier) {
    val travel = remember { Animatable(0f) }
    val settle = remember { Animatable(0f) }
    LaunchedEffect(motionKey) {
        if (motionKey > 0) {
            travel.snapTo(0f)
            travel.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            settle.snapTo(1f)
            settle.animateTo(0f, spring(dampingRatio = .38f, stiffness = 340f))
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * .372f
        val beadCount = 33
        val baseR = size.minDimension * .039f
        val currentSlot = count % beadCount
        val stepAngle = 2.0 * PI / beadCount
        val progress = travel.value
        val bob = settle.value

        drawCircle(
            Brush.radialGradient(listOf(Gold.copy(alpha = .105f), Color.Transparent), Offset(cx, cy), radius * 1.55f),
            radius * 1.55f, Offset(cx, cy)
        )
        drawCircle(Color(0xFF8D7140).copy(alpha = .42f), radius, style = Stroke(size.minDimension * .008f))
        drawCircle(Color(0xFF050706), radius, style = Stroke(size.minDimension * .003f))

        for (i in 0 until beadCount) {
            val baseAngle = -PI / 2.0 + i * stepAngle
            val isActive = i == currentSlot
            val previousActive = i == ((currentSlot - 1 + beadCount) % beadCount)
            // New active bead travels around the loop. The previously active bead
            // yields into the next slot so the motion reads as a physical bead sliding
            // along a continuous cord rather than a stationary highlight.
            val angularOffset = when {
                isActive -> -stepAngle * (1f - progress)
                previousActive -> stepAngle * progress
                else -> 0.0
            }
            val angle = baseAngle + angularOffset
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            val r = baseR * (if (isActive) 1f + .12f * bob else 1f)
            val color = if (isActive) GoldLight else if (i < currentSlot) Color(0xFFC39B55) else Color(0xFF76613B)

            drawCircle(Color.Black.copy(alpha = .80f), r * 1.15f, Offset(x + r * .16f, y + r * .27f))
            drawCircle(
                Brush.radialGradient(
                    colors = if (isActive) listOf(Color(0xFFFFF7D8), GoldLight, Color(0xFFAE803B), Color(0xFF553A1B))
                    else listOf(Color(0xFFF0D28A), color, Color(0xFF604622), Color(0xFF302516)),
                    center = Offset(x - r * .34f, y - r * .40f),
                    radius = r * 1.72f
                ),
                r, Offset(x, y)
            )
            drawCircle(Color.White.copy(alpha = if (isActive) .76f else .34f), r * .16f, Offset(x - r * .38f, y - r * .44f))
            drawCircle(Color(0xFF352313).copy(alpha = .48f), r * .88f, Offset(x, y), style = Stroke(r * .05f))
            if (isActive) drawCircle(GoldLight.copy(alpha = .22f * (1f - progress)), r * 1.55f, Offset(x, y))
        }

        val ix = cx
        val iy = cy + radius
        val imamR = baseR * 1.48f
        drawCircle(Color.Black.copy(alpha = .8f), imamR * 1.14f, Offset(ix + imamR * .12f, iy + imamR * .27f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE9A8), Gold, Color(0xFF795020), Color(0xFF332313)), Offset(ix - imamR * .35f, iy - imamR * .4f), imamR * 1.7f),
            imamR, Offset(ix, iy)
        )
        drawCircle(Color.White.copy(alpha = .52f), imamR * .16f, Offset(ix - imamR * .38f, iy - imamR * .45f))
        drawLine(Gold.copy(alpha = .7f), Offset(ix, iy + imamR * 1.08f), Offset(ix, iy + imamR * 2.0f), size.minDimension * .014f)
        for (j in -2..2) {
            drawLine(Gold.copy(alpha = .7f), Offset(ix, iy + imamR * 1.8f), Offset(ix + j * imamR * .22f, iy + imamR * 2.65f), size.minDimension * .007f)
        }
    }
}

@Composable
private fun ProgressTrack(count: Int, target: Int) {
    val fraction = (count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(420), label = "progress")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SESSION PROGRESS", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Text("${(animated * 100).toInt()}%", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF2A2D25))) {
            Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF92713C), GoldLight))))
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(62.dp).clip(RoundedCornerShape(19.dp)).background(Panel)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Gold else Muted.copy(alpha = .35f), modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (enabled) Muted else Muted.copy(alpha = .35f), fontSize = 8.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun QuickChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.height(42.dp).clickable(onClick = onClick), color = Panel,
        shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3028))) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = Ivory, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun TargetDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var custom by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Choose your target", color = Ivory) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(33, 99, 100).forEach { target ->
                        FilterChip(selected = current == target, onClick = { onSave(target) }, label = { Text(target.toString()) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Ink, labelColor = Ivory))
                    }
                }
                OutlinedTextField(value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text("Custom target (1–9999)") }, singleLine = true, colors = darkFieldColors())
            }
        },
        confirmButton = { TextButton(onClick = { custom.toIntOrNull()?.takeIf { it in 1..9999 }?.let(onSave) }) { Text("SAVE", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Muted) } }
    )
}

@Composable
private fun DhikrDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf("SubhanAllah", "Alhamdulillah", "Allahu Akbar", "Astaghfirullah", "La ilaha illallah", "Allahumma salli 'ala Muhammad")
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Choose dhikr", color = Ivory) },
        text = {
            Column {
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(option) }.padding(vertical = 11.dp, horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(option, Modifier.weight(1f), color = if (option == current) Gold else Ivory, fontSize = 14.sp)
                        if (option == current) Icon(Icons.Default.Check, null, tint = Gold, modifier = Modifier.size(17.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = Muted) } }
    )
}

@Composable
private fun SettingsDialog(state: TasbeehState, onDismiss: () -> Unit, onSave: (Boolean, Boolean) -> Unit) {
    var sound by remember(state.soundEnabled) { mutableStateOf(state.soundEnabled) }
    var haptics by remember(state.hapticsEnabled) { mutableStateOf(state.hapticsEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Preferences", color = Ivory) },
        text = {
            Column {
                SettingToggle("Counting sound", "A soft sound on each bead", sound) { sound = it }
                SettingToggle("Haptic feedback", "A gentle tactile response", haptics) { haptics = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(sound, haptics); onDismiss() }) { Text("DONE", color = Gold) } }
    )
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ivory, fontSize = 14.sp)
            Text(description, color = Muted, fontSize = 11.sp)
        }
        Switch(checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold))
    }
}

@Composable
private fun HistoryDialog(sessions: List<SavedSession>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.92f).fillMaxHeight(.78f), color = Panel,
            shape = RoundedCornerShape(26.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383326))) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your sessions", color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("A record of your remembrance", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Muted) }
                }
                Spacer(Modifier.height(12.dp))
                if (sessions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Completed sessions will appear here.", color = Muted, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions, key = { it.id }) { session ->
                            Surface(color = Panel2, shape = RoundedCornerShape(15.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(session.dhikr, color = Ivory, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(session.completedAt)), color = Muted, fontSize = 10.sp)
                                    }
                                    Text("${session.count}/${session.target}", color = Gold, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ivory, unfocusedTextColor = Ivory, focusedBorderColor = Gold,
    unfocusedBorderColor = Color(0xFF45483F), focusedLabelColor = Gold,
    unfocusedLabelColor = Muted, cursorColor = Gold
)
