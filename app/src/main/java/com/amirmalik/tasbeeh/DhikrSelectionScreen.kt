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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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

private val dhikrWhitespaceRegex = Regex("\\s+")

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
    }.replace(dhikrWhitespaceRegex, " ").trim()
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
    val recitationCredit: String = "",
    val categories: List<String> = listOf(category).filter { it.isNotBlank() },
    val sourceEntryId: String = id,
    val contentType: String = "invocation",
    val isCountable: Boolean = contentType == "invocation"
)

internal fun isCounterSelectableDhikr(item: DhikrItem): Boolean = item.customId != null

internal fun isQuranCategorySurah(item: DhikrItem): Boolean =
    QuranicDuaCatalog.isFullSurah(item)

internal const val DEFAULT_DHIKR_LIBRARY_CATEGORY = "Favorites"

internal fun orderedDhikrCategories(openDuaCategories: List<String>): List<String> {
    val pinned = listOf("Favorites", "My Dhikhrs", "Quran")
    val topics = openDuaCategories.map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { topic ->
            topic.equals("All", ignoreCase = true) || pinned.any { it.equals(topic, ignoreCase = true) }
        }
    return (pinned + topics).distinct()
}

@Composable
internal fun DhikrSelectionScreen(
    catalogItems: List<DhikrItem>,
    openDuaCategories: List<String>,
    openDuaIsRefreshing: Boolean,
    openDuaRefreshError: String?,
    openDuaCacheWriteFailed: Boolean,
    isStoreBusy: Boolean,
    hasInternetConnection: Boolean,
    quranIsRefreshing: Boolean,
    quranRefreshError: String?,
    quranCacheWriteFailed: Boolean,
    selectedQuranReciter: QuranReciter,
    activeRecitationTitle: String,
    activeRecitationArtist: String,
    activeRecitationSubtitle: String,
    onPlayQuranRecitation: (String, List<String>) -> Unit,
    isSpeaking: Boolean,
    isPreparingRecitation: Boolean,
    isResolvingQuranRecitation: Boolean,
    onPlayRecitation: (String, String, List<String>) -> Unit,
    onToggleRecitationPlayback: () -> Unit,
    onStopRecitation: () -> Unit,
    favoriteItemIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onRetryQuranText: () -> Unit,
    onRetryOpenDuaCatalog: () -> Unit,
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
    var category by remember { mutableStateOf(DEFAULT_DHIKR_LIBRARY_CATEGORY) }
    var pendingDelete by remember { mutableStateOf<CustomDhikr?>(null) }
    var readingSurah by remember { mutableStateOf<DhikrItem?>(null) }
    val categories = remember(openDuaCategories) {
        orderedDhikrCategories(openDuaCategories)
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val customItems = remember(customDhikrs) {
        customDhikrs.map { custom ->
            DhikrItem(
                id = "custom-${custom.id}",
                value = custom.title,
                title = custom.title,
                category = "My Dhikhrs",
                arabic = custom.arabic,
                transliteration = custom.transliteration,
                meaning = custom.meaning,
                customId = custom.id,
                categories = listOf("My Dhikhrs"),
                contentType = "custom",
                isCountable = true
            )
        }
    }
    val allItems = remember(customItems, catalogItems) { catalogItems + customItems }
    // Normalize searchable fields once per catalogue revision, not on every
    // keystroke. This keeps search responsive while preserving field boundaries.
    val searchableItems = remember(allItems) {
        allItems.map { item ->
            val fields = if (QuranicDuaCatalog.isFullSurah(item)) {
                // Do not normalize megabytes of Quran text on the Compose thread.
                listOf(item.title, item.reference, item.transliteration.take(120))
            } else {
                listOf(item.title, item.transliteration, item.meaning, item.arabic, item.reference)
            }
            item to (fields + item.categories).map(::normalizeDhikrSearchText)
        }
    }
    val filteredItems = remember(query, category, searchableItems, favoriteItemIds) {
        val normalizedQuery = normalizeDhikrSearchText(query)
        searchableItems.asSequence().filter { (item, searchableFields) ->
            val matchesCategory = when (category) {
                "" -> true
                "Favorites" -> item.id in favoriteItemIds
                "My Dhikhrs" -> item.customId != null
                "Quran" -> isQuranCategorySurah(item)
                else -> category in item.categories || item.category == category
            }
            val matchesSearch = normalizedQuery.isBlank() ||
                searchableFields.any { it.contains(normalizedQuery) }
            matchesCategory && matchesSearch
        }.map { it.first }.toList()
    }
    val customDhikrsById = remember(customDhikrs) { customDhikrs.associateBy { it.id } }
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
                Text("${allItems.size} ITEMS", color = Color(0xFFFFD875), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
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
                    enabled = !isStoreBusy,
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

        RecitationPlaybackBar(
            title = activeRecitationTitle,
            artist = activeRecitationArtist,
            subtitle = activeRecitationSubtitle,
            isPlaying = isSpeaking,
            isPreparing = isPreparingRecitation || isResolvingQuranRecitation,
            onTogglePlayback = onToggleRecitationPlayback,
            onStop = onStopRecitation,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
        )

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

        if ((category.isBlank() || category in openDuaCategories) &&
            (openDuaIsRefreshing || openDuaRefreshError != null || openDuaCacheWriteFailed || !hasInternetConnection)
        ) {
            CatalogStatusPanel(
                catalogName = "OpenDua",
                isRefreshing = openDuaIsRefreshing,
                hasCachedData = catalogItems.any { it.id.startsWith("opendua-") },
                errorMessage = openDuaRefreshError,
                cacheWriteFailed = openDuaCacheWriteFailed,
                isOffline = !hasInternetConnection,
                itemCount = catalogItems.count { it.id.startsWith("opendua-") },
                onRetry = onRetryOpenDuaCatalog
            )
        }
        val quranItems = catalogItems.filter(::isQuranCategorySurah)
        val quranTextIsCached = quranItems.any {
            it.arabic.isNotBlank() && it.transliteration.isNotBlank()
        }
        if ((category.isBlank() || category == "Quran") &&
            (quranIsRefreshing || quranRefreshError != null || quranCacheWriteFailed)
        ) {
            CatalogStatusPanel(
                catalogName = "Qur'anic text",
                isRefreshing = quranIsRefreshing,
                hasCachedData = quranTextIsCached,
                errorMessage = quranRefreshError,
                cacheWriteFailed = quranCacheWriteFailed,
                isOffline = !hasInternetConnection,
                itemCount = quranItems.count {
                    QuranicDuaCatalog.isFullSurah(it) && it.arabic.isNotBlank() && it.transliteration.isNotBlank()
                },
                onRetry = onRetryQuranText
            )
        }
        Spacer(Modifier.height(3.dp))
        if (filteredItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (category == "Favorites") "No favorites yet" else "No dhikr found",
                        color = Color(0xFFFFF8EA),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (category == "Favorites") {
                            "Tap the star on any Quran or dua entry to save it here."
                        } else {
                            "Try another name or clear your filters."
                        },
                        color = Color(0xFFB0AA9C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(9.dp))
                    TextButton(onClick = {
                        query = ""
                        category = ""
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
                    val isSelected = item.customId != null &&
                        item.customId == currentCustomDhikrId
                    val custom = item.customId?.let(customDhikrsById::get)
                    val isQuran = item.quranAyahKeys.isNotEmpty()
                    val isFullSurah = QuranicDuaCatalog.isFullSurah(item)
                    val showSurahSummary = category == "Quran" && isFullSurah
                    val hasRecording = isQuran || item.recitationAudioUrls.isNotEmpty()
                    val openReader = item.takeIf(QuranicDuaCatalog::isFullSurah)?.let { { readingSurah = it } }
                    DhikrOptionCard(
                        item = item,
                        selected = isSelected,
                        enabled = !isStoreBusy,
                        selectable = isCounterSelectableDhikr(item),
                        favorite = item.id in favoriteItemIds,
                        onToggleFavorite = { onToggleFavorite(item.id) },
                        selectedQuranReciter = selectedQuranReciter,
                        showListenButton = true,
                        listenEnabled = hasRecording && hasInternetConnection &&
                            !isPreparingRecitation && !isResolvingQuranRecitation,
                        showQuranSurahSummary = showSurahSummary,
                        onClick = {
                            if (showSurahSummary) openReader?.invoke()
                            else if (isCounterSelectableDhikr(item)) onSelect(item)
                        },
                        onListen = if (hasRecording) {
                            {
                                if (isQuran) onPlayQuranRecitation(item.title, item.quranAyahKeys)
                                else onPlayRecitation(
                                    item.title,
                                    item.recitationCredit.ifBlank { "OpenDua recitation" },
                                    item.recitationAudioUrls
                                )
                            }
                        } else null,
                        onRead = openReader,
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
                TextButton(
                    enabled = !isStoreBusy,
                    onClick = { onDeleteCustom(entry) { pendingDelete = null } }
                ) { Text(if (isStoreBusy) "DELETING…" else "DELETE", color = Color(0xFFFF9B82)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("KEEP", color = Color(0xFFFFD56A)) }
            }
        )
    }
    readingSurah?.let { surah ->
        QuranSurahReaderDialog(
            item = surah,
            isSpeaking = isSpeaking,
            isPreparingRecitation = isPreparingRecitation,
            isResolvingQuranRecitation = isResolvingQuranRecitation,
            activeRecitationTitle = activeRecitationTitle,
            selectedQuranReciter = selectedQuranReciter,
            hasInternetConnection = hasInternetConnection,
            onPlayQuranRecitation = onPlayQuranRecitation,
            onToggleRecitationPlayback = onToggleRecitationPlayback,
            onStopRecitation = onStopRecitation,
            onDismiss = { readingSurah = null }
        )
    }
}

@Composable
private fun CatalogStatusPanel(
    catalogName: String,
    isRefreshing: Boolean,
    hasCachedData: Boolean,
    errorMessage: String?,
    cacheWriteFailed: Boolean,
    isOffline: Boolean,
    itemCount: Int,
    onRetry: () -> Unit
) {
    val panelColor = Color(0xFF151A15)
    val accent = Color(0xFFFFD56A)
    val title: String
    val message: String
    val showRetry: Boolean
    when {
        isOffline && hasCachedData -> {
            title = "Offline · saved $catalogName available"
            message = "Your saved copy is ready on this device. Connect to the internet to check for updates."
            showRetry = false
        }
        isOffline -> {
            title = "No internet connection"
            message = if (catalogName == "OpenDua") {
                "The dua catalogue has not been downloaded on this device yet. Connect to the internet to download it."
            } else {
                "The 114 surahs remain listed, but their verified text has not been downloaded on this device yet. Connect to the internet to download the Qur'an."
            }
            // Network callbacks automatically start the refresh when connectivity
            // returns; offering Retry while offline would only repeat the same no-op.
            showRetry = false
        }
        cacheWriteFailed -> {
            title = "$catalogName loaded, offline save incomplete"
            message = "The content is available now, but Android could not confirm that the offline copy was saved. Check available device storage and retry."
            showRetry = true
        }
        errorMessage != null && hasCachedData -> {
            title = "Using your saved $catalogName"
            message = "The latest update could not be downloaded. Your saved content is still available.\n$errorMessage"
            showRetry = true
        }
        errorMessage != null -> {
            title = "$catalogName unavailable"
            message = "The content is not available yet. Check your connection and retry.\n$errorMessage"
            showRetry = true
        }
        isRefreshing && hasCachedData -> {
            title = "Saved $catalogName ready"
            message = "Checking for updates in the background."
            showRetry = false
        }
        isRefreshing -> {
            title = "Preparing $catalogName"
            message = "Downloading and verifying the content for first use."
            showRetry = false
        }
        else -> {
            title = "$catalogName ready"
            message = "$itemCount entries are available on this device."
            showRetry = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        color = panelColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF3A4438))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing && errorMessage == null && !cacheWriteFailed) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isOffline || errorMessage != null) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = if (isOffline || errorMessage != null || cacheWriteFailed) Color(0xFFE7B66A) else Color(0xFF83B994),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    color = Color(0xFFFFF8EA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                color = Color(0xFFBEB9AC),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            if (showRetry) {
                Spacer(Modifier.height(7.dp))
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("RETRY", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DhikrOptionCard(
    item: DhikrItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    selectable: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    selectedQuranReciter: QuranReciter,
    showListenButton: Boolean,
    listenEnabled: Boolean,
    showQuranSurahSummary: Boolean = false,
    onListen: (() -> Unit)? = null,
    onRead: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val gold = Color(0xFFFFD56A)
    val isFullSurah = QuranicDuaCatalog.isFullSurah(item)
    val isOpenDuaItem = item.id.startsWith("opendua-")
    val displayReference = if (isOpenDuaItem) {
        item.reference.removePrefix("OpenDua v${OpenDuaCatalogParser.DATA_VERSION} · ")
    } else {
        item.reference
    }
    val arabicPreview = if (isFullSurah && item.arabic.length > 190) {
        item.arabic.take(190).trimEnd() + " …"
    } else item.arabic
    val transliterationPreview = if (isFullSurah && item.transliteration.length > 150) {
        item.transliteration.take(150).trimEnd() + " …"
    } else item.transliteration
    val meaningPreview = if (isFullSurah && item.meaning.length > 170) {
        item.meaning.take(170).trimEnd() + " …"
    } else item.meaning
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(
            enabled = enabled && (selectable || showQuranSurahSummary),
            role = if (selectable || showQuranSurahSummary) Role.Button else null,
            onClick = onClick
        ),
        color = if (selected) Color(0xFF252014) else Color(0xFF121714),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(if (selected) 1.4.dp else 1.dp, if (selected) Color(0xFFE7B84D).copy(alpha = .92f) else Color(0xFF30372F)),
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            if (showQuranSurahSummary) {
                val surahNumber = item.quranAyahKeys.firstOrNull()
                    ?.substringBefore(':')?.toIntOrNull() ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(39.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF292317))
                            .border(1.dp, Color(0xFF8C6B31), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            surahNumber.toString(),
                            color = gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.title,
                        color = Color(0xFFFFF8EA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${item.quranAyahKeys.size} ayahs",
                        color = Color(0xFFB9C0B2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        enabled = enabled,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (favorite) "Remove ${item.title} from favorites" else "Add ${item.title} to favorites",
                            tint = if (favorite) gold else Color(0xFF8E968A),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (showListenButton) {
                        IconButton(
                            onClick = { onListen?.invoke() },
                            enabled = listenEnabled && onListen != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Listen to ${item.title}",
                                tint = if (listenEnabled && onListen != null) gold else Color(0xFF667064),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            } else {
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
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = enabled,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (favorite) "Remove ${item.title} from favorites" else "Add ${item.title} to favorites",
                        tint = if (favorite) gold else Color(0xFF8E968A),
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (showListenButton) {
                    IconButton(
                        onClick = { onListen?.invoke() },
                        enabled = listenEnabled && onListen != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Listen to ${item.title}",
                            tint = if (listenEnabled && onListen != null) gold else Color(0xFF667064),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0xFF4A3919) else Color(0xFF222820))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        item.category.uppercase(),
                        color = if (selected) Color(0xFFFFD875) else Color(0xFF9FAD96),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = gold, modifier = Modifier.size(17.dp))
                }
                if (!selectable) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(Color(0xFF3A2B1A))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("REFERENCE", color = Color(0xFFE7C17C), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onEdit != null) {
                    IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit custom dhikr", tint = Color(0xFFB8B49F), modifier = Modifier.size(15.dp))
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete custom dhikr", tint = Color(0xFFC98C78), modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (item.arabic.isNotBlank()) {
                Spacer(Modifier.height(11.dp))
                Text(
                    arabicPreview,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFE7AD),
                    fontSize = if (arabicPreview.length > 90) 18.sp else 23.sp,
                    lineHeight = if (arabicPreview.length > 90) 32.sp else 38.sp,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (isFullSurah) 3 else Int.MAX_VALUE,
                    overflow = if (isFullSurah) TextOverflow.Ellipsis else TextOverflow.Clip
                )
            } else if (item.quranAyahKeys.isNotEmpty()) {
                Spacer(Modifier.height(11.dp))
                Text(
                    "Arabic text is unavailable offline. Connect to the internet and retry to load the verified Qur'anic text.",
                    color = Color(0xFFE7C17C),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            if (item.transliteration.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    transliterationPreview,
                    color = gold,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (isFullSurah) 2 else Int.MAX_VALUE,
                    overflow = if (isFullSurah) TextOverflow.Ellipsis else TextOverflow.Clip
                )
            } else if (item.quranAyahKeys.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Transliteration will appear once the complete Qur'anic text has loaded.",
                    color = Color(0xFFBEB9AC),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (item.meaning.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meaningPreview,
                    color = Color(0xFFBEB9AC),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = if (isFullSurah) 2 else Int.MAX_VALUE,
                    overflow = if (isFullSurah) TextOverflow.Ellipsis else TextOverflow.Clip
                )
            }
            if (displayReference.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(displayReference, color = Color(0xFF7F8C78), fontSize = 10.sp, letterSpacing = .25.sp)
            }
            if (!selectable && !isOpenDuaItem) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "REFERENCE ONLY · NOT A COUNTABLE DU'A",
                    color = Color(0xFFE7C17C),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .45.sp
                )
            }
            if (item.quranAyahKeys.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "QUR'AN RECITATION · ${selectedQuranReciter.displayName.uppercase()}",
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
                if (onRead != null) {
                    TextButton(
                        onClick = onRead,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 5.dp)
                    ) {
                        Text(
                            "READ ALL ${item.quranAyahKeys.size} AYAHS",
                            color = gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .45.sp
                        )
                    }
                }
            }
            else if (item.recitationAudioUrls.isNotEmpty()) {
                // The speaker control already communicates audio availability.
                // Do not repeat provider and reciter details on every OpenDua card.
                if (!isOpenDuaItem) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "RECORDED RECITATION",
                        color = Color(0xFF83B994),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .45.sp
                    )
                    Text(
                        item.recitationCredit.ifBlank { "Audio streamed from the recording linked to this entry" },
                        color = Color(0xFF7F8C78),
                        fontSize = 9.sp
                    )
                }
            } else if (item.customId != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "NO AUDIO RECORDING ATTACHED",
                    color = Color(0xFF8B8F82),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .45.sp
                )
            } else if (item.isCountable) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "NO RECORDING PROVIDED BY OPENDUA",
                    color = Color(0xFF8B8F82),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .45.sp
                )
            }
            }
        }
    }
}
