package com.filamentvision.data.settings

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
import com.filamentvision.model.FusionProfile
import com.filamentvision.model.GrayscaleMethod
import com.filamentvision.model.ImageEncoding
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.model.PixelFormat
import com.filamentvision.model.ThresholdMode
import com.filamentvision.model.TransportProtocol
import com.filamentvision.model.WifiEndpointProfile
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

object ConnectionProfileCodec {
    private const val VERSION = "1"

    fun encode(profile: ConnectionProfile): String {
        val values = Properties()
        values["version"] = VERSION
        values["topology"] = profile.topology.name
        values["preferredEndpointId"] = profile.preferredEndpointId
        values["autoReconnect"] = profile.autoReconnect.toString()
        values["connectionTimeoutMillis"] = profile.connectionTimeoutMillis.toString()
        values["fusion.cameraAWeight"] = profile.fusion.cameraAWeight.toString()
        values["fusion.cameraBWeight"] = profile.fusion.cameraBWeight.toString()
        values["fusion.minimumConfidence"] = profile.fusion.minimumConfidence.toString()
        values["fusion.maximumTimestampDifferenceMillis"] = profile.fusion.maximumTimestampDifferenceMillis.toString()
        values["endpoint.count"] = profile.endpoints.size.toString()
        profile.endpoints.forEachIndexed { index, endpoint -> writeEndpoint(values, "endpoint.$index", endpoint) }
        values["camera.count"] = profile.cameras.size.toString()
        profile.cameras.forEachIndexed { index, camera -> writeCamera(values, "camera.$index", camera) }
        return StringWriter().also { values.store(it, null) }.toString()
    }

    fun decodeOrDefault(encoded: String?): ConnectionProfile {
        if (encoded.isNullOrBlank()) return ConnectionProfile()
        return runCatching {
            val values = Properties().apply { load(StringReader(encoded)) }
            require(values.getProperty("version") == VERSION)
            val endpoints = List(values.int("endpoint.count")) { readEndpoint(values, "endpoint.$it") }
            val cameras = List(values.int("camera.count")) { readCamera(values, "camera.$it") }
            ConnectionProfile(
                topology = values.enum("topology"),
                endpoints = endpoints,
                cameras = cameras,
                preferredEndpointId = values.text("preferredEndpointId"),
                autoReconnect = values.boolean("autoReconnect"),
                connectionTimeoutMillis = values.long("connectionTimeoutMillis"),
                fusion = FusionProfile(
                    values.double("fusion.cameraAWeight"),
                    values.double("fusion.cameraBWeight"),
                    values.double("fusion.minimumConfidence"),
                    values.long("fusion.maximumTimestampDifferenceMillis"),
                ),
            )
        }.getOrDefault(ConnectionProfile())
    }

    private fun writeEndpoint(values: Properties, prefix: String, endpoint: EndpointProfile) {
        values["$prefix.kind"] = when (endpoint) { is WifiEndpointProfile -> "wifi"; is BluetoothEndpointProfile -> "bluetooth" }
        values["$prefix.id"] = endpoint.endpointId
        when (endpoint) {
            is WifiEndpointProfile -> {
                values["$prefix.protocol"] = endpoint.protocol.name
                values["$prefix.host"] = endpoint.host
                values["$prefix.port"] = endpoint.port.toString()
                values["$prefix.urlPath"] = endpoint.urlPath
                values["$prefix.username"] = endpoint.username
                values["$prefix.credentialKey"] = endpoint.credentialKey.orEmpty()
            }
            is BluetoothEndpointProfile -> {
                values["$prefix.mode"] = endpoint.mode.name
                values["$prefix.deviceName"] = endpoint.deviceName
                values["$prefix.deviceAddress"] = endpoint.deviceAddress
                values["$prefix.deviceUid"] = endpoint.deviceUid
                values["$prefix.serviceUuid"] = endpoint.serviceUuid
                values["$prefix.imageCharacteristicUuid"] = endpoint.imageCharacteristicUuid
                values["$prefix.commandCharacteristicUuid"] = endpoint.commandCharacteristicUuid
                values["$prefix.mtu"] = endpoint.mtu.toString()
                values["$prefix.packetHeaderLength"] = endpoint.packetHeaderLength.toString()
            }
        }
    }

    private fun readEndpoint(values: Properties, prefix: String): EndpointProfile = when (values.text("$prefix.kind")) {
        "wifi" -> WifiEndpointProfile(
            values.text("$prefix.id"), values.enum("$prefix.protocol"), values.text("$prefix.host"),
            values.int("$prefix.port"), values.text("$prefix.urlPath"), values.text("$prefix.username"),
            values.text("$prefix.credentialKey").ifBlank { null },
        )
        "bluetooth" -> BluetoothEndpointProfile(
            values.text("$prefix.id"), values.enum("$prefix.mode"), values.text("$prefix.deviceName"),
            values.text("$prefix.deviceAddress"), values.text("$prefix.deviceUid"), values.text("$prefix.serviceUuid"),
            values.text("$prefix.imageCharacteristicUuid"), values.text("$prefix.commandCharacteristicUuid"),
            values.int("$prefix.mtu"), values.int("$prefix.packetHeaderLength"),
        )
        else -> error("Unknown endpoint kind")
    }

    private fun writeCamera(values: Properties, prefix: String, camera: CameraProfile) {
        values["$prefix.id"] = camera.cameraId
        values["$prefix.endpointId"] = camera.endpointId
        values["$prefix.cameraUid"] = camera.cameraUid
        values["$prefix.channelId"] = camera.channelId
        values["$prefix.streamId"] = camera.streamId
        with(camera.frameParsing) {
            values["$prefix.parsing.boundaryMode"] = boundaryMode.name
            values["$prefix.parsing.headerHex"] = headerHex
            values["$prefix.parsing.footerHex"] = footerHex
            values["$prefix.parsing.lengthFieldOffset"] = lengthFieldOffset.toString()
            values["$prefix.parsing.lengthFieldSizeBytes"] = lengthFieldSizeBytes.toString()
            values["$prefix.parsing.fixedFrameSize"] = fixedFrameSize.toString()
            values["$prefix.parsing.messageTypeOffset"] = messageTypeOffset?.toString().orEmpty()
            values["$prefix.parsing.cameraIdentifierOffset"] = cameraIdentifierOffset?.toString().orEmpty()
            values["$prefix.parsing.cameraIdentifierSize"] = cameraIdentifierSize.toString()
            values["$prefix.parsing.payloadOffset"] = payloadOffset.toString()
            values["$prefix.parsing.maximumFrameBytes"] = maximumFrameBytes.toString()
            values["$prefix.parsing.byteOrder"] = byteOrder.name
        }
        with(camera.imageFormat) {
            values["$prefix.image.encoding"] = encoding.name
            values["$prefix.image.width"] = width.toString()
            values["$prefix.image.height"] = height.toString()
            values["$prefix.image.pixelFormat"] = pixelFormat.name
            values["$prefix.image.bitDepth"] = bitDepth.toString()
            values["$prefix.image.rowStride"] = rowStride.toString()
            values["$prefix.image.pixelStride"] = pixelStride.toString()
            values["$prefix.image.planeLayout"] = planeLayout
            values["$prefix.image.endian"] = endian.name
            values["$prefix.image.rotationDegrees"] = rotationDegrees.toString()
            values["$prefix.image.mirror"] = mirrorHorizontally.toString()
            values["$prefix.image.mirrorVertical"] = mirrorVertically.toString()
        }
        with(camera.calibration) {
            values["$prefix.calibration.roiLeft"] = roiLeft.toString()
            values["$prefix.calibration.roiTop"] = roiTop.toString()
            values["$prefix.calibration.roiRight"] = roiRight.toString()
            values["$prefix.calibration.roiBottom"] = roiBottom.toString()
            values["$prefix.calibration.grayscaleMethod"] = grayscaleMethod.name
            values["$prefix.calibration.thresholdMode"] = thresholdMode.name
            values["$prefix.calibration.manualThreshold"] = manualThreshold.toString()
            values["$prefix.calibration.edgeSensitivity"] = edgeSensitivity.toString()
            values["$prefix.calibration.minimumPixelWidth"] = minimumPixelWidth.toString()
            values["$prefix.calibration.maximumPixelWidth"] = maximumPixelWidth.toString()
            values["$prefix.calibration.mmPerPixel"] = mmPerPixel.toString()
            values["$prefix.calibration.minimumConfidence"] = minimumConfidence.toString()
        }
    }

    private fun readCamera(values: Properties, prefix: String): CameraProfile = CameraProfile(
        cameraId = values.text("$prefix.id"),
        endpointId = values.text("$prefix.endpointId"),
        cameraUid = values.text("$prefix.cameraUid"),
        channelId = values.text("$prefix.channelId"),
        streamId = values.text("$prefix.streamId"),
        frameParsing = FrameParsingProfile(
            boundaryMode = values.enum("$prefix.parsing.boundaryMode"),
            headerHex = values.text("$prefix.parsing.headerHex"),
            footerHex = values.text("$prefix.parsing.footerHex"),
            lengthFieldOffset = values.int("$prefix.parsing.lengthFieldOffset"),
            lengthFieldSizeBytes = values.int("$prefix.parsing.lengthFieldSizeBytes"),
            fixedFrameSize = values.int("$prefix.parsing.fixedFrameSize"),
            messageTypeOffset = values.optionalInt("$prefix.parsing.messageTypeOffset"),
            cameraIdentifierOffset = values.optionalInt("$prefix.parsing.cameraIdentifierOffset"),
            cameraIdentifierSize = values.int("$prefix.parsing.cameraIdentifierSize"),
            payloadOffset = values.int("$prefix.parsing.payloadOffset"),
            maximumFrameBytes = values.int("$prefix.parsing.maximumFrameBytes"),
            byteOrder = values.enum("$prefix.parsing.byteOrder"),
        ),
        imageFormat = ImageFormatProfile(
            encoding = values.enum("$prefix.image.encoding"), width = values.int("$prefix.image.width"),
            height = values.int("$prefix.image.height"), pixelFormat = values.enum("$prefix.image.pixelFormat"),
            bitDepth = values.int("$prefix.image.bitDepth"), rowStride = values.int("$prefix.image.rowStride"),
            pixelStride = values.int("$prefix.image.pixelStride"), planeLayout = values.text("$prefix.image.planeLayout"),
            endian = values.enum("$prefix.image.endian"), rotationDegrees = values.int("$prefix.image.rotationDegrees"),
            mirrorHorizontally = values.boolean("$prefix.image.mirror"),
            mirrorVertically = values.optionalBoolean("$prefix.image.mirrorVertical", default = false),
        ),
        calibration = CalibrationProfile(
            roiLeft = values.int("$prefix.calibration.roiLeft"), roiTop = values.int("$prefix.calibration.roiTop"),
            roiRight = values.int("$prefix.calibration.roiRight"), roiBottom = values.int("$prefix.calibration.roiBottom"),
            grayscaleMethod = values.enum("$prefix.calibration.grayscaleMethod"), thresholdMode = values.enum("$prefix.calibration.thresholdMode"),
            manualThreshold = values.int("$prefix.calibration.manualThreshold"), edgeSensitivity = values.int("$prefix.calibration.edgeSensitivity"),
            minimumPixelWidth = values.int("$prefix.calibration.minimumPixelWidth"), maximumPixelWidth = values.int("$prefix.calibration.maximumPixelWidth"),
            mmPerPixel = values.double("$prefix.calibration.mmPerPixel"), minimumConfidence = values.double("$prefix.calibration.minimumConfidence"),
        ),
    )

    private fun Properties.text(key: String) = getProperty(key) ?: error("Missing $key")
    private fun Properties.int(key: String) = text(key).toInt()
    private fun Properties.optionalInt(key: String) = text(key).ifBlank { null }?.toInt()
    private fun Properties.long(key: String) = text(key).toLong()
    private fun Properties.double(key: String) = text(key).toDouble()
    private fun Properties.boolean(key: String) = text(key).toBooleanStrict()
    private fun Properties.optionalBoolean(key: String, default: Boolean) =
        getProperty(key)?.toBooleanStrictOrNull() ?: default
    private inline fun <reified T : Enum<T>> Properties.enum(key: String): T = enumValueOf(text(key))
}
