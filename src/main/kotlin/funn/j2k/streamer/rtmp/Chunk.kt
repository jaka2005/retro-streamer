package funn.j2k.streamer.rtmp

import funn.j2k.streamer.Writeable
import funn.j2k.streamer.write
import funn.j2k.streamer.write24Bits
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully


data class Chunk(
    val header: ChunkHeader,
    val data: ByteArray,
) : Writeable {
    override suspend fun write(output: ByteWriteChannel) {
        output.write(header)
        output.writeFully(data)
    }
}

data class ChunkHeader(
    val fmt: Int,
    val streamId: Int,
    val timestamp: Int,
    val messageLength: Int,
    val messageTypeId: Int,
    val messageStreamId: Int
) : Writeable {

    override suspend fun write(output: ByteWriteChannel) {
        val firstByte = fmt shl 6
        when (streamId) {
            in 2..63 -> {
                output.writeByte((firstByte or streamId).toByte())
            }
            in 64..319 -> {
                output.writeByte(firstByte.toByte())
                output.writeByte((streamId - 64).toByte())
            }
            in 320..65599 -> {
                output.writeByte(firstByte.inc().toByte())
                output.writeShort((streamId - 64).toShort())
            }
        }

        val hasExtendedTimestamp = timestamp >= 0xff_ff_ff
        val coercedTimestamp = if (hasExtendedTimestamp) 0xff_ff_ff else timestamp
        if (fmt < 3)
            output.write24Bits(coercedTimestamp)
        if (fmt < 2) {
            output.write24Bits(messageLength)
            output.writeByte(messageTypeId.toByte())
        }
        if (fmt == 0)  {
            output.writeInt(messageStreamId)
        }
        if (hasExtendedTimestamp) {
            output.writeByte((timestamp shr 24).toByte())
        }
    }
}