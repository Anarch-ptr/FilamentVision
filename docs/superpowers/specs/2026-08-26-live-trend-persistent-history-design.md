# FilamentVision Live Trend and Persistent History Design

## Objective

Add a dedicated live Trend product surface and persist every official monitoring-session measurement, alarm transition, summary, and historical curve in Room. The implementation must preserve one 8 Hz producer, bounded live UI memory, background recording through an Android foreground service, and stable navigation.

This phase does not add real Camera, CameraX, Bluetooth, Wi-Fi, OpenCV, WakeLock, WorkManager, Boot Receiver, CSV export, cloud sync, or complex active-session recovery.

## Non-negotiable lifecycle semantics

- Application scope guarantees exactly one `MonitoringRuntime` per application process. It does not survive process death.
- `FilamentVisionApplication` is a small composition root. It constructs and holds Room, repositories, the fake hardware boundary, and one runtime; it contains no monitoring behavior.
- `MonitoringForegroundService` is a lifecycle and notification host, not the monitoring engine.
- `MonitoringRuntime` is the single source of monitoring truth and the only owner of `FakeVisionSource` lifecycle.
- `CONNECTED + READY` means connected but not recording: producer count is zero, buffers do not advance, alarms are not evaluated, statistics do not change, and Room receives no measurements.
- `MONITORING` means one 8 Hz producer, alarm evaluation, incremental statistics, bounded realtime buffering, and batched Room persistence.
- Starting or stopping twice is safe. Duplicate commands never create a second producer, finalize twice, or flush twice.

The intended state progression is:

```text
CONNECTED + READY (0 Hz)
        ↓ Start command through foreground service
STARTING
        ↓
CONNECTED + MONITORING (8 Hz, producer = 1)
        ↓ Stop or critical interruption
STOPPING
        ↓ unified finalize pipeline
COMPLETED or INTERRUPTED (producer = 0)
        ↓ summary acknowledged
CONNECTED + READY (0 Hz)
```

## Composition root

`FilamentVisionApplication` will lazily create:

- `FilamentVisionDatabase`
- Room-backed session, measurement, and alarm repositories
- one `FakeDeviceConnection`
- one `FakeVisionSource`
- one `MonitoringRuntime`

The Android manifest will name this Application and declare the foreground service and required foreground-service permission/type. The Application exposes dependencies for the service and Activity/ViewModel factories, but does not expose domain operations such as start, stop, alarm evaluation, flushing, or statistics calculation.

## Foreground service boundary

UI starts monitoring by sending an explicit start action through `startForegroundService`. It never calls `MonitoringRuntime.startMonitoring()` directly.

The service start sequence is strict:

1. Receive the start intent.
2. Create the notification channel if necessary.
3. Call `startForeground()` immediately with a persistent monitoring notification.
4. Launch the runtime start command in a service-owned coroutine.
5. Observe low-frequency runtime status to update notification content.

The service performs no sample loop, alarm calculation, statistics work, buffering, or DAO access. A notification Stop action sends an explicit stop command back to the service. UI Stop uses the same service command path. After the runtime reports finalized state, the service removes its notification and stops itself.

If runtime startup fails, the runtime publishes an error state, the service removes the foreground notification, and the service stops. Service destruction requests a safe runtime interruption only when an official session is active; duplicate destruction and stop commands remain idempotent.

## MonitoringRuntime

`MonitoringRuntime` owns:

- fake connection state and commands
- the only `FakeVisionSource`
- monitoring state transitions
- active session identity and target configuration
- alarm evaluation and alarm episodes
- incremental session statistics
- the 300-sample realtime ring buffer
- the measurement and alarm write buffers
- persistence orchestration
- the latest measurement and final measurement timestamp

It exposes immutable StateFlows, including a domain-level state similar to:

```kotlin
data class MonitoringRuntimeState(
    val connectionState: ConnectionState,
    val monitoringState: MonitoringState,
    val activeSession: ActiveSessionSnapshot?,
    val latestMeasurement: VisionMeasurement?,
    val latestMeasurementTimestamp: Long?,
    val alarmState: AlarmState,
    val activeProducerCount: Int,
    val totalProducerStarts: Int,
    val realtimeBufferSize: Int,
)
```

The runtime uses a `Mutex` to serialize lifecycle commands. `startMonitoring()` is a no-op in `STARTING` or `MONITORING`. `stopMonitoring()` is a no-op in `IDLE`, `READY`, `STOPPING`, `COMPLETED`, or `INTERRUPTED`. Source-level locking remains as a second defensive layer but is not the primary runtime state machine.

Runtime StateFlows own domain truth. ViewModels map them into UI state and do not independently infer whether a session is ready, active, stopped, or interrupted.

## Start and finalize pipelines

Starting an official session performs:

1. Validate `CONNECTED + READY` under the runtime lifecycle mutex.
2. Set `STARTING`.
3. Create and persist an `ACTIVE` session row before accepting samples.
4. Reset statistics, alarm evaluator, realtime buffer, and persistence buffers.
5. Enable measurement acceptance.
6. Start `FakeVisionSource` once.
7. Await source startup and publish `MONITORING` with producer count one.

Normal stops, connection loss, camera failure, and service termination share one private suspend finalize pipeline. The outcome is either completed or interrupted with a reason.

```text
MONITORING
  ↓
STOPPING
  ↓
disable acceptance of new measurements
  ↓
stop and await producer termination
  ↓
flush MeasurementWriteBuffer
  ↓
flush pending alarm transitions
  ↓
finalize incremental statistics
  ↓
persist final SessionEntity
  ↓
COMPLETED or INTERRUPTED
```

Disabling acceptance before stopping and flushing prevents a late sample from entering a completed session. The same function handles all outcomes so interrupted sessions cannot omit final batches that completed sessions preserve.

After finalization, producer count is zero. The latest measurement is retained as a last-known value with its timestamp for READY UI, but the realtime ring buffer does not continue advancing.

## Alarm model

Alarm evaluation is separate from vision-quality `MeasurementStatus`. It calculates deviation from the active target using the measurement's fused diameter:

```text
warning high  = target × 1.02
warning low   = target × 0.98
critical high = target × 1.05
critical low  = target × 0.95
```

The evaluator produces `NORMAL`, `WARNING`, or `CRITICAL` plus direction. It emits persisted events only on transitions such as warning started, critical started, or returned to normal. Alarm events and measurements use the exact same `VisionMeasurement.timestamp`. Persistent markers therefore align with historical chart data without generating a second clock value.

## Room schema

`FilamentVisionDatabase` contains three entities.

### MonitoringSessionEntity

Stores:

- session ID primary key
- start and end timestamps
- target diameter
- warning and critical percentages
- status
- end/interruption reason
- average, minimum, maximum, population standard deviation
- average confidence
- sample count and abnormal-event count

Summary fields are finalized incrementally; opening History never scans all measurements to reconstruct them.

### MeasurementEntity

Stores an auto-generated ID, session ID, original measurement timestamp, Camera A and B diameters/confidences, fused diameter, shape difference, fused confidence, and vision-quality status. A foreign key references the session with cascade deletion. A composite index on `(sessionId, timestamp)` supports range queries, and the generated ID provides a stable pagination cursor when timestamps are equal.

### AlarmEventEntity

Stores an auto-generated ID, session ID, original measurement timestamp, alarm transition/level/direction, fused diameter, deviation percentage, and description. It also cascades on session deletion and is indexed by `(sessionId, timestamp)`.

Room DAOs expose suspend insert, range query, paged range query, final session update, lookup, list, and delete operations. Composables never access DAOs. Database work runs from repository/runtime coroutines rather than the Main thread.

Deleting a session is a confirmed user action. One Room transaction deletes its session row and relies on foreign-key cascade for measurements and alarms, preventing orphans. No automatic retention deletion is added.

## Repository boundaries

- `SessionRepository` creates active sessions, finalizes summaries, observes session lists, retrieves sessions, marks stale active sessions interrupted, and deletes sessions transactionally.
- `MeasurementRepository` batch-inserts measurements and retrieves time ranges or paged ranges.
- `AlarmRepository` batch-inserts transitions and retrieves markers for a time range.

Repository entities are mapped to domain models at the data boundary. UI state is never persisted. The schema keeps timestamp, Camera A/B, fused diameter, confidence, and alarm fields compatible with a future CSV export, but export is not implemented.

## Realtime and persistence buffers

The two buffers remain deliberately independent:

```text
RealtimeMeasurementBuffer
- UI-oriented
- capacity 300
- overwrites the oldest value
- lossy by design
- provides the fastest 30-second window

MeasurementWriteBuffer
- I/O-oriented
- approximately 8–16 pending samples
- never silently overwrites
- flushes to one Room batch transaction
- is empty in READY
```

`MeasurementWriteBuffer` uses two triggers: batch size reaches eight, or one second elapses since the previous flush. Its input channel is bounded so database backpressure cannot create unlimited memory growth; sending may suspend the runtime persistence branch rather than dropping a persisted measurement. The live rendering branch remains independent.

The buffer flushes on batch threshold, periodic threshold, normal stop, interruption, service termination, and runtime shutdown. Rare alarm transitions use a small separate pending-event buffer and follow the same periodic/final flush boundary.

At 8 Hz, one hour contains approximately 28,800 measurements. Writes normally produce about one measurement transaction per second instead of 28,800 individual transactions.

## Trend data sources

The Trend screen is a new top-level destination:

```text
Monitor | Trend | History | Device
```

It answers current fluctuation and alarm-boundary questions without duplicating History.

- 30 seconds: the current runtime ring-buffer snapshot, at most 240 expected samples.
- 1 minute and 5 minutes: Room range data plus the current unflushed runtime tail, de-duplicated by session/timestamp.
- Historical Session Detail: Room only, requested by time range.

READY shows `NOT RECORDING`, the last measurement and age, and a stationary completed waveform or empty state. It never pretends to have a live waveform. Disconnected shows `NO LIVE DATA`. MONITORING shows `LIVE`, active session duration, current diameter, and a right-following plot.

`TrendViewModel` does not own a producer, source, write buffer, database writer, alarm evaluator, or session lifecycle. It observes runtime StateFlows and repositories, owns chart-window/toggle/inspection choices, and produces bounded `TrendUiState`.

## Chart model and rendering

`TrendChartPoint` contains only timestamp, Camera A diameter, Camera B diameter, and fused diameter. Alarm markers are a separate lightweight chart model.

One reusable Compose Canvas component renders both live and historical curves. It supports:

- Camera A, Camera B, and fused series toggles
- target line
- dynamically calculated ±2% warning and ±5% critical lines
- axes and horizontal time progression
- alarm transition markers
- tap-to-inspect selection
- paused inspection and Jump to Live

Fused diameter is visually dominant. Camera lines are lighter. Threshold lines are distinct without large saturated background regions. Measurement frequency remains 8 Hz; any visual interpolation is rendering-only and never increases sensor or database frequency.

Canvas geometry, threshold values, labels, and Paths are calculated with `remember`/derived state when their inputs change, not allocated per animation frame. Statistics come from runtime/session summaries, not by scanning chart points in Composables.

## Downsampling and long sessions

No chart receives more than roughly 600–800 final points. Long historical ranges are read with keyset pagination ordered by `(timestamp, id)`, so an eight-hour session is not loaded as one 230,000-element list.

`TrendDownsampler` incrementally assigns source rows to time buckets. Each bucket retains chronological first, minimum, maximum, and last candidates, removes duplicate candidates, and emits them in timestamp order. This preserves short warning/critical spikes that average-only downsampling would hide. The final point budget determines bucket width.

The downsampler is pure Kotlin and is tested with a critical 1.900 mm spike surrounded by normal 1.750 mm values. The spike must remain in output.

## Inspect and pause behavior

Tapping a point freezes automatic chart following and displays timestamp, Camera A, Camera B, fused diameter, deviation percentage, and alarm level. Tapping an alarm marker shows its transition, timestamp, diameter, and deviation.

Pausing/inspecting changes only chart viewport behavior. Runtime measurement, alarm evaluation, statistics, notification, and Room persistence continue unchanged. `Jump to Live` clears selection and resumes right-edge following.

## History integration

History observes Room session rows only. Each completed or interrupted session remains after Activity recreation, app close, and process restart. Session Detail reuses `DiameterTrendChart` with fixed historical ranges and Room-backed data. History never subscribes to the 8 Hz runtime stream.

Session deletion requires confirmation and removes the session, measurements, and alarms. Restarting the app must not restore a deleted session.

## Process death boundary

Foreground service plus process importance covers the intended Home/background flow, but no component is described as surviving process death. On process creation, the composition root creates a new runtime.

Room initialization performs one small recovery action: any session row still marked `ACTIVE` while the newly created runtime has no active session is changed to `INTERRUPTED` with reason `PROCESS_TERMINATED`. Its end time is the last persisted measurement timestamp, falling back to its start timestamp when no measurement exists. The phase does not resume that session or automatically restart monitoring.

## UI recomposition boundaries

- Runtime publishes current measurement at 8 Hz only to live consumers.
- Navigation, History, Device, and service notification do not collect the high-frequency point list.
- Trend collects live chart data only while its lifecycle is started.
- Session timer remains a separate 1 Hz stream.
- Room session-list flows update on session mutations, not on every measurement insert.
- Chart point lists are bounded; no one gigantic list serves UI, history, persistence, and statistics.

Navigation between Monitor, Trend, History, Device, and Camera Detail observes the same Application runtime and never starts a producer or writer.

## Error handling and consistency

- Session creation must succeed before sample acceptance begins.
- A batch insert failure moves runtime to an error/interruption finalize path; it is not silently discarded.
- Finalization awaits source termination and all persistence flushes before saving the final session status.
- Alarm and measurement timestamps originate from the same measurement.
- A failed session deletion leaves the existing session visible and reports an error instead of partially removing UI state.
- Runtime shutdown and service destruction are cancellation-safe and idempotent.

## Automated verification

Tests will cover:

- realtime ring-buffer capacity, overwrite order, clear, and disconnect behavior
- runtime duplicate start/stop commands and producer count at most one
- READY producer zero; MONITORING producer one; finalized producer zero
- batch-size and one-second measurement flush triggers
- stop and interruption final flushes
- completed and interrupted session persistence
- preview/READY measurements never persisted
- session isolation between A and B
- alarm transition persistence with measurement timestamps
- cascade deletion of measurements and alarm events
- stale ACTIVE session recovery as interrupted
- 30-second RAM source versus longer Room-backed sources
- min/max downsampling point budget and critical-spike preservation
- ViewModel/navigation creation never starting a producer
- Room DAO range ordering and indexing behavior

Tests follow red-green-refactor. Room integration tests use an in-memory Room database; pure runtime, buffer, alarm, and downsampling behavior use local JVM tests where Android dependencies are unnecessary.

## Manual and performance verification

The final validation runs `testDebugUnitTest`, Android/Room tests as configured, `assembleDebug`, and `lintDebug`, then installs the app in the emulator.

The manual flow covers start, live Trend, warning/critical markers, Home for at least one minute, reopen with no chart gap, stop and flush, Summary, historical chart, process restart persistence, deletion, and deletion persistence.

A longer fake session records CPU, PSS, thread count, database size, measurement row count, producer count, realtime-buffer size, and chart frame behavior. Expected invariants are:

- producer count never exceeds one
- READY and finalized states have producer zero
- realtime buffer never exceeds 300
- Room measurement count grows only during official monitoring
- background recording continues while the service is active
- navigation does not duplicate producers or writers
- Stop flushes all accepted measurements
- UI memory does not grow with session duration
- historical rendering preserves critical spikes within the bounded point budget

## Phase completion boundary

The completed product flow is:

```text
Monitor current state
        ↓
Trend live waveform
        ↓
MonitoringRuntime split paths
        ├── bounded realtime UI buffer
        └── batched Room persistence
                    ↓
History sessions
        ↓
Session Detail historical waveform
```

After validation and reporting, development stops and waits for the next instruction.
