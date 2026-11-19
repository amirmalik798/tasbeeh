package com.amirmalik.tasbeeh

import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray

/**
 * Owns the recitation player independently of MainActivity.
 *
 * MediaSessionService automatically promotes active playback to the
 * mediaPlayback foreground-service type and publishes system media controls.
 * Its playlist checkpoint also allows playback resumption after the service
 * process has been terminated.
 */
@UnstableApi
internal class QuranRecitationPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var preferences: SharedPreferences
    private var mediaSession: MediaSession? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistenceRunnable = object : Runnable {
        override fun run() {
            if (!::player.isInitialized) return
            if (player.mediaItemCount > 0) persistPlaybackCheckpoint()
            if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                mainHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: androidx.media3.common.Timeline,
                    reason: Int
                ) {
                    if (mediaItemCount > 0) {
                        persistPlaybackCheckpoint()
                        scheduleCheckpoint()
                    } else {
                        clearPlaybackCheckpoint()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mediaItemCount > 0) persistPlaybackCheckpoint()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (mediaItemCount > 0) persistPlaybackCheckpoint()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (mediaItemCount > 0) persistPlaybackCheckpoint()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            clearPlaybackCheckpoint()
                            mainHandler.removeCallbacks(persistenceRunnable)
                        }
                        Player.STATE_BUFFERING, Player.STATE_READY ->
                            if (mediaItemCount > 0) scheduleCheckpoint()
                        Player.STATE_IDLE ->
                            if (mediaItemCount == 0) mainHandler.removeCallbacks(persistenceRunnable)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Recorded recitation playback failed", error)
                    clearPlaybackCheckpoint()
                    mainHandler.removeCallbacks(persistenceRunnable)
                }
            })
        }

        // The service owns its playlist while alive. ExoPlayer and MediaSession
        // keep playback independent of Activity lifecycle, including backgrounding.
        mediaSession = MediaSession.Builder(this, player)
            .build()

        restoreSavedPlaybackIfAvailable()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mainHandler.removeCallbacks(persistenceRunnable)
        if (::player.isInitialized && player.mediaItemCount > 0) {
            persistPlaybackCheckpoint()
        }
        // Detach MediaSession controllers before releasing their backing player.
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    private fun scheduleCheckpoint() {
        mainHandler.removeCallbacks(persistenceRunnable)
        mainHandler.postDelayed(persistenceRunnable, CHECKPOINT_INTERVAL_MS)
    }

    private fun persistPlaybackCheckpoint() {
        if (!::player.isInitialized || player.mediaItemCount <= 0) return
        val itemCount = player.mediaItemCount
        if (itemCount > MAX_STORED_TRACKS) {
            clearPlaybackCheckpoint()
            return
        }
        val urls = buildList {
            repeat(itemCount) { index ->
                val uri: Uri? = player.getMediaItemAt(index).localConfiguration?.uri
                val value = uri?.toString()?.takeIf(::isTrustedRecitationUrl)
                if (value != null) add(value)
            }
        }
        // Never checkpoint a filtered playlist with the original ExoPlayer index:
        // that could restore the wrong track or seek position after process death.
        if (urls.size != itemCount || urls.isEmpty()) {
            clearPlaybackCheckpoint()
            return
        }

        val currentItem = player.currentMediaItem
        val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
        val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
        preferences.edit()
            .putString(KEY_URLS, JSONArray(urls).toString())
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .putInt(KEY_CURRENT_INDEX, player.currentMediaItemIndex.coerceIn(0, urls.lastIndex))
            .putLong(KEY_POSITION_MS, player.currentPosition.coerceAtLeast(0L))
            .putBoolean(KEY_PLAY_WHEN_READY, player.playWhenReady)
            .apply()
    }

    private fun readPlaybackCheckpoint(): PlaybackCheckpoint? = runCatching {
        val encoded = preferences.getString(KEY_URLS, null) ?: return@runCatching null
        if (encoded.length > MAX_STORED_PLAYLIST_CHARS) return@runCatching null
        val array = JSONArray(encoded)
        if (array.length() !in 1..MAX_STORED_TRACKS) return@runCatching null

        val urls = buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (!isTrustedRecitationUrl(value)) return@runCatching null
                add(value)
            }
        }
        if (urls.isEmpty()) return@runCatching null

        val title = preferences.getString(KEY_TITLE, null)
            ?.takeIf { it.isNotBlank() } ?: "Qur'anic recitation"
        val artist = preferences.getString(KEY_ARTIST, null)
            ?.takeIf { it.isNotBlank() } ?: QuranRecitationAudio.ATTRIBUTION
        val items = createRecitationMediaItems(title, artist, urls)
        if (items.isEmpty()) return@runCatching null

        PlaybackCheckpoint(
            items = items,
            startIndex = preferences.getInt(KEY_CURRENT_INDEX, 0).coerceIn(0, items.lastIndex),
            startPositionMs = preferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
            playWhenReady = preferences.getBoolean(KEY_PLAY_WHEN_READY, false)
        )
    }.getOrNull()

    /**
     * If Android has reclaimed this foreground service, restore the saved
     * trusted playlist and seek position when it is next recreated. Only
     * resume automatically when the last saved player intent was playing.
     */
    private fun restoreSavedPlaybackIfAvailable() {
        val checkpoint = readPlaybackCheckpoint() ?: return
        runCatching {
            player.setMediaItems(
                checkpoint.items,
                checkpoint.startIndex,
                checkpoint.startPositionMs
            )
            player.prepare()
            player.playWhenReady = checkpoint.playWhenReady
        }.onFailure { error ->
            Log.w(TAG, "Could not restore the saved recitation queue", error)
            clearPlaybackCheckpoint()
            player.clearMediaItems()
        }
    }

    private fun clearPlaybackCheckpoint() {
        preferences.edit()
            .remove(KEY_URLS)
            .remove(KEY_TITLE)
            .remove(KEY_ARTIST)
            .remove(KEY_CURRENT_INDEX)
            .remove(KEY_POSITION_MS)
            .remove(KEY_PLAY_WHEN_READY)
            .apply()
    }

    private data class PlaybackCheckpoint(
        val items: List<MediaItem>,
        val startIndex: Int,
        val startPositionMs: Long,
        val playWhenReady: Boolean
    )

    private companion object {
        const val TAG = "QuranPlaybackService"
        const val PREFERENCES_NAME = "recitation_playback_checkpoint"
        const val KEY_URLS = "urls"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_CURRENT_INDEX = "current_index"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_PLAY_WHEN_READY = "play_when_ready"
        const val CHECKPOINT_INTERVAL_MS = 5_000L
        const val MAX_STORED_TRACKS = 700
        const val MAX_STORED_PLAYLIST_CHARS = 180_000
    }
}

internal fun createRecitationMediaItems(
    title: String,
    artist: String,
    urls: List<String>
): List<MediaItem> {
    if (title.isBlank() || urls.isEmpty() || urls.size > 700) return emptyList()
    if (urls.any { !isTrustedRecitationUrl(it) }) return emptyList()

    return urls.mapIndexed { index, url ->
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { "Recorded recitation" })
            .setSubtitle("Track ${index + 1} of ${urls.size}")
            .build()
        MediaItem.Builder()
            .setMediaId("recitation-${index + 1}")
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }
}