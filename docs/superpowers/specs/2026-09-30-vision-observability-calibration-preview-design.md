# FilamentVision Vision Observability and Calibration Preview Design

## Scope

This phase makes the real vision pipeline observable without adding a hardware protocol. Raw, grayscale, threshold, edge, detection overlay, per-camera measurement, and fusion information become simultaneous outputs rather than mutually exclusive UI modes. It also adds a calibration preview whose draft settings affect only preview processing until the user explicitly saves or applies them.

The existing process-scoped `MonitoringRuntime`, foreground service, Room history, alarm evaluation, trend views, connection profile, and single official measurement producer remain authoritative. No fake image, fake measurement, or simulated connection may be introduced.

## Core invariants

1. A physical input frame is decoded once and processed once per distinct configuration snapshot. Five UI stages never cause five processing passes. If formal monitoring and an unsaved calibration draft coexist, they may require one active-snapshot pass and one isolated draft-preview pass over the same canonical frame.
2. UI code displays pipeline output; it never independently reruns grayscale, threshold, edge, or detection algorithms.
3. Camera A and Camera B retain independent processing results and independent profiles.
4. Only the latest processing result for each camera is retained. Intermediate image history is not accumulated or written to Room.
5. Processing resolution and preview refresh rate are independent. Detection uses the required source resolution; UI previews may be downscaled and throttled to 5–10 Hz.
6. Calibration preview measurements never enter production history, alarms, trend data, or session statistics.
7. Draft settings never mutate the saved or applied profile until an explicit command succeeds.
8. Missing input remains `WAITING FOR DEVICE`, `WAITING FOR FRAME`, or `DRIVER UNAVAILABLE`; it never falls back to generated content.

## Processing model

### Canonical decoded image

`ImageDecoder` produces a canonical decoded pixel frame without display geometry changes. A dedicated geometry transform applies rotation and horizontal or vertical mirroring before grayscale conversion and detection. All downstream images and coordinates therefore share the transformed coordinate system.

Configured width and height describe the encoded/canonical frame. Calibration ROI coordinates describe the transformed frame. A 90° or 270° rotation swaps the effective processing width and height. ROI validation uses those effective dimensions.

### Single-pass output

The processing API returns one immutable `VisionProcessingResult` for one camera frame. It contains:

- camera ID, timestamp, sequence, source format, canonical and transformed dimensions;
- raw display preview;
- grayscale preview derived from the exact grayscale values used by detection;
- threshold preview derived from the exact manual or automatic threshold output used by detection;
- edge preview derived from the exact edge representation used during width selection;
- effective threshold value and threshold mode;
- transformed ROI and detected left/right filament boundaries;
- pixel width, diameter in millimetres, confidence, validity, and typed detection error;
- decode, geometry, preprocessing, detection, and total processing durations;
- processing frame-rate metadata.

The full-resolution grayscale, binary, and edge working arrays are temporary. Before returning, a preview builder creates bounded display representations and releases references to full-resolution intermediates. Preview dimensions have a configurable upper bound while preserving aspect ratio. Downscaling occurs after the full-resolution measurement has been calculated and cannot affect diameter results.

### Detection failure

Every result belongs to exactly one input timestamp. A failed result contains no stale edge coordinates. The UI displays its typed error such as invalid calibration, invalid ROI, no edge, width out of range, low confidence, or decode/data-format failure. The previous successful overlay is never combined with a newer failed frame.

## Runtime ownership and streams

A process-scoped vision processing coordinator is owned by the application composition root and used by `MonitoringRuntime`; screens do not instantiate hardware producers. It owns the single current input pipeline created from the applied immutable `ConnectionProfile` snapshot.

The coordinator exposes lightweight state flows:

- latest Camera A `VisionProcessingResult`;
- latest Camera B `VisionProcessingResult`;
- latest fusion explanation/result;
- input state and processing diagnostics;
- input pipeline, official measurement producer, and preview observer counts.

Official monitoring consumes valid per-camera results through `FusionEngine`. Only that path writes measurement batches, evaluates alarms, updates statistics, and publishes trend samples. Diagnostics and calibration pages are observers of the latest result flow.

When the device is connected but no formal monitoring session is active, opening calibration preview may acquire an input-preview lease. This can activate the real input pipeline while the page is foreground, but the official measurement producer count remains zero. Multiple UI observers reuse the same pipeline. Releasing the final preview lease stops preview-only input unless official monitoring still owns it.

For shared topology, Camera A and B share one transport lifecycle and are split after frame parsing. For independent topology, the coordinator may own two configured endpoint transports, but there remains only one official measurement-production workflow and no duplicate transport for the same endpoint.

## Calibration draft lifecycle

### Draft creation

Opening calibration for a camera copies its saved `CameraProfile` into an independent `CalibrationDraft`. The draft includes calibration fields and hot geometry fields needed by preview:

- ROI;
- grayscale method;
- threshold mode and manual threshold;
- edge sensitivity;
- minimum and maximum valid pixel width;
- millimetres per pixel;
- minimum confidence;
- rotation;
- horizontal mirror;
- vertical mirror.

The saved profile and runtime applied snapshot remain unchanged.

### Live draft preview

Draft edits are held in destination-scoped ViewModel state. Each valid edit is supplied to preview processing through a conflated/latest-only stream. Rapid slider or ROI drag events cancel obsolete preview calculations. No draft event writes SharedPreferences, DataStore, Room, or another permanent store.

Draft-preview processing is limited to the configured UI preview rate. If formal monitoring is active, its applied-snapshot processing remains independent and authoritative; the draft pass cannot replace or delay official measurement, alarm, persistence, or statistics work.

The next real frame is processed with the latest draft. When practical, changes such as `mmPerPixel` may also recalculate the displayed diameter from the current draft result without waiting for another frame, while still remaining preview-only. ROI, threshold, grayscale, edge, rotation, and mirror changes require reprocessing the latest available canonical frame and then future frames.

Calibration preview never creates a formal session, updates an existing session, evaluates an alarm, appends a trend sample, or persists a measurement.

### Dirty state and exit

The UI explicitly shows one of:

- `SAVED AND APPLIED`;
- `SAVED — APPLY REQUIRED`;
- `UNSAVED CHANGES`.

`Reset` restores the draft to the saved camera profile captured when the editor was opened. `Restore Defaults` loads safe defaults into the draft only. Neither command persists.

Back or navigation away with dirty state opens a three-action confirmation:

- `Discard changes`: leave and discard the draft;
- `Continue editing`: remain on the page;
- `Save changes`: validate and persist, then leave without applying to a running snapshot.

There is no automatic save or silent discard.

### Save

Save validates the complete resulting `ConnectionProfile`, not only the changed fields. On success it persists the profile and marks it saved. It does not mutate an already-applied runtime snapshot. When the saved and applied revisions differ, UI surfaces `SAVED — APPLY REQUIRED`.

Validation failure leaves the draft intact and displays field-level errors. No partial profile is written.

### Apply

Apply validates and persists the draft, then sends one idempotent configuration-apply command to the runtime. The runtime serializes this command with connect, disconnect, start, and stop commands.

If formal monitoring is inactive, apply stops the input pipeline, awaits termination, clears packet/parser/decoder transient state, creates a new immutable snapshot, rebuilds the input source, and enters the truthful resulting input state.

If formal monitoring is active, apply performs this sequence:

1. stop accepting formal measurements;
2. stop and await the current input producer;
3. flush pending measurements and alarms;
4. finalize the old session with `CONFIGURATION_CHANGED`;
5. clear packet, parser, and decoder transient state;
6. activate the new immutable profile snapshot;
7. rebuild and reconnect the real input source;
8. if reconnection succeeds, start a new session using the previous target diameter; otherwise remain in the actual error or waiting state.

This intentionally creates a session boundary so a single persisted session never mixes two calibration snapshots. Repeated Apply commands for the same revision are safe no-ops.

## Hot and restart parameters

Calibration preview edits only hot parameters. They reuse the current canonical frame stream and do not reconnect transport:

- ROI and all detection/calibration values;
- rotation and horizontal/vertical mirror.

Transport, framing, and decode-layout parameters remain in Connection Settings. They include protocol, host, port, UUIDs, MTU, frame markers and offsets, image dimensions, pixel format, stride, plane layout, and endian. Saving those parameters may create a saved/applied revision difference. Applying them rebuilds the pipeline. The UI must label the restart requirement before applying.

## UI structure

### Monitoring

The Monitor screen remains compact. For each camera it displays state, raw preview, detection overlay, latest diameter, and confidence. It also displays final fused diameter, fusion confidence, active source set, A/B difference, and alarm state. It links to Vision Diagnostics and per-camera Calibration Preview.

### Vision Diagnostics

Vision Diagnostics shows independent Camera A and Camera B sections. Each section contains persistent stage cards for Raw, Grayscale, Threshold, Edge, Detection Overlay, and Measurement. No mutually exclusive view-mode selector exists.

On compact portrait screens, stage cards appear in a vertical list or non-exclusive horizontally scrollable pipeline whose complete stage list is visible. On wide or landscape screens they use a grid. Sections may be collapsed, but their headers and stage availability remain visible and default to expanded.

Diagnostic metadata includes threshold mode/value, ROI, `mmPerPixel`, pixel width, diameter, confidence, detection status, frame resolution, camera ID, image format, processing FPS, decode time, and detection time.

### Calibration Preview

Calibration Preview reuses the same stage-card component and current real input. Controls edit only the draft. The raw panel supports ROI overlay and drag interaction. Controls and overlays update immediately from the draft. Save, Apply, Reset, Restore Defaults, and dirty-state actions remain visible without hiding pipeline stages.

If no frame exists, all panels show a consistent waiting state instead of retaining stale imagery.

## Compose and memory behavior

- ViewModels expose only the latest immutable result per camera.
- Frame flows are conflated; UI preview publication is sampled independently at 5–10 Hz.
- Measurement and alarm processing are not sampled by the preview throttle.
- Compose converts preview arrays to display images once per result identity using `remember`; it does not allocate a new image every animation frame.
- Stage cards receive stable state objects and do not collect independent frame streams.
- Raw bytes, full-resolution temporary arrays, and multiple historical bitmaps are not stored in Compose state.
- Navigating between Monitor, Diagnostics, and Calibration adds observers but never another official producer.

## Persistence

Room continues to store sessions, measurements, alarms, errors, and bounded error snapshots only. Pipeline intermediate images and calibration preview measurements are never inserted. Profile persistence remains in the connection settings repository. A monotonically increasing saved/applied revision or stable profile fingerprint distinguishes saved configuration from the active immutable runtime snapshot without logging credentials.

## Error handling

Decode, parsing, connection, and detection errors remain separate. A bad frame yields a typed failed result and increments bounded diagnostics, but does not crash or permanently stop the runtime. Repeated format failures may transition input state to `DATA_FORMAT_ERROR`. Parameter validation errors remain local to the draft until Save or Apply.

## Test strategy

Unit tests will verify:

- one processing call produces raw, grayscale, threshold, edge, and detection outputs from the same pixels;
- manual and automatic threshold previews match the threshold actually used by detection;
- failed frames clear overlay edges and expose the correct typed error;
- 0°, 90°, 180°, and 270° transforms;
- horizontal mirror, vertical mirror, and combined mirror transforms;
- transformed dimensions, ROI validation, ROI overlay, and detection-edge coordinates share one coordinate system;
- Camera A and B retain independent formats, geometry, ROI, threshold, and calibration;
- entering calibration copies the saved profile into a draft;
- threshold, ROI, and `mmPerPixel` draft edits update preview without changing saved/applied state;
- Reset and Discard restore or remove draft changes;
- Save persists only after successful whole-profile validation;
- Apply installs a new immutable snapshot and is idempotent;
- active-session Apply flushes/finalizes before starting a new configuration session;
- rapid draft updates process only the latest pending configuration;
- calibration results never reach Room, Alarm, Trend, or session statistics;
- navigation and multiple observers do not multiply producers.

Compose/UI tests will verify all five stage labels coexist, waiting/error states do not show stale overlays, compact and wide layouts remain accessible, and dirty-exit actions have the specified semantics.

Device verification will include repeated navigation, preview entry/exit, memory checks, producer diagnostics, and an extended waiting-for-device run. Real streaming behavior can only be device-tested after a concrete transport adapter and protocol fixture are available.

## Explicit non-goals

- No Bluetooth, Wi-Fi, RTSP, CameraX, or manufacturer-specific transport implementation.
- No fake preview or generated measurement.
- No storage of normal intermediate images in Room.
- No perspective calibration, distortion correction, or multi-point calibration mapping.
- No change to the 8 Hz formal measurement target, foreground service ownership, alarm thresholds, or bounded trend/history design.
