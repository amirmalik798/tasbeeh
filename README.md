# Tasbeeh — Android

Native Android dhikr counter built with Kotlin and Jetpack Compose.

## Requirements
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35

Open in Android Studio, sync Gradle, and run the `app` configuration on an Android device or emulator (minimum Android 8 / API 26).

## Experience
- Kaaba-inspired Kiswah black, muted emerald and warm gold palette.
- Custom 33-bead misbaha with dimensional shading, specular highlights and cast shadows.
- Each count advances the active bead to the next strand position, while the outgoing bead moves back into its place. Spring settling creates tactile momentum rather than a simple color pulse.
- Imam bead and tassel.
- Current count, target, dhikr and preferences persisted using Android Preferences DataStore.
- Completion history (up to 100 records), undo, reset confirmation, target presets and haptic feedback.

## Persistence
Count and preferences are committed to Preferences DataStore after each change. DataStore survives process death and normal restarts. Clearing app storage or uninstalling removes local app-private data.

## Build
```bash
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```
