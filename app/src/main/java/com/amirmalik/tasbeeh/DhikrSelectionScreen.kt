package com.amirmalik.tasbeeh

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmalik.tasbeeh.data.CustomDhikr
import java.text.Normalizer
import java.util.Locale

internal fun normalizeDhikrSearchText(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return buildString(decomposed.length) {
        decomposed.forEach { character ->
            val isCombiningMark = character in '\u0300'..'\u036F' ||
                character in '\u1AB0'..'\u1AFF' ||
                character in '\u1DC0'..'\u1DFF' ||
                character in '\u20D0'..'\u20FF' ||
                character in '\uFE20'..'\uFE2F' ||
                character in '\u064B'..'\u065F' ||
                character == '\u0670' ||
                character in '\u06D6'..'\u06ED'
            if (!isCombiningMark && character != '\u0640') append(character)
        }
    }.replace(Regex("\\s+"), " ").trim()
}

internal data class DhikrItem(
    val id: String,
    val value: String,
    val title: String,
    val category: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val reference: String = "",
    val customId: String? = null,
    val quranAyahKeys: List<String> = emptyList(),
    val recitationAudioUrls: List<String> = emptyList(),
    val recitationCredit: String = ""
)

@Composable
internal fun DhikrSelectionScreen(
    catalogItems: List<DhikrItem>,
    currentDhikr: String,
    currentCustomDhikrId: String?,
    customDhikrs: List<CustomDhikr>,
    onBack: () -> Unit,
    onSelect: (DhikrItem) -> Unit,
    onCreateCustom: () -> Unit,
    onEditCustom: (CustomDhikr) -> Unit,
    onDeleteCustom: (CustomDhikr, onDeleted: () -> Unit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var pendingDelete by remember { mutableStateOf<CustomDhikr?>(null) }
    val categories = remember(catalogItems) {
        listOf("All", "My Dhikr") + catalogItems.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val customItems = remember(customDhikrs) {
        customDhikrs.map { custom ->
            DhikrItem(
                id = "custom-${custom.id}",
                value = custom.title,
                title = custom.title,
                category = "My Dhikr",
                arabic = custom.arabic,
                transliteration = custom.transliteration,
                meaning = custom.meaning,
                customId = custom.id
            )
        }
    }
    val allItems = remember(customItems, catalogItems) { catalogItems + customItems }
    val filteredItems = remember(query, category, allItems) {
        val normalizedQuery = normalizeDhikrSearchText(query)
        val matches = allItems.filter { item ->
            val matchesCategory = category == "All" || item.category == category
            val matchesSearch = normalizedQuery.isBlank() ||
                listOf(item.title, item.transliteration, item.meaning, item.arabic, item.reference)
                    .any { value -> normalizeDhikrSearchText(value).contains(normalizedQuery) }
            matchesCategory && matchesSearch
        }
        if (category == "Sleep") {
            val sleepPriority = mapOf(
                "sleep-four-qul" to 0,
                "sleep-ayat-al-kursi" to 1,
                "sleep-last-two-baqarah" to 2
            )
            matches.sortedBy { sleepPriority[it.id] ?: 3 }
        } else {
            matches
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF090B0A))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.Start
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
                Text("DHIKR LIBRARY", color = Color(0xFFFFD56A), fontSize = 12.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF252014))
                    .border(1.dp, Color(0xFFD6A63D).copy(alpha = .38f), RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text("${allItems.size} DHIKRS", color = Color(0xFFFFD875), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            color = Color(0xFF131914),
            shape = RoundedCornerShape(21.dp),
            border = BorderStroke(1.dp, Color(0xFF3C463B))
        ) {
            Row(
                Modifier.fillMaxWidth().height(66.dp).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFFFFD56A),
                    modifier = Modifier.padding(start = 11.dp).size(22.dp)
                )
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)
                        .semantics {
                            contentDescription = "Search dhikr by name, Arabic, pronunciation or meaning"
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    textStyle = TextStyle(
                        color = Color(0xFFFFF8EA),
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFFFFD56A)),
                    decorationBox = { innerTextField ->
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search dhikr",
                                    color = Color(0xFF8F958A),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { query = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFFB8AD97),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(5.dp))
                IconButton(
                    onClick = onCreateCustom,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFE39A), Color(0xFFF0BF4E), Color(0xFFD99D32))
                            )
                        )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add your own dhikr",
                        tint = Color(0xFF17130A),
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item, fontSize = 12.sp) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF0BF4E),
                            selectedLabelColor = Color(0xFF17130A),
                            containerColor = Color(0xFF171B17),
                            labelColor = Color(0xFFCAC3B3)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = category == item,
                            borderColor = Color(0xFF383D34),
                            selectedBorderColor = Color(0xFFFFD875)
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        if (filteredItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No dhikr found", color = Color(0xFFFFF8EA), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Try another name or clear your filters.", color = Color(0xFFB0AA9C), fontSize = 12.sp)
                    Spacer(Modifier.height(9.dp))
                    TextButton(onClick = {
                        query = ""
                        category = "All"
                        keyboardController?.hide()
                    }) {
                        Text("CLEAR SEARCH & FILTERS", color = Color(0xFFFFD56A), fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 3.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isSelected = if (item.customId != null) {
                        item.customId == currentCustomDhikrId
                    } else {
                        currentCustomDhikrId == null && item.value == currentDhikr
                    }
                    val custom = item.customId?.let { id -> customDhikrs.firstOrNull { it.id == id } }
                    DhikrOptionCard(
                        item = item,
                        selected = isSelected,
                        onClick = { onSelect(item) },
                        onEdit = custom?.let { entry -> { onEditCustom(entry) } },
                        onDelete = custom?.let { entry -> { pendingDelete = entry } }
                    )
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Color(0xFF171B17),
            title = { Text("Delete this dhikr?", color = Color(0xFFFFF8EA)) },
            text = {
                Text(
                    "${entry.title} will be removed from your personal collection. Your current session record will remain available in Session History.",
                    color = Color(0xFFC4BCA9)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCustom(entry) { pendingDelete = null }
                }) { Text("DELETE", color = Color(0xFFFF9B82)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("KEEP", color = Color(0xFFFFD56A)) }
            }
        )
    }
}

@Composable
private fun DhikrOptionCard(
    item: DhikrItem,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val gold = Color(0xFFFFD56A)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        color = if (selected) Color(0xFF252014) else Color(0xFF121714),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(if (selected) 1.4.dp else 1.dp, if (selected) Color(0xFFE7B84D).copy(alpha = .92f) else Color(0xFF30372F)),
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = Color(0xFFFFF8EA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0xFF4A3919) else Color(0xFF222820))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(item.category.uppercase(), color = if (selected) Color(0xFFFFD875) else Color(0xFF9FAD96), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                }
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = gold, modifier = Modifier.size(17.dp))
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit custom dhikr", tint = Color(0xFFB8B49F), modifier = Modifier.size(15.dp))
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete custom dhikr", tint = Color(0xFFC98C78), modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (item.arabic.isNotBlank()) {
                Spacer(Modifier.height(11.dp))
                Text(
                    item.arabic,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFE7AD),
                    fontSize = if (item.arabic.length > 90) 18.sp else 23.sp,
                    lineHeight = if (item.arabic.length > 90) 32.sp else 38.sp,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Medium
                )
            }
            if (item.transliteration.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(item.transliteration, color = gold, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            }
            if (item.meaning.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.meaning, color = Color(0xFFBEB9AC), fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (item.reference.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(item.reference, color = Color(0xFF7F8C78), fontSize = 10.sp, letterSpacing = .25.sp)
            }
            if (item.quranAyahKeys.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "QUR'AN RECITATION · ABDULBASET ABDULSAMAD",
                    color = Color(0xFF83B994),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .55.sp
                )
                Text(
                    "Audio streamed from Quran.Foundation",
                    color = Color(0xFF7F8C78),
                    fontSize = 9.sp
                )
            }
            else if (item.recitationAudioUrls.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "RECORDED RECITATION · OPENDUA",
                    color = Color(0xFF83B994),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .55.sp
                )
                Text(
                    item.recitationCredit.ifBlank { "Audio streamed from the recording linked to this entry" },
                    color = Color(0xFF7F8C78),
                    fontSize = 9.sp
                )
            }
        }
    }
}
