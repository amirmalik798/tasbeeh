package com.amirmalik.tasbeeh.voice

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Downloads a pinned, multilingual Whisper-tiny ONNX model into app-private storage.
 * The model is only fetched after the user explicitly chooses voice enrollment.
 * Speech and transcripts are never uploaded.
 */
internal object VoiceModelRepository {
    const val MODEL_ID = "sherpa-onnx-whisper-tiny-ar-v1"
    private const val MODEL_REVISION = "65176e2deb88badc814a94058666cadccc29b61c"
    private const val TOKEN_FILE_MAX_BYTES = 1_000_000L
    private const val ENCODER_MAX_BYTES = 20_000_000L
    private const val DECODER_MAX_BYTES = 100_000_000L
    private const val REQUIRED_FREE_BYTES = 125_000_000L

    data class ModelFiles(
        val directory: File,
        val encoder: File,
        val decoder: File,
        val tokens: File
    )

    private data class Asset(
        val fileName: String,
        val url: String,
        val maxBytes: Long,
        val sha256: String?
    )

    private val assets = listOf(
        Asset(
            fileName = "tiny-encoder.int8.onnx",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/$MODEL_REVISION/tiny-encoder.int8.onnx",
            maxBytes = ENCODER_MAX_BYTES,
            sha256 = "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"
        ),
        Asset(
            fileName = "tiny-decoder.int8.onnx",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/$MODEL_REVISION/tiny-decoder.int8.onnx",
            maxBytes = DECODER_MAX_BYTES,
            sha256 = "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"
        ),
        Asset(
            fileName = "tiny-tokens.txt",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/$MODEL_REVISION/tiny-tokens.txt",
            maxBytes = TOKEN_FILE_MAX_BYTES,
            sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
        )
    )

    fun modelFiles(context: Context): ModelFiles {
        val directory = File(context.filesDir, "voice-models/$MODEL_ID")
        return ModelFiles(
            directory = directory,
            encoder = File(directory, assets[0].fileName),
            decoder = File(directory, assets[1].fileName),
            tokens = File(directory, assets[2].fileName)
        )
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        val files = modelFiles(context)
        files.encoder.isFile &&
            files.encoder.length() in 1..ENCODER_MAX_BYTES &&
            sha256(files.encoder) == assets[0].sha256 &&
            files.decoder.isFile &&
            files.decoder.length() in 1..DECODER_MAX_BYTES &&
            sha256(files.decoder) == assets[1].sha256 &&
            files.tokens.isFile &&
            files.tokens.length() in 100_000..TOKEN_FILE_MAX_BYTES &&
            sha256(files.tokens) == assets[2].sha256 &&
            validateTokenFile(files.tokens)
    }.getOrDefault(false)

    suspend fun ensureInstalled(
        context: Context,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ModelFiles = withContext(Dispatchers.IO) {
        val files = modelFiles(context)
        if (isInstalled(context)) return@withContext files

        val stats = StatFs(context.filesDir.absolutePath)
        val available = stats.availableBytes
        if (available < REQUIRED_FREE_BYTES) {
            throw IOException("At least 125 MB of free app storage is required to install offline voice recognition.")
        }
        if (!files.directory.exists() && !files.directory.mkdirs()) {
            throw IOException("Could not create the private voice-model directory.")
        }

        val destinations = listOf(files.encoder, files.decoder, files.tokens)
        val totalBytes = assets.sumOf { asset ->
            when (asset.fileName) {
                "tiny-encoder.int8.onnx" -> 13_500_000L
                "tiny-decoder.int8.onnx" -> 89_855_401L
                else -> 820_000L
            }
        }
        var completedBytes = 0L
        assets.forEachIndexed { index, asset ->
            val destination = destinations[index]
            val validExisting = destination.isFile &&
                destination.length() in 1..asset.maxBytes &&
                (asset.sha256 == null || sha256(destination) == asset.sha256) &&
                (asset.fileName != "tiny-tokens.txt" || validateTokenFile(destination))
            if (!validExisting) {
                destination.delete()
                downloadAsset(asset, destination) { bytesForThisAsset ->
                    onProgress((completedBytes + bytesForThisAsset).coerceAtMost(totalBytes), totalBytes)
                }
            }
            completedBytes += destination.length()
            onProgress(completedBytes.coerceAtMost(totalBytes), totalBytes)
        }

        if (!isInstalled(context)) {
            files.directory.listFiles()?.forEach { runCatching { it.delete() } }
            throw IOException("The downloaded voice model did not pass its integrity checks. Please retry.")
        }
        files
    }

    private fun downloadAsset(asset: Asset, destination: File, onProgress: (Long) -> Unit) {
        val temporary = File(destination.parentFile, destination.name + ".part")
        temporary.delete()
        val connection = openTrustedConnection(asset.url)
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("The voice model host returned HTTP $status.")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > asset.maxBytes) {
                throw IOException("The voice model file exceeded its permitted size.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > asset.maxBytes) {
                            throw IOException("The voice model file exceeded its permitted size.")
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onProgress(downloaded)
                    }
                    output.flush()
                }
            }
            if (declaredLength >= 0L && downloaded != declaredLength) {
                throw IOException("The voice model download was incomplete.")
            }
            if (downloaded <= 0L) throw IOException("The voice model host returned an empty file.")
            val actualHash = digest.digest().toHex()
            if (asset.sha256 != null && !actualHash.equals(asset.sha256, ignoreCase = true)) {
                throw IOException("The voice model checksum did not match the trusted release.")
            }
            if (asset.fileName == "tiny-tokens.txt" && !validateTokenFile(temporary)) {
                throw IOException("The voice model vocabulary file is invalid.")
            }
            java.nio.file.Files.move(
                temporary.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openTrustedConnection(initialUrl: String): HttpURLConnection {
        var current = URL(initialUrl)
        repeat(6) {
            if (current.protocol != "https" || !isTrustedModelHost(current.host)) {
                throw IOException("The model download redirected to an untrusted host.")
            }
            val connection = current.openConnection() as? HttpURLConnection
                ?: throw IOException("Unsupported model download connection.")
            connection.connectTimeout = 20_000
            connection.readTimeout = 40_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "SereneDhikr/1.0 offline-model")
            connection.connect()
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("The model host returned an invalid redirect.")
                    current = URL(current, location)
                    connection.disconnect()
                }
                else -> {
                    connection.disconnect()
                    throw IOException("Could not download the voice model (HTTP $status).")
                }
            }
        }
        throw IOException("The model host redirected too many times.")
    }

    private fun isTrustedModelHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT)
        return normalized == "huggingface.co" ||
            normalized.endsWith(".huggingface.co") ||
            normalized.endsWith(".xethub.hf.co")
    }

    private fun validateTokenFile(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() !in 100_000..TOKEN_FILE_MAX_BYTES) return@runCatching false
        var lineCount = 0
        var previousId = -1
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val separator = line.lastIndexOf(' ')
                if (separator <= 0) return@runCatching false
                val id = line.substring(separator + 1).trim().toIntOrNull()
                    ?: return@runCatching false
                if (id <= previousId || id > 60_000) return@runCatching false
                previousId = id
                lineCount++
                if (lineCount > 60_000) return@runCatching false
            }
        }
        lineCount in 50_000..55_000 && previousId >= 50_000
    }.getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
}
