package com.filamentvision.domain.connection

import com.filamentvision.model.BluetoothEndpointProfile
import com.filamentvision.model.BluetoothMode
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.ConnectionTopology
import com.filamentvision.model.EndpointProfile
import com.filamentvision.model.FrameBoundaryMode
import com.filamentvision.model.ImageEncoding
import com.filamentvision.model.PixelFormat
import com.filamentvision.model.WifiEndpointProfile
import java.util.UUID

sealed interface ProfileValidation {
    data object Valid : ProfileValidation
    data class Invalid(val errors: Map<String, String>) : ProfileValidation
}

object ConnectionProfileValidator {
    private const val MAX_IMAGE_DIMENSION = 16_384
    private const val MAX_IMAGE_PIXELS = 67_108_864L
    private val macAddress = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    fun validate(profile: ConnectionProfile): ProfileValidation {
        val errors = linkedMapOf<String, String>()
        val endpointsById = profile.endpoints.associateBy(EndpointProfile::endpointId)

        if (profile.endpoints.any { it.endpointId.isBlank() }) errors["endpoints"] = "Endpoint ID is required"
        if (endpointsById.size != profile.endpoints.size) errors["endpoints"] = "Endpoint IDs must be unique"
        profile.endpoints.forEach { endpoint -> validateEndpoint(endpoint, errors) }

        val camerasById = profile.cameras.associateBy { it.cameraId.uppercase() }
        if (camerasById.keys != setOf("A", "B")) errors["cameras"] = "Camera A and Camera B are required"
        profile.cameras.forEach { camera ->
            val prefix = "cameras.${camera.cameraId.uppercase()}"
            if (camera.endpointId !in endpointsById) errors["$prefix.endpointId"] = "Referenced endpoint does not exist"
            validateImage(camera.imageFormat, "$prefix.imageFormat", errors)
            validateParsing(camera.frameParsing, "$prefix.frameParsing", errors)
            with(camera.calibration) {
                if (roiLeft < 0 || roiTop < 0 || roiRight <= roiLeft || roiBottom <= roiTop) {
                    errors["$prefix.calibration.roi"] = "ROI must have positive bounds"
                }
                if (manualThreshold !in 0..255) errors["$prefix.calibration.manualThreshold"] = "Threshold must be 0 to 255"
                if (edgeSensitivity !in 1..255) errors["$prefix.calibration.edgeSensitivity"] = "Edge sensitivity must be 1 to 255"
                if (minimumPixelWidth <= 0 || minimumPixelWidth > maximumPixelWidth) {
                    errors["$prefix.calibration.pixelWidthRange"] = "Minimum width must not exceed maximum width"
                }
                if (!mmPerPixel.isFinite() || mmPerPixel <= 0.0) {
                    errors["$prefix.calibration.mmPerPixel"] = "Millimetres per pixel must be greater than zero"
                }
                if (minimumConfidence !in 0.0..1.0) {
                    errors["$prefix.calibration.minimumConfidence"] = "Confidence must be between zero and one"
                }
            }
        }

        if (profile.topology == ConnectionTopology.SHARED_CONNECTION &&
            profile.cameras.map { it.endpointId }.filter(String::isNotBlank).distinct().size > 1
        ) {
            errors["topology"] = "Shared cameras must reference one endpoint"
        }
        with(profile.fusion) {
            if (!cameraAWeight.isFinite() || cameraAWeight < 0.0) errors["fusion.cameraAWeight"] = "Fusion weight must not be negative"
            if (!cameraBWeight.isFinite() || cameraBWeight < 0.0) errors["fusion.cameraBWeight"] = "Fusion weight must not be negative"
            if (cameraAWeight.coerceAtLeast(0.0) + cameraBWeight.coerceAtLeast(0.0) == 0.0) {
                errors["fusion.weights"] = "At least one fusion weight must be greater than zero"
            }
            if (minimumConfidence !in 0.0..1.0) errors["fusion.minimumConfidence"] = "Confidence must be between zero and one"
            if (maximumTimestampDifferenceMillis !in 0..60_000) {
                errors["fusion.maximumTimestampDifferenceMillis"] = "Timestamp tolerance is out of range"
            }
        }
        if (profile.connectionTimeoutMillis !in 250..120_000) errors["connectionTimeoutMillis"] = "Timeout is out of range"
        if (profile.preferredEndpointId.isNotBlank() && profile.preferredEndpointId !in endpointsById) {
            errors["preferredEndpointId"] = "Preferred endpoint does not exist"
        }
        return if (errors.isEmpty()) ProfileValidation.Valid else ProfileValidation.Invalid(errors)
    }

    private fun validateEndpoint(endpoint: EndpointProfile, errors: MutableMap<String, String>) {
        val prefix = "endpoints.${endpoint.endpointId}"
        when (endpoint) {
            is WifiEndpointProfile -> {
                if (endpoint.host.isBlank()) errors["$prefix.host"] = "Host is required"
                if (endpoint.port !in 1..65_535) errors["$prefix.port"] = "Port must be between 1 and 65535"
                if (endpoint.protocol !in setOf(
                        com.filamentvision.model.TransportProtocol.WIFI_TCP,
                        com.filamentvision.model.TransportProtocol.WIFI_UDP,
                        com.filamentvision.model.TransportProtocol.HTTP,
                        com.filamentvision.model.TransportProtocol.WEBSOCKET,
                        com.filamentvision.model.TransportProtocol.RTSP,
                    )
                ) errors["$prefix.protocol"] = "Wi-Fi endpoint requires a network protocol"
            }
            is BluetoothEndpointProfile -> {
                if (endpoint.deviceAddress.isNotBlank() && !macAddress.matches(endpoint.deviceAddress.uppercase())) {
                    errors["$prefix.deviceAddress"] = "Invalid Bluetooth address"
                }
                if (endpoint.mode == BluetoothMode.BLE) {
                    if (!isUuid(endpoint.serviceUuid)) errors["$prefix.serviceUuid"] = "Invalid Service UUID"
                    if (!isUuid(endpoint.imageCharacteristicUuid)) {
                        errors["$prefix.imageCharacteristicUuid"] = "Invalid image characteristic UUID"
                    }
                    if (endpoint.commandCharacteristicUuid.isNotBlank() && !isUuid(endpoint.commandCharacteristicUuid)) {
                        errors["$prefix.commandCharacteristicUuid"] = "Invalid command characteristic UUID"
                    }
                }
                if (endpoint.mtu !in 23..517) errors["$prefix.mtu"] = "MTU must be between 23 and 517"
                if (endpoint.packetHeaderLength !in 0..64) {
                    errors["$prefix.packetHeaderLength"] = "Packet header length must be 0 to 64"
                }
            }
        }
    }

    private fun validateImage(
        image: com.filamentvision.model.ImageFormatProfile,
        prefix: String,
        errors: MutableMap<String, String>,
    ) {
        val dimensionsValid = image.width in 1..MAX_IMAGE_DIMENSION &&
            image.height in 1..MAX_IMAGE_DIMENSION && image.width.toLong() * image.height <= MAX_IMAGE_PIXELS
        if (!dimensionsValid) errors["$prefix.dimensions"] = "Image dimensions are invalid or unsafe"
        if (image.rotationDegrees !in setOf(0, 90, 180, 270)) errors["$prefix.rotationDegrees"] = "Rotation must be 0, 90, 180, or 270"
        if (image.encoding in setOf(ImageEncoding.GRAYSCALE_RAW, ImageEncoding.RGB_RAW, ImageEncoding.YUV_RAW)) {
            if (image.bitDepth !in setOf(8, 10, 12, 16, 24, 32)) errors["$prefix.bitDepth"] = "Unsupported bit depth"
            if (image.rowStride !in 0..64_000_000 || image.pixelStride !in 0..64) {
                errors["$prefix.stride"] = "Stride is invalid or unsafe"
            }
        }
        val compatible = when (image.encoding) {
            ImageEncoding.JPEG, ImageEncoding.PNG -> true
            ImageEncoding.GRAYSCALE_RAW -> image.pixelFormat in setOf(PixelFormat.GRAY8, PixelFormat.GRAY16)
            ImageEncoding.RGB_RAW -> image.pixelFormat in setOf(PixelFormat.RGB24, PixelFormat.RGBA32, PixelFormat.ARGB8888)
            ImageEncoding.YUV_RAW -> image.pixelFormat in setOf(PixelFormat.NV12, PixelFormat.NV21, PixelFormat.YUV420P, PixelFormat.YUY2)
        }
        if (!compatible) errors["$prefix.pixelFormat"] = "Pixel format does not match image encoding"
    }

    private fun validateParsing(
        parsing: com.filamentvision.model.FrameParsingProfile,
        prefix: String,
        errors: MutableMap<String, String>,
    ) {
        if (parsing.maximumFrameBytes !in 1..64_000_000) errors["$prefix.maximumFrameBytes"] = "Maximum frame size is unsafe"
        if (parsing.payloadOffset !in 0..parsing.maximumFrameBytes.coerceAtLeast(0)) {
            errors["$prefix.payloadOffset"] = "Payload offset is outside the configured frame"
        }
        if (parsing.messageTypeOffset != null && parsing.messageTypeOffset !in 0 until parsing.maximumFrameBytes.coerceAtLeast(1)) {
            errors["$prefix.messageTypeOffset"] = "Message type offset is outside the configured frame"
        }
        if (parsing.cameraIdentifierOffset != null &&
            (parsing.cameraIdentifierOffset !in 0 until parsing.maximumFrameBytes.coerceAtLeast(1) ||
                parsing.cameraIdentifierSize !in 1..64 ||
                parsing.cameraIdentifierOffset + parsing.cameraIdentifierSize > parsing.maximumFrameBytes)
        ) {
            errors["$prefix.cameraIdentifier"] = "Camera identifier location is invalid"
        }
        when (parsing.boundaryMode) {
            FrameBoundaryMode.HEADER_FOOTER -> if (!isHexBytes(parsing.headerHex) || !isHexBytes(parsing.footerHex)) {
                errors["$prefix.markers"] = "Header and footer are required"
            }
            FrameBoundaryMode.LENGTH_FIELD -> if (parsing.lengthFieldOffset < 0 || parsing.lengthFieldSizeBytes !in setOf(1, 2, 4)) {
                errors["$prefix.lengthField"] = "Length field must have a valid offset and size"
            }
            FrameBoundaryMode.FIXED_SIZE -> if (parsing.fixedFrameSize !in 1..parsing.maximumFrameBytes) {
                errors["$prefix.fixedFrameSize"] = "Fixed frame size is invalid"
            }
        }
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

    private fun isHexBytes(value: String): Boolean {
        val normalized = value.filterNot(Char::isWhitespace)
        return normalized.isNotEmpty() && normalized.length % 2 == 0 && normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
}
