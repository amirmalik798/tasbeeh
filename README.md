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

The built-in library contains 100 selected remembrances and supplications from the Qur'an and Sunnah. Qur'anic passages are referenced by surah and ayah; narrations include their hadith collection and number. The collection was cross-checked against Hisn al-Muslim and OpenDua's versioned reference material. Transliteration is provided as a pronunciation aid; recitation and tajwid should be learned from a qualified teacher or reliable reciter.

OpenDua editorial material and transliteration references are attributed under CC BY 4.0:

OpenDua dataset v0.0.4 (https://opendua.org), by Ihsan Labs at the Human Development Fund. Based on Hisn al-Muslim by Sa'id ibn Ali ibn Wahf al-Qahtani. OpenDua editorial text, dataset structure and the recitation by Muhammad Jumah are licensed CC BY 4.0. OpenDua makes no ownership claim over the underlying religious texts.

Licence: https://opendua.org/licence/data


## Qur'anic recitation

Qur'anic selections use streamed human recitation by AbdulBaset AbdulSamad (Mujawwad) from Quran Foundation's documented recitation audio host. Complete cited ayah recordings are streamed in order; a short supplication excerpt may therefore play its complete source ayah. Recordings are streamed on demand and are not bundled, cached by the app, or rehosted. Credit is shown in the app. This feature requires an internet connection. Non-Qur'anic adhkar continue to use the device's Arabic text-to-speech engine, which is not a substitute for tajwid.

The category filter row places **My Dhikr** immediately after **All**.
