package com.filamentvision.model

enum class ConnectionTopology { SHARED_CONNECTION, INDEPENDENT_CONNECTION }

enum class TransportProtocol {
    WIFI_TCP,
    WIFI_UDP,
    HTTP,
    WEBSOCKET,
    RTSP,
    BLE,
    CLASSIC_BLUETOOTH,
}

enum class BluetoothMode { BLE, CLASSIC }

sealed interface EndpointProfile {
    val endpointId: String
    val protocol: TransportProtocol
}

data class WifiEndpointProfile(
    override val endpointId: String,
    override val protocol: TransportProtocol,
    val host: String,
    val port: Int,
    val urlPath: String = "",
    val username: String = "",
    /** Key into a separate credential store. Never contains the password itself. */
    val credentialKey: String? = null,
) : EndpointProfile

data class BluetoothEndpointProfile(
    override val endpointId: String,
    val mode: BluetoothMode,
    val deviceName: String,
    val deviceAddress: String,
    val deviceUid: String,
    val serviceUuid: String,
    val imageCharacteristicUuid: String,
    val commandCharacteristicUuid: String,
    val mtu: Int,
    val packetHeaderLength: Int,
) : EndpointProfile {
    override val protocol: TransportProtocol = when (mode) {
        BluetoothMode.BLE -> TransportProtocol.BLE
        BluetoothMode.CLASSIC -> TransportProtocol.CLASSIC_BLUETOOTH
    }
}

enum class FrameBoundaryMode { HEADER_FOOTER, LENGTH_FIELD, FIXED_SIZE }
enum class ByteOrderProfile { BIG_ENDIAN, LITTLE_ENDIAN }

data class FrameParsingProfile(
    val boundaryMode: FrameBoundaryMode = FrameBoundaryMode.LENGTH_FIELD,
    val headerHex: String = "",
    val footerHex: String = "",
    val lengthFieldOffset: Int = 0,
    val lengthFieldSizeBytes: Int = 4,
    val fixedFrameSize: Int = 0,
    val messageTypeOffset: Int? = null,
    val cameraIdentifierOffset: Int? = null,
    val cameraIdentifierSize: Int = 0,
    val payloadOffset: Int = 0,
    val maximumFrameBytes: Int = 8_000_000,
    val byteOrder: ByteOrderProfile = ByteOrderProfile.BIG_ENDIAN,
)

enum class ImageEncoding { JPEG, PNG, GRAYSCALE_RAW, RGB_RAW, YUV_RAW }
enum class PixelFormat { ARGB8888, GRAY8, GRAY16, RGB24, RGBA32, NV12, NV21, YUV420P, YUY2 }

data class ImageFormatProfile(
    val encoding: ImageEncoding = ImageEncoding.JPEG,
    val width: Int = 0,
    val height: Int = 0,
    val pixelFormat: PixelFormat = PixelFormat.ARGB8888,
    val bitDepth: Int = 8,
    val rowStride: Int = 0,
    val pixelStride: Int = 0,
    val planeLayout: String = "",
    val endian: ByteOrderProfile = ByteOrderProfile.BIG_ENDIAN,
    val rotationDegrees: Int = 0,
    val mirrorHorizontally: Boolean = false,
    val mirrorVertically: Boolean = false,
)

enum class GrayscaleMethod { LUMA_BT601, AVERAGE }
enum class ThresholdMode { MANUAL, AUTOMATIC }

data class CalibrationProfile(
    val roiLeft: Int = 0,
    val roiTop: Int = 0,
    val roiRight: Int = 0,
    val roiBottom: Int = 0,
    val grayscaleMethod: GrayscaleMethod = GrayscaleMethod.LUMA_BT601,
    val thresholdMode: ThresholdMode = ThresholdMode.MANUAL,
    val manualThreshold: Int = 128,
    val edgeSensitivity: Int = 24,
    val minimumPixelWidth: Int = 1,
    val maximumPixelWidth: Int = 1,
    val mmPerPixel: Double = 0.0,
    val minimumConfidence: Double = 0.7,
)

data class CameraProfile(
    val cameraId: String,
    val endpointId: String,
    val cameraUid: String = "",
    val channelId: String = "",
    val streamId: String = "",
    val frameParsing: FrameParsingProfile = FrameParsingProfile(),
    val imageFormat: ImageFormatProfile = ImageFormatProfile(),
    val calibration: CalibrationProfile = CalibrationProfile(),
)

data class FusionProfile(
    val cameraAWeight: Double = 0.5,
    val cameraBWeight: Double = 0.5,
    val minimumConfidence: Double = 0.7,
    val maximumTimestampDifferenceMillis: Long = 250,
)

data class ConnectionProfile(
    val topology: ConnectionTopology = ConnectionTopology.SHARED_CONNECTION,
    val endpoints: List<EndpointProfile> = emptyList(),
    val cameras: List<CameraProfile> = listOf(CameraProfile("A", ""), CameraProfile("B", "")),
    val preferredEndpointId: String = "",
    val autoReconnect: Boolean = true,
    val connectionTimeoutMillis: Long = 5_000,
    val fusion: FusionProfile = FusionProfile(),
)
