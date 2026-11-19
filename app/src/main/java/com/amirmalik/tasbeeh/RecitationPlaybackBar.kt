package com.amirmalik.tasbeeh

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RecitationPlaybackBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    isPreparing: Boolean,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = ""
) {
    if (title.isBlank()) return
    val gold = Color(0xFFFFD56A)
    Surface(
        modifier = modifier,
        color = Color(0xFF171E19),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF67532D).copy(alpha = .78f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        isPreparing -> "PREPARING RECITATION"
                        isPlaying -> "NOW PLAYING"
                        else -> "RECITATION PAUSED"
                    },
                    color = gold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .85.sp,
                    maxLines = 1
                )
                Text(title, color = Color(0xFFFFF8EA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOf(subtitle, artist).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(detail, color = Color(0xFFAAB2A5), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onTogglePlayback, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause recitation" else "Resume recitation",
                    tint = if (isPreparing) Color(0xFF778073) else gold
                )
            }
            IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop recitation", tint = Color(0xFFEAA38A))
            }
        }
    }
}
