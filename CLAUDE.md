# CLAUDE.md

Guidance for Claude Code (or any future agent) working in this repository.

## What this is

CrowdMesh: an Android-only, fully offline crowd-density mesh app. No
backend, no cloud, no internet dependency. Devices exchange presence data
peer-to-peer over BLE (primary) and Wi-Fi Aware/Wi-Fi Direct (secondary,
thinner) using a gossip protocol, and each device renders its own local
heatmap. See `README.md` for the feature overview and
`docs/ARCHITECTURE.md` / `docs/PROTOCOL.md` / `docs/SEQUENCE_DIAGRAMS.md`
for the design.

**Status**: generated prototype, not yet built/run in the environment that
created it (no Android SDK/Gradle/emulator was available there). Treat any
"it works" claim as unverified until `./gradlew testDebugUnitTest` and an
actual device run have been done.

## Project layout

Single `:app` Gradle module, layered by package (not by Gradle module —
see the trade-off note in `docs/ARCHITECTURE.md`):

```
app/src/main/kotlin/com/crowdmesh/
  di/              Hilt modules (Database, Repository bindings, Dispatchers, WorkManager)
  domain/          models, geohash, heatmap aggregation, use cases, repository "ports" (interfaces)
  data/            Room entities/DAOs, repository implementations, wire (de)serialization, location provider
  mesh/            the mesh engine: transport/, ble/, discovery/, peer/, sync/, protocol/, MeshEngine.kt facade
  identity/        on-device random UUID (DataStore-backed)
  work/            WorkManager jobs (periodic sync burst, expired-record cleanup)
  presentation/    Compose UI (home/, map/, permissions/, common/, navigation/)
  util/            framework-free helpers (TimeProvider, CoroutineDispatchers, Logger, AppResult)
```

Dependency direction is strictly inward: `presentation -> domain <- data`,
and `mesh -> domain` (mesh never depends on presentation; data never
depends on mesh). `domain` has zero Android framework dependencies except
where a port (e.g. `LocationProvider`, `IdentityProvider`, `MeshController`)
is implemented elsewhere.

## Conventions to follow when editing

- **New domain concepts go through a port + impl split.** If you need
  something from Android framework/Play Services/etc. inside a use case,
  add an interface under `domain/repository/` and implement it in `data/`
  or `mesh/`, bound in `di/RepositoryModule.kt`. Don't let `domain/*` import
  `android.*` directly (small, framework-free `util/` classes are the
  accepted exception — see existing ones before adding more).
- **Mesh stays UI-independent.** `mesh/*` must never import from
  `presentation/*`. It talks to `domain` only through the `MeshController`
  port (implemented by `MeshEngine`).
- **One row per user, no history.** `PresenceRecordEntity`/`PresenceRecord`
  is always a REPLACE on write. Don't add history/audit tables.
- **Gossip protocol changes must stay symmetric.** `SyncManager.syncOverConnection`
  runs identically on both ends of a connection (no fixed client/server
  role at the protocol level, even though BLE's radio role is asymmetric).
  If you add a new packet type, update `mesh/protocol/PacketType.kt`,
  `data/serialization/WireModels.kt` + `PacketCodec`, and
  `docs/PROTOCOL.md` together.
- **Battery discipline.** Never add continuous scanning or a foreground
  service. New background work goes through `mesh/ble/AdaptiveScanScheduler`'s
  duty-cycle model or a bounded `WorkManager` job — see `README.md`'s
  Battery section before changing scan/advertise behavior.
- **BLE frame delivery uses `Channel`, not `SharedFlow`, per-connection.**
  This was a deliberate fix for a replay=0 late-subscriber race (see the
  comments in `BleGattClientManager`/`BleGattServerManager`). Don't
  "simplify" per-connection frame delivery back to a `SharedFlow` without
  re-reading that reasoning.
- **Version catalog, not inline versions.** All dependency versions live in
  `gradle/libs.versions.toml`. If Android Studio suggests a version bump,
  update the catalog, not a `build.gradle.kts` literal.
- **No analytics, no network calls, no telemetry.** This is a hard
  constraint of the app's privacy model, not just current scope — don't
  add any, even for debugging/crash-reporting purposes.

## Testing

- Pure-JVM unit tests live in `app/src/test/kotlin`. Prefer fakes (see
  `app/src/test/kotlin/com/crowdmesh/fakes/`) over mocking frameworks for
  repositories/providers — most of the domain/mesh logic is already
  designed to be exercised against in-memory fakes rather than Room or real
  radios (`SyncManagerTest` runs two `SyncManager`s against an in-memory
  `FakeTransportConnection` pair to verify actual gossip propagation).
- Anything that needs real Room/SQLite or real BLE/Wi-Fi APIs goes in
  `app/src/androidTest/kotlin` and requires a device/emulator — these
  cannot be run or verified in a plain shell environment.
- Run `./gradlew testDebugUnitTest` after any change to `domain/`, `data/`,
  or `mesh/sync` logic. See `README.md` for the full build/run command
  list.

## Known simplifications (don't "fix" these without reading why first)

See `docs/ARCHITECTURE.md`'s "Known simplifications" section: cross-transport
peer identity isn't correlated, Wi-Fi Aware uses message-based exchange
instead of a data-path socket, Wi-Fi Direct discovery isn't service-filtered.
These are intentional scope decisions for a "BLE is primary, Wi-Fi
transports are thinner" prototype, not oversights.

## Offline map

`app/src/main/assets/map/offline_style.json` is a placeholder (flat
background, no tile source) — real basemap tile packages are too large to
generate as source text. See `app/src/main/assets/map/README.md` before
changing map rendering.
