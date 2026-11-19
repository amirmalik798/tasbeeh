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
    val customId: String? = null
)

internal object DhikrCatalog {
    val items = listOf(
        DhikrItem("subhanallah", "SubhanAllah", "SubhanAllah", "Tasbih", "سُبْحَانَ اللَّهِ", "Subḥānallāh", "Glory be to Allah."),
        DhikrItem("alhamdulillah", "Alhamdulillah", "Alhamdulillah", "Praise", "الْحَمْدُ لِلَّهِ", "Alḥamdulillāh", "All praise belongs to Allah."),
        DhikrItem("allahu-akbar", "Allahu Akbar", "Allahu Akbar", "Takbir", "اللَّهُ أَكْبَرُ", "Allāhu akbar", "Allah is the Greatest."),
        DhikrItem("tahlil", "La ilaha illallah", "La ilaha illallah", "Tahlil", "لَا إِلَٰهَ إِلَّا اللَّهُ", "Lā ilāha illallāh", "There is no deity worthy of worship except Allah."),
        DhikrItem("subhanallah-bihamdihi", "SubhanAllahi wa bihamdihi", "SubhanAllahi wa bihamdihi", "Tasbih", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "Subḥānallāhi wa biḥamdih", "Glory be to Allah and praise be to Him.", "Sahih Muslim"),
        DhikrItem("subhanallah-azim", "SubhanAllahil-Azim", "SubhanAllahil-Azim", "Tasbih", "سُبْحَانَ اللَّهِ الْعَظِيمِ", "Subḥānallāhil-ʿAẓīm", "Glory be to Allah, the Magnificent."),
        DhikrItem("four-phrases", "The four beloved phrases", "The four beloved phrases", "Tasbih", "سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَٰهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ", "Subḥānallāh · Alḥamdulillāh · Lā ilāha illallāh · Allāhu akbar", "Glory be to Allah, all praise belongs to Allah, none is worthy of worship except Allah, and Allah is the Greatest."),
        DhikrItem("hawqalah", "La hawla wa la quwwata illa billah", "La hawla wa la quwwata illa billah", "Tasbih", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "Lā ḥawla wa lā quwwata illā billāh", "There is no power and no strength except through Allah.", "Sahih al-Bukhari · Sunan Ibn Majah 3824"),
        DhikrItem("astaghfirullah", "Astaghfirullah", "Astaghfirullah", "Istighfar", "أَسْتَغْفِرُ اللَّهَ", "Astaghfirullāh", "I seek forgiveness from Allah."),
        DhikrItem("astaghfirullah-tubu", "Astaghfirullaha wa atubu ilayh", "Astaghfirullah wa atubu ilayh", "Istighfar", "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", "Astaghfirullāha wa atūbu ilayh", "I seek Allah's forgiveness and turn to Him in repentance."),
        DhikrItem("sayyid-istighfar", "Sayyid al-Istighfar", "Sayyid al-Istighfar", "Istighfar", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ", "Allāhumma anta rabbī, lā ilāha illā anta, khalaqtanī wa anā ʿabduka, wa anā ʿalā ʿahdika wa waʿdika mā istaṭaʿtu, aʿūdhu bika min sharri mā ṣanaʿtu, abūʾu laka bi-niʿmatika ʿalayya, wa abūʾu bi-dhanbī, faghfir lī, fa-innahū lā yaghfirudh-dhunūba illā anta.", "O Allah, You are my Lord. There is no deity worthy of worship except You. You created me and I am Your servant. I keep my covenant with You as best I can. I seek refuge in You from the evil I have done, acknowledge Your blessings and confess my sins. Forgive me, for none forgives sins except You.", "Sahih al-Bukhari 6306"),
        DhikrItem("salawat", "Allahumma salli 'ala Muhammad", "Salawat upon the Prophet ﷺ", "Salawat", "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ", "Allāhumma ṣalli wa sallim ʿalā nabiyyinā Muḥammad", "O Allah, send Your blessings and peace upon our Prophet Muhammad ﷺ.", "Hisn al-Muslim 98"),
        DhikrItem("tahlil-complete", "La ilaha illallah wahdahu...", "Tahlil · complete remembrance", "Tahlil", "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ", "Lā ilāha illallāhu waḥdahu lā sharīka lah, lahul-mulku wa lahul-ḥamdu, wa huwa ʿalā kulli shayʾin qadīr", "There is no deity worthy of worship except Allah alone, without partner. His is the dominion and His is all praise, and He has power over all things.", "Sahih al-Bukhari 6403 · Sahih Muslim"),
        DhikrItem("hasbunallah", "Hasbunallahu wa ni'mal-wakil", "Hasbunallahu wa ni'mal-wakil", "Qur'anic", "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ", "Ḥasbunallāhu wa niʿmal-wakīl", "Allah is sufficient for us, and He is the best Disposer of affairs.", "Qur'an 3:173"),
        DhikrItem("inna-lillah", "Inna lillahi wa inna ilayhi raji'un", "Inna lillahi wa inna ilayhi raji'un", "Qur'anic", "إِنَّا لِلَّهِ وَإِنَّا إِلَيْهِ رَاجِعُونَ", "Innā lillāhi wa innā ilayhi rājiʿūn", "We belong to Allah, and to Him we shall return.", "Qur'an 2:156"),
        DhikrItem("dua-yunus", "Dua of Prophet Yunus", "Dua of Prophet Yunus", "Qur'anic", "لَا إِلَٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ", "Lā ilāha illā anta, subḥānaka, innī kuntu minaẓ-ẓālimīn", "There is no deity except You; glory be to You. I was among the wrongdoers.", "Qur'an 21:87"),
        DhikrItem("rabbana-atina", "Rabbana atina fid-dunya", "Rabbana atina fid-dunya", "Qur'anic", "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ", "Rabbanā ātinā fid-dunyā ḥasanah, wa fil-ākhirati ḥasanah, wa qinā ʿadhāban-nār", "Our Lord, grant us good in this world and good in the Hereafter, and protect us from the punishment of the Fire.", "Qur'an 2:201"),
        DhikrItem("rabbi-ghfir-li", "Rabbighfir li", "Rabbighfir li", "Istighfar", "رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ إِنَّكَ أَنْتَ التَّوَّابُ الرَّحِيمُ", "Rabbighfir lī wa tub ʿalayya, innaka antat-Tawwābur-Raḥīm", "My Lord, forgive me and accept my repentance. You are the Accepter of repentance, the Most Merciful."),
        DhikrItem("audhu-kalimat", "A'udhu bi kalimatillah", "A'udhu bi kalimatillah", "Protection", "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ", "Aʿūdhu bi-kalimātillāhit-tāmmāti min sharri mā khalaq", "I seek refuge in the perfect words of Allah from the evil of what He has created.", "Hisn al-Muslim 97"),
        DhikrItem("bismillah-protection", "Bismillahilladhi la yadurru", "Bismillahilladhi la yadurru", "Protection", "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", "Bismillāhilladhī lā yaḍurru maʿa-smihī shayʾun fil-arḍi wa lā fis-samāʾi wa huwas-Samīʿul-ʿAlīm", "In the name of Allah, with whose name nothing on earth or in the heavens can cause harm. He is the All-Hearing, the All-Knowing.", "Sunan Abi Dawud 5088"),
        DhikrItem("moses-dua", "Rabbi inni lima anzalta", "Rabbi inni lima anzalta", "Qur'anic", "رَبِّ إِنِّي لِمَا أَنْزَلْتَ إِلَيَّ مِنْ خَيْرٍ فَقِيرٌ", "Rabbi innī limā anzalta ilayya min khayrin faqīr", "My Lord, I am truly in need of whatever good You may send down to me.", "Qur'an 28:24"),
        DhikrItem("beneficial-knowledge", "Allahumma inni as'aluka ilman nafi'an", "Dua for beneficial knowledge", "Daily Du'a", "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا وَرِزْقًا طَيِّبًا وَعَمَلًا مُتَقَبَّلًا", "Allāhumma innī asʾaluka ʿilman nāfiʿan, wa rizqan ṭayyiban, wa ʿamalan mutaqabbalan", "O Allah, I ask You for beneficial knowledge, good provision and accepted deeds.", "Sunan Ibn Majah 925")
    )

    fun byValue(value: String): DhikrItem? = items.firstOrNull { it.value == value }
}

@Composable
internal fun DhikrSelectionScreen(
    currentDhikr: String,
    currentCustomDhikrId: String?,
    customDhikrs: List<CustomDhikr>,
    onBack: () -> Unit,
    onSelect: (DhikrItem) -> Unit,
    onCreateCustom: () -> Unit,
    onEditCustom: (CustomDhikr) -> Unit,
    onDeleteCustom: (CustomDhikr) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var pendingDelete by remember { mutableStateOf<CustomDhikr?>(null) }
    val categories = listOf("All", "Tasbih", "Praise", "Takbir", "Tahlil", "Istighfar", "Salawat", "Qur'anic", "Protection", "Daily Du'a", "My Dhikr")
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
    val allItems = remember(customItems) { DhikrCatalog.items + customItems }
    val filteredItems = remember(query, category, allItems) {
        val normalizedQuery = normalizeDhikrSearchText(query)
        allItems.filter { item ->
            val matchesCategory = category == "All" || item.category == category
            val matchesSearch = normalizedQuery.isBlank() ||
                listOf(item.title, item.transliteration, item.meaning, item.arabic)
                    .any { value -> normalizeDhikrSearchText(value).contains(normalizedQuery) }
            matchesCategory && matchesSearch
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
                    onDeleteCustom(entry)
                    pendingDelete = null
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
        }
    }
}
