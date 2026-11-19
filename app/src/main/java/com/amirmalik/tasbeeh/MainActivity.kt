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
                    TasbeehStrand(count = state.count, onCount = { increment() }, modifier = Modifier.size(diameter))
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
private fun TasbeehStrand(count: Int, onCount: () -> Unit, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(count.toFloat()) }
    val pressure = remember { Animatable(0f) }
    var previousCount by remember { mutableIntStateOf(count) }

    LaunchedEffect(count) {
        val changedBy = count - previousCount
        previousCount = count
        if (changedBy != 0) {
            // The whole strand glides by one bead pitch. A spring gives the beads
            // a little natural inertia instead of making one highlighted bead jump.
            launch {
                pressure.snapTo(0f)
                pressure.animateTo(1f, tween(105, easing = FastOutSlowInEasing))
                pressure.animateTo(0f, spring(dampingRatio = .46f, stiffness = 560f))
            }
            rotation.animateTo(
                count.toFloat(),
                spring(dampingRatio = .78f, stiffness = 520f)
            )
        }
    }

    Canvas(modifier.clickable(onClickLabel = "Count one dhikr", onClick = onCount)) {
        val minDimension = size.minDimension
        val cx = size.width * .5f
        val cy = size.height * .425f
        val radiusX = size.width * .355f
        val radiusY = size.height * .325f
        val beadCount = 33
        val beadRadius = minDimension * .037f
        val pitch = 2.0 * PI / beadCount
        val activeIndex = ((count % beadCount) + beadCount) % beadCount
        val press = pressure.value

        // A quiet pool of light gives the polished stones some separation from
        // the dark background without turning the strand into a glowing graphic.
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0xFF40533B).copy(alpha = .22f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radiusX * 1.5f
            ),
            radiusX * 1.5f,
            Offset(cx, cy)
        )

        // The dark braided cord is drawn first so it remains visible in the
        // narrow spaces between individual beads.
        val cordTopLeft = Offset(cx - radiusX, cy - radiusY)
        val cordSize = Size(radiusX * 2f, radiusY * 2f)
        drawOval(
            color = Color(0xFF020805).copy(alpha = .9f),
            topLeft = cordTopLeft,
            size = cordSize,
            style = Stroke(width = beadRadius * .19f)
        )
        drawOval(
            color = Color(0xFF66745A).copy(alpha = .48f),
            topLeft = cordTopLeft,
            size = cordSize,
            style = Stroke(width = beadRadius * .055f)
        )
        drawOval(
            color = Color(0xFFB08B50).copy(alpha = .16f),
            topLeft = Offset(cx - radiusX, cy - radiusY - beadRadius * .045f),
            size = cordSize,
            style = Stroke(width = beadRadius * .025f)
        )

        // Sort by depth so the front beads naturally overlap the cord and the
        // rear beads, just as a real loop of round beads would.
        val beadPositions = (0 until beadCount).map { index ->
            val angle = -PI / 2.0 + (index - rotation.value) * pitch
            val sine = sin(angle).toFloat()
            val cosine = cos(angle).toFloat()
            val depth = (sine + 1f) * .5f
            val isActive = index == activeIndex
            val localPressure = when {
                isActive -> press
                index == (activeIndex + 1) % beadCount -> press * .22f
                else -> 0f
            }
            val radius = beadRadius * (.90f + .17f * depth) * (1f + .045f * localPressure)
            val radialInset = beadRadius * .045f * localPressure
            val x = cx + cosine * (radiusX - radialInset)
            val y = cy + sine * (radiusY - radialInset)
            Triple(index, Offset(x, y), radius)
        }.sortedBy { it.second.y }

        beadPositions.forEach { (index, center, radius) ->
            val x = center.x
            val y = center.y
            val isActive = index == activeIndex
            val depth = ((y - (cy - radiusY)) / (radiusY * 2f)).coerceIn(0f, 1f)

            // Soft contact shadow and a deep jade/onyx body create the rounded,
            // polished-stone appearance of real misbaha beads.
            drawCircle(
                color = Color.Black.copy(alpha = .64f),
                radius = radius * 1.12f,
                center = Offset(x + radius * .12f, y + radius * .23f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF9CB99A).copy(alpha = if (isActive) .98f else .76f),
                        .15f to Color(0xFF527C5A),
                        .42f to Color(0xFF28563B),
                        .76f to Color(0xFF12321F),
                        1f to Color(0xFF06120B)
                    ),
                    center = Offset(x - radius * .34f, y - radius * .42f),
                    radius = radius * 1.62f
                ),
                radius = radius,
                center = center
            )

            // A thin natural edge and a broad, soft specular reflection make
            // the bead read as a spherical polished material, not a flat dot.
            drawCircle(
                color = Color(0xFF020A06).copy(alpha = .58f),
                radius = radius * .91f,
                center = center,
                style = Stroke(width = radius * .045f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isActive) .53f else .38f),
                        Color(0xFFB8D7B2).copy(alpha = .16f),
                        Color.Transparent
                    ),
                    center = Offset(x - radius * .35f, y - radius * .47f),
                    radius = radius * .78f
                ),
                topLeft = Offset(x - radius * .57f, y - radius * .67f),
                size = Size(radius * 1.05f, radius * .67f)
            )
            drawArc(
                color = Color.White.copy(alpha = if (isActive) .30f else .17f),
                startAngle = 202f,
                sweepAngle = 58f,
                useCenter = false,
                topLeft = Offset(x - radius * .60f, y - radius * .57f),
                size = Size(radius * 1.18f, radius * .86f),
                style = Stroke(width = radius * .045f)
            )
            drawCircle(
                color = Color.White.copy(alpha = if (isActive) .62f else .38f),
                radius = radius * .055f,
                center = Offset(x - radius * .35f, y - radius * .46f)
            )

            // The active bead stays stone-coloured; only its fine gold edge and
            // reflected light change, like light catching the bead being rolled.
            if (isActive) {
                drawCircle(
                    color = Gold.copy(alpha = .12f + .16f * press),
                    radius = radius * 1.30f,
                    center = center
                )
                drawCircle(
                    color = GoldLight.copy(alpha = .70f),
                    radius = radius * 1.015f,
                    center = center,
                    style = Stroke(width = beadRadius * .035f)
                )
            }

            // A tiny change in the rear/front sheen adds depth without changing
            // the stone's natural dark-green colour.
            if (depth < .18f) {
                drawCircle(
                    color = Color(0xFFB9C7A7).copy(alpha = .035f),
                    radius = radius * .72f,
                    center = Offset(x, y - radius * .05f)
                )
            }
        }

        // The lower connection, elongated imam bead and tassel are separate
        // physical elements rather than another round bead on the ring.
        val strandBottom = cy + radiusY
        val imamWidth = beadRadius * 1.02f
        val imamHeight = beadRadius * 2.18f
        val imamTop = strandBottom + beadRadius * .92f
        val imamBottom = imamTop + imamHeight
        val imamCenter = Offset(cx, (imamTop + imamBottom) * .5f)

        drawLine(
            color = Color(0xFF171D13),
            start = Offset(cx, strandBottom - beadRadius * .05f),
            end = Offset(cx, imamTop + beadRadius * .18f),
            strokeWidth = beadRadius * .14f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Gold.copy(alpha = .52f),
            start = Offset(cx - beadRadius * .035f, strandBottom + beadRadius * .32f),
            end = Offset(cx - beadRadius * .035f, imamTop + beadRadius * .12f),
            strokeWidth = beadRadius * .025f,
            cap = StrokeCap.Round
        )
        drawOval(
            color = Color.Black.copy(alpha = .62f),
            topLeft = Offset(cx - imamWidth * .58f, imamTop + beadRadius * .12f),
            size = Size(imamWidth * 1.16f, imamHeight * 1.04f)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF9AB58C),
                    Color(0xFF426B49),
                    Color(0xFF1C432C),
                    Color(0xFF07170D)
                ),
                center = Offset(imamCenter.x - imamWidth * .25f, imamCenter.y - imamHeight * .28f),
                radius = imamHeight * .92f
            ),
            topLeft = Offset(cx - imamWidth * .5f, imamTop),
            size = Size(imamWidth, imamHeight)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = .36f), Color.Transparent),
                center = Offset(cx - imamWidth * .24f, imamTop + imamHeight * .23f),
                radius = imamWidth * .56f
            ),
            topLeft = Offset(cx - imamWidth * .37f, imamTop + imamHeight * .10f),
            size = Size(imamWidth * .48f, imamHeight * .58f)
        )
        drawLine(
            color = Gold.copy(alpha = .62f),
            start = Offset(cx - imamWidth * .47f, imamTop + imamHeight * .10f),
            end = Offset(cx + imamWidth * .47f, imamTop + imamHeight * .10f),
            strokeWidth = beadRadius * .055f
        )
        drawLine(
            color = Gold.copy(alpha = .48f),
            start = Offset(cx - imamWidth * .34f, imamBottom - imamHeight * .09f),
            end = Offset(cx + imamWidth * .34f, imamBottom - imamHeight * .09f),
            strokeWidth = beadRadius * .045f
        )

        // Fine, slightly uneven silk strands replace the old rigid fan-shaped
        // tassel lines. Their gentle curve gives the tail a hanging quality.
        val tasselRoot = imamBottom - imamHeight * .035f
        for (strand in 0 until 15) {
            val spread = (strand - 7) / 7f
            val endX = cx + spread * beadRadius * 1.42f
            val endY = tasselRoot + beadRadius * (1.78f + .38f * (1f - kotlin.math.abs(spread)))
            val path = Path().apply {
                moveTo(cx + spread * beadRadius * .12f, tasselRoot)
                cubicTo(
                    cx + spread * beadRadius * .25f,
                    tasselRoot + beadRadius * .62f,
                    cx + spread * beadRadius * 1.05f,
                    endY - beadRadius * .42f,
                    endX,
                    endY
                )
            }
            drawPath(
                path = path,
                color = if (strand % 3 == 0) Color(0xFFD6B36E).copy(alpha = .82f)
                else Color(0xFF8D6D3D).copy(alpha = .76f),
                style = Stroke(width = beadRadius * .027f, cap = StrokeCap.Round)
            )
        }
        drawLine(
            color = Gold.copy(alpha = .62f),
            start = Offset(cx - beadRadius * .48f, tasselRoot + beadRadius * .12f),
            end = Offset(cx + beadRadius * .48f, tasselRoot + beadRadius * .12f),
            strokeWidth = beadRadius * .07f,
            cap = StrokeCap.Round
        )
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
