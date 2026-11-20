package com.amirmalik.tasbeeh.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Stores only the finalized recognition phrase (never audio or a speaker embedding)
 * in app-private noBackupFilesDir. DataStore keeps a random profile ID and version
 * metadata only, so OS app backup cannot upload the transcript or model data.
 */
internal object VoiceProfileRepository {
    private const val DIRECTORY_NAME = "voice-profiles"
    private const val PROFILE_VERSION = 1
    private const val MAX_PROFILE_BYTES = 4096
    private const val MAX_REFERENCE_TEXT_CHARS = 1000

    data class VoicePhraseProfile(
        val referenceText: String,
        val sourceHash: String,
        val createdAt: Long
    )

    suspend fun save(
        context: Context,
        profileId: String,
        dhikrId: String,
        referenceText: String,
        sourceHash: String,
        createdAt: Long
    ) = withContext(Dispatchers.IO) {
        requireValidProfileId(profileId)
        if (dhikrId.isBlank()) throw IOException("The Dhikr identity is missing.")
        val phrase = referenceText.trim().take(MAX_REFERENCE_TEXT_CHARS)
        if (phrase.isBlank()) throw IOException("The recognized Dhikr phrase is empty.")
        if (!sourceHash.matches(Regex("[a-f0-9]{64}"))) {
            throw IOException("The Dhikr voice profile metadata is invalid.")
        }
        if (createdAt <= 0L) throw IOException("The Dhikr voice profile timestamp is invalid.")

        val directory = profileDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private voice-profile directory.")
        }
        val destination = profileFile(directory, profileId)
        val temporary = File(directory, "${profileId}.json.part")
        val payload = JSONObject().apply {
            put("version", PROFILE_VERSION)
            put("dhikrIdHash", hashId(dhikrId))
            put("referenceText", phrase)
            put("sourceHash", sourceHash)
            put("createdAt", createdAt)
        }.toString().toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_PROFILE_BYTES) {
            throw IOException("The Dhikr voice profile exceeded its permitted size.")
        }

        try {
            temporary.delete()
            FileOutputStream(temporary).use { output ->
                output.write(payload)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (error: Exception) {
            temporary.delete()
            throw IOException("Could not save the private Dhikr voice profile.", error)
        } finally {
            payload.fill(0)
        }
    }

    suspend fun load(
        context: Context,
        profileId: String,
        dhikrId: String,
        expectedSourceHash: String
    ): VoicePhraseProfile? = withContext(Dispatchers.IO) {
        runCatching {
            requireValidProfileId(profileId)
            if (dhikrId.isBlank() || !expectedSourceHash.matches(Regex("[a-f0-9]{64}"))) {
                return@runCatching null
            }
            val file = profileFile(profileDirectory(context), profileId)
            if (!file.isFile || file.length() !in 1..MAX_PROFILE_BYTES.toLong()) {
                return@runCatching null
            }
            val raw = file.readBytes()
            try {
                val json = JSONObject(raw.toString(Charsets.UTF_8))
                if (json.optInt("version") != PROFILE_VERSION ||
                    json.optString("dhikrIdHash") != hashId(dhikrId)
                ) {
                    return@runCatching null
                }
                val sourceHash = json.optString("sourceHash")
                if (sourceHash != expectedSourceHash) return@runCatching null
                val phrase = json.optString("referenceText")
                    .trim()
                    .take(MAX_REFERENCE_TEXT_CHARS)
                val createdAt = json.optLong("createdAt")
                if (phrase.isBlank() || createdAt <= 0L) return@runCatching null
                VoicePhraseProfile(phrase, sourceHash, createdAt)
            } finally {
                raw.fill(0)
            }
        }.getOrNull()
    }

    suspend fun delete(context: Context, profileId: String) = withContext(Dispatchers.IO) {
        if (!isValidProfileId(profileId)) return@withContext
        val directory = profileDirectory(context)
        runCatching { profileFile(directory, profileId).delete() }
        runCatching { File(directory, "${profileId}.json.part").delete() }
    }

    private fun profileDirectory(context: Context): File =
        File(context.noBackupFilesDir, DIRECTORY_NAME)

    private fun profileFile(directory: File, profileId: String): File =
        File(directory, "${profileId}.json")

    private fun requireValidProfileId(profileId: String) {
        if (!isValidProfileId(profileId)) throw IOException("The Dhikr voice profile ID is invalid.")
    }

    private fun isValidProfileId(profileId: String): Boolean =
        runCatching { UUID.fromString(profileId); true }.getOrDefault(false)

    private fun hashId(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }
}
