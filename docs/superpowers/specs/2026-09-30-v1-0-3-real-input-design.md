# FilamentVision v1.0.3 Real Image Input Foundation

## Goal

Move the release runtime from generated camera frames and measurements to a configuration-driven boundary that safely waits for real Camera A/B image streams. Preserve the existing single `MonitoringRuntime`, foreground-service lifecycle, Room persistence, alarm evaluation, bounded realtime trend, history, and camera image views.

## Release truth

- Version name is `1.0.3`; version code is `103`.
- Release code never creates a fake frame, fake detection, fake connection success, or random measurement.
- No Bluetooth or Wi-Fi transport is claimed as implemented in this release.
- A selected but unavailable transport reports `DRIVER_UNAVAILABLE`, has producer count zero, and cannot start monitoring.
- Camera processing modes remain Original, Grayscale, Threshold, Edge Map, Detection, and Measurement.
- Measurement persistence begins only after real decoded frames produce valid calibrated detections.

## Configuration model

`ConnectionProfile` owns topology, endpoints, cameras, parsing, image format, calibration, fusion, reconnect, and timeout settings.

```text
ConnectionProfile
├── topology: SHARED_CONNECTION | INDEPENDENT_CONNECTION
├── endpoints: List<EndpointProfile>
├── cameras: CameraProfile(A), CameraProfile(B)
└── fusion: FusionProfile

CameraProfile ──endpointId──> EndpointProfile
```

An endpoint describes one physical connection. Shared topology therefore creates one transport instance, while both logical cameras reference it and are separated by configured identifiers. Endpoint variants represent Wi-Fi and Bluetooth without implying driver availability. Image format and frame parsing are explicit profiles; no IP, port, UUID, camera identifier, image size, byte marker, calibration value, or detection threshold is embedded in business logic.

Configuration validation runs before persistence. It validates endpoint references, topology, ports, UUIDs, dimensions and allocation limits, raw pixel layout, frame parsing offsets and markers, MTU, calibration ranges, confidence, timestamp tolerance, and fusion weights. Authentication data is stored separately from ordinary profile data and is never included in `toString`-style diagnostic output or logs.

## Input pipeline

```text
TransportAdapter
  -> PacketAssembler
  -> FrameParser
  -> ImageDecoder
  -> DetectionPipeline
  -> CameraMeasurement
  -> FusionEngine
  -> VisionMeasurement
  -> MonitoringRuntime
  -> Alarm / Room / Trend
```

`TransportAdapter` only manages connection lifecycle and chunks. `PacketAssembler` handles fragmentation. `FrameParser` extracts complete frames and logical camera identity. `ImageDecoder` creates the internal pixel frame. `DetectionPipeline` performs image work and knows nothing about network or Bluetooth configuration. `FusionEngine` applies validity, confidence, and timestamp rules before using configured weights.

`ImageInputSourceFactory` resolves the endpoint protocol to a registered driver. v1.0.3 ships no release driver, so every protocol resolves to an unavailable source. This is intentional and observable rather than simulated.

## Input state

The canonical state is one sealed value:

- `UNCONFIGURED`
- `DRIVER_UNAVAILABLE`
- `CONNECTING`
- `CONNECTED`
- `WAITING_FOR_FRAME`
- `STREAMING`
- `RECONNECTING`
- `DATA_FORMAT_ERROR`
- `CONNECTION_ERROR`
- `STOPPED`

UI derives connection labels, action availability, camera status, and monitoring availability from this state. No independent Boolean set may contradict it.

## Detection

The existing grayscale, threshold, and edge processors remain. `FakeDetectionProvider` is replaced by `FilamentWidthDetector`, which searches a configured ROI for paired edges, calculates robust width across scan rows, rejects widths outside configured limits, and reports confidence from the number and consistency of valid rows. Manual and automatic threshold modes are supported. The calibrated diameter is `pixelWidth * mmPerPixel`, with `mmPerPixel > 0` required.

Detection output retains camera ID, pixel width, diameter in millimetres, confidence, timestamp, validity, and an error reason. Camera A and B measurements remain separate. Fusion uses only valid measurements above the configured confidence threshold, renormalizes active weights, and refuses to combine samples farther apart than the configured timestamp tolerance. One valid camera may supply the fused result; two invalid cameras produce no `VisionMeasurement`.

## Runtime and lifecycle

`MonitoringRuntime` depends on `DeviceConnection`/`VisionSource` interfaces rather than fake concrete classes. The application composition root creates one profile-driven production source per process. ViewModels observe runtime state and never own producers. The foreground service remains only the lifecycle and notification host.

Starting monitoring while unconfigured, driver-unavailable, or without calibrated streaming cameras is a safe no-op with an explicit state/message. It does not create a session, start the service producer, update alarms, or write Room. Shared endpoints are deduplicated by endpoint ID in the source factory/runtime composition.

Large frame buffers remain outside Compose state and are processed on a background dispatcher with latest-frame backpressure. UI state contains only the latest lightweight display result and measurement metadata. The realtime measurement buffer remains bounded at 300 samples.

## Persistence migration

Room moves from schema 2 to 3. `measurements` gains non-null `sourceType`, with new release writes fixed to `REAL`. The one-time 2-to-3 migration deletes sessions and dependent measurements/alarms created by the pre-1.0.3 all-fake runtime, clears legacy fake error rows/snapshot metadata, and creates the new column/table shape. This cleanup occurs only in that migration; later migrations do not repeat it. Snapshot files orphaned by the migration are reconciled by the existing startup maintenance.

## UI

Device settings edit topology, endpoint protocol-specific fields, logical camera routing, frame parsing, image format, calibration/detection, fusion, reconnect, and timeout settings. Fields are shown only when applicable. Save validates the complete profile and displays field-specific errors.

Monitor, Device, Camera Detail, and Diagnostics show actual input state. Camera detail shows an empty waiting surface until a real frame exists. Diagnostics reports selected endpoint/protocol, driver status, packets, frames, decoded frames, valid detections, invalid/dropped frames, reconnect count, and producer count without per-frame logging. Simulation scenario controls are removed from release UI.

## Test boundary

Deterministic test sources and fixed image fixtures remain only under `src/test`/`src/androidTest`; they are not selectable by release dependency wiring. Tests cover validation, fragmentation and frame extraction, decoding, real width detection, calibration, fusion validity/timestamps, state transitions, runtime refusal when unavailable, Room migration, and absence of fake release dependencies.

