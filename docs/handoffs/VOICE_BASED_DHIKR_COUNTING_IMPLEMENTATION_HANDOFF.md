# Serene Dhikr — Voice-Based Dhikr Counting Implementation Handoff

**Status:** Planning / implementation handoff only. No application code has been changed by preparing this document.

**Repository:** https://github.com/amirmalik798/tasbeeh  
**Inspected branch:** `main`  
**Inspected HEAD:** `e76344ec2c40ff57f4ac7f1223d62efb24cc2f41`  
**Required implementation branch:** `feature/voice-based-dhikr-counting-2026-11-20`  
**Target base:** `main` at the latest commit at implementation start.

---

## 1. Objective

Implement an optional, on-device voice-based Dhikr counter in Serene Dhikr.

A user must be able to:

1. Configure voice counting while creating or editing a personal Dhikr in **My Dhikhrs**.
2. Enrol a spoken sample for that Dhikr by explicitly recording it with the microphone.
3. Save a local voice-recognition profile associated with that specific Dhikr.
4. Open **My Dhikhrs** and use a clearly labelled **Voice count** action on a configured personal Dhikr card.
5. Speak repetitions naturally. The app recognizes only valid repetitions of the configured Dhikr, counts them once each, and updates the existing counter and session.
6. See live recognition / listening state, count progress, target progress, and clear errors or permission guidance.
7. Receive a respectful target-completion alert when the existing session reaches its target.

The existing tap-based counter must remain fully available and unchanged in behaviour. Voice counting is an additional input method, not a replacement counter or a parallel session system.

**Core invariant:** all accepted voice repetitions and all tap repetitions must pass through the existing authoritative count operation, `TasbeehStore.countDhikr()`, so there is exactly one count, target, session, undo, and persistence source of truth.

## 2. Mandatory branch and Git workflow

This is a substantial multi-file feature. **Do not implement it on `main`.**

1. Fetch the latest `main` and inspect its HEAD before editing.
2. Create exactly one feature branch from the latest main:
   `feature/voice-based-dhikr-counting-2026-11-20`.
3. Perform all voice-counting feature work and its tests on that branch. Do not make implementation commits directly to `main`.
4. Do not create a new branch for each work package. Use multiple focused commits on this one feature branch; multiple commits are explicitly acceptable.
5. Never force-push or rewrite branch history. Preserve unrelated changes and do not reset, revert, or overwrite user work.
6. Keep the feature branch based on the repository's current app architecture and toolchain. Do not upgrade unrelated dependencies or Gradle plugins merely as part of this feature.
7. Push the feature branch and open a pull request targeting `main`. Prefer a draft PR until implementation, tests, and review are complete.
8. Do not merge the PR unless the project owner explicitly asks for the merge.
9. Report branch name, commit SHAs, PR URL, changed files, and actual CI status. Never claim that a build/test passed unless its run has completed successfully.

If the named branch already exists, inspect its HEAD and history first. Do not overwrite it blindly. Continue on that branch only if it is clearly the intended, compatible feature work; otherwise create a uniquely suffixed branch and explain why.

## 3. Current project state verified on main

The following is based on direct inspection of repository files at HEAD `e76344ec2c40ff57f4ac7f1223d62efb24cc2f41`. Re-read files on the feature branch before editing because main may have advanced.

### 3.1 App and build configuration

- Native Android app using Kotlin and Jetpack Compose.
- Application ID / namespace: `com.amirmalik.tasbeeh`.
- `compileSdk = 36`, `targetSdk = 35`, `minSdk = 26`.
- Java and Kotlin target 17.
- Android Gradle Plugin 8.10.1; Kotlin 2.2.0; Compose compiler plugin 2.2.0.
- Preferences DataStore 1.1.1 is used for persisted app state.
- Media3 ExoPlayer and MediaSession are used for Quran/OpenDua recorded-recitation playback.
- Unit tests use JUnit 4.13.2.

Do not casually change this stack. Add only the smallest compatible set of dependencies needed for the feature after verifying their current release, Android/ABI support, licenses, and security posture.

### 3.2 Existing counter and session system

Relevant file:

- `app/src/main/java/com/amirmalik/tasbeeh/MainActivity.kt`
- `app/src/main/java/com/amirmalik/tasbeeh/data/TasbeehStore.kt`

MainActivity's `increment()` currently:
- Requires the selected personal Dhikr.
- Captures the current timestamp.
- Calls `store.countDhikr(...)` through the existing store-operation wrapper.
- Runs the existing tap haptic callback only when the store returns `CountOutcome.COUNTED`.

`TasbeehStore.countDhikr()` performs the authoritative counter/session update inside one DataStore `edit { }` transaction. It:
- Reads the current session and count.
- Uses the active session's stored target when continuing an active session, otherwise uses the saved target.
- Increments the count.
- Creates or updates the session.
- Marks the session completed when the new count reaches or exceeds its target.
- Persists the count and session state together.

Existing state includes:
- Count and target.
- Current Dhikr and selected custom Dhikr ID.
- Current session and its status/timeline.
- Custom Dhikr collection.
- Favorites and preferences.

Session statuses include active, paused, completed, and incomplete. Target values are bounded to 1–9999. Undo, pause/resume, reset, target changes, and session history already exist and must remain authoritative.

**Required integration:** implement one shared count-entry function in the existing app/store boundary, or reuse the existing safe increment path, so tap and voice both call the same `countDhikr()` transaction. Do not increment UI state directly, write a second voice-only count, or bypass store-operation error handling.

### 3.3 Custom Dhikr model and editor

Relevant files:

- `app/src/main/java/com/amirmalik/tasbeeh/data/TasbeehStore.kt`
- `app/src/main/java/com/amirmalik/tasbeeh/CustomDhikrEditorScreen.kt`
- `app/src/main/java/com/amirmalik/tasbeeh/MainActivity.kt`

Current `CustomDhikr` model contains:
- `id`
- `title`
- `arabic`
- `transliteration`
- `meaning`
- `createdAt`

The editor currently handles the text fields, validation, preview, discard confirmation, and save callback. It has no microphone enrollment or voice configuration yet.

Custom Dhikrs are serialized in a JSON string held by Preferences DataStore. The store uses a strict decoder when mutating the collection so a corrupt/unreadable saved collection is not silently treated as empty and overwritten. Current field bounds include title 60 characters, Arabic 800, transliteration 800, and meaning 500.

**Migration requirement:** adding optional voice fields must be backward-compatible. Old JSON objects without those fields must decode to a valid Dhikr with voice counting disabled. Do not discard or rewrite unrelated custom Dhikr data. Keep stable IDs and duplicate-ID protections. Treat voice-profile metadata separately from large binary/audio data.

### 3.4 Dhikr library

Relevant file:

- `app/src/main/java/com/amirmalik/tasbeeh/DhikrSelectionScreen.kt`

The library:
- Has pinned categories Favorites, My Dhikhrs, Quran, then OpenDua topics.
- Converts saved custom Dhikrs into selectable My Dhikhrs entries.
- Shows edit and delete controls for personal Dhikrs.
- Uses stable item IDs in the Compose list.
- Has `DhikrOptionCard` with existing favorite, recitation, read, edit, and delete actions.

The central counter is intentionally restricted to personal My Dhikhrs. Quran and OpenDua entries are browseable reference/recitation content; do not make them voice-countable or alter this restriction.

Add a **Voice count** action only for custom Dhikr cards with an enrolled voice profile. For unconfigured Dhikrs, either show a clearly labelled setup action or make setup available through Edit; do not present a misleading enabled action. Avoid interfering with the existing favorite, listen, edit, delete, or select hit targets. Ensure accessibility labels and small-screen behaviour.

### 3.5 Existing audio playback service

Relevant file:

- `app/src/main/java/com/amirmalik/tasbeeh/QuranRecitationPlaybackService.kt`

This is a dedicated Media3 `MediaSessionService` that owns the ExoPlayer and recorded Quran/OpenDua playback independently of the Activity. It supports playback checkpointing and media controls.

**Do not repurpose this service for microphone capture or speech recognition.** Recording/recognition is a different lifecycle and foreground-service use case. Implement a dedicated voice-counting service or a deliberately scoped foreground microphone service, with a clear controller/API and independent resource ownership. Keep recorded recitation playback intact.

### 3.6 Current Android permissions

The current `AndroidManifest.xml` declares:
- INTERNET
- ACCESS_NETWORK_STATE
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_MEDIA_PLAYBACK
- WAKE_LOCK
- POST_NOTIFICATIONS

It does **not** currently declare:
- RECORD_AUDIO
- FOREGROUND_SERVICE_MICROPHONE

There is no current gallery/media-read permission in the manifest. Preserve that privacy-minimal stance. Do not request photos, videos, shared audio files, contacts, location, or broad storage access for voice counting.

The app has `POST_NOTIFICATIONS` in the manifest, but the inspected MainActivity did not show a runtime notification-permission request flow. Implement runtime notification permission handling only where needed and degrade gracefully if denied.

### 3.7 Target completion feedback

MainActivity currently shows the in-app message:

`Target reached — MashaAllah`

when the persisted count reaches the effective target. There is not yet a verified target-specific local notification flow. The new feature should add a true notification only with careful deduplication and permission handling, while retaining the in-app target feedback and existing session completion behaviour.

### 3.8 Existing automated tests

Unit tests are under `app/src/test/java/com/amirmalik/tasbeeh` and `.../data`. Existing test coverage includes counter target logic, session behaviour, favorites, Dhikr selection policy, catalogue integrity, Quran text/recitation helpers, trusted recitation URLs, network reads, and related pure helpers.

Add tests alongside the relevant existing test packages. Do not weaken or remove existing tests to make the new feature pass.

---

## 4. User experience and functional requirements

### 4.1 Creating/editing a Dhikr

Add a visually integrated **Voice counting** section to `CustomDhikrEditorScreen`, in the existing dark emerald / warm-gold design.

The section should:
- Explain briefly that voice counting recognizes this Dhikr locally on the device.
- Offer **Set up voice** / **Record sample**.
- Request microphone permission only after an explicit user action and a clear explanation.
- Show recording progress and a clear Stop/Cancel action.
- Provide an audibly clear sample prompt, timer/level feedback where practical, and retry.
- Ask the user to speak the Dhikr naturally at a normal pace. Encourage a quiet environment.
- Validate that the recognition result is sufficiently consistent with the configured Dhikr text before saving the enrollment.
- Support Arabic Dhikr and transliteration; do not silently assume English-only recognition.
- Allow replacing/re-recording and deleting the profile.
- Make setup optional. Saving the Dhikr without voice setup must continue to work exactly as before.
- Show an explicit disabled/not-configured state if no valid voice profile exists.

For a reliable speaker identity profile, a single utterance may be insufficient. Evaluate several short enrollment samples (e.g. multiple repetitions) and aggregate embeddings where the chosen model supports it. Do not store raw sample audio after enrollment unless a documented technical need exists; prefer deleting temporary raw audio immediately after the profile is created.

### 4.2 Voice counting from My Dhikhrs

Each custom Dhikr card should expose a **Voice count** action only when it is eligible.

The voice-count experience should show:
- Dhikr name and Arabic text.
- Current persisted count and target.
- Remaining repetitions and progress.
- A strong listening/paused/stopped/error state.
- A live recognition preview if supported, clearly differentiated from accepted counts.
- Start, pause/resume, and stop actions.
- A fallback **Tap count** action or clear route back to the ordinary counter.
- A short explanation that voice recognition can make mistakes and accepted counts can be undone using the existing Undo action if allowed by session state.

The default behaviour must not auto-start the microphone when opening a card, app, or editor. The user must explicitly start a voice-count session. Never keep the microphone active after Stop, screen navigation that ends the session, permission revocation, process teardown, fatal recognizer error, or explicit user cancellation.

### 4.3 Recognition and count acceptance

A raw ASR transcript is not itself a count. A repetition is accepted only when the recognizer has detected a complete utterance and the matching/validation layer accepts it.

Implement a robust repetition segmenter and matching policy:
- Use VAD / endpointing to detect utterance boundaries.
- Normalize Arabic consistently (Arabic diacritics, tatweel, alef variants, punctuation, whitespace, and common orthographic variations) without making materially different Dhikr phrases equivalent.
- Normalize model output and configured Arabic/transliteration separately; do not compare unnormalized raw strings.
- Use a conservative similarity threshold validated against real samples.
- Avoid double counts from partial/interim transcript updates.
- Count at most once per completed utterance/segment.
- Require a short silence or endpoint between repeated utterances as appropriate.
- Reject partial, uncertain, empty, unrelated, overlapped, clipped, or low-confidence recognition.
- Add cooldown/debounce only as a secondary safeguard, never as the sole duplicate prevention method.
- Do not accept the same finalized result twice because of repeated recognizer callbacks.
- Do not count speech that happens before explicit voice-count start or after stop.
- Handle corrections/revisions in streaming ASR safely: do not count provisional hypotheses.
- When confidence is below threshold, show a neutral “Not counted — please repeat” status rather than silently incrementing.

Use a pure, unit-testable recognition decision policy, separate from audio capture and UI.

### 4.4 Voice identity versus phrase recognition

Do not conflate two separate capabilities:

1. **Phrase recognition:** does the user appear to have spoken the configured Dhikr?
2. **Speaker verification:** was it spoken by the enrolled speaker?

If speaker verification is included, use a distinct speaker-embedding model/API and evaluate quality on-device. Enroll the user's voice profile explicitly. Use thresholding with an unknown/reject outcome and tune false-accept / false-reject rates on representative device recordings.

Do not treat ordinary speech-to-text text similarity as proof of speaker identity. If the speaker verification model or Arabic support cannot be validated, ship the first phase as phrase matching only and be explicit in UI/implementation documentation. Do not advertise voice biometrics as implemented when it is not.

---

## 5. Recognition engine and model selection

### 5.1 Candidate engine: sherpa-onnx

sherpa-onnx is a candidate because it supports local/offline speech recognition and multiple audio / speaker-related tasks, including streaming recognition, VAD, speaker identification, and speaker verification. It has Android support and an Apache-2.0 project license. The project can perform inference locally after model assets are available. See:
- https://github.com/k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/

Do not add it blindly. The implementation branch must perform a documented technical spike before locking the dependency and model pair.

### 5.2 Security caveat — mandatory review

A GitHub Advisory Database record published 13 October 2026 reports CVE-2026-108954 affecting sherpa-onnx up to 1.13.8 in the offline transducer greedy-search decoder. The advisory describes a buffer-overflow risk involving model vocabulary metadata and notes unknown patched versions:
- https://github.com/advisories/GHSA-f5q5-vp8g-wh4x

Therefore:
- Re-check the advisory and upstream fixes at implementation time.
- Do not use an affected offline transducer decoder in production.
- Do not assume “official model files” eliminate the need for validation.
- Prefer a verified unaffected decoder/model architecture or a confirmed patched release.
- Validate model metadata against tensor dimensions before inference wherever possible.
- Only load trusted, version-pinned model assets.
- Verify downloaded assets using pinned SHA-256 hashes.
- Do not support arbitrary user-supplied model files.
- Keep model assets read-only to the app and stored in app-private storage.
- Document native library provenance, ABI contents, licenses, and update process.
- If a safe, compatible combination cannot be established, do not ship the recognizer dependency; report the blocker rather than accepting an avoidable native memory-safety risk.

### 5.3 Arabic capability gate

Do not assume that a model supports the desired Arabic dialect, Quranic/classical Arabic pronunciation, or the user's particular Dhikr merely because it supports Arabic ASR generally.

Evaluate at least:
- The exact Arabic Dhikr phrases intended for use.
- Different speakers, speed, volume, accents, and microphone qualities.
- Arabic diacritics/no diacritics and common pronunciation variants.
- Repeated utterances separated by short and long silences.
- Background noise, reverberation, TV/radio speech, and competing voices.
- Short Dhikr phrases where ASR language-model bias may hallucinate the expected text.

A small proof of concept must measure false counts and missed counts before implementation is considered production-ready.

### 5.4 Model distribution and offline behaviour

Prefer bundled or explicitly downloaded, version-pinned models with integrity verification. Decide based on APK/AAB size and model licensing.

If models are downloaded:
- Do so only through a clear user-facing setup step.
- Use bounded network reads and timeouts, as the app already does for its catalogue/network paths.
- Download to a temporary file, verify SHA-256, then atomically promote it.
- Keep the previous valid model until the new model is fully verified.
- Recover safely from interruption, low storage, and network loss.
- Do not fetch model files during an active counting session.
- Show model setup/download state and allow retry.
- Once installed, recognition must work without a network connection.

No live microphone audio, transcripts, speaker embeddings, or recognition events may be sent to any server.

---

## 6. Recommended architecture

Keep platform, recognition, persistence, and UI responsibilities separate. Suggested package structure (adapt to existing package conventions after branch inspection):

```
app/src/main/java/com/amirmalik/tasbeeh/
  voice/
    VoiceCountingController.kt
    VoiceCountingService.kt
    VoiceCountingState.kt
    VoicePermissionPolicy.kt
    AudioCapture.kt
    DhikrPhraseMatcher.kt
    ArabicTextNormalizer.kt
    RepetitionSegmenter.kt
    VoiceEnrollmentManager.kt
    VoiceProfileRepository.kt
    SpeechRecognizerEngine.kt
    SherpaOnnxSpeechRecognizerEngine.kt
    VoiceModelManager.kt
    VoiceNotificationManager.kt
```

Suggested responsibility boundaries:

- **SpeechRecognizerEngine:** interface for streaming input and finalized phrase results; allows fake recognizers in tests and alternative engines later.
- **SherpaOnnxSpeechRecognizerEngine:** concrete adapter only if the security, licensing, model and ABI evaluation passes.
- **AudioCapture:** owns AudioRecord creation, read loop, sample format/rate, buffer sizing, audio focus/interruption handling, and deterministic release.
- **RepetitionSegmenter:** converts audio/ASR events into complete candidate utterance segments; no persistence or UI.
- **ArabicTextNormalizer:** pure deterministic text normalization.
- **DhikrPhraseMatcher:** pure comparison and conservative acceptance/rejection policy.
- **VoiceEnrollmentManager:** explicit microphone enrollment workflow and profile generation.
- **VoiceProfileRepository:** persists profile metadata/embedding locally and manages deletion/replacement.
- **VoiceModelManager:** model acquisition, integrity checks, versioning, and cleanup.
- **VoiceCountingController:** coordinates the selected Dhikr, service lifecycle, recognizer, final-result decisions, and shared count callback.
- **VoiceCountingService:** owns microphone capture and recognition lifecycle if background/screen-off capture is supported.
- **VoiceNotificationManager:** target-completion notification and deduplication.

Keep this architecture as small as possible; do not create unnecessary abstractions if the implementation can preserve clean testable seams with fewer files. The key requirement is that microphone/recognition code must not be embedded in Compose UI or the existing recitation MediaSessionService.

### 6.1 One authoritative count pipeline

Design an explicit callback/event boundary such as:

`onAcceptedRepetition(dhikrId, finalizedUtteranceId, acceptedAt)`

Before calling the store:
- Confirm the voice session is active.
- Confirm the currently selected Dhikr ID still matches the session's Dhikr ID.
- Confirm the utterance has not already been accepted.
- Confirm the session is not paused or completed.
- Confirm recognition policy accepted the complete utterance.
- Apply bounded in-memory event de-duplication for callbacks.

Then invoke the same store-backed count pipeline used by the tap counter. A voice event must not count a different Dhikr if the user changes selection while the recognizer is running. Stop or invalidate voice counting before selection changes, Dhikr edit/deletion, reset, or other operations that invalidate its identity.

A single store transaction remains the source of truth. Do not calculate target completion from stale Compose state.

### 6.2 Persistence model

Keep model binaries and embeddings out of the current custom Dhikr JSON string.

Recommended metadata addition to `CustomDhikr` only if necessary:
- Optional profile ID / enrollment status.
- Profile schema version.
- Recognition engine ID/version.
- Model ID/version.
- Enrollment timestamp.
- Optional normalizer/profile configuration version.

Keep actual embedding/profile payload in a dedicated app-private repository/file or bounded DataStore record. Prefer a structured, versioned record with validation and atomic writes. Never persist raw microphone streams as a long-term default.

Define behaviour for:
- Creating a Dhikr without voice setup.
- Editing text after voice enrollment (mark profile stale and request re-enrollment when phrase/profile consistency matters).
- Replacing enrollment.
- Deleting a Dhikr (delete linked profile and temporary files).
- Corrupted profile (disable voice count, preserve Dhikr, offer re-enrollment).
- Model version changes (validate/re-enroll/migrate safely).
- App process death and reinstall (normal app-private storage is removed on uninstall; do not promise recovery across uninstall).

Keep DataStore mutations atomic and ensure custom Dhikr JSON can still be read from all previous app versions.

### 6.3 Service/lifecycle policy

A microphone service is materially different from the existing media playback service.

For continuous voice counting while the screen is off:
- Use a dedicated microphone foreground service with a persistent, truthful notification.
- Start it only following an explicit user action while the app has a visible Activity and RECORD_AUDIO is granted.
- On Android 14+ declare the `microphone` foreground-service type and `FOREGROUND_SERVICE_MICROPHONE` permission; the runtime prerequisite is RECORD_AUDIO.
- Never try to bootstrap microphone capture from an arbitrary background state or boot receiver.
- Stop promptly and release the microphone on stop/cancel, permission loss, errors, service teardown, and all terminal states.
- Maintain only the minimum state needed to show a session was interrupted. Do not silently resume microphone capture after process recreation.
- The user must explicitly restart after process death/reboot/force-stop.
- Test notification actions for Stop/Pause only if they can reliably coordinate with the controller and service.

If always-on/screen-off capture is not essential or device policy makes it unreliable, prefer foreground-only voice counting while the screen remains on. State the chosen behaviour honestly in UI. Do not use WorkManager for continuous microphone capture.

For Android 14+ the official guidance requires the microphone FGS type, `FOREGROUND_SERVICE_MICROPHONE`, and granted `RECORD_AUDIO`; starting a microphone FGS is subject to while-in-use restrictions and must occur while the app is visible except for defined exemptions:
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

Do not modify the existing playback service's type, permissions, intents, checkpoint logic or lifecycle as a side effect.

### 6.4 Permission and privacy policy

- Ask for microphone permission contextually when the user taps Set up voice or Start voice count.
- Explain clearly why the microphone is required.
- Handle denial, permanent denial, revoked permission, and settings return.
- Never request microphone permission at app startup.
- Do not add gallery/photo/video/shared-audio/contacts/location permissions.
- Use app-private storage only.
- No network transmission of voice audio, transcripts, profiles, embeddings, or speaker identity.
- No background microphone use outside an explicitly running voice-count session.
- Show a visible and persistent listening indicator while capture is active.
- Provide an obvious Stop action.
- Provide delete voice profile / clear voice data controls.
- Delete temporary raw enrollment audio after profile generation.
- Do not log audio, transcripts, embeddings, or personal voice-derived data in Logcat, crash reports, analytics, or notifications.
- Do not expose the service or internal receiver to other apps unless strictly necessary; use `android:exported="false"` for internal-only components.
- Document that Android microphone privacy indicators and system-level controls remain in effect.

---

## 7. Target-completion notification

When the existing store returns a target-reached outcome (or a reliable state transition identifies that the session has just become COMPLETED), issue one local notification for that session.

Requirements:
- Use a stable notification channel and unique notification ID.
- Use the session ID as the deduplication key.
- Notify only on the transition into completed, not on every recomposition or repeated callback.
- Never issue a second alert for the same session because both UI and service observed the event.
- Retain the in-app “Target reached — MashaAllah” feedback.
- Respect notification permission on Android 13+.
- If permission is denied, continue normal counting and in-app feedback without crashing or repeatedly prompting.
- Notification content should be respectful and minimal; do not expose the full personal Dhikr text on the lock screen by default.
- Notification completion must not be the mechanism that completes the session; the DataStore transaction already does that.
- Decide whether voice and tap completions share the same notification path (recommended: yes).

Add tests for event deduplication, repeated emission, app recreation, and permission-denied behaviour.

---

## 8. Work packages

Implement all packages on the single feature branch. Multiple commits are encouraged; do not split the task across multiple feature branches.

### WP0 — Reconnaissance and design spike

- Re-fetch latest main and inspect all relevant current code.
- Confirm actual target/CI state; do not rely on this handoff as a substitute for inspecting current source.
- Evaluate sherpa-onnx release/advisories, relevant Android artifacts, native ABIs, model licensing, Arabic model accuracy, streaming recognizer support, VAD, and speaker verification separately.
- Build a small non-production spike to measure recognition latency, memory, CPU/battery use, false acceptance, and misses on target devices.
- Record a short decision note: selected engine/model and why, or a justified no-go / alternative.
- Do not start full production integration until model safety and Arabic suitability have been demonstrated.

### WP1 — Pure recognition policy

Implement and test:
- Arabic normalization.
- Candidate text normalization.
- Phrase similarity/matching thresholds.
- Utterance identity/de-duplication.
- Repetition segmentation and endpoint acceptance rules.
- Rejection reasons and user feedback state.
- Speaker verification policy if it passes the separate quality gate.

These should be testable without Android microphone, native libraries, or Compose.

### WP2 — Local voice profile and model repository

Implement:
- Versioned profile model and serialization.
- App-private profile storage.
- Atomic write / replace / delete.
- Validation and bounded decoding.
- Profile-to-Dhikr stable ID linking.
- Model manager with trusted source, SHA-256 verification, bounded download and interrupted-download recovery if model downloads are required.
- Corrupt/stale profile recovery without data loss.

### WP3 — Microphone capture and recognition engine

Implement:
- Runtime RECORD_AUDIO request flow.
- AudioRecord lifecycle with correct sample rate, mono/stereo choice, PCM format and buffers for the selected model.
- Background worker/thread/coroutine with cancellation and bounded buffers.
- Streaming recognition with finalized endpoints only.
- VAD/noise and silence segmentation as appropriate.
- Deterministic disposal of streams, recognizer, native resources and AudioRecord.
- Audio focus/interruption and route change handling.
- Robust error mapping and recoverable states.
- Dedicated microphone foreground service if screen-off capture is part of the accepted product behaviour.

Do not block the main thread. Do not allocate large arrays per audio callback. Ensure the producer cannot create an unbounded queue if recognition falls behind.

### WP4 — Editor enrollment UI

Integrate optional setup into `CustomDhikrEditorScreen`:
- Enrollment instructions and status.
- Permission education and contextual request.
- Record / stop / retry / cancel.
- Preview recognized phrase and enrollment quality.
- Confirm save / replace / delete.
- Stale-profile state after phrase text changes.
- Preserve editor save, validation, preview, dirty-state, discard confirmation, and back handling.

### WP5 — Voice count UI in My Dhikhrs

Integrate into `DhikrSelectionScreen` and `DhikrOptionCard`:
- Voice count entry for configured personal Dhikrs.
- Setup affordance when unconfigured.
- Dedicated voice-count session screen or well-integrated UI state.
- Listening/paused/error states.
- Current count / target / remaining / progress.
- Accepted/rejected utterance feedback.
- Stop and fallback to tap counting.
- Accessibility and responsive layout.
- Preserve select, favourite, edit, delete and playback actions.

### WP6 — Shared counter and session integration

- Route accepted voice repetitions through existing `MainActivity.increment()` / shared count callback or an equivalent single store-backed function.
- Ensure tap and voice counts update the same counter and session.
- Prevent duplicate recognizer callbacks and race conditions.
- Lock the voice flow to a stable Dhikr ID and stop/invalidate it before destructive changes.
- Respect paused/completed sessions, undo, reset, target changes, and Dhikr deletion.
- Trigger haptic feedback only if intentional and consistent with existing settings; do not vibrate for provisional transcripts.
- Ensure target completion is observed exactly once.

### WP7 — Notifications and Android lifecycle

- Add microphone FGS manifest permissions/type only when the chosen design uses the service.
- Use `android:exported="false"` for internal-only voice service/receivers.
- Request notification permission at a suitable point; never block core counter functionality if denied.
- Implement target completion channel/notification and session-keyed deduplication.
- Respect Android 12+ background FGS start restrictions and Android 14+ microphone while-in-use restrictions.
- Ensure the service cannot restart microphone capture without a fresh explicit user action.
- Test app backgrounding, screen lock, screen off, process kill, force-stop, permission revocation, low memory, and service destruction.

### WP8 — Regression, performance and security testing

- Run all existing unit tests and add voice-focused tests.
- Run Android lint and debug APK assembly.
- Test real physical devices, especially current Android 14/15+ behaviour and at least one low-memory device where available.
- Measure voice model load time, warm-up time, time from completed spoken phrase to accepted count, memory, CPU, battery drain, thermal impact, and model storage size.
- Review privacy permissions, native libraries, model provenance, vulnerability advisories, and exported components.
- Verify that recorded recitation playback, Quran reader, OpenDua library, favorites, My Dhikhrs, settings, target, tap counter, undo, reset, pause/resume, session history, and persistence remain unchanged.

### WP9 — Documentation and PR

- Update README with the exact voice-count capability, permission, offline model setup, limitations, and data-retention behaviour.
- Document model source/license/hash/version and security review.
- Document whether screen-off counting is supported and any Android/device limitations.
- Open a draft PR to main with design decision, screenshots/video, test results and known limitations.
- Do not merge without owner approval.

---

## 9. Data migration and failure-resilience requirements

This app already protects persisted state against malformed collections and uses atomic DataStore updates. Voice integration must maintain those standards.

- Old custom Dhikr records must remain readable.
- Missing voice metadata means disabled/unconfigured.
- Unknown future profile versions must fail closed for voice functionality while preserving the custom Dhikr itself.
- A corrupt profile must not crash app startup or make the whole Dhikr library unavailable.
- A profile write failure must preserve the previous valid profile.
- Never clear the whole custom Dhikr collection to recover one malformed voice entry.
- Bound every stored field, file, queue, buffer, and decoded object.
- Use UUID/stable IDs and reject profile-path traversal or invalid filenames.
- Do not store temporary audio in external/public storage.
- Clean up temporary audio/model files on cancellation, exceptions and next-launch recovery.
- Never lose tap counts due to recognizer process failure.
- On microphone/ASR failure, stop voice mode and leave the ordinary counter usable.
- Process recreation should restore visible non-sensitive session metadata if useful, but must not silently resume microphone capture.

---

## 10. Required automated tests

At minimum add coverage for the following.

### Recognition policy
- Exact Arabic phrase accepted.
- Exact transliteration accepted only under the documented language/mode policy.
- Diacritics and tatweel normalization.
- Common alef/hamza/orthographic variants according to explicit rules.
- Different phrase rejected.
- Partial phrase rejected.
- Empty/low-confidence transcript rejected.
- Transcript revision/interim result not counted.
- One finalized utterance delivered multiple times counts once.
- Two distinct repetitions count twice.
- Adjacent repetitions without sufficient endpoint are not merged into a false single count or duplicated.
- Background speech and noise do not produce accepted counts under the acceptance policy.
- Arabic punctuation/spacing and Unicode edge cases.

### Enrollment/profile
- Missing optional profile fields decode old custom Dhikr.
- Profile create, replace, delete.
- Wrong Dhikr ID cannot use the profile.
- Malformed/oversized profile rejected safely.
- Unknown profile version disables voice without losing Dhikr.
- Stale profile after phrase edit requires re-enrollment.
- Failed atomic write leaves previous profile usable.
- Temporary audio deleted after successful/cancelled/failed enrollment.
- Model hash mismatch rejects model.
- Interrupted model download can recover without replacing the previously valid model.

### Counter integration
- Tap still counts exactly as before.
- Voice accepted repetition increments same count exactly once.
- Voice and tap mixed in one active session produce one continuous count.
- Voice count respects active session target.
- Target completion occurs exactly once regardless of input method.
- Paused session cannot be incremented by voice.
- Completed session cannot be incremented by stale recognition callback.
- Undo behaviour remains the existing policy.
- Reset and target changes invalidate or stop active voice state correctly.
- Dhikr selection change stops or invalidates current voice session.
- Delete/edit of configured Dhikr cannot leave an orphan active recognizer/profile.
- Store error is surfaced without losing existing count/session state.

### Android permission/service/UI
- Microphone not requested on app startup.
- Permission denial leaves tap counter usable.
- Permanent denial gives a settings route without crash.
- Start is rejected safely when permission is not granted.
- Foreground service starts only from an allowed visible user flow.
- Notification channel and notification are correct.
- Stop always releases AudioRecord and native streams.
- Process death does not silently restart microphone capture.
- Screen-off behaviour matches the documented design.
- Notification permission denial does not break the in-app completion feedback.
- Target notification is emitted once per completed session.
- My Dhikhrs cards retain select/favorite/edit/delete actions.
- Quran/OpenDua never show Voice count.
- Editor discard/back/save workflows remain intact.

### Existing full regression
- Run every existing test file; no test deletion or weakening.
- Run `:app:lint`.
- Run `:app:assembleDebug`.
- Run available Android instrumentation/device tests.
- Attach CI result and APK artifact link to the PR if CI produces it.

---

## 11. Manual acceptance checklist

On a physical Android device:

1. Install an existing build and confirm all normal functionality works before the feature test.
2. Create a personal Dhikr without voice enrollment; save it; tap-count it. This path must be unchanged.
3. Create a personal Dhikr with Arabic text; enroll voice; close and reopen the editor; confirm enrollment status persists.
4. Deny microphone permission; confirm no crash and tap counting still works.
5. Grant microphone permission; start Voice count explicitly.
6. Speak the configured Dhikr once; confirm exactly one accepted count.
7. Repeat at natural speed; confirm one increment per completed repetition.
8. Speak a different phrase; confirm no increment.
9. Speak only part of the phrase; confirm no increment.
10. Speak the phrase with natural pronunciation variation; record whether it is accepted correctly.
11. Repeat while TV/radio/background voices are present; verify false counts are acceptably low.
12. Mix tap and voice counts in one session; verify one continuous persisted count.
13. Pause session; speak; verify count does not change.
14. Resume; verify voice counting works.
15. Reach target; verify in-app completion and one notification when permitted.
16. Deny notifications; verify in-app completion still works.
17. Undo where allowed; verify counter/session consistency.
18. Reset during voice mode; confirm microphone stops and no stale event increments afterward.
19. Switch Dhikr while listening; confirm previous session is stopped/inactivated.
20. Delete an enrolled Dhikr; confirm profile cleanup and no active listener.
21. Edit the phrase; confirm stale enrollment is handled safely.
22. Lock screen / turn screen off and test the declared capture policy.
23. Force-stop/kill the app; confirm microphone does not resume automatically after relaunch.
24. Toggle microphone permission in system settings while app is installed; verify recovery.
25. Use Quran playback and OpenDua playback before, during and after voice use; ensure their MediaSession controls remain unaffected.
26. Restart app and device; confirm custom Dhikrs, counts, sessions and valid voice profiles persist as expected.

Do not report “accurate” or “production-ready” based on only a few clean-environment tests. Record actual false-accept and false-reject observations.

---

## 12. Non-goals and prohibited shortcuts

- Do not replace or remove tap counting.
- Do not create a second counter or separate voice session history.
- Do not make Quran/OpenDua references countable.
- Do not auto-start microphone listening.
- Do not continuously record audio when voice counting is stopped.
- Do not upload audio, transcripts, speaker embeddings or voice profiles.
- Do not request gallery, contacts, location or broad storage permissions.
- Do not repurpose `QuranRecitationPlaybackService`.
- Do not use Android's generic speech recognizer if it causes audio to leave the device or requires network processing; local-only behaviour is a core product requirement.
- Do not assume phrase text similarity verifies the speaker.
- Do not ship sherpa-onnx with the affected offline transducer decoder/version until the advisory is resolved and reviewed.
- Do not load user-supplied model files.
- Do not log microphone content or embeddings.
- Do not implement a background microphone service without user-visible ongoing notification and Android-compliant service startup.
- Do not claim screen-off capture is guaranteed across all Android vendors or force-stop states.
- Do not change unrelated screens, branding, catalogues, playback, app icon, build stack, or unrelated architecture.

---

## 13. Definition of done

The feature is complete only when:

- All implementation is on the required dedicated feature branch, not main.
- Engine/model choice has a documented Arabic accuracy, license, ABI and security decision.
- Voice enrollment works and persists safely.
- Voice count works from My Dhikhrs for configured entries.
- Accepted voice repetitions go through the existing store transaction.
- Tap and voice can coexist in the same session.
- Target completion and notifications are deduplicated and correct.
- Permission and lifecycle handling pass real-device testing.
- Voice/audio/profile data remain local and app-private.
- All old custom Dhikr records and all existing features remain compatible.
- All existing tests pass, all new tests pass, lint passes, and debug APK assembly passes in a completed CI run.
- README and PR documentation explain model/licensing/security/data handling and limitations.
- A draft or ready-for-review PR targeting main is opened.
- No merge to main occurs without explicit owner approval.

## 14. Final implementation report

The implementer must report:

- Feature branch name and base SHA.
- All commit SHAs and concise change summaries.
- PR link and status.
- Exact recognition engine/library version and native ABIs included.
- Exact ASR and speaker-verification model names, versions, source URLs, licenses, and SHA-256 hashes.
- Android permissions added and why.
- Whether recognition works offline after model setup.
- Whether screen-off voice counting is supported and the exact constraints.
- Voice profile/raw audio retention and deletion policy.
- Unit test, lint, build, and device test outcomes—clearly separating passed, failed, skipped, and not run.
- Measured false-accept / false-reject results and performance observations.
- Known limitations or blockers.

**Do not merge the feature branch into main without explicit approval.**
