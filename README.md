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


## Dhikr library sources and attribution

The library combines the 100-entry curated built-in collection, 63 additional Qur'anic supplications/passages (for 100 Qur'anic entries total), and up to 87 published OpenDua supplications, for a 250-entry catalogue when the OpenDua API returns its complete target set. OpenDua categories, recitation blocks, citation metadata and linked recordings are read from its versioned API. New API results are cached locally; the curated collection remains available while offline. Category and review metadata are shown as source data, not as a claim that every entry has received independent scholarly review.

Qur'anic passages carry explicit surah/ayah mappings. Arabic text is refreshed from AlQuran Cloud's Uthmani edition and the English meaning from the Sahih International translation, with the source and translator credited. Audio is streamed as complete cited ayahs from Quran Foundation. The current Quran Foundation Content API requires app credentials, so no client secret is embedded in the Android app. The Sleep filter starts with the four Quls (al-Kafirun, al-Ikhlas, al-Falaq and an-Nas), Ayat al-Kursi, and the final two ayahs of al-Baqarah. Transliteration is a pronunciation aid; recitation and tajwid should be learned from a qualified teacher or reliable reciter.

Qur'anic text source: AlQuran Cloud (https://alquran.cloud), using the Uthmani text edition. English translation: Sahih International (served as en.sahih); translation rights remain with the translator and are attributed in-app and here. See the [AlQuran Cloud API](https://alquran.cloud/api) and its [terms](https://alquran.cloud/terms-and-conditions).

OpenDua API v2 is currently consumed from its public staging preview (https://api.staging.opendua.org). The API SDK documentation describes this as a developer preview and says scholar review may be incomplete. The app preserves entry citations, collections, block order and recitation links from the API. OpenDua editorial material and dataset structure are CC BY 4.0; the underlying religious texts are not claimed by OpenDua. Audio rights are separate and follow the API's rights registry. Review before redistribution:
- API documentation: https://api.staging.opendua.org/docs
- OpenDua: https://opendua.org
- Data licence: https://opendua.org/licence/data


## Qur'anic recitation

Qur'anic selections use streamed human recitation by AbdulBaset AbdulSamad (Mujawwad) from Quran Foundation's documented recitation audio host. Complete cited ayah recordings are streamed in order; a short supplication excerpt may therefore play its complete source ayah. Recordings are streamed on demand and are not bundled, cached by the app, or rehosted. Credit is shown in the app. This feature requires an internet connection. Non-Qur'anic OpenDua entries use the recitation recordings linked by the API. The app contains no Android TextToSpeech integration and no female/male voice selector. When sound is enabled, the app plays a linked recording if one exists; it does not synthesize speech for entries without audio. This requires an internet connection.

The category filter row places **My Dhikr** immediately after **All**. The remaining categories are populated from the active catalogue. The Sleep category pins the four Quls, Ayat al-Kursi and the last two ayahs of al-Baqarah at the top.
