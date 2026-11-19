package com.amirmalik.tasbeeh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.style.TextDirection
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
    isSaving: Boolean = false,
    onBack: () -> Unit,
    onSave: (CustomDhikr) -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var arabic by remember(existing?.id) { mutableStateOf(existing?.arabic.orEmpty()) }
    var transliteration by remember(existing?.id) { mutableStateOf(existing?.transliteration.orEmpty()) }
    var meaning by remember(existing?.id) { mutableStateOf(existing?.meaning.orEmpty()) }
    var attemptedSave by remember(existing?.id) { mutableStateOf(false) }
    var showPreview by remember(existing?.id) { mutableStateOf(false) }
    var showDiscardDialog by remember(existing?.id) { mutableStateOf(false) }

    val cleanTitle = title.trim()
    val duplicateTitle = cleanTitle.isNotEmpty() &&
        existingTitles.any { it.trim().equals(cleanTitle, ignoreCase = true) }
    val titleError = attemptedSave && (cleanTitle.isEmpty() || duplicateTitle)
    val canSave = cleanTitle.isNotEmpty() && !duplicateTitle
    val isDirty = title != existing?.title.orEmpty() ||
        arabic != existing?.arabic.orEmpty() ||
        transliteration != existing?.transliteration.orEmpty() ||
        meaning != existing?.meaning.orEmpty()

    fun requestLeave() {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    // A save is in flight in the parent store scope. Keep this editor mounted
    // until it succeeds or fails; otherwise Back could cancel the write.
    BackHandler(enabled = !showDiscardDialog && !isSaving) { requestLeave() }

    Column(Modifier.fillMaxSize().background(EditorInk).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { requestLeave() },
                enabled = !isSaving,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF171B17))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFFFD56A))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (existing == null) "PERSONAL COLLECTION" else "EDIT YOUR DHIKR",
                    color = EditorGold,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (existing == null) "Create a dhikr" else "Refine your dhikr",
                    color = EditorIvory,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            EditorSectionLabel("NAME", "01")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Dhikr name *") },
                placeholder = { Text("e.g. My morning remembrance") },
                supportingText = {
                    when {
                        titleError && cleanTitle.isEmpty() ->
                            Text("A name is required.", color = Color(0xFFFF9D80))
                        titleError && duplicateTitle ->
                            Text("This name is already used in your dhikr library.", color = Color(0xFFFF9D80))
                        else -> Text("${title.length}/60 · Required", color = EditorMuted)
                    }
                },
                isError = titleError,
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(18.dp))
            EditorSectionLabel("ARABIC TEXT", "02")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = arabic,
                onValueChange = { arabic = it.take(800) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                label = { Text("Arabic (optional)") },
                placeholder = {
                    Text(
                        "اكتب الذكر هنا",
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                textStyle = TextStyle(
                    color = EditorIvory,
                    fontSize = 22.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.End,
                    textDirection = TextDirection.Rtl
                ),
                supportingText = {
                    Text("${arabic.length}/800", color = EditorMuted)
                },
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(18.dp))
            EditorSectionLabel("TRANSLITERATION", "03")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = transliteration,
                onValueChange = { transliteration = it.take(800) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 9,
                label = { Text("Pronunciation / transliteration") },
                placeholder = { Text("Write how the dhikr is pronounced") },
                textStyle = TextStyle(
                    color = EditorIvory,
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                ),
                supportingText = {
                    Text(
                        "${transliteration.length}/800",
                        color = if (transliteration.length >= 760) EditorGold else EditorMuted
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(18.dp))
            EditorSectionLabel("MEANING OR PERSONAL NOTE", "04")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                label = { Text("Meaning (optional)") },
                placeholder = { Text("Add a translation or a note for yourself") },
                textStyle = TextStyle(
                    color = EditorIvory,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                ),
                supportingText = { Text("${meaning.length}/500", color = EditorMuted) },
                shape = RoundedCornerShape(16.dp),
                colors = editorFieldColors()
            )

            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF141A15),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF393D30))
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EditorGold,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Preview your dhikr",
                            color = EditorIvory,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showPreview = !showPreview }) {
                            Text(
                                if (showPreview) "HIDE" else "SHOW",
                                color = EditorGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = .8.sp
                            )
                            Icon(
                                if (showPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = EditorGold,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    if (showPreview) {
                        Box(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(Color(0xFF34392F))
                        )
                        Spacer(Modifier.height(13.dp))
                        Text(
                            cleanTitle.ifBlank { "Your dhikr name" },
                            color = EditorIvory,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (arabic.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                arabic,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFFE7AD),
                                fontSize = 23.sp,
                                lineHeight = 36.sp,
                                textAlign = TextAlign.End,
                                style = TextStyle(textDirection = TextDirection.Rtl)
                            )
                        }
                        if (transliteration.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                transliteration,
                                color = EditorGold,
                                fontSize = 12.sp,
                                lineHeight = 19.sp
                            )
                        }
                        if (meaning.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                meaning,
                                color = Color(0xFFBEB9AC),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                        if (arabic.isBlank() && transliteration.isBlank() && meaning.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your Arabic text, pronunciation and meaning will appear here.",
                                color = EditorMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Surface(
            color = Color(0xFF101511),
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFF302B20))
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
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
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EditorGold,
                        contentColor = Color(0xFF17130A)
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF17130A),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            isSaving -> "SAVING…"
                            existing == null -> "SAVE DHIKR"
                            else -> "SAVE CHANGES"
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = Color(0xFF171B17),
            title = { Text("Discard your changes?", color = EditorIvory) },
            text = {
                Text(
                    "Your unsaved edits will be lost. You can stay here and continue editing.",
                    color = Color(0xFFC4BCA9)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("DISCARD", color = Color(0xFFFF9B82))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("KEEP EDITING", color = EditorGold)
                }
            }
        )
    }
}

@Composable
private fun EditorSectionLabel(title: String, number: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = EditorGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.35.sp
        )
        Spacer(Modifier.weight(1f))
        Text(number, color = EditorMuted, fontSize = 11.sp, letterSpacing = 1.sp)
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
