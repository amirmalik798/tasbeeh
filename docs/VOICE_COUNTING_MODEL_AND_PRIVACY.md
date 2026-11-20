# Serene Dhikr — Voice Counting Model, Security and Privacy

## Recognition engine

- Android runtime: sherpa-onnx **v2.0.2**, using its offline **Whisper** recognizer API.
- Model: sherpa-onnx Whisper Tiny multilingual int8, language hint `ar`, task `transcribe`.
- Upstream model: https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny
- Immutable model revision: `65176e2deb88badc814a94058666cadccc29b61c`
- sherpa-onnx license: Apache-2.0.
- Whisper model lineage/license: OpenAI Whisper, MIT. Retain the corresponding notices when redistributing.

The selected decoder is Whisper. The implementation does **not** instantiate sherpa-onnx's offline transducer greedy-search decoder. This is a scoped implementation choice, not a statement that every native code path in the library is free of vulnerabilities. Re-check upstream advisories and release changes before every dependency update.

## Model assets and integrity

The app downloads only these files, on explicit voice setup, over HTTPS into its private `noBackupFilesDir/voice-models/` directory (excluded from Android app backup):

| File | Expected size | SHA-256 |
|---|---:|---|
| `tiny-encoder.int8.onnx` | 12,937,772 bytes | `d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434` |
| `tiny-decoder.int8.onnx` | 89,855,401 bytes | `d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925` |
| `tiny-tokens.txt` | 816,730 bytes | `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` |

The two ONNX model files and vocabulary are pinned to the upstream conversion revision above. The vocabulary checksum is cross-checked against the upstream-pinned Whisper Tiny file manifest. The installer bounds file sizes, validates SHA-256 for all three assets, validates the vocabulary structure, rejects redirects outside the Hugging Face model hosting domains, downloads to temporary files, and promotes verified files atomically. It does not accept user-supplied model files. If verification fails, voice setup fails closed and ordinary tap counting remains available.

Model source references:
- https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/main
- https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/commit/65176e2deb88badc814a94058666cadccc29b61c
- https://huggingface.co/newbeeforever/whisper-tiny-sherpa-onnx (checksum manifest referencing the pinned upstream revision)
- https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.2

## Android native packaging

The sherpa-onnx v2.0.2 dependency was inspected in a successful CI debug APK artifact (build commit `3f046b8e002a3d24eb885f02847aab8e7ef2c061`; the sherpa dependency declaration was unchanged by the later persistence/UI edits). The universal APK contained these native ABIs:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

Each ABI contained the sherpa/ONNX native runtime libraries: `libonnxruntime.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, and `libsherpa-onnx-jni.so`. The APK also contained Compose's `libandroidx.graphics.path.so`. No ABI filters are configured. The inspected universal debug APK was 153,204,181 bytes; this includes all four ABI variants and should not be treated as the size of a Play-delivered ABI-split install.

## CVE and decoder scope

A sherpa-onnx advisory describes an offline transducer greedy-search decoder vulnerability (CVE-2026-108954 / GHSA-f5q5-vp8g-wh4x). The implementation deliberately uses the Whisper recognizer configuration instead of that transducer decoder path. This reduces exposure to that specific decoder issue, but does not replace dependency review, model validation, native-code testing, or future advisory monitoring.

Advisory: https://github.com/advisories/GHSA-f5q5-vp8g-wh4x

Before upgrading sherpa-onnx or changing model/decoder families, repeat the security review. Do not silently switch to an offline transducer decoder.

## Audio and profile data handling

- Microphone access is requested only after an explicit user action.
- Audio capture uses Android `AudioRecord` with the `VOICE_RECOGNITION` source, 16 kHz mono PCM.
- Enrollment audio is kept in memory only. The temporary sample buffer is zeroed after transcription and is never saved as a file.
- Recognition audio is segmented into bounded utterances (maximum eight seconds per candidate) with a bounded pending-segment queue.
- The recognized phrase is stored as a small, versioned JSON profile under app-private `noBackupFilesDir/voice-profiles/`, keyed by a random UUID and bound to a SHA-256 hash of the Dhikr ID. The file also stores the phrase hash and enrollment timestamp. It is **not** a speaker embedding or biometric identity profile.
- If the configured Dhikr text changes, the existing sample becomes stale and voice count is disabled until the user re-enrols.
- Live audio, transcripts, and profile text are not uploaded to any server or written to logs. The transcript is not stored in Preferences DataStore, not passed through the foreground-service Intent, and not included in Android app backup.
- Model files and recognized phrase profiles stay in app-private no-backup storage and are downloaded/re-enrolled after a device restore; the existing DataStore stores only the profile UUID, Dhikr-text hash and enrollment timestamp with the user's custom Dhikr.
- No gallery, photos, videos, shared audio, contacts, location, or broad storage permission is added.
- The dedicated microphone foreground service is non-exported, starts from an explicit visible-UI action, and uses `START_NOT_STICKY`. It does not resume microphone capture automatically after process death.
- The service is designed to remain active when the Activity is backgrounded or the screen is off after a valid foreground start. Screen-off behaviour has **not** been verified on a physical device and may vary with Android/OEM battery policies. Swiping the app task away calls `onTaskRemoved` and stops capture.
- The existing Media3 Quran/OpenDua playback service is not used for microphone capture.

## Recognition limitations

This version validates the spoken phrase against the enrolled transcript/Arabic Dhikr text. It does **not** verify that the speaker is the person who recorded the sample. Another person or background recording that produces the same recognized phrase may be accepted. Phrase similarity is conservative and short phrases require an exact normalized match, but actual false-accept and false-reject rates must be measured on physical devices with different speakers, accents, noise, microphones, and Dhikr phrases before describing the feature as production-accurate.

## Target announcement

The existing store transaction returns a distinct `TARGET_REACHED` outcome together with the completed session UUID. The original `countDhikr()` API remains available and returns the same outcome enum; tap and voice paths use the result-bearing wrapper over the same transaction. Both paths call the shared Android TextToSpeech feedback with the session UUID. The spoken phrase is exactly **TARGET REACHED**.

A bounded set of the last 64 announced session UUIDs is persisted in private app preferences. Duplicate events for an already announced session are ignored across Activity/process recreation. The notification ID is deterministically derived from that UUID, providing a second deduplication layer. On Android 13+, the app requests notification permission once, on the user's first explicit Voice count start. Denial does not block voice counting, the spoken alert, or the in-app completion banner; the app does not repeatedly prompt after denial. Notifications are silent and contain no Dhikr text.
