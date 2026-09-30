# Vision Observability and Calibration Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the real Raw → Grayscale → Threshold → Edge → Detection pipeline for Camera A and B, and add a real-input calibration workbench whose draft updates preview immediately but persists or changes formal monitoring only through explicit Save or Apply.

**Architecture:** A process-scoped `VisionPipelineRuntime` owns the only image-input pipeline and publishes latest-only per-camera `VisionProcessingResult` flows. `VisionProcessor` performs one full-resolution pass per configuration snapshot and creates bounded previews from the exact intermediate arrays used by detection. Diagnostics observes applied results; calibration observes an isolated conflated draft pass over the latest canonical frame and cannot reach Room, Alarm, Trend, or session statistics.

**Tech Stack:** Kotlin, coroutines/StateFlow, Jetpack Compose Material 3, Android graphics, Room, JUnit4, kotlinx-coroutines-test, Android instrumented tests.

**Spec:** `docs/superpowers/specs/2026-09-30-vision-observability-calibration-preview-design.md`

## Global Constraints

- No Bluetooth, Wi-Fi, RTSP, CameraX, or manufacturer-specific transport implementation.
- No fake preview or generated production measurement.
- Keep exactly one official measurement producer per process and keep it at zero during preview-only use.
- Retain only the latest Camera A result and latest Camera B result; do not persist normal intermediate images.
- Detection uses full-resolution pixels; preview downscale and 5–10 Hz UI publication cannot affect measurement or Alarm timing.
- Calibration draft changes never persist automatically and never mutate the active immutable runtime snapshot.
- Missing input remains `UNCONFIGURED`, `DRIVER_UNAVAILABLE`, `WAITING FOR DEVICE`, or `WAITING FOR FRAME`.
- Preserve the existing 8 Hz formal measurement target, 300-sample realtime buffer, foreground service, Room, Alarm, Trend, and History behavior.

---

## File structure

New focused production files:

- `vision/geometry/ImageGeometryTransformer.kt`: rotation/mirror and coordinate mapping.
- `vision/processing/VisionProcessingResult.kt`: one-frame observable output and preview models.
- `vision/processing/VisionProcessor.kt`: one-pass full-resolution preprocessing and detection.
- `vision/processing/VisionPreviewFactory.kt`: bounded display downscale only.
- `vision/runtime/VisionPipelineRuntime.kt`: process-scoped source ownership, latest results, draft preview leases, diagnostics.
- `hardware/PipelineVisionSource.kt`: adapts official fused runtime output to existing `VisionSource`.
- `ui/vision/VisionPipelineCards.kt`: reusable non-exclusive stage cards.
- `ui/vision/VisionDiagnosticsScreen.kt`: complete Camera A/B pipeline and fusion diagnostics.
- `ui/calibration/CalibrationDraft.kt`: immutable draft and dirty/revision status.
- `ui/calibration/CalibrationPreviewViewModel.kt`: draft reducer, preview requests, save/apply commands.
- `ui/calibration/CalibrationPreviewScreen.kt`: controls, ROI overlay, confirmation dialog, full stage output.

Existing files are modified only where their responsibility already applies: input profiles/codecs/validation, decoder geometry semantics, application composition, monitoring apply lifecycle, navigation, Monitor/Device entry points, and tests.

---

### Task 1: Unified image geometry and vertical mirror

**Files:**
- Create: `app/src/main/java/com/filamentvision/vision/geometry/ImageGeometryTransformer.kt`
- Modify: `app/src/main/java/com/filamentvision/model/InputProfiles.kt`
- Modify: `app/src/main/java/com/filamentvision/input/decode/RawImageDecoder.kt`
- Modify: `app/src/main/java/com/filamentvision/input/decode/AndroidCompressedImageDecoder.kt`
- Modify: `app/src/main/java/com/filamentvision/data/settings/ConnectionProfileCodec.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/device/ConnectionConfigurationForm.kt`
- Test: `app/src/test/java/com/filamentvision/vision/geometry/ImageGeometryTransformerTest.kt`
- Test: `app/src/test/java/com/filamentvision/data/settings/ConnectionProfileCodecTest.kt`

**Interfaces:**
- Produces: `ImageGeometryTransformer.transform(frame, rotationDegrees, mirrorHorizontally, mirrorVertically): GeometryTransformResult`.
- Produces: `ImageGeometryTransformer.mapPoint(...)` and `mapRect(...)` for overlay assertions.
- Changes: `ImageFormatProfile.mirrorVertically: Boolean = false`.
- Decoder output becomes canonical: decoders no longer apply rotation or mirroring.

- [ ] **Step 1: Write failing transform tests**

```kotlin
@Test fun rotation90MapsPixelsAndDimensions() {
    val frame = frame(width = 2, height = 3, pixels = intArrayOf(1, 2, 3, 4, 5, 6))
    val result = ImageGeometryTransformer.transform(frame, 90, false, false)
    assertEquals(3, result.frame.width)
    assertEquals(2, result.frame.height)
    assertArrayEquals(intArrayOf(5, 3, 1, 6, 4, 2), result.frame.pixels)
}

@Test fun verticalMirrorMapsRoiAndEdgesWithPixels() {
    val result = ImageGeometryTransformer.transform(frame(3, 2), 0, false, true)
    assertEquals(IntPoint(1, 1), result.mapping.mapPoint(IntPoint(1, 0)))
    assertEquals(IntRect(0, 0, 3, 1), result.mapping.mapRect(IntRect(0, 1, 3, 2)))
}
```

- [ ] **Step 2: Run RED tests**

Run: `./gradlew testDebugUnitTest --tests '*ImageGeometryTransformerTest'`

Expected: compilation failure because the transformer and vertical mirror field do not exist.

- [ ] **Step 3: Implement canonical decode plus one geometry transformer**

Implement all four rotations and both mirror axes in one pixel loop. Return transformed dimensions and a coordinate mapping object. Remove duplicate transform functions from both decoders. Add `mirrorVertical` codec key and UI toggle.

- [ ] **Step 4: Run GREEN tests and codec regression**

Run: `./gradlew testDebugUnitTest --tests '*ImageGeometryTransformerTest' --tests '*RawImageDecoderTest' --tests '*ConnectionProfileCodecTest'`

Expected: all pass, including 0°, 90°, 180°, 270°, horizontal mirror, vertical mirror, combined mirrors, point mapping, and rectangle mapping.

- [ ] **Step 5: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/vision/geometry app/src/main/java/com/filamentvision/model/InputProfiles.kt app/src/main/java/com/filamentvision/input/decode app/src/main/java/com/filamentvision/data/settings/ConnectionProfileCodec.kt app/src/main/java/com/filamentvision/ui/device/ConnectionConfigurationForm.kt app/src/test/java/com/filamentvision/vision/geometry app/src/test/java/com/filamentvision/data/settings/ConnectionProfileCodecTest.kt
git commit -m "feat: unify camera image geometry"
```

### Task 2: One-pass observable detection output

**Files:**
- Create: `app/src/main/java/com/filamentvision/vision/processing/VisionProcessingResult.kt`
- Create: `app/src/main/java/com/filamentvision/vision/processing/VisionPreviewFactory.kt`
- Create: `app/src/main/java/com/filamentvision/vision/processing/VisionProcessor.kt`
- Modify: `app/src/main/java/com/filamentvision/vision/detection/DetectionPipeline.kt`
- Modify: `app/src/main/java/com/filamentvision/vision/preprocessing/GrayscaleProcessor.kt`
- Modify: `app/src/main/java/com/filamentvision/vision/preprocessing/ThresholdProcessor.kt`
- Modify: `app/src/main/java/com/filamentvision/vision/preprocessing/EdgeMapProcessor.kt`
- Test: `app/src/test/java/com/filamentvision/vision/processing/VisionProcessorTest.kt`
- Test: `app/src/test/java/com/filamentvision/vision/processing/VisionPreviewFactoryTest.kt`

**Interfaces:**
- Produces: `VisionProcessor.process(frame: ArgbFrame, camera: CameraProfile, decodeNanos: Long = 0): VisionProcessingResult`.
- Produces: `VisionProcessingResult` with `rawPreview`, `grayscalePreview`, `thresholdPreview`, `edgePreview`, `overlay`, `measurement`, `effectiveThreshold`, `timings`, and `error`.
- Produces: `PreviewFrame(width, height, pixels: IntArray)` capped by `VisionPreviewFactory(maxLongEdge = 480)`.

- [ ] **Step 1: Write a failing fixed-image single-pass test**

```kotlin
@Test fun onePassExposesTheExactStagesUsedForMeasurement() {
    val result = VisionProcessor().process(fixedFilamentFrame(), calibratedCamera())
    assertEquals(40, result.effectiveThreshold)
    assertArrayEquals(expectedGrayArgb, result.grayscalePreview.pixels)
    assertArrayEquals(expectedBinaryArgb, result.thresholdPreview.pixels)
    assertArrayEquals(expectedEdgesArgb, result.edgePreview.pixels)
    assertEquals(4.0, result.measurement!!.pixelWidth, 0.0)
    assertEquals(result.measurement.leftEdge, result.overlay.leftEdge)
    assertEquals(result.measurement.rightEdge, result.overlay.rightEdge)
}
```

The fixed test image is smaller than the preview cap, so preview pixels are an exact representation of each full-resolution intermediate without exposing production working arrays.

- [ ] **Step 2: Run RED tests**

Run: `./gradlew testDebugUnitTest --tests '*VisionProcessorTest' --tests '*VisionPreviewFactoryTest'`

Expected: compilation failure because result/processor types do not exist.

- [ ] **Step 3: Implement the one-pass pipeline**

Use this main flow:

```kotlin
fun process(frame: ArgbFrame, camera: CameraProfile, decodeNanos: Long): VisionProcessingResult {
    val transformed = geometryTransformer.transform(frame, camera.imageFormat)
    val gray = grayscaleProcessor.process(transformed.frame, camera.calibration.grayscaleMethod)
    val threshold = thresholdProcessor.process(gray, camera.calibration)
    val edges = edgeMapProcessor.process(gray, threshold, camera.calibration.edgeSensitivity)
    val detection = detectionPipeline.detect(gray, threshold, edges, transformed.frame, camera.calibration)
    return resultBuilder.build(transformed, gray, threshold, edges, detection, decodeNanos)
}
```

Make `DetectionPipeline` consume these exact intermediates instead of recalculating grayscale/threshold internally. Failed detection returns an overlay with ROI only and null edges.

- [ ] **Step 4: Add manual/Otsu, failure, and downscale assertions**

Verify automatic threshold reports the actual Otsu value, low-confidence/no-edge output contains no stale edge, preview long edge is at most 480, and preview pixels preserve aspect ratio.

- [ ] **Step 5: Run GREEN tests**

Run: `./gradlew testDebugUnitTest --tests '*VisionProcessorTest' --tests '*VisionPreviewFactoryTest' --tests '*DetectionPipelineTest' --tests '*CameraDisplayPipelineTest'`

Expected: all pass.

- [ ] **Step 6: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/vision app/src/test/java/com/filamentvision/vision app/src/test/java/com/filamentvision/ui/camera/CameraDisplayPipelineTest.kt
git commit -m "feat: expose single-pass vision processing results"
```

### Task 3: Process-scoped latest-result runtime

**Files:**
- Create: `app/src/main/java/com/filamentvision/vision/runtime/VisionPipelineRuntime.kt`
- Create: `app/src/main/java/com/filamentvision/vision/runtime/VisionPipelineDiagnostics.kt`
- Create: `app/src/main/java/com/filamentvision/hardware/PipelineVisionSource.kt`
- Modify: `app/src/main/java/com/filamentvision/input/ImageInputSourceFactory.kt`
- Modify: `app/src/main/java/com/filamentvision/FilamentVisionApplication.kt`
- Modify: `app/src/main/java/com/filamentvision/hardware/VisionSource.kt`
- Test: `app/src/test/java/com/filamentvision/vision/runtime/VisionPipelineRuntimeTest.kt`
- Test: `app/src/test/java/com/filamentvision/hardware/PipelineVisionSourceTest.kt`

**Interfaces:**
- Produces: `VisionPipelineRuntime.results(cameraId): StateFlow<VisionProcessingResult?>`.
- Produces: `acquirePreview(ownerId): PreviewLease` and idempotent `release()`.
- Produces: `draftPreview(cameraId: String, draft: StateFlow<CameraProfile>): StateFlow<VisionProcessingResult?>`; it reprocesses the latest canonical frame with `collectLatest` and never publishes to the official measurement flow.
- Produces: `officialMeasurements: Flow<VisionMeasurement>` consumed by `PipelineVisionSource`.
- Produces diagnostics with `activeInputPipelineCount`, `officialMeasurementProducerCount`, `previewObserverCount`, processed/dropped frames, and processing FPS.

- [ ] **Step 1: Write failing lifecycle tests**

```kotlin
@Test fun twoPreviewObserversReuseOneInputAndRetainOnlyLatestResult() = runTest {
    val runtime = runtimeWithControlledSource()
    val first = runtime.acquirePreview("diagnostics")
    val second = runtime.acquirePreview("calibration-A")
    source.emit(frame(sequence = 1)); source.emit(frame(sequence = 2))
    advanceUntilIdle()
    assertEquals(1, runtime.diagnostics.value.activeInputPipelineCount)
    assertEquals(2, runtime.diagnostics.value.previewObserverCount)
    assertEquals(2, runtime.results("A").value!!.sequence)
    first.release(); second.release()
    assertEquals(0, runtime.diagnostics.value.activeInputPipelineCount)
}
```

- [ ] **Step 2: Run RED tests**

Run: `./gradlew testDebugUnitTest --tests '*VisionPipelineRuntimeTest' --tests '*PipelineVisionSourceTest'`

Expected: missing runtime/adapter types.

- [ ] **Step 3: Implement source ownership and latest-only processing**

Use a `Mutex` around start/stop/rebuild, `conflate()` before processing, per-camera `MutableStateFlow`, and a preview publication ticker capped at 8 Hz. Keep official result-to-fusion delivery independent of the UI ticker. `close()` cancels all owned jobs and closes the source exactly once.

- [ ] **Step 4: Verify unavailable production behavior**

Add a test that the default factory reports `DRIVER_UNAVAILABLE`, input/official counts remain zero, and no result or measurement is emitted.

- [ ] **Step 5: Run GREEN tests**

Run: `./gradlew testDebugUnitTest --tests '*VisionPipelineRuntimeTest' --tests '*PipelineVisionSourceTest' --tests '*MonitoringRuntimeUnavailableTest'`

Expected: all pass.

- [ ] **Step 6: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/vision/runtime app/src/main/java/com/filamentvision/hardware app/src/main/java/com/filamentvision/input/ImageInputSourceFactory.kt app/src/main/java/com/filamentvision/FilamentVisionApplication.kt app/src/test/java/com/filamentvision/vision/runtime app/src/test/java/com/filamentvision/hardware
git commit -m "feat: add process scoped vision pipeline runtime"
```

### Task 4: Calibration draft reducer and latest-only preview

**Files:**
- Create: `app/src/main/java/com/filamentvision/ui/calibration/CalibrationDraft.kt`
- Create: `app/src/main/java/com/filamentvision/ui/calibration/CalibrationPreviewViewModel.kt`
- Test: `app/src/test/java/com/filamentvision/ui/calibration/CalibrationPreviewViewModelTest.kt`

**Interfaces:**
- Produces: `CalibrationDraft(saved: CameraProfile, current: CameraProfile)` with `hasUnsavedChanges`.
- Produces commands: `updateCalibration`, `updateGeometry`, `reset`, `restoreDefaults`, and `discard`.
- Consumes: repository `profile` to create the opening snapshot and `VisionPipelineRuntime.draftPreview(cameraId, draftProfile)` for isolated preview. Save and Apply are added only after their revision contract exists in Task 5.

- [ ] **Step 1: Write failing draft isolation tests**

```kotlin
@Test fun thresholdEditUpdatesPreviewButNotSavedOrAppliedProfiles() = runTest {
    val vm = viewModel(savedThreshold = 100, appliedThreshold = 100)
    vm.updateManualThreshold(140)
    source.emit(fixedFrame())
    advanceUntilIdle()
    assertEquals(140, vm.state.value.result!!.effectiveThreshold)
    assertEquals(100, repository.profile.value.camera("A").calibration.manualThreshold)
    assertEquals(100, runtime.appliedProfile.value.profile.camera("A").calibration.manualThreshold)
    assertTrue(vm.state.value.hasUnsavedChanges)
}
```

Add separate RED tests for ROI, `mmPerPixel`, Reset, Discard, and rapid `100, 110, 120, 130` updates producing only the latest result.

- [ ] **Step 2: Run RED tests**

Run: `./gradlew testDebugUnitTest --tests '*CalibrationPreviewViewModelTest'`

Expected: missing draft/ViewModel types.

- [ ] **Step 3: Implement immutable draft state**

Create the draft from the saved profile once per destination. Feed edits to a `MutableStateFlow<CameraProfile>` and process with `distinctUntilChanged().collectLatest`. Do not call repository save from any update function.

- [ ] **Step 4: Implement Reset, Restore Defaults, Discard and dirty status**

Defaults must remain draft-only and can be invalid until the user completes calibration; show validation errors instead of persisting them.

- [ ] **Step 5: Run GREEN tests**

Run: `./gradlew testDebugUnitTest --tests '*CalibrationPreviewViewModelTest'`

Expected: all lifecycle and conflation tests pass.

- [ ] **Step 6: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/ui/calibration app/src/test/java/com/filamentvision/ui/calibration
git commit -m "feat: add isolated live calibration draft"
```

### Task 5: Saved/applied revisions and idempotent Apply lifecycle

**Files:**
- Modify: `app/src/main/java/com/filamentvision/data/settings/ConnectionSettingsRepository.kt`
- Modify: `app/src/main/java/com/filamentvision/data/settings/SharedPreferencesConnectionSettingsRepository.kt`
- Modify: `app/src/main/java/com/filamentvision/data/settings/ConnectionProfileCodec.kt`
- Modify: `app/src/main/java/com/filamentvision/domain/monitoring/MonitoringRuntime.kt`
- Modify: `app/src/main/java/com/filamentvision/domain/monitoring/MonitoringRuntimeState.kt`
- Modify: `app/src/main/java/com/filamentvision/model/MonitoringSession.kt`
- Modify: `app/src/main/java/com/filamentvision/vision/runtime/VisionPipelineRuntime.kt`
- Test: `app/src/test/java/com/filamentvision/domain/monitoring/MonitoringRuntimeConfigurationTest.kt`
- Test: `app/src/test/java/com/filamentvision/ui/calibration/CalibrationPreviewViewModelTest.kt`

**Interfaces:**
- Produces: `SavedConnectionProfile(profile, revision)` and `AppliedConnectionProfile(profile, revision)`.
- Produces: `MonitoringRuntime.applyConnectionProfile(savedProfile): ApplyConfigurationResult`.
- Adds session end reason `CONFIGURATION_CHANGED`.
- Extends `CalibrationPreviewViewModel` with `save()` and `apply()` only in this task.

- [ ] **Step 1: Write failing Save/Apply tests**

```kotlin
@Test fun savePersistsRevisionWithoutChangingAppliedSnapshot() = runTest {
    viewModel.updateMmPerPixel(0.0123)
    viewModel.save()
    assertEquals(0.0123, repository.saved.value.profile.camera("A").calibration.mmPerPixel, 0.0)
    assertEquals(0.0120, runtime.appliedProfile.value.profile.camera("A").calibration.mmPerPixel, 0.0)
    assertEquals(CalibrationPersistenceStatus.SAVED_APPLY_REQUIRED, viewModel.state.value.persistenceStatus)
}

@Test fun applyDuringMonitoringFlushesFinalizesAndStartsNewSnapshotSession() = runTest {
    runtime.startMonitoring(1.75)
    runtime.applyConnectionProfile(savedRevision2)
    assertEquals("CONFIGURATION_CHANGED", sessions.completed.single().endReason)
    assertTrue(measurements.pending.isEmpty())
    assertEquals(2L, runtime.state.value.appliedProfileRevision)
    assertTrue(runtime.state.value.activeSession!!.id != sessions.completed.single().id)
}
```

- [ ] **Step 2: Run RED tests**

Run: `./gradlew testDebugUnitTest --tests '*MonitoringRuntimeConfigurationTest' --tests '*CalibrationPreviewViewModelTest'`

Expected: revision/apply API missing.

- [ ] **Step 3: Implement atomic Save and revision persistence**

Validate the complete profile first. Persist encoded profile and revision in one SharedPreferences edit. Never expose credentials in revision hashing or logs.

- [ ] **Step 4: Implement serialized Apply**

Under the existing lifecycle mutex: stop accepting, stop/await source, flush both buffers, finalize with `CONFIGURATION_CHANGED`, rebuild the vision runtime, reconnect, and conditionally start a new session using the previous target. Make applying the current revision a no-op.

- [ ] **Step 5: Prove preview isolation**

Assert preview results do not call measurement, alarm, session, or trend repositories and official producer count stays zero in preview-only mode.

- [ ] **Step 6: Run GREEN tests**

Run: `./gradlew testDebugUnitTest --tests '*MonitoringRuntimeConfigurationTest' --tests '*CalibrationPreviewViewModelTest' --tests '*MonitoringRuntimeTest'`

Expected: all pass, including repeated Apply and concurrent stop/apply safety.

- [ ] **Step 7: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/data/settings app/src/main/java/com/filamentvision/domain/monitoring app/src/main/java/com/filamentvision/model/MonitoringSession.kt app/src/main/java/com/filamentvision/vision/runtime app/src/test/java/com/filamentvision/domain/monitoring app/src/test/java/com/filamentvision/ui/calibration
git commit -m "feat: separate saved and applied calibration profiles"
```

### Task 6: Reusable simultaneous pipeline UI

**Files:**
- Create: `app/src/main/java/com/filamentvision/ui/vision/VisionPipelineCards.kt`
- Create: `app/src/main/java/com/filamentvision/ui/vision/VisionDiagnosticsScreen.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/components/camera/CameraVisualFrameArea.kt`
- Remove: `app/src/main/java/com/filamentvision/ui/components/camera/CameraModeSelector.kt`
- Remove: `app/src/main/java/com/filamentvision/ui/camera/CameraDisplayPipeline.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/camera/CameraDetailScreen.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/camera/CameraDetailUiState.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/camera/CameraDetailViewModel.kt`
- Modify: `app/src/main/java/com/filamentvision/navigation/Routes.kt`
- Modify: `app/src/main/java/com/filamentvision/navigation/AppNavigation.kt`
- Remove: `app/src/test/java/com/filamentvision/ui/camera/CameraDisplayPipelineTest.kt`
- Test: `app/src/androidTest/java/com/filamentvision/ui/vision/VisionDiagnosticsScreenTest.kt`

**Interfaces:**
- Produces: `VisionPipelineSection(cameraId, result, inputState, expanded, onExpandedChange)`.
- Produces: stable cards `RawStageCard`, `GrayscaleStageCard`, `ThresholdStageCard`, `EdgeStageCard`, `DetectionStageCard`, and `MeasurementStageCard`.
- Adds `VISION_DIAGNOSTICS_ROUTE`; the existing system Diagnostics route remains separate.

- [ ] **Step 1: Write a failing Compose semantics test**

```kotlin
@Test fun allPipelineStagesExistAtTheSameTime() {
    compose.setContent { VisionPipelineSection("A", fixedResult(), InputState.Streaming, true) {} }
    listOf("Raw", "Grayscale", "Threshold", "Edge", "Detection", "Measurement")
        .forEach { compose.onNodeWithText(it).assertExists() }
}
```

Add a failure-state test asserting `NO EDGES` is shown and no previous edge overlay semantics remain.

- [ ] **Step 2: Run RED Android test compile/test**

Run: `./gradlew compileDebugAndroidTestKotlin`

Expected: missing pipeline section/cards.

- [ ] **Step 3: Implement stable preview rendering and overlays**

Convert `PreviewFrame` to `ImageBitmap` with `remember(result.timestamp, stage)`. Draw ROI and detected edges in the same transformed preview coordinate space. Use `BoxWithConstraints`: compact layouts stack cards; width at least 720 dp uses a three-column grid.

Below the cards, show effective threshold/mode, transformed ROI, `mmPerPixel`, pixel width, diameter, confidence, detection status, transformed resolution, source image format, processing FPS, decode duration, and detection duration from the same result.

- [ ] **Step 4: Remove mutually exclusive mode state**

Delete `CameraDisplayMode`, mode selector, and UI-side `CameraDisplayPipeline` calls. Camera pages observe `VisionPipelineRuntime` results only.

- [ ] **Step 5: Run GREEN tests**

Run: `./gradlew connectedDebugAndroidTest`

Expected: stage coexistence, waiting, and failure semantics pass.

- [ ] **Step 6: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/ui/vision app/src/main/java/com/filamentvision/ui/camera app/src/main/java/com/filamentvision/ui/components/camera app/src/androidTest/java/com/filamentvision/ui/vision
git commit -m "feat: show complete vision pipeline simultaneously"
```

### Task 7: Calibration Preview screen and unsaved-exit workflow

**Files:**
- Create: `app/src/main/java/com/filamentvision/ui/calibration/CalibrationPreviewScreen.kt`
- Create: `app/src/main/java/com/filamentvision/ui/calibration/CalibrationControls.kt`
- Modify: `app/src/main/java/com/filamentvision/navigation/Routes.kt`
- Modify: `app/src/main/java/com/filamentvision/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/device/DeviceScreen.kt`
- Test: `app/src/androidTest/java/com/filamentvision/ui/calibration/CalibrationPreviewScreenTest.kt`

**Interfaces:**
- Adds route `calibration/{cameraId}` and `calibrationRoute(cameraId)`.
- Screen callbacks: `onBackRequested`, `onDiscardConfirmed`, `onSaveAndExit`, and `onApply`.

- [ ] **Step 1: Write failing UI behavior tests**

Assert the screen simultaneously shows all six stages, `UNSAVED CHANGES`, Save, Apply, Reset, Restore Defaults, and the three dirty-exit actions. Assert waiting state contains no generated preview.

- [ ] **Step 2: Run RED test compile**

Run: `./gradlew compileDebugAndroidTestKotlin`

Expected: missing screen/route.

- [ ] **Step 3: Implement live controls and ROI editing**

Bind sliders/text fields to draft-only methods. Implement ROI drag handles over the raw preview, map display coordinates back through the preview scale, clamp to transformed frame bounds, and publish latest coordinates through the conflated draft state.

- [ ] **Step 4: Implement dirty-exit confirmation**

Use `BackHandler` and a Material dialog with exactly `Discard changes`, `Continue editing`, and `Save changes`. Navigation occurs only after the selected command succeeds.

- [ ] **Step 5: Run GREEN UI tests**

Run: `./gradlew connectedDebugAndroidTest`

Expected: all calibration controls and exit semantics pass.

- [ ] **Step 6: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/ui/calibration app/src/main/java/com/filamentvision/navigation app/src/main/java/com/filamentvision/ui/device/DeviceScreen.kt app/src/androidTest/java/com/filamentvision/ui/calibration
git commit -m "feat: add real-input calibration preview screen"
```

### Task 8: Monitoring summary and fusion explanation

**Files:**
- Modify: `app/src/main/java/com/filamentvision/ui/monitor/MonitorUiState.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/monitor/MonitorViewModel.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/monitor/MonitorScreen.kt`
- Modify: `app/src/main/java/com/filamentvision/ui/components/CameraPreviewTile.kt`
- Modify: `app/src/main/java/com/filamentvision/vision/fusion/FusionEngine.kt`
- Test: `app/src/test/java/com/filamentvision/vision/fusion/FusionEngineTest.kt`
- Test: `app/src/androidTest/java/com/filamentvision/ui/monitor/MonitorVisionSummaryTest.kt`

**Interfaces:**
- Produces `FusionResult` containing final diameter, confidence, active camera IDs, A/B difference, timestamp compatibility, and fallback reason.
- Monitor state contains latest bounded previews/results, never raw transport bytes or full-resolution working arrays.

- [ ] **Step 1: Write failing fusion explanation tests**

```kotlin
@Test fun fallbackExplainsWhyOnlyCameraAWasUsed() {
    val result = engine.fuseDetailed(validA, lowConfidenceB, profile)!!
    assertEquals(setOf("A"), result.activeCameraIds)
    assertEquals(FusionReason.CAMERA_B_LOW_CONFIDENCE, result.reason)
}
```

- [ ] **Step 2: Run RED tests**

Run: `./gradlew testDebugUnitTest --tests '*FusionEngineTest'`

Expected: detailed fusion API missing.

- [ ] **Step 3: Implement detailed fusion and compact Monitor panels**

Keep existing `VisionMeasurement` output for Room/Alarm compatibility, but derive it from `FusionResult`. Display Raw plus Detection overlay for A/B and the fusion explanation. Add links to full diagnostics and calibration.

- [ ] **Step 4: Run GREEN tests**

Run: `./gradlew testDebugUnitTest --tests '*FusionEngineTest' && ./gradlew connectedDebugAndroidTest`

Expected: unit and Monitor semantics tests pass.

- [ ] **Step 5: Commit this task**

```bash
git add app/src/main/java/com/filamentvision/ui/monitor app/src/main/java/com/filamentvision/ui/components/CameraPreviewTile.kt app/src/main/java/com/filamentvision/vision/fusion app/src/test/java/com/filamentvision/vision/fusion app/src/androidTest/java/com/filamentvision/ui/monitor
git commit -m "feat: add live camera and fusion observability"
```

### Task 9: Full regression, device stability, and source audit

**Files:**
- Update: `docs/superpowers/plans/2026-09-30-vision-observability-calibration-preview.md` checkboxes.

- [ ] **Step 1: Run complete unit and instrumented suites**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin --no-daemon
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Expected: all tests, Lint, and both APK builds pass.

- [ ] **Step 2: Audit production source**

Run:

```powershell
rg -n "FakeVisionSource|FakeDetectionProvider|SimulationScenario|kotlin\.random|java\.util\.Random" app/src/main
```

Expected: no matches.

- [ ] **Step 3: Verify real waiting behavior on an AVD**

Install and cold-start the Debug APK. Confirm unconfigured/driver-unavailable UI shows no images, no measurements, no trend changes, Camera A/B offline or waiting, input producer zero, and official producer zero.

- [ ] **Step 4: Verify navigation and memory stability**

Navigate Monitor → Vision Diagnostics → Calibration A → Calibration B → Device repeatedly. Confirm observer counts return to baseline, official producer does not multiply, only latest A/B results are retained, and PSS stabilizes after initial destination allocations.

- [ ] **Step 5: Verify a controlled real-frame fixture through test-only source**

In instrumented/debug tests only, feed fixed frames at 30 FPS, confirm processing remains correct, UI preview publishes at 5–10 FPS, draft slider bursts resolve to the latest value, and formal measurement cadence remains 8 Hz.

- [ ] **Step 6: Commit verification fixes and record evidence**

```bash
git add app docs/superpowers/plans/2026-09-30-vision-observability-calibration-preview.md
git commit -m "test: verify vision diagnostics and calibration lifecycle"
```

Do not claim live Bluetooth/Wi-Fi streaming. Report that device-stream verification remains dependent on a manufacturer protocol and a real adapter.
