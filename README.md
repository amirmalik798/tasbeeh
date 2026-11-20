# Serene Dhikr — Android

Native Android dhikr counter built with Kotlin and Jetpack Compose.

## Build the Android app
- Open in Android Studio and run the app on a device/emulator; or
- On macOS/Linux run `./gradlew :app:assembleDebug`; on Windows run `gradlew.bat :app:assembleDebug`.

The lightweight Gradle bootstrap scripts download Gradle 8.9 when it is not already cached. Java 17 is required.

## GitHub Actions
Pushes and pull requests targeting `main` run the Android CI workflow. It runs unit tests, Android Lint, assembles the debug APK, and uploads `Serene-Dhikr-debug-apk` as a 7-day GitHub Actions artifact. You can also trigger it manually from the Actions tab.

## Experience
- Kaaba-inspired Kiswah black, muted emerald and warm gold.
- 33 individually shaded beads with specular highlights and cast shadows.
- Active bead advances around the strand on every count; the previous bead yields to the next slot with spring settling.
- Imam bead and tassel.
- Count, target, dhikr and preferences persisted using Android Preferences DataStore.
- One persistent current session with pause/resume, start/end timestamps and elapsed duration.
- A focused Session History screen showing only the current or most recently completed session.
- Undo, reset confirmation, target presets, custom dhikr and haptic feedback.

## Persistence
The counter, preferences, custom dhikr collection, Favorites and single current-session record are committed to Preferences DataStore. Favorites store stable library item IDs, so starring or unstarring Quran, OpenDua or personal Dhikr entries survives catalogue refreshes and normal app restarts without copying catalogue content. The previous multi-session history keys are migrated once and removed. DataStore survives process death and normal restarts. Existing Quran/OpenDua counter selections are cleared during migration; their content remains available in the library and their previous session records remain in Session History. The counter is enabled only for an entry in **My Dhikhrs**. Clearing app storage or uninstalling removes local app-private data.

Recorded audio is played by an Android Media3 `MediaSessionService`, not by the Activity. Playback continues when the app UI is backgrounded or the screen is off, with a system media notification and lock-screen / Bluetooth media controls. The service maintains a bounded, trusted-URL playlist checkpoint so playback can be resumed if Android terminates the service process. As with any Android foreground service, the OS, battery restrictions, loss of connectivity, or user force-stop can still interrupt playback; the service is not a guarantee against every device/vendor shutdown.

## Voice counting

Voice counting is an optional, on-device input method for personal Dhikrs in **My Dhikhrs**. A user explicitly records a short sample in the custom Dhikr editor and then starts listening from that Dhikr's **Voice count** action. The first setup downloads the pinned multilingual Whisper Tiny int8 model (about 104 MB extracted) into Android's app-private no-backup storage; after setup, recognition works offline. Temporary microphone samples are held in memory only and cleared after transcription. The recognized phrase is saved separately in app-private no-backup profile storage; the existing DataStore saves only a random profile ID, text-version hash and enrollment timestamp. Serene Dhikr does not upload microphone audio, recognized phrases, or voice profiles. Android device/app backup does not include the model or phrase profile; users can re-enrol after restoring onto another device.

The current implementation performs Arabic phrase matching, not speaker identity verification: another speaker saying the same phrase may also be accepted, and background audio can cause recognition errors. Voice counting is therefore a convenience, not an identity/security feature. Users can always return to the existing tap counter. The app asks for microphone access only when the user chooses to record a sample or start voice counting; it does not request gallery, shared-media, contacts, location, or broad storage permissions. A dedicated microphone foreground service owns active listening and is stopped by Pause, Stop, target completion, or session teardown. It does not reuse the Quran/OpenDua playback service. Background/screen-off capture is designed for a valid foreground-service session but has not yet been verified on physical devices or across OEM battery policies.

When the saved session reaches its target through either tap or voice counting, Android TTS announces **“TARGET REACHED”** once for that session. The in-app completion banner remains. On Android 13+, notification permission is requested once at the first explicit Voice count start; a quiet notification is shown when permitted. Denial does not affect speech or counting.

Model, dependency, checksum, security, and privacy details are documented in [Voice counting model and privacy](docs/VOICE_COUNTING_MODEL_AND_PRIVACY.md).

## Catalogue structure

The built-in library is now sourced from two separate collections. The category order is fixed:

1. **All**
2. **Favorites**
3. **My Dhikhrs**
4. **Quran**
5. **OpenDua topical categories**, in the exact order published by OpenDua

The **Quran** category presents only the **114 surahs**, in canonical order. Each row shows its surah number, name and total ayah count; selecting the row opens the full reader. The original quick selections — 4 Qul (al-Kafirun, al-Ikhlas, al-Falaq and an-Nas), Ayat al-Kursi, and the last two ayahs of Surah al-Baqarah — remain available under **All**, and Surah al-Fatihah keeps its existing saved-selection identity. The reader shows verified Arabic Uthmani text, transliteration and Sahih International English translation ayah by ayah.

Preferences includes a Quran Foundation reciter selector. It loads the public ayah-by-ayah recitation catalogue, keeps different recitation styles as distinct choices, and caches the list for offline use. The selected provider recitation ID is saved in Preferences DataStore and is used consistently by Quran sound buttons and the full-surah reader. Before playback, the app resolves the selected reciter's actual verse URLs from Quran Foundation in bounded pages and validates that the complete requested ayah sequence is available. If an audio URL cannot be resolved, it rejects the incomplete playlist rather than playing a partial surah.

**My Dhikhrs** is the only category that can be selected on the main Tasbeeh counter. Quran and OpenDua entries are browseable reference content and cannot be selected or counted. Each library card has a favorite star action; recorded-audio controls sit at the top-right, separate from the title and category/reference metadata. Each dua with a supplied recording has a sound-icon-only button; entries without a supplied recording show the disabled audio control. Recitation playback is started from these individual controls or from the full-surah reader, not automatically from counter taps or Preferences.

When a Quran or OpenDua recitation is active, an in-app playback bar appears on the home and library screens. It identifies the current surah or dua by name and shows the recording details, with pause/resume and stop controls. The full-surah reader also offers pause/resume and stop for its active surah. These controls use the shared Media3 playback session, so they control the same audio that continues in the background.

Qur'anic text is fetched from AlQuran Cloud in complete chapter units. Each fully validated surah is saved independently using an atomic private cache file. Interrupted downloads therefore retain previously completed chapters, and the next refresh requests only missing chapters. The category remains browsable offline; cached surahs and the legacy four-entry text cache remain readable. Recitation streaming requires an internet connection. The app does not embed Quran Foundation credentials or synthesize speech.

The OpenDua library uses the official, immutable **v0.0.4** release, which publishes **267 contextual entries**, **262 canonical dua texts**, **249 recording files** and **19 topical tags**. All 267 source entries are retained. Where an entry has context-specific variations, each variation is presented as its own browseable reference card so its Arabic, translation and associated recitation stay together. This can make the number of visible options slightly higher than the number of source entries. Instruction and narration records are preserved as read-only references; they are not countable supplications. Audio is played only when OpenDua supplies a recording for the selected option; the app does not create substitute audio for entries without recordings.

OpenDua's release is cached locally after its version, record counts, unique source-entry IDs, canonical dua IDs and recording IDs pass integrity checks. If the network or source is unavailable, the app keeps the last complete cached release and the Quran category remains available. The dataset is loaded from the version-pinned catalogue published by OpenDua rather than the former staging API.

## Sources and attribution

**OpenDua / Hisn al-Muslim**
- Dataset: https://opendua.org/v0.0.4/catalogue.json
- Developer documentation and attribution: https://opendua.org/developers
- Data licence: https://opendua.org/licence/data
- Audio licence: https://opendua.org/licence/audio

OpenDua's published attribution: *OpenDua dataset v0.0.4 (https://opendua.org), by Ihsan Labs at the Human Development Fund. Based on Hisn al-Muslim by Sa'id ibn Ali ibn Wahf al-Qahtani. OpenDua editorial text, dataset structure and the recitation by Muhammad Jumah are licensed under CC BY 4.0. OpenDua makes no ownership claim over the underlying religious texts.* Reciter credit is displayed alongside the relevant recording.

**Qur'anic text**
- Arabic: AlQuran Cloud, Uthmani text edition — https://alquran.cloud
- English translation: Sahih International, served through AlQuran Cloud's `en.sahih` edition
- Recitation: Quran Foundation — https://quran.foundation

Quranic audio is streamed from the selected Quran Foundation ayah-by-ayah recitation. OpenDua recitations are streamed from their linked audio URLs and credited to Muhammad Jumah; the Quran reciter preference does not alter OpenDua audio. Both require an internet connection. The app contains no Android TextToSpeech integration.
