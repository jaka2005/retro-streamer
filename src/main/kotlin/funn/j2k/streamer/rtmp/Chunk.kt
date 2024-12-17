package funn.j2k.streamer.rtmp

import funn.j2k.streamer.Writeable
import funn.j2k.streamer.write
import funn.j2k.streamer.write24Bits
import io.ktor.utils.io.*


data class Chunk(
    val header: ChunkHeader,
    val data: ByteArray,
) : Writeable {
    override suspend fun write(output: ByteWriteChannel) {
        output.write(header)
        output.writeFully(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chunk

        if (header != other.header) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class ChunkHeader(
    val fmt: Int,
    val streamId: Int,
    val timestamp: Int? = null,
    val messageLength: Int? = null,
    val messageTypeId: Int? = null,
    val messageStreamId: Int? = null
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

        val hasExtendedTimestamp = timestamp?.let { it >= 0xff_ff_ff }
        val coercedTimestamp = if (hasExtendedTimestamp == true) 0xff_ff_ff else timestamp
        if (fmt < 3)
            output.write24Bits(coercedTimestamp!!)
        if (fmt < 2) {
            output.write24Bits(messageLength!!)
            output.writeByte(messageTypeId!!.toByte())
        }
        if (fmt == 0)  {
            output.writeInt(messageStreamId!!)
        }
        if (hasExtendedTimestamp == true) {
            output.writeByte((timestamp shr 24).toByte())
        }
    }
}