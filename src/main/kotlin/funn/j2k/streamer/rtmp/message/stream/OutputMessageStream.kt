package funn.j2k.streamer.rtmp.message.stream

import funn.j2k.streamer.amf.serialization.AmfSerializer
import funn.j2k.streamer.rtmp.Chunk
import funn.j2k.streamer.rtmp.ChunkHeader
import funn.j2k.streamer.rtmp.message.Message
import funn.j2k.streamer.write
import io.ktor.utils.io.ByteWriteChannel

class OutputMessageStream(
    val id: Int,
    val csId: Int,
    message: Message,
    serializer: AmfSerializer,
    timestamp: Int = 0,
) {
    private lateinit var bytes: ByteArray
    lateinit var message: Message
        private set
    var bytesSent = 0
        private set
    var sent: Boolean = false
        private set

    private var previousChunk: Chunk? = null
    private var timestamp = 0
    private var format: Int = 0

    init {
        setMessage(message, serializer, timestamp, true)
    }


    fun setMessage(message: Message, serializer: AmfSerializer, timestamp: Int = 0, reuse: Boolean = false) {
        this.timestamp = timestamp
        this.message = message
        bytesSent = 0

        bytes = message.serialize(serializer)
        sent = false

        if (reuse) previousChunk = null

        format = if (reuse) 0 else {
            if (previousChunk?.header?.messageLength == bytes.size
                && previousChunk?.header?.messageTypeId == message.messageType.id) {
                when (previousChunk?.header?.fmt) {
                    0 -> 2
                    1, 2, 3 -> if (previousChunk?.header?.timestamp == timestamp) 3 else 2
                    else -> error("Unsupported fmt: ${previousChunk?.header?.fmt}")
                }
            } else 1
        }
    }

    suspend fun sendPart(output: ByteWriteChannel, chunkSize: Int = 128): Boolean {
        if (sent) return true

        val partSize = minOf(bytes.size - bytesSent, chunkSize)
        val part = bytes.copyOfRange(bytesSent, bytesSent + partSize)

        if (bytesSent != 0) format = 3

        val chunk = Chunk(
            header = ChunkHeader(
                fmt = format,
                streamId = csId,
                timestamp = timestamp,
                messageLength = bytes.size,
                messageTypeId = message.messageType.id,
                messageStreamId = id
            ),
            data = part
        )

        output.write(chunk)
        output.flush()

        bytesSent += partSize
        if (bytesSent == bytes.size) {
            sent = true
            return true
        }

        return false
    }

    suspend fun sendFull(output: ByteWriteChannel, chunkSize: Int = 128) {
        while (!sendPart(output, chunkSize)) {}
    }
}