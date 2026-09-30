package com.filamentvision.input.frame

import com.filamentvision.input.packet.PacketAssembler
import com.filamentvision.model.ByteOrderProfile
import com.filamentvision.model.FrameBoundaryMode
import com.filamentvision.model.FrameParsingProfile

data class EncodedCameraFrame(
    val cameraId: String,
    val timestampMillis: Long,
    val payload: ByteArray,
)

data class FrameParseBatch(
    val frames: List<EncodedCameraFrame>,
    val errors: List<String>,
)

class ConfigurableFrameParser(
    private val profile: FrameParsingProfile,
    private val cameraIdentifiers: Map<String, ByteArray>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val assembler = PacketAssembler(profile.maximumFrameBytes)
    val bufferedByteCount: Int get() = assembler.size

    fun accept(chunk: ByteArray): FrameParseBatch {
        val frames = mutableListOf<EncodedCameraFrame>()
        val errors = mutableListOf<String>()
        if (!assembler.append(chunk)) errors += "Input buffer exceeded configured maximum"
        while (true) {
            val parsed = when (profile.boundaryMode) {
                FrameBoundaryMode.LENGTH_FIELD -> parseLengthFrame(errors)
                FrameBoundaryMode.HEADER_FOOTER -> parseMarkerFrame(errors)
                FrameBoundaryMode.FIXED_SIZE -> parseFixedFrame(errors)
            } ?: break
            frames += parsed
        }
        return FrameParseBatch(frames, errors)
    }

    private fun parseLengthFrame(errors: MutableList<String>): EncodedCameraFrame? {
        val fieldEnd = profile.lengthFieldOffset + profile.lengthFieldSizeBytes
        while (assembler.size >= fieldEnd) {
            val bytes = assembler.bytes()
            val payloadLength = readUnsigned(bytes, profile.lengthFieldOffset, profile.lengthFieldSizeBytes)
            val totalSize = fieldEnd.toLong() + payloadLength
            if (payloadLength <= 0 || totalSize > profile.maximumFrameBytes) {
                errors += "Invalid configured frame length: $payloadLength"
                assembler.discard(1)
                continue
            }
            if (bytes.size < totalSize) return null
            val whole = bytes.copyOfRange(0, totalSize.toInt())
            assembler.discard(totalSize.toInt())
            return buildFrame(whole, totalSize.toInt())
        }
        return null
    }

    private fun parseMarkerFrame(errors: MutableList<String>): EncodedCameraFrame? {
        val header = decodeHex(profile.headerHex)
        val footer = decodeHex(profile.footerHex)
        if (header.isEmpty() || footer.isEmpty()) {
            errors += "Frame header/footer is not valid hexadecimal"
            assembler.discard(assembler.size)
            return null
        }
        while (true) {
            var bytes = assembler.bytes()
            val headerAt = bytes.indexOfSequence(header)
            if (headerAt < 0) {
                assembler.discard((bytes.size - header.size + 1).coerceAtLeast(0))
                return null
            }
            if (headerAt > 0) assembler.discard(headerAt)
            bytes = assembler.bytes()
            val footerAt = bytes.indexOfSequence(footer, header.size)
            val nextHeaderAt = bytes.indexOfSequence(header, header.size)
            if (nextHeaderAt >= 0 && (footerAt < 0 || nextHeaderAt < footerAt)) {
                errors += "Discarded marker-delimited frame with a missing footer"
                assembler.discard(nextHeaderAt)
                continue
            }
            if (footerAt < 0) return null
            val total = footerAt + footer.size
            if (total > profile.maximumFrameBytes) {
                errors += "Marker-delimited frame exceeded configured maximum"
                assembler.discard(total)
                continue
            }
            val whole = bytes.copyOfRange(0, total)
            assembler.discard(total)
            return buildFrame(whole, footerAt)
        }
    }

    private fun parseFixedFrame(errors: MutableList<String>): EncodedCameraFrame? {
        if (profile.fixedFrameSize !in 1..profile.maximumFrameBytes) {
            errors += "Invalid fixed frame size"
            assembler.discard(assembler.size)
            return null
        }
        val bytes = assembler.bytes()
        if (bytes.size < profile.fixedFrameSize) return null
        val whole = bytes.copyOfRange(0, profile.fixedFrameSize)
        assembler.discard(profile.fixedFrameSize)
        return buildFrame(whole, whole.size)
    }

    private fun buildFrame(whole: ByteArray, payloadEnd: Int): EncodedCameraFrame {
        val identifier = profile.cameraIdentifierOffset?.let { offset ->
            if (offset >= 0 && profile.cameraIdentifierSize > 0 && offset + profile.cameraIdentifierSize <= whole.size) {
                whole.copyOfRange(offset, offset + profile.cameraIdentifierSize)
            } else null
        }
        val cameraId = cameraIdentifiers.entries.firstOrNull { (_, value) -> identifier?.contentEquals(value) == true }?.key
            ?: if (cameraIdentifiers.size == 1) cameraIdentifiers.keys.single() else "UNKNOWN"
        val start = profile.payloadOffset.coerceIn(0, payloadEnd)
        return EncodedCameraFrame(cameraId, nowMillis(), whole.copyOfRange(start, payloadEnd))
    }

    private fun readUnsigned(bytes: ByteArray, offset: Int, size: Int): Long {
        var result = 0L
        val indices = if (profile.byteOrder == ByteOrderProfile.BIG_ENDIAN) 0 until size else (size - 1 downTo 0)
        for (index in indices) result = (result shl 8) or (bytes[offset + index].toLong() and 0xFF)
        return result
    }

    private fun decodeHex(value: String): ByteArray {
        val normalized = value.filterNot(Char::isWhitespace)
        if (normalized.length % 2 != 0) return ByteArray(0)
        return runCatching {
            ByteArray(normalized.length / 2) { index -> normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }.getOrDefault(ByteArray(0))
    }
}

private fun ByteArray.indexOfSequence(target: ByteArray, start: Int = 0): Int {
    if (target.isEmpty()) return start.coerceAtMost(size)
    for (index in start.coerceAtLeast(0)..(size - target.size)) {
        var matches = true
        for (offset in target.indices) if (this[index + offset] != target[offset]) { matches = false; break }
        if (matches) return index
    }
    return -1
}
