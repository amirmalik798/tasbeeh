# NOOR-E-DHIKR — Android

Native Android dhikr counter built with Kotlin and Jetpack Compose.

## Build the Android app
- Open in Android Studio and run the app on a device/emulator; or
- On macOS/Linux run `./gradlew :app:assembleDebug`; on Windows run `gradlew.bat :app:assembleDebug`.

The lightweight Gradle bootstrap scripts download Gradle 8.9 when it is not already cached. Java 17 is required.

## GitHub Actions
Pushes and pull requests targeting `main` run the Android CI workflow. It runs unit tests, Android Lint, assembles the debug APK, and uploads `Noor-e-Dhikr-debug-apk` as a 7-day GitHub Actions artifact. You can also trigger it manually from the Actions tab.

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
The counter, preferences, custom dhikr collection and single current-session record are committed to Preferences DataStore. The previous multi-session history keys are migrated once and removed. DataStore survives process death and normal restarts. Clearing app storage or uninstalling removes local app-private data.

## Catalogue structure

The built-in library is now sourced from two separate collections. The category order is fixed:

1. **All**
2. **Quran**
3. **My Dhikhrs**
4. **OpenDua topical categories**, in the exact order published by OpenDua

The **Quran** category contains exactly four selections, in this order: Surah al-Fatihah; 4 Qul (al-Kafirun, al-Ikhlas, al-Falaq and an-Nas); Ayat al-Kursi; and the last two ayahs of Surah al-Baqarah. Each Qur'anic selection has an explicit surah/ayah mapping and streams its recitation from Quran Foundation. Audio uses AbdulBaset AbdulSamad (Mujawwad); complete ayahs are played in sequence. Qur'anic Arabic text is refreshed from AlQuran Cloud's Uthmani edition and its English meaning from Sahih International. The app does not embed Quran Foundation credentials or synthesize speech.

The OpenDua library uses the official, immutable **v0.0.4** release, which publishes **267 contextual entries**, **262 canonical dua texts**, **249 recording files** and **19 topical tags**. All 267 source entries are retained. Where an entry has context-specific variations, each variation is presented as its own selectable option so its Arabic, translation and associated recitation stay together. This can make the number of visible options slightly higher than the number of source entries. Instruction and narration records are preserved as read-only references; they are not countable supplications. Audio is played only when OpenDua supplies a recording for the selected option; the app does not create substitute audio for entries without recordings.

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

The Qur'anic audio player uses the Quran Foundation ayah streams independently of OpenDua. OpenDua recitations are streamed from their linked audio URLs and credited to Muhammad Jumah. Both require an internet connection. The app contains no Android TextToSpeech integration and no male/female voice selector.
