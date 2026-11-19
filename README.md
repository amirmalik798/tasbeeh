# Tasbeeh — Android

Native Android dhikr counter built with Kotlin and Jetpack Compose.

## Build the Android app
- Open in Android Studio and run the app on a device/emulator; or
- On macOS/Linux run `./gradlew :app:assembleDebug`; on Windows run `gradlew.bat :app:assembleDebug`.

The lightweight Gradle bootstrap scripts download Gradle 8.9 when it is not already cached. Java 17 is required.

## GitHub Actions
Pushes and pull requests targeting `main` run the Android CI workflow. It runs unit tests, assembles the debug APK, and uploads `Tasbeeh-debug-apk` as a 7-day GitHub Actions artifact. You can also trigger it manually from the Actions tab.

## Experience
- Kaaba-inspired Kiswah black, muted emerald and warm gold.
- 33 individually shaded beads with specular highlights and cast shadows.
- Active bead advances around the strand on every count; the previous bead yields to the next slot with spring settling.
- Imam bead and tassel.
- Count, target, dhikr and preferences persisted using Android Preferences DataStore.
- Session completion history, undo, reset confirmation, target presets and haptic feedback.

## Persistence
Count and preferences are committed to Preferences DataStore after every change. DataStore survives process death and normal restarts. Clearing app storage or uninstalling removes local app-private data.
