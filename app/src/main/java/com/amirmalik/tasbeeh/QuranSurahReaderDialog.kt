package com.amirmalik.tasbeeh

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QuranSurahReaderDialog(
    item: DhikrItem,
    isSpeaking: Boolean,
    isPreparingRecitation: Boolean,
    activeRecitationTitle: String,
    selectedQuranReciter: QuranReciter,
    hasInternetConnection: Boolean,
    onPlayQuranRecitation: (String, List<String>) -> Unit,
    onStopRecitation: () -> Unit,
    onDismiss: () -> Unit
) {
    val arabicAyahs = remember(item.id, item.arabic) {
        item.arabic.split(QuranicDuaCatalog.VERSE_SEPARATOR)
    }
    val transliterationAyahs = remember(item.id, item.transliteration) {
        item.transliteration.split(QuranicDuaCatalog.VERSE_SEPARATOR)
    }
    val translationAyahs = remember(item.id, item.meaning) {
        item.meaning.split(QuranicDuaCatalog.VERSE_SEPARATOR)
    }
    val textIsComplete = arabicAyahs.size == item.quranAyahKeys.size &&
        transliterationAyahs.size == item.quranAyahKeys.size &&
        translationAyahs.size == item.quranAyahKeys.size &&
        arabicAyahs.all { it.isNotBlank() } &&
        transliterationAyahs.all { it.isNotBlank() } &&
        translationAyahs.all { it.isNotBlank() }
    val canPlay = item.quranAyahKeys.isNotEmpty() &&
        item.quranAyahKeys.all { QuranRecitationAudio.audioUrlForAyah(it) != null }
    val isThisSurahActive = (isSpeaking || isPreparingRecitation) &&
        activeRecitationTitle.equals(item.title, ignoreCase = true)
    val accent = Color(0xFFFFD56A)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            color = Color(0xFF101612),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF6A552E))
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            color = Color(0xFFFFF8EA),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            item.reference,
                            color = Color(0xFF9AA394),
                            fontSize = 11.sp
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("CLOSE", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(9.dp))
                FilledTonalButton(
                    onClick = {
                        if (isThisSurahActive) onStopRecitation()
                        else onPlayQuranRecitation(item.title, item.quranAyahKeys)
                    },
                    enabled = isThisSurahActive || (hasInternetConnection && canPlay),
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF263629),
                        contentColor = Color(0xFFFFE7AD),
                        disabledContainerColor = Color(0xFF202720),
                        disabledContentColor = Color(0xFF838B7F)
                    )
                ) {
                    Text(
                        when {
                            isThisSurahActive && isPreparingRecitation -> "STOP PREPARING RECITATION"
                            isThisSurahActive -> "STOP RECITATION"
                            else -> "PLAY FULL SURAH · ${selectedQuranReciter.displayName.uppercase()}"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    selectedQuranReciter.attribution,
                    color = Color(0xFF879184),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                if (!textIsComplete) {
                    Text(
                        "The complete verified Arabic text, transliteration and translation for this surah are not available on this device yet. Connect to the internet and retry the Qur'an catalogue download.",
                        color = Color(0xFFE7C17C),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = item.quranAyahKeys,
                            key = { _, ayahKey -> ayahKey }
                        ) { index, ayahKey ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    "AYAH " + ayahKey.substringAfter(':'),
                                    color = accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = .65.sp
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    arabicAyahs[index],
                                    color = Color(0xFFFFE7AD),
                                    fontSize = 24.sp,
                                    lineHeight = 42.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    transliterationAyahs[index],
                                    color = accent,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    translationAyahs[index],
                                    color = Color(0xFFB9C0B2),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            if (index < item.quranAyahKeys.lastIndex) {
                                Spacer(Modifier.height(3.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(1.dp),
                                    color = Color(0xFF303A31)
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}