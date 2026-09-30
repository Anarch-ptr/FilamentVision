package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.filamentvision.domain.connection.ConnectionProfileValidator
import com.filamentvision.domain.connection.ProfileValidation
import com.filamentvision.model.BluetoothEndpointProfile
import com.filamentvision.model.BluetoothMode
import com.filamentvision.model.ByteOrderProfile
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.ConnectionTopology
import com.filamentvision.model.EndpointProfile
import com.filamentvision.model.FrameBoundaryMode
import com.filamentvision.model.FrameParsingProfile
import com.filamentvision.model.GrayscaleMethod
import com.filamentvision.model.ImageEncoding
import com.filamentvision.model.PixelFormat
import com.filamentvision.model.ThresholdMode
import com.filamentvision.model.TransportProtocol
import com.filamentvision.model.WifiEndpointProfile

@Composable
fun ConnectionProfileForm(
    initialProfile: ConnectionProfile,
    onCancel: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    var profile by remember(initialProfile) { mutableStateOf(initialProfile.editable()) }
    var errors by remember(initialProfile) { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connection topology", fontWeight = FontWeight.SemiBold)
        EnumButton("Topology", profile.topology) { topology -> profile = profile.withTopology(topology) }
        BooleanButton("Auto reconnect", profile.autoReconnect) { profile = profile.copy(autoReconnect = it) }
        NumberField("Connection timeout (ms)", profile.connectionTimeoutMillis.toString()) {
            profile = profile.copy(connectionTimeoutMillis = it.toLongOrNull() ?: 0L)
        }
        profile.endpoints.forEachIndexed { index, endpoint ->
            EndpointEditor(endpoint, errors) { updated ->
                val endpoints = profile.endpoints.toMutableList().apply { set(index, updated) }
                profile = profile.copy(endpoints = endpoints)
            }
        }
        profile.cameras.sortedBy(CameraProfile::cameraId).forEachIndexed { index, camera ->
            CameraEditor(camera, profile.endpoints, errors) { updated ->
                val cameras = profile.cameras.sortedBy(CameraProfile::cameraId).toMutableList().apply { set(index, updated) }
                profile = profile.copy(cameras = cameras)
            }
        }
        HorizontalDivider()
        Text("Fusion", fontWeight = FontWeight.SemiBold)
        DecimalField("Camera A weight", profile.fusion.cameraAWeight.toString()) {
            profile = profile.copy(fusion = profile.fusion.copy(cameraAWeight = it.toDoubleOrNull() ?: -1.0))
        }
        DecimalField("Camera B weight", profile.fusion.cameraBWeight.toString()) {
            profile = profile.copy(fusion = profile.fusion.copy(cameraBWeight = it.toDoubleOrNull() ?: -1.0))
        }
        DecimalField("Minimum confidence", profile.fusion.minimumConfidence.toString()) {
            profile = profile.copy(fusion = profile.fusion.copy(minimumConfidence = it.toDoubleOrNull() ?: -1.0))
        }
        NumberField("Maximum timestamp difference (ms)", profile.fusion.maximumTimestampDifferenceMillis.toString()) {
            profile = profile.copy(fusion = profile.fusion.copy(maximumTimestampDifferenceMillis = it.toLongOrNull() ?: -1L))
        }
        if (errors.isNotEmpty()) {
            Text("Please correct these settings:", fontWeight = FontWeight.SemiBold)
            errors.forEach { (field, message) -> Text("$field: $message") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    when (val validation = ConnectionProfileValidator.validate(profile)) {
                        ProfileValidation.Valid -> onSave(profile)
                        is ProfileValidation.Invalid -> errors = validation.errors
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save configuration") }
        }
        Text("Saving a protocol does not install its driver. Unsupported selections remain DRIVER UNAVAILABLE.")
    }
}

@Composable
private fun EndpointEditor(
    endpoint: EndpointProfile,
    errors: Map<String, String>,
    onChange: (EndpointProfile) -> Unit,
) {
    val prefix = "endpoints.${endpoint.endpointId}"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Endpoint ${endpoint.endpointId}", fontWeight = FontWeight.SemiBold)
            EnumButton("Transport", endpoint.protocol) { protocol -> onChange(endpoint.forProtocol(protocol)) }
            when (endpoint) {
                is WifiEndpointProfile -> {
                    ProfileField("Hostname / IP", endpoint.host, errors["$prefix.host"]) { onChange(endpoint.copy(host = it)) }
                    NumberField("Port", endpoint.port.toString(), errors["$prefix.port"]) { onChange(endpoint.copy(port = it.toIntOrNull() ?: 0)) }
                    ProfileField("URL path", endpoint.urlPath) { onChange(endpoint.copy(urlPath = it)) }
                    ProfileField("Username (optional)", endpoint.username) { onChange(endpoint.copy(username = it)) }
                    ProfileField("Credential reference (optional)", endpoint.credentialKey.orEmpty()) {
                        onChange(endpoint.copy(credentialKey = it.ifBlank { null }))
                    }
                }
                is BluetoothEndpointProfile -> {
                    EnumButton("Bluetooth mode", endpoint.mode) { onChange(endpoint.copy(mode = it)) }
                    ProfileField("Device name", endpoint.deviceName) { onChange(endpoint.copy(deviceName = it)) }
                    ProfileField("MAC address", endpoint.deviceAddress, errors["$prefix.deviceAddress"]) { onChange(endpoint.copy(deviceAddress = it)) }
                    ProfileField("Device UID", endpoint.deviceUid) { onChange(endpoint.copy(deviceUid = it)) }
                    ProfileField("Service UUID", endpoint.serviceUuid, errors["$prefix.serviceUuid"]) { onChange(endpoint.copy(serviceUuid = it)) }
                    ProfileField("Image characteristic UUID", endpoint.imageCharacteristicUuid, errors["$prefix.imageCharacteristicUuid"]) {
                        onChange(endpoint.copy(imageCharacteristicUuid = it))
                    }
                    ProfileField("Command characteristic UUID", endpoint.commandCharacteristicUuid) {
                        onChange(endpoint.copy(commandCharacteristicUuid = it))
                    }
                    NumberField("MTU", endpoint.mtu.toString(), errors["$prefix.mtu"]) { onChange(endpoint.copy(mtu = it.toIntOrNull() ?: 0)) }
                    NumberField("Packet header length", endpoint.packetHeaderLength.toString()) {
                        onChange(endpoint.copy(packetHeaderLength = it.toIntOrNull() ?: -1))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraEditor(
    camera: CameraProfile,
    endpoints: List<EndpointProfile>,
    errors: Map<String, String>,
    onChange: (CameraProfile) -> Unit,
) {
    val prefix = "cameras.${camera.cameraId}"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera ${camera.cameraId}", fontWeight = FontWeight.SemiBold)
            EnumButton("Endpoint", camera.endpointId, endpoints.map(EndpointProfile::endpointId)) { onChange(camera.copy(endpointId = it)) }
            ProfileField("Camera UID", camera.cameraUid) { onChange(camera.copy(cameraUid = it)) }
            ProfileField("Channel ID", camera.channelId) { onChange(camera.copy(channelId = it)) }
            ProfileField("Stream ID", camera.streamId) { onChange(camera.copy(streamId = it)) }
            FrameParsingEditor(camera.frameParsing, errors, prefix) { onChange(camera.copy(frameParsing = it)) }
            ImageFormatEditor(camera.imageFormat) { onChange(camera.copy(imageFormat = it)) }
            CalibrationEditor(camera.calibration, errors, prefix) { onChange(camera.copy(calibration = it)) }
        }
    }
}

@Composable
private fun FrameParsingEditor(
    value: FrameParsingProfile,
    errors: Map<String, String>,
    prefix: String,
    onChange: (FrameParsingProfile) -> Unit,
) {
    Text("Frame parsing", fontWeight = FontWeight.SemiBold)
    EnumButton("Boundary", value.boundaryMode) { onChange(value.copy(boundaryMode = it)) }
    if (value.boundaryMode == FrameBoundaryMode.HEADER_FOOTER) {
        ProfileField("Frame header (hex)", value.headerHex) { onChange(value.copy(headerHex = it)) }
        ProfileField("Frame footer (hex)", value.footerHex) { onChange(value.copy(footerHex = it)) }
    }
    if (value.boundaryMode == FrameBoundaryMode.LENGTH_FIELD) {
        NumberField("Length field offset", value.lengthFieldOffset.toString()) { onChange(value.copy(lengthFieldOffset = it.toIntOrNull() ?: -1)) }
        NumberField("Length field bytes", value.lengthFieldSizeBytes.toString()) { onChange(value.copy(lengthFieldSizeBytes = it.toIntOrNull() ?: 0)) }
    }
    if (value.boundaryMode == FrameBoundaryMode.FIXED_SIZE) {
        NumberField("Fixed frame size", value.fixedFrameSize.toString()) { onChange(value.copy(fixedFrameSize = it.toIntOrNull() ?: 0)) }
    }
    NumberField("Message type offset (-1 unused)", (value.messageTypeOffset ?: -1).toString()) {
        onChange(value.copy(messageTypeOffset = it.toIntOrNull()?.takeIf { offset -> offset >= 0 }))
    }
    NumberField("Camera identifier offset (-1 unused)", (value.cameraIdentifierOffset ?: -1).toString()) {
        onChange(value.copy(cameraIdentifierOffset = it.toIntOrNull()?.takeIf { offset -> offset >= 0 }))
    }
    NumberField("Camera identifier bytes", value.cameraIdentifierSize.toString()) { onChange(value.copy(cameraIdentifierSize = it.toIntOrNull() ?: 0)) }
    NumberField("Image payload offset", value.payloadOffset.toString()) { onChange(value.copy(payloadOffset = it.toIntOrNull() ?: -1)) }
    NumberField("Maximum frame bytes", value.maximumFrameBytes.toString(), errors["$prefix.frameParsing.maximumFrameBytes"]) {
        onChange(value.copy(maximumFrameBytes = it.toIntOrNull() ?: 0))
    }
    EnumButton("Byte order", value.byteOrder) { onChange(value.copy(byteOrder = it)) }
}

@Composable
private fun ImageFormatEditor(value: com.filamentvision.model.ImageFormatProfile, onChange: (com.filamentvision.model.ImageFormatProfile) -> Unit) {
    Text("Image format", fontWeight = FontWeight.SemiBold)
    EnumButton("Encoding", value.encoding) { onChange(value.copy(encoding = it)) }
    EnumButton("Pixel format", value.pixelFormat) { onChange(value.copy(pixelFormat = it)) }
    NumberField("Expected width", value.width.toString()) { onChange(value.copy(width = it.toIntOrNull() ?: 0)) }
    NumberField("Expected height", value.height.toString()) { onChange(value.copy(height = it.toIntOrNull() ?: 0)) }
    NumberField("Bit depth", value.bitDepth.toString()) { onChange(value.copy(bitDepth = it.toIntOrNull() ?: 0)) }
    NumberField("Row stride (0 automatic)", value.rowStride.toString()) { onChange(value.copy(rowStride = it.toIntOrNull() ?: -1)) }
    NumberField("Pixel stride (0 automatic)", value.pixelStride.toString()) { onChange(value.copy(pixelStride = it.toIntOrNull() ?: -1)) }
    ProfileField("Plane layout", value.planeLayout) { onChange(value.copy(planeLayout = it)) }
    EnumButton("Endian", value.endian) { onChange(value.copy(endian = it)) }
    EnumButton("Rotation", value.rotationDegrees, listOf(0, 90, 180, 270)) { onChange(value.copy(rotationDegrees = it)) }
    BooleanButton("Mirror horizontally", value.mirrorHorizontally) { onChange(value.copy(mirrorHorizontally = it)) }
    BooleanButton("Mirror vertically", value.mirrorVertically) { onChange(value.copy(mirrorVertically = it)) }
}

@Composable
private fun CalibrationEditor(value: CalibrationProfile, errors: Map<String, String>, prefix: String, onChange: (CalibrationProfile) -> Unit) {
    Text("Detection and calibration", fontWeight = FontWeight.SemiBold)
    NumberField("ROI left", value.roiLeft.toString()) { onChange(value.copy(roiLeft = it.toIntOrNull() ?: -1)) }
    NumberField("ROI top", value.roiTop.toString()) { onChange(value.copy(roiTop = it.toIntOrNull() ?: -1)) }
    NumberField("ROI right", value.roiRight.toString()) { onChange(value.copy(roiRight = it.toIntOrNull() ?: 0)) }
    NumberField("ROI bottom", value.roiBottom.toString()) { onChange(value.copy(roiBottom = it.toIntOrNull() ?: 0)) }
    EnumButton("Grayscale", value.grayscaleMethod) { onChange(value.copy(grayscaleMethod = it)) }
    EnumButton("Threshold mode", value.thresholdMode) { onChange(value.copy(thresholdMode = it)) }
    NumberField("Manual threshold", value.manualThreshold.toString()) { onChange(value.copy(manualThreshold = it.toIntOrNull() ?: -1)) }
    NumberField("Edge sensitivity", value.edgeSensitivity.toString()) { onChange(value.copy(edgeSensitivity = it.toIntOrNull() ?: 0)) }
    NumberField("Minimum pixel width", value.minimumPixelWidth.toString()) { onChange(value.copy(minimumPixelWidth = it.toIntOrNull() ?: 0)) }
    NumberField("Maximum pixel width", value.maximumPixelWidth.toString()) { onChange(value.copy(maximumPixelWidth = it.toIntOrNull() ?: 0)) }
    DecimalField("Millimetres per pixel", value.mmPerPixel.toString(), errors["$prefix.calibration.mmPerPixel"]) {
        onChange(value.copy(mmPerPixel = it.toDoubleOrNull() ?: 0.0))
    }
    DecimalField("Minimum confidence", value.minimumConfidence.toString()) { onChange(value.copy(minimumConfidence = it.toDoubleOrNull() ?: -1.0)) }
}

@Composable
private fun ProfileField(label: String, value: String, error: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, isError = error != null,
        supportingText = error?.let { message -> { Text(message) } }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NumberField(label: String, value: String, error: String? = null, onChange: (String) -> Unit) =
    OutlinedTextField(value, { onChange(it.filter { char -> char.isDigit() || char == '-' }) }, label = { Text(label) },
        isError = error != null, supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())

@Composable
private fun DecimalField(label: String, value: String, error: String? = null, onChange: (String) -> Unit) =
    OutlinedTextField(value, { onChange(it.filter { char -> char.isDigit() || char == '.' || char == '-' }) }, label = { Text(label) },
        isError = error != null, supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())

@Composable
private inline fun <reified T : Enum<T>> EnumButton(label: String, value: T, noinline onChange: (T) -> Unit) =
    EnumButton(label, value, enumValues<T>().toList(), onChange)

@Composable
private fun <T> EnumButton(label: String, value: T, values: List<T>, onChange: (T) -> Unit) {
    OutlinedButton(onClick = { onChange(values[(values.indexOf(value) + 1).mod(values.size)]) }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value")
    }
}

@Composable
private fun BooleanButton(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    OutlinedButton(onClick = { onChange(!value) }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${if (value) "ON" else "OFF"}") }
}

private fun EndpointProfile.forProtocol(protocol: TransportProtocol): EndpointProfile = when (protocol) {
    TransportProtocol.WIFI_TCP, TransportProtocol.WIFI_UDP, TransportProtocol.HTTP, TransportProtocol.WEBSOCKET, TransportProtocol.RTSP ->
        (this as? WifiEndpointProfile)?.copy(protocol = protocol)
            ?: WifiEndpointProfile(endpointId, protocol, "", 0)
    TransportProtocol.BLE, TransportProtocol.CLASSIC_BLUETOOTH ->
        (this as? BluetoothEndpointProfile)?.copy(mode = if (protocol == TransportProtocol.BLE) BluetoothMode.BLE else BluetoothMode.CLASSIC)
            ?: BluetoothEndpointProfile(endpointId, if (protocol == TransportProtocol.BLE) BluetoothMode.BLE else BluetoothMode.CLASSIC, "", "", "", "", "", "", 247, 0)
}

private fun ConnectionProfile.editable(): ConnectionProfile = withTopology(topology)

private fun ConnectionProfile.withTopology(newTopology: ConnectionTopology): ConnectionProfile {
    val first = endpoints.firstOrNull() ?: WifiEndpointProfile("endpoint-a", TransportProtocol.WIFI_TCP, "", 0)
    val normalized = when (newTopology) {
        ConnectionTopology.SHARED_CONNECTION -> listOf(first)
        ConnectionTopology.INDEPENDENT_CONNECTION -> listOf(
            first,
            endpoints.getOrNull(1) ?: WifiEndpointProfile("endpoint-b", TransportProtocol.WIFI_TCP, "", 0),
        )
    }
    val cameraA = cameras.firstOrNull { it.cameraId.equals("A", true) } ?: CameraProfile("A", normalized[0].endpointId)
    val cameraB = cameras.firstOrNull { it.cameraId.equals("B", true) } ?: CameraProfile("B", normalized.last().endpointId)
    return copy(
        topology = newTopology,
        endpoints = normalized,
        preferredEndpointId = normalized.first().endpointId,
        cameras = listOf(
            cameraA.copy(endpointId = normalized.first().endpointId),
            cameraB.copy(endpointId = if (newTopology == ConnectionTopology.SHARED_CONNECTION) normalized.first().endpointId else normalized.last().endpointId),
        ),
    )
}
