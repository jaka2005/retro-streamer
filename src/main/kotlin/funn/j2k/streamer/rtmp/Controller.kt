package funn.j2k.streamer.rtmp

import funn.j2k.streamer.amf.serialization.AmfDeserializer
import funn.j2k.streamer.amf.serialization.AmfSerializer
import funn.j2k.streamer.rtmp.message.Message
import funn.j2k.streamer.rtmp.message.Message.ProtocolControlMessage.SetPeerBandwidth.LimitType
import funn.j2k.streamer.rtmp.message.Message.UserControlMessage
import funn.j2k.streamer.rtmp.message.MessageType
import funn.j2k.streamer.rtmp.message.stream.InputMessageStream
import funn.j2k.streamer.rtmp.message.stream.OutputMessageStream
import funn.j2k.streamer.rtmp.message.stream.readChunkBasicHeader
import funn.j2k.streamer.rtmp.message.stream.streamId
import io.ktor.utils.io.*

class Controller(val input: ByteReadChannel, val output: ByteWriteChannel) {
    private var windowAcknowledgementSize = 0L
    private var limitType = LimitType.HARD
    private var chunkSize = 128
    private var time = 0

    private var receivedBytes = 0L

    private val serializer = AmfSerializer()
    private val deserializer = AmfDeserializer()

    private val outputChunkStreams = mutableMapOf<Int, OutputMessageStream>()
    private val inputChunkStreams = mutableMapOf<Int, InputMessageStream>()

    suspend fun receive() {
        val basicHeader = input.readChunkBasicHeader()
        var isNewStream = false

        val messageStream = inputChunkStreams.getOrPut(basicHeader.streamId) {
            isNewStream = true
            InputMessageStream.fromCurrentChunkHeader(input, basicHeader, chunkSize)
        }

        if (!isNewStream) {
            messageStream.readNextChunk(input, basicHeader, chunkSize)
        }

        if (messageStream.readingIsComplete) {
            handleMessage(messageStream.messageType, messageStream.rawMessage)
        }
    }

    fun handleMessage(messageType: MessageType, data: ByteArray) {
        when (messageType) {
            MessageType.SET_CHUNK_SIZE -> chunkSize = data.getUInt(0).toInt()
            MessageType.ABORT -> TODO()
            MessageType.ACKNOWLEDGEMENT -> TODO()
            MessageType.USER_CONTROL_MESSAGE -> {
                val eventType = UserControlMessage.EventType.entries[data.getUShort(0).toInt()]
                handleUserControlMessage(eventType, data.copyOfRange(2, data.size))
            }
            MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE -> windowAcknowledgementSize = data.getUInt(0).toLong()
            MessageType.SET_PEER_BANDWIDTH -> {
                windowAcknowledgementSize = data.getUInt(0).toLong()
                limitType = LimitType.entries[data.last().toInt()]
            }
            MessageType.AUDIO_MESSAGE -> TODO()
            MessageType.VIDEO_MESSAGE -> TODO()
            MessageType.DATA_MESSAGE -> TODO()
            MessageType.SHARED_OBJECT_MESSAGE -> TODO()
            MessageType.COMMAND_MESSAGE -> {
                handleCommandMessage(data)
            }
            MessageType.AGGREGATE_MESSAGE -> TODO()
            null -> TODO()
        }
    }

    private fun handleCommandMessage(bytes: ByteArray) {

    }

    private fun handleUserControlMessage(
        eventType: UserControlMessage.EventType,
        data: ByteArray
    ) {
        // TODO: make stream managing
        when (eventType) {
            UserControlMessage.EventType.STREAM_BEGIN -> { }
            UserControlMessage.EventType.STREAM_EOF -> { }
            UserControlMessage.EventType.STREAM_DRY -> { }
            UserControlMessage.EventType.SET_BUFFER_LENGTH -> { }
            UserControlMessage.EventType.STREAM_IS_RECORDED -> { }
            UserControlMessage.EventType.PING_REQUEST -> { }
            UserControlMessage.EventType.PING_RESPONSE -> { }
        }
    }

    suspend fun sendMessage(message: Message) {
        val chunkStreamId = outputChunkStreams.keys.maxOrNull()?.inc() ?: 3

        val messageStream = outputChunkStreams.getOrPut(chunkStreamId) {
            OutputMessageStream(
                id = outputChunkStreams.maxOfOrNull { entry -> entry.value.id }?.inc() ?: 1,
                csId = chunkStreamId,
                message = message,
                serializer = serializer
            )
        }

        messageStream.sendFull(output, chunkSize)
    }

    fun printState() {
        println(windowAcknowledgementSize)
        println(limitType)
    }

    suspend fun handshake() {
        output.writeByte(RTMP_VERSION) // c0 packet
        val c1 = ByteArray(1536) { 0 }
        output.writeFully(c1) // c1 packet
        output.flush()

        val s0 = input.readByte() // s0 packet

        check(s0 == RTMP_VERSION)

        val s1 = ByteArray(1536) // s1 packet
        input.readFully(s1)

        output.writeFully(s1)
        output.flush()

        val s2 = ByteArray(1536)
        input.readFully(s2)

        assert(c1.contentEquals(s2))
    }

    companion object {
        const val RTMP_VERSION: Byte = 3
        const val CONTROL_CHUNK_STREAM_ID: Int = 2
        const val CONTROL_MESSAGE_STREAM_ID: Int = 0
    }
}

fun ByteArray.getUInt(at: Int) = ((this[at].toUInt() and 0xFFu) shl 24) or
        ((this[at + 1].toUInt() and 0xFFu) shl 16) or
        ((this[at + 2].toUInt() and 0xFFu) shl 8) or
        (this[at + 3].toUInt() and 0xFFu)

fun ByteArray.getUShort(at: Int) = ((this[at].toUInt() and 0xFFu) shl 8) or
        (this[at + 1].toUInt() and 0xFFu)
