package funn.j2k.streamer.rtmp.message.stream

import funn.j2k.streamer.amf.serialization.AmfDeserializer
import funn.j2k.streamer.read24Bits
import funn.j2k.streamer.rtmp.ChunkHeader
import funn.j2k.streamer.rtmp.message.MessageType
import io.ktor.utils.io.*

class InputMessageStream private constructor(header: ChunkHeader) {
    private var fullHeader: ChunkHeader = header

    val id: Int get() = fullHeader.messageStreamId!!
    val csId: Int get() = fullHeader.streamId
    var timestamp: Int = fullHeader.timestamp!!
        private set

    private var timestampDelta = 0
    var bytesRead = 0
        private set

    val readingIsComplete get() = bytesRead == fullHeader.messageLength!!
    val messageType get() = MessageType.from(fullHeader.messageTypeId!!)!!
    var rawMessage = ByteArray(fullHeader.messageLength!!)
        get() = field.copyOf()
        private set

    private val deserializer: AmfDeserializer = AmfDeserializer()

    suspend fun readNextChunk(
        input: ByteReadChannel,
        chunkBasicHeader: ChunkBasicHeader,
        chunkSize: Int = 128
    ): Int {
        if (readingIsComplete) return 0

        val header = input.readChunkHeader(chunkBasicHeader)
        val readBytes = readData(input, chunkSize)

        when(header.fmt) {
            0 -> timestamp = header.timestamp!!
            1, 2 -> {
                timestampDelta = header.timestamp!!
                timestamp += timestampDelta
            }
            3 -> timestamp += timestampDelta
        }

        return header.size + readBytes
    }

    private suspend fun readData(input: ByteReadChannel, chunkSize: Int): Int {
        val bytesToRead = (fullHeader.messageLength!! - bytesRead).coerceAtMost(chunkSize)
        val bytes = ByteArray(bytesToRead)
        input.readFully(bytes)

        bytes.copyInto(rawMessage, 0, bytesRead, bytesRead + bytesToRead)
        bytesRead += bytesToRead

        return bytesToRead
    }

    companion object {
        suspend fun fromCurrentChunkHeader(
            input: ByteReadChannel,
            chunkBasicHeader: ChunkBasicHeader,
            chunkSize: Int,
        ): InputMessageStream {
            val header = input.readChunkHeader(chunkBasicHeader)
            return InputMessageStream(header).apply { readData(input, chunkSize)  }
        }
    }
}

val ChunkHeader.size get() = when(fmt) {
    0 -> 11
    1 -> 7
    2 -> 3
    3 -> 0
    else -> error("Unsupported fmt: ${fmt}")
}

suspend fun ByteReadChannel.readChunkHeader(basicHeader: ChunkBasicHeader): ChunkHeader {
    var timestamp: Int? = null
    var messageLength: Int? = null
    var messageTypeId: Int? = null
    var messageStreamId: Int? = null
    if (basicHeader.fmt < 3) {
        timestamp = read24Bits()
    }
    if (basicHeader.fmt < 2) {
        messageLength = read24Bits()
        messageTypeId = readByte().toInt()
    }
    if (basicHeader.fmt == 0) {
        messageStreamId = readInt()
    }

    return ChunkHeader(
        basicHeader.fmt,
        basicHeader.streamId,
        timestamp,
        messageLength,
        messageTypeId,
        messageStreamId
    )
}

typealias ChunkBasicHeader = Pair<Int, Int>

val ChunkBasicHeader.fmt: Int
    get() = first
val ChunkBasicHeader.streamId: Int
    get() = second
val ChunkBasicHeader.size: Int get() = when(streamId) {
    in 0..63 -> 1
    in 64..319 -> 2
    else -> 3
}

suspend fun ByteReadChannel.readChunkBasicHeader(): ChunkBasicHeader {
    val chunkStreamId = readByte().toInt()
    val fmt = 0b1100_0000 and chunkStreamId shr 6
    var streamId = 0b0011_1111 and chunkStreamId

    when (streamId) {
        0 -> {
            streamId = readByte().toInt() + 64
        }

        1 -> {
            streamId = readShort().toInt() + 64
        }
    }

    return ChunkBasicHeader(fmt, streamId)
}