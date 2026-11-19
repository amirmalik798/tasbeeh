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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmalik.tasbeeh.data.CustomDhikr
import java.util.UUID

private val EditorInk = Color(0xFF090B0A)
private val EditorPanel = Color(0xFF121714)
private val EditorGold = Color(0xFFF0BF4E)
private val EditorIvory = Color(0xFFFFF8EA)
private val EditorMuted = Color(0xFF9A9B8D)

@Composable
internal fun CustomDhikrEditorScreen(
    existing: CustomDhikr?,
    existingTitles: List<String>,
    onBack: () -> Unit,
    onSave: (CustomDhikr) -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var arabic by remember(existing?.id) { mutableStateOf(existing?.arabic.orEmpty()) }
    var transliteration by remember(existing?.id) { mutableStateOf(existing?.transliteration.orEmpty()) }
    var meaning by remember(existing?.id) { mutableStateOf(existing?.meaning.orEmpty()) }
    var attemptedSave by remember(existing?.id) { mutableStateOf(false) }

    val cleanTitle = title.trim()
    val duplicateTitle = cleanTitle.isNotEmpty() &&
        existingTitles.any { it.trim().equals(cleanTitle, ignoreCase = true) }
    val titleError = attemptedSave && (cleanTitle.isEmpty() || duplicateTitle)
    val canSave = cleanTitle.isNotEmpty() && !duplicateTitle

    Column(
        Modifier.fillMaxSize().background(EditorInk).statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF171B17))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFFFD56A))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (existing == null) "PERSONAL COLLECTION" else "EDIT YOUR DHIKR",
                    color = Color(0xFFFFD56A),
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (existing == null) "Create a dhikr" else "Refine your dhikr",
                    color = EditorIvory,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Color(0xFF272012))
                    .border(1.dp, EditorGold.copy(alpha = .35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EditorGold, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = EditorPanel,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF514226).copy(alpha = .72f))
            ) {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(
                            listOf(Color(0xFF292111), Color(0xFF172019), Color(0xFF111713))
                        )
                    ).padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YOUR WORDS. YOUR REMEMBRANCE.", color = EditorGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text(
                            "Make space for the dhikr you return to.",
                            color = EditorIvory,
                            fontSize = 21.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Add its Arabic text, pronunciation and meaning. You can save a simple name on its own, too.",
                            color = Color(0xFFC4BCA9),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            EditorSectionLabel("IDENTITY", "01")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Dhikr name *") },
                placeholder = { Text("e.g. My morning remembrance") },
                supportingText = {
                    when {
                        titleError && cleanTitle.isEmpty() -> Text("Give your dhikr a name.", color = Color(0xFFFF9D80))
                        titleError && duplicateTitle -> Text("You already have a dhikr with this name.", color = Color(0xFFFF9D80))
                        else -> Text("${title.length}/60", color = EditorMuted)
                    }
                },
                isError = titleError,
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(22.dp))
            EditorSectionLabel("THE WORDS", "02")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = arabic,
                onValueChange = { arabic = it.take(800) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                label = { Text("Arabic text") },
                placeholder = { Text("اكتب الذكر هنا", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                textStyle = TextStyle(
                    color = EditorIvory,
                    fontSize = 23.sp,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.End
                ),
                supportingText = { Text("${arabic.length}/800", color = EditorMuted) },
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = transliteration,
                onValueChange = { transliteration = it.take(180) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                label = { Text("Pronunciation / transliteration") },
                placeholder = { Text("How it is pronounced") },
                supportingText = { Text("${transliteration.length}/180", color = EditorMuted) },
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(22.dp))
            EditorSectionLabel("MEANING & PREVIEW", "03")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text("Meaning or personal note") },
                placeholder = { Text("A translation or a note to help you remember") },
                supportingText = { Text("${meaning.length}/500", color = EditorMuted) },
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(14.dp))
            Surface(
                color = Color(0xFF171B16),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EditorGold.copy(alpha = .35f))
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LIVE PREVIEW", color = EditorGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EditorGold.copy(alpha = .8f), modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        cleanTitle.ifBlank { "Your dhikr name" },
                        color = EditorIvory,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (arabic.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            arabic,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFE7AD),
                            fontSize = 24.sp,
                            lineHeight = 38.sp,
                            textAlign = TextAlign.End
                        )
                    }
                    if (transliteration.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(transliteration, color = EditorGold, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (meaning.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(meaning, color = Color(0xFFBEB9AC), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (arabic.isBlank() && transliteration.isBlank() && meaning.isBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Your Arabic text, pronunciation and meaning will appear here.", color = EditorMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF83BD91), modifier = Modifier.size(16.dp))
                Text("Saved privately on this device", color = EditorMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(20.dp))
        }

        Surface(
            color = Color(0xFF101511),
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF302B20))
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        attemptedSave = true
                        if (canSave) {
                            onSave(
                                CustomDhikr(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    title = cleanTitle,
                                    arabic = arabic.trim(),
                                    transliteration = transliteration.trim(),
                                    meaning = meaning.trim(),
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EditorGold,
                        contentColor = Color(0xFF17130A),
                        disabledContainerColor = Color(0xFF4D4635),
                        disabledContentColor = Color(0xFFB3A98E)
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (existing == null) "SAVE DHIKR" else "SAVE CHANGES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCEL", color = EditorMuted, letterSpacing = 1.1.sp)
                }
            }
        }
    }
}

@Composable
private fun EditorSectionLabel(title: String, number: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = EditorGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Spacer(Modifier.weight(1f))
        Text(number, color = EditorMuted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EditorIvory,
    unfocusedTextColor = EditorIvory,
    focusedBorderColor = EditorGold,
    unfocusedBorderColor = Color(0xFF41463D),
    focusedLabelColor = EditorGold,
    unfocusedLabelColor = EditorMuted,
    cursorColor = EditorGold,
    focusedContainerColor = EditorPanel,
    unfocusedContainerColor = EditorPanel
)
