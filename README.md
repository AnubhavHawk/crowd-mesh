# CrowdMesh

An Android-only, fully offline crowd-density mesh app. There is no backend,
no cloud, and no internet dependency anywhere in this codebase — every
Android device is simultaneously a client and a peer. Press one button to
share your presence; nearby phones exchange presence data automatically over
Bluetooth LE (and opportunistically Wi-Fi Aware / Wi-Fi Direct) using a
gossip protocol, and everyone renders their own local heatmap from whatever
they've learned so far.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the component
breakdown, [`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the wire/gossip
protocol spec, and [`docs/SEQUENCE_DIAGRAMS.md`](docs/SEQUENCE_DIAGRAMS.md)
for the main runtime flows.

## Status

This is a generated prototype. It was written and reviewed without access to
the Android SDK, Gradle, or a device/emulator in the generating environment
— so while every file is written to compile against real, current Android
APIs to the best of available knowledge, **it has not been built or run**.
Before relying on it:

1. Open the project in a recent Android Studio (Ladybug+). Let Gradle sync;
   it will fetch `gradle-wrapper.jar` and resolve dependencies. It's normal
   for Android Studio to suggest bumping AGP/Kotlin/Compose BOM versions —
   accept those suggestions rather than fighting the pinned versions in
   `gradle/libs.versions.toml`, which were chosen for internal consistency,
   not because they're necessarily the newest available.
2. Run `./gradlew testDebugUnitTest` — this exercises geohashing, heatmap
   aggregation, conflict resolution, the wire codec, message dedup/TTL/hop
   rules, the repository, a two-peer gossip sync integration test, and both
   ViewModels, all without needing a device.
3. Run `./gradlew connectedAndroidTest` on a device/emulator for the one
   instrumented Room test (`PresenceRecordDaoTest`).
4. BLE/Wi-Fi Aware/Wi-Fi Direct behavior can only really be verified on two
   or more physical devices — there is no way to simulate real radios in an
   emulator. Budget time for that pass separately.

## Build & Run

### Prerequisites

- Android Studio (Ladybug or newer) **or** a standalone JDK 17 + Android
  SDK with `ANDROID_HOME`/`ANDROID_SDK_ROOT` set, if you want to use the
  command line only.
- First run needs network access once, to let Gradle fetch
  `gradle-wrapper.jar` and resolve dependencies (Compose BOM, Hilt, Room,
  MapLibre, etc.) — after that, builds work offline.
- On Windows, use `gradlew.bat` instead of `./gradlew` in every command
  below.

### One-time setup

Open the project root in Android Studio and let it sync — this is the
easiest way to materialize `gradle-wrapper.jar` correctly and pick up any
newer stable AGP/Kotlin/Compose versions it suggests. From the command line
instead, running any `./gradlew` command will bootstrap the wrapper on
first use as long as a system Gradle or Android Studio's bundled Gradle is
reachable.

### Build a debug APK

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

### Build a release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`. This is
**unsigned** — `assembleRelease` also runs R8 shrinking/minification (see
`app/build.gradle.kts`). To produce an installable signed release APK, add
signing config (a keystore + `signingConfigs` block) to
`app/build.gradle.kts`, then either:

```bash
./gradlew assembleRelease   # if a signingConfig is wired into the release buildType
```

or sign the unsigned APK manually with `apksigner` from the Android SDK
build-tools.

### Build an Android App Bundle (for Play Store distribution)

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

### Install straight to a connected device/emulator

```bash
./gradlew installDebug
```

(Requires a device authorized over `adb` — check with `adb devices` first.)

### Run tests

```bash
./gradlew testDebugUnitTest        # JVM unit tests, no device needed
./gradlew connectedAndroidTest      # instrumented tests, needs a device/emulator
./gradlew check                     # both plus lint
```

### Clean

```bash
./gradlew clean
```

## What's actually implemented

- **BLE** is the fully-implemented primary transport: real GATT
  advertiser/scanner/server/client code with MTU-aware chunking and
  reassembly (`mesh/ble/`).
- **Wi-Fi Aware** and **Wi-Fi Direct** are real (not stubbed) but
  deliberately thinner secondary transports — see the class doc comments on
  `WifiAwareTransport`/`WifiDirectTransport` for exactly what's simplified
  and why.
- The **gossip protocol** (digest exchange → need-based record request →
  batch response, LWW conflict resolution, TTL expiry, hop-count bounding)
  is transport-agnostic and lives in `mesh/sync/` + `mesh/protocol/`.
- The **heatmap** is 100% locally computed from `PresenceRecord`s already on
  the device — nothing about the heatmap itself, or any image, is ever
  synchronized.
- The **offline map** ships with a placeholder no-network MapLibre style
  (flat background). See
  [`app/src/main/assets/map/README.md`](app/src/main/assets/map/README.md)
  for how to drop in a real bundled basemap for your region — actual map
  tile packages are far too large to generate as source text.

## Permissions

CrowdMesh requests, at runtime, whatever subset of these its running API
level actually needs (see `presentation/permissions/RequiredPermissions.kt`):
fine/coarse location (to read a GPS fix), `BLUETOOTH_SCAN` /
`BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` (API 31+), and
`NEARBY_WIFI_DEVICES` (API 33+). Nothing is requested until the user opens
the app; the mesh engine itself doesn't start until permissions are
confirmed granted (`HomeViewModel.onPermissionsGranted`).

## Privacy

- No account, no login, no analytics, no crash reporting, no network calls
  of any kind.
- A random UUID is generated once on first launch (`identity/DeviceIdentityProvider.kt`,
  backed by DataStore) and never leaves the device except peer-to-peer as
  the `userId` on your own presence record.
- `PresenceRecord` never keeps history: every update replaces the previous
  one, both locally and everywhere it's already propagated to (via version
  numbers, not by rewriting the past).

## Battery

There is no continuous scanning and no foreground service. Radios only wake
for: (1) a short burst right when the app opens or you tap "Update", (2)
gentle duty-cycled bursts while the app stays open in the foreground, and
(3) one bounded ~25s burst per `PeriodicMeshSyncWorker` execution
(WorkManager's 15-minute minimum period, gated on `BatteryNotLow`). See
`mesh/ble/AdaptiveScanScheduler.kt`.

## Scalability notes

Targeting up to ~70,000 users was never going to mean 70,000 simultaneous
BLE connections — it means the total addressable population, with any two
devices only ever talking to whoever is physically nearby at a given
moment. The practical levers that make that tractable here:

- Digest exchanges are capped (`GossipPolicy.DIGEST_ENTRY_LIMIT`, default
  500 most-recently-updated records) rather than diffing an unbounded set
  every encounter.
- Records expire (`GossipPolicy.RECORD_TTL_MILLIS`, default 30 minutes), so
  the total live working set at any point is bounded by how many people are
  *currently* out and about, not by cumulative history.
- Digest-based diffing means only records a peer is actually missing or has
  an older version of are ever sent — no blind flooding.
- Propagation is gradual and eventual, by design: a record spreads as
  people carrying it physically move near new people, the same way real
  crowd information would.

## Module layout

Single `:app` Gradle module, layered by package rather than by Gradle
module (see `docs/ARCHITECTURE.md` for the full rationale and diagram):

```
app/src/main/kotlin/com/crowdmesh/
  di/              Hilt modules
  domain/          models, geohash, heatmap aggregation, use cases, repository ports
  data/            Room entities/DAOs, repository implementations, wire (de)serialization
  mesh/            the mesh engine: transport, ble, discovery, peer, sync, protocol
  identity/        on-device random UUID
  work/            WorkManager jobs
  presentation/    Compose UI, ViewModels, navigation
  util/            small framework-free helpers
```
