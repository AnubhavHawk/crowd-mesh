# Sequence Diagrams

Four flows that together cover the whole app. (The gossip wire handshake
itself is diagrammed separately in `docs/PROTOCOL.md` — this file shows how
each flow drives it, not the packet-level detail.)

## 1. Tapping "Update My Presence"

```mermaid
sequenceDiagram
    actor User
    participant HomeScreen
    participant HomeViewModel
    participant UpdatePresenceUseCase
    participant LocationProvider as FusedLocationProvider
    participant GeohashEncoder
    participant PresenceRepository
    participant Room as Room (presence_records)
    participant MeshController as MeshEngine

    User->>HomeScreen: tap button
    HomeScreen->>HomeViewModel: onUpdateTapped()
    HomeViewModel->>UpdatePresenceUseCase: invoke()
    UpdatePresenceUseCase->>LocationProvider: getCurrentFix()
    LocationProvider-->>UpdatePresenceUseCase: LocationFix(lat, lon, ...)
    UpdatePresenceUseCase->>GeohashEncoder: encode(lat, lon, precision=8)
    GeohashEncoder-->>UpdatePresenceUseCase: geohash
    UpdatePresenceUseCase->>PresenceRepository: upsertOwnRecord(userId, geohash, timestamp)
    PresenceRepository->>Room: REPLACE INTO presence_records
    Room-->>PresenceRepository: ok
    PresenceRepository-->>UpdatePresenceUseCase: PresenceRecord(version = prev+1)
    UpdatePresenceUseCase->>MeshController: notifyLocalRecordChanged()
    MeshController-->>MeshController: re-advertise + switch to ACTIVE duty cycle
    UpdatePresenceUseCase-->>HomeViewModel: AppResult.Success(record)
    HomeViewModel-->>HomeScreen: uiState updates (via Room Flow)
```

If `LocationProvider.getCurrentFix()` returns `null` (no permission, no
signal, timeout), `UpdatePresenceUseCase` returns
`AppResult.Failure(LOCATION_UNAVAILABLE)` and nothing is written — the UI
surfaces a Snackbar and the button becomes tappable again immediately.

## 2. Two peers meet: discovery through gossip merge

```mermaid
sequenceDiagram
    participant SchedA as AdaptiveScanScheduler (A)
    participant TransA as TransportManager (A)
    participant ConnA as ConnectionManager (A)
    participant SyncA as SyncManager (A)
    participant TransB as TransportManager (B)
    participant SyncB as SyncManager (B)
    participant RepoB as PresenceRepository (B)

    SchedA->>TransA: BLE scan burst (ScanCadence.ACTIVE or FOREGROUND_IDLE)
    TransA-->>ConnA: DiscoveredPeer(B's address)
    ConnA->>ConnA: PeerManager.shouldSync(peer)? (resync cooldown check)
    ConnA->>TransA: openConnection(peer)
    TransA->>TransB: BLE GATT connect (A = central, B = peripheral)
    TransB-->>TransA: TransportConnection established
    Note over TransB: B's incomingConnections emits the new connection
    par A's side
        ConnA->>SyncA: syncOverConnection(connection)
    and B's side
        TransB-->>SyncB: syncOverConnection(connection)
    end
    Note over SyncA,SyncB: HELLO / DIGEST / RECORD_REQUEST / RECORD_BATCH<br/>handshake — see docs/PROTOCOL.md
    SyncB->>RepoB: mergeRemoteRecord(record learned from A)
    RepoB-->>RepoB: LWW check, REPLACE if newer
    Note over RepoB: Room Flow re-emits -> B's heatmap recomputes automatically
```

## 3. Background sync via WorkManager

```mermaid
sequenceDiagram
    participant WM as WorkManager
    participant Worker as PeriodicMeshSyncWorker
    participant Discovery as PeerDiscoveryManager
    participant ConnMgr as ConnectionManager
    participant Sync as SyncManager

    WM->>Worker: doWork() (every >=15 min, BatteryNotLow)
    Worker->>Discovery: startAdvertising(identityPayload)
    Worker->>Discovery: discover(MeshDutyCycle.BACKGROUND_BURST)
    loop for each peer found within ~25s budget
        Discovery-->>Worker: DiscoveredPeer
        Worker->>ConnMgr: connectIfWorthwhile(peer) { sync }
        ConnMgr->>Sync: syncOverConnection(connection)
    end
    Worker->>Discovery: stopAdvertising()
    Worker-->>WM: Result.success()
```

`ExpiredRecordCleanupWorker` runs independently, roughly hourly, and simply
calls `PruneExpiredRecordsUseCase` — no radios involved.

## 4. Heatmap render pipeline

```mermaid
sequenceDiagram
    participant Room as Room (presence_records)
    participant Repo as PresenceRepositoryImpl
    participant UseCase as ObserveHeatmapUseCase
    participant Agg as HeatmapAggregator
    participant VM as MapViewModel
    participant Builder as HeatmapLayerBuilder
    participant Map as MapLibre (GeoJsonSource)

    Room-->>Repo: Flow<List<PresenceRecordEntity>> (on any write, and on a 30s decay tick)
    Repo-->>UseCase: Flow<List<PresenceRecord>> (TTL-filtered)
    UseCase->>Agg: aggregate(records, now, cellPrecision)
    Agg-->>UseCase: List<HeatmapCell> (count, confidence, DensityLevel)
    UseCase-->>VM: heatmapCells StateFlow
    VM-->>Builder: buildGeoJson(cells)
    Builder-->>Map: source.setGeoJson(...)
    Map-->>Map: FillLayer re-renders (color by level, opacity by confidence)
```

Nothing in this pipeline ever leaves the device, and no image or rendered
heatmap is ever part of the gossip protocol — only the underlying
`PresenceRecord`s are (see step 2 above).
