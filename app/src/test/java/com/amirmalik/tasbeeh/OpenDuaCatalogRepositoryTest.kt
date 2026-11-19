package com.amirmalik.tasbeeh

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenDuaCatalogRepositoryTest {

    @Test
    fun parserKeepsEveryPublishedEntryAllDuasAndEveryRecording() {
        val snapshot = OpenDuaCatalogParser.parse(sampleRelease())
        assertNotNull(snapshot)
        snapshot!!
        assertEquals(OpenDuaCatalogParser.DATA_VERSION, snapshot.dataVersion)
        assertEquals(267, snapshot.items.map { it.sourceEntryId }.distinct().size)
        assertEquals(262, snapshot.canonicalDuaCount)
        assertEquals(249, snapshot.recordingCount)
        assertEquals(249, snapshot.items.flatMap { it.recitationAudioUrls }.distinct().size)
        assertEquals(19, snapshot.categories.size)
        assertEquals("Tag 01", snapshot.categories.first())
        assertEquals("Tag 19", snapshot.categories.last())
        assertEquals(5, snapshot.items.count { !it.isCountable })
        assertTrue(snapshot.items.all { it.categories.isNotEmpty() })
    }

    @Test
    fun contextualVariationsStaySeparateAndKeepTheirOwnTitlesAndAudio() {
        val snapshot = OpenDuaCatalogParser.parse(sampleRelease())!!
        val firstEntry = snapshot.items.filter { it.sourceEntryId == "entry-000001" }
        assertEquals(2, firstEntry.size)
        assertEquals(listOf("Example invocation", "Example invocation — When returning"),
            firstEntry.map { it.title })
        assertTrue(firstEntry.all { it.isCountable })
        assertTrue(firstEntry.all { it.recitationAudioUrls.isNotEmpty() })
    }

    @Test
    fun instructionRecordsRemainVisibleButAreNotCountable() {
        val snapshot = OpenDuaCatalogParser.parse(sampleRelease())!!
        val instruction = snapshot.items.first { it.sourceEntryId == "entry-000263" }
        assertFalse(instruction.isCountable)
        assertEquals("instruction", instruction.contentType)
        assertEquals("Instruction text 263", instruction.meaning)
    }

    @Test
    fun parserRejectsWrongOrPartialDatasetReleases() {
        val root = sampleRelease()
        root.put("dataVersion", "0.0.3")
        assertEquals(null, OpenDuaCatalogParser.parse(root))

        val wrongCount = sampleRelease()
        wrongCount.getJSONObject("counts").put("entries", 266)
        assertEquals(null, OpenDuaCatalogParser.parse(wrongCount))
    }

    private fun sampleRelease(): JSONObject {
        val tagIds = listOf(
            "morning-and-evening", "sleep-and-waking", "prayer-and-the-masjid",
            "wudu-and-purification", "food-and-drink", "fasting", "home-and-family",
            "travel", "hajj-and-umrah", "weather-and-nature", "distress-and-anxiety",
            "illness-and-healing", "protection-and-refuge", "forgiveness-and-repentance",
            "provision-and-debt", "knowledge-and-guidance", "gatherings-and-greetings",
            "death-and-the-deceased", "dhikr-and-praise"
        )
        val tags = JSONArray()
        tagIds.forEachIndexed { index, id ->
            tags.put(JSONObject().put("id", id).put("title", "Tag ${(index + 1).toString().padStart(2, '0')}"))
        }
        val reciters = JSONArray().put(JSONObject().put("id", "reciter-0001").put("name", "Muhammad Jumah"))
        val chapters = JSONArray().put(JSONObject().put("id", "chapter-000001").put("title", "Test chapter"))
        val entries = JSONArray()

        for (index in 1..267) {
            val id = "entry-${index.toString().padStart(6, '0')}"
            val instruction = index > 262
            val steps = if (instruction) {
                JSONArray().put(
                    JSONObject()
                        .put("type", "instruction")
                        .put("arabic", "نَصٌّ")
                        .put("text", "Instruction text $index")
                )
            } else {
                val dua = JSONObject()
                    .put("id", "dua-${index.toString().padStart(6, '0')}")
                    .put("arabic", "اللَّهُمَّ")
                    .put("transliteration", "Allahumma")
                    .put("translation", "O Allah")
                val recordings = JSONArray()
                if (index <= 249) {
                    recordings.put(
                        JSONObject()
                            .put("id", "recording-${index.toString().padStart(6, '0')}")
                            .put("reciterId", "reciter-0001")
                            .put("url", "https://audio.opendua.org/hisn/test/OD-$index.mp3")
                    )
                }
                JSONArray().put(
                    JSONObject()
                        .put("type", "recitation")
                        .put("items", JSONArray().put(JSONObject().put("dua", dua)))
                        .put("recordings", recordings)
                )
            }
            val variations = JSONArray().put(
                JSONObject().put("id", "standard").put("steps", steps)
            )
            if (index == 1) {
                variations.put(
                    JSONObject().put("id", "returning").put("label", "When returning").put("steps", steps)
                )
            }
            entries.put(
                JSONObject()
                    .put("id", id)
                    .put("title", if (index == 1) "Example invocation" else "Entry $index")
                    .put("type", if (instruction) "instruction" else "invocation")
                    .put("chapterId", "chapter-000001")
                    .put("sourceReference", index.toString())
                    .put("tags", JSONArray().put(tagIds[(index - 1) % tagIds.size]))
                    .put("variations", variations)
            )
        }

        return JSONObject()
            .put("dataVersion", "0.0.4")
            .put("counts", JSONObject().put("entries", 267).put("duas", 262).put("recordings", 249))
            .put("tags", tags)
            .put("reciters", reciters)
            .put("chapters", chapters)
            .put("entries", entries)
    }
}
