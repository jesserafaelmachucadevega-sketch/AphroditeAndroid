# Aphrodite

A native Android workspace for chat, agents, research, documents, email, notes, calendar and
local models — built entirely in Kotlin and Jetpack Compose.

Aphrodite is a native Android port of [Odysseus](https://github.com/odysseus-dev/odysseus),
reimplemented as a **standalone on-device application**. There is no server component: the
database is local, mail and calendar talk to your providers directly, web search calls the
providers you configure, and models run either on your device, on a machine on your LAN, or in
the cloud.

## Install

Grab the latest APK from the [releases page](../../releases/latest) and sideload it. Builds are
debug-signed, so Android will warn about an unknown source — expected for a development build.

- Package: `com.aphrodite.debug`
- Minimum: Android 8.0 (API 26)
- Versioning: previews step up by a hundredth (0.01, 0.02, ...), the finished app is 1.0, and
  after that normal semantic versioning applies

## Status

Under active construction. What exists and what does not is described in each release's notes;
the short version is that the foundation is complete — the build, all 32 modules, the design
system, the adaptive shell, the local database, credentials and the provider layer — and the
feature screens are being filled in on top of it.

## Requirements

- JDK 17 or newer (JDK 21 recommended)
- Android SDK with platform 37 and build-tools 37.0.0
- Gradle 9.1+ (the wrapper pins 9.8.1 and downloads it)
- Android 8.0 (API 26) or newer on the device

## Building

```bash
# Point Gradle at your SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK
./gradlew test                        # unit tests
./gradlew lint                        # Android lint
./gradlew :app:connectedAndroidTest   # instrumented tests (device/emulator attached)
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Layout

The app adapts to the device it is running on. **Settings → Appearance → Layout** controls it:

| Option | Behaviour |
| --- | --- |
| Automatic | Detect the current form factor and switch when it changes |
| Phone | Bottom navigation, single pane, full-screen detail views |
| Tablet | Navigation rail, two panes side by side (list on the left, detail on the right) |
| Foldable | Rail when open, bottom bar when folded, content avoids the hinge |
| Desktop | Three resizable panes, keyboard shortcuts enabled |

Automatic is live: rotating, unfolding or resizing the window re-lays-out immediately.

## Architecture

```
app/                  Application, activity, navigation host, DI root
core/
  common/             Result and error types, dispatchers, time helpers
  designsystem/       Colour tokens, themes, typography, density, components, backgrounds
  adaptive/           Device profiles, window size classes, pane scaffolds
  database/           Room entities, DAOs, migrations, full-text search
  datastore/          Preferences and secure storage
  security/           Keystore, vault encryption, TOTP, biometric unlock
  network/            HTTP client, streaming reader, retry and TLS handling
  llm/                Provider adapters, model registry, capability data, streaming
  markdown/           Markdown rendering, syntax highlighting, math and diagrams
  media/              Image pipeline, codecs, import and export
  speech/             Speech recognition and synthesis
  notifications/      Channels, scheduling, reminders
feature/
  auth  chat  compare  research  notes  tasks  calendar  contacts
  email  documents  gallery  memory  skills  search  cookbook
  tools  settings  admin
inference/llama/      On-device inference engine
build-logic/          Gradle convention plugins
```

Each `core` module owns one concern; each `feature` module owns one product surface and depends
only on the core modules it needs.

## Configuration

Everything is configured inside the app — provider API keys, mail accounts, CalDAV and CardDAV
servers, search provider keys, and model endpoints. Credentials are stored with the Android
Keystore and never leave the device.

## License

AGPL-3.0-or-later. See [LICENSE](LICENSE) and [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md).
