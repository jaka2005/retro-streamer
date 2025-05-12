package funn.j2k.streamer.rtmp

import funn.j2k.streamer.amf.serialization.AmfDeserializer
import funn.j2k.streamer.amf.serialization.AmfObject
import funn.j2k.streamer.amf.serialization.AmfSerializer
import funn.j2k.streamer.rtmp.message.ClientState
import funn.j2k.streamer.rtmp.message.Message
import funn.j2k.streamer.rtmp.message.Message.Command
import funn.j2k.streamer.rtmp.message.Message.ProtocolControlMessage.Acknowledgement
import funn.j2k.streamer.rtmp.message.Message.ProtocolControlMessage.SetPeerBandwidth.LimitType
import funn.j2k.streamer.rtmp.message.Message.ProtocolControlMessage.WindowAcknowledgementSize
import funn.j2k.streamer.rtmp.message.Message.UserControlMessage
import funn.j2k.streamer.rtmp.message.MessageType
import funn.j2k.streamer.rtmp.message.stream.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// TODO: maybe make public message streams and control interface for users(stream control will be better)
// TODO: add logging
class Client(
    private val controller: IController,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val config: Config
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var windowAcknowledgementSize = 0L
    private var limitType = LimitType.HARD
    private var chunkSize = 128
    private var time = 0

    private var receivedBytes = 0L

    private val serializer = AmfSerializer()
    private val deserializer = AmfDeserializer()

    private val outputChunkStreams = mutableMapOf<Int, OutputMessageStream>()
    private val inputChunkStreams = mutableMapOf<Int, InputMessageStream>()

    private val outputControlStream get() = outputChunkStreams.getOrPut(CONTROL_CHUNK_STREAM_ID) {
        OutputMessageStream(CONTROL_MESSAGE_STREAM_ID, CONTROL_CHUNK_STREAM_ID, null, serializer)
    }

    var state = ClientState.START
        private set

    fun run() {
        coroutineScope.launch {
            handshake()
            controller.onInit()
            // TODO: Make initialization config class
            sendMessage(Command.Connect(
                "retro-streamer",
                "rtmp://localhost:1935/retro-streamer",
                "rtmp://localhost:1935/retro-streamer"
            ))
            loop()
        }
    }

    private suspend fun loop() {
        while (true) {
            receive()
            update()
            if (receivedBytes >= windowAcknowledgementSize) {
                outputControlStream.setMessage(
                    Acknowledgement(receivedBytes.toInt()),
                    serializer,
                    timestamp = time,
                    reuse = true
                )
                receivedBytes = 0
            }

            send()
        }
    }

    private fun update() {
        if (state == ClientState.CONNECTION) {

        }
    }

    private val streamIds = outputChunkStreams.keys.apply { remove(CONTROL_CHUNK_STREAM_ID) }
    private var currentIterator = streamIds.toList().iterator()

    private suspend fun send() {
        if (!outputControlStream.sent) {
            outputControlStream.sendFull(output, chunkSize)
            return
        }

        if (!currentIterator.hasNext()) {
            currentIterator = streamIds.toList().iterator()
        }

        var stream = outputChunkStreams[currentIterator.next()] ?: error("Stream not found")

        while (stream.sent) {
            if (!currentIterator.hasNext()) return
            stream = outputChunkStreams[currentIterator.next()] ?: error("Stream not found")
        }

        stream.sendPart(output, chunkSize)
    }

    private suspend fun receive() {
        if (input.availableForRead == 0) return

        val basicHeader = input.readChunkBasicHeader()
        receivedBytes += basicHeader.size
        var isNewStream = false

        val messageStream = inputChunkStreams.getOrPut(basicHeader.streamId) {
            isNewStream = true
            streamIds.add(basicHeader.streamId)
            InputMessageStream.fromCurrentChunkHeader(input, basicHeader, chunkSize)
        }

        receivedBytes += if (!isNewStream) {
            messageStream.readNextChunk(input, basicHeader, chunkSize)
        } else {
            messageStream.bytesRead + ChunkHeader(
                basicHeader.fmt,
                basicHeader.streamId
            ).size
        }

        if (messageStream.readingIsComplete) {
            handleMessage(messageStream.messageType, messageStream.rawMessage)
        }
    }

    private suspend fun handleMessage(messageType: MessageType, data: ByteArray) {
        when (messageType) {
            MessageType.SET_CHUNK_SIZE -> chunkSize = data.getUInt(0).toInt()
            MessageType.ABORT -> {
                val chunkStream = data.getUInt(0).toInt()
                outputChunkStreams[chunkStream]?.brokeMessage()
            }
            MessageType.USER_CONTROL_MESSAGE -> {
                val eventType = UserControlMessage.EventType.entries[data.getUShort(0).toInt()]
                handleUserControlMessage(eventType, data.copyOfRange(2, data.size))
            }
            MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE -> windowAcknowledgementSize = data.getUInt(0).toLong()
            MessageType.SET_PEER_BANDWIDTH -> {
                val newWindowAcknowledgementSize = data.getUInt(0).toLong()
                if (newWindowAcknowledgementSize != windowAcknowledgementSize) {
                    outputControlStream.setMessage(
                        WindowAcknowledgementSize(windowAcknowledgementSize.toInt()), serializer
                    )
                }

                windowAcknowledgementSize = newWindowAcknowledgementSize
                limitType = LimitType.entries[data.last().toInt()]
            }
            MessageType.SHARED_OBJECT_MESSAGE -> TODO()
            MessageType.COMMAND_MESSAGE -> {
                handleCommandMessage(data)
            }
            MessageType.AGGREGATE_MESSAGE -> TODO()
            MessageType.ACKNOWLEDGEMENT -> { }
            // nothing to do
            MessageType.AUDIO_MESSAGE -> { }
            MessageType.VIDEO_MESSAGE -> { }
            MessageType.DATA_MESSAGE -> { }
        }
    }

    private suspend fun handleCommandMessage(bytes: ByteArray) {
        val commandName = deserializePartAs<String>(bytes)
        val transactionId = deserializePartAs<Int>(bytes)

        when (commandName) {
            "_result", "_error" -> {
                if (transactionId == 1) {
                    // is connect response
                    val properties = deserializePartAs<AmfObject>(bytes)
                    val info = deserializePartAs<AmfObject>(bytes)

                    if (commandName == "_error") {
                        error("Connect error: $info")
                    }

                    controller.onConnect()
                    state = ClientState.IDLE
                } else {
                    // is createStream response
                    val commandInfo = deserializePartAs<AmfObject?>(bytes)
                    val streamId = deserializePartAs<Int>(bytes)
                }
            }
            "onStatus" -> {
                check(transactionId == 0) { "transaction id must be 0 for onStatus command" }
                // skip command object since it null
                deserializePartAs<AmfObject?>(bytes)
                val info = deserializePartAs<AmfObject>(bytes)
                // TODO: handle info
            }
            else -> error("Unknown command: $commandName")
        }

        deserializer.reset()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any?> deserializePartAs(bytes: ByteArray): T = deserializer.read(
        bytes.copyOfRange(deserializer.bytesRead, bytes.size)
    ) as T

    private fun handleUserControlMessage(
        eventType: UserControlMessage.EventType,
        data: ByteArray
    ) {
        when (eventType) {
            UserControlMessage.EventType.STREAM_BEGIN -> { }
            UserControlMessage.EventType.STREAM_EOF -> { }
            UserControlMessage.EventType.STREAM_DRY -> { }
            UserControlMessage.EventType.PING_REQUEST -> {
                val timestamp = data.getUInt(0).toInt()
                outputControlStream.setMessage(UserControlMessage.PingResponse(timestamp), serializer)
            }
            // nothing to do
            UserControlMessage.EventType.SET_BUFFER_LENGTH -> { }
            UserControlMessage.EventType.STREAM_IS_RECORDED -> { }
            UserControlMessage.EventType.PING_RESPONSE -> { }
        }
    }

    fun sendMessage(message: Message) {
        val messageStreams = outputChunkStreams.filter { it.key > 2 && it.value.sent }.values

        val messageStream = if (messageStreams.isEmpty()) {
            val chunkStreamId = outputChunkStreams.keys.maxOrNull()?.inc() ?: 3
            outputChunkStreams.getOrPut(chunkStreamId) {
                OutputMessageStream(
                    id = outputChunkStreams.maxOfOrNull { entry -> entry.value.id }?.inc() ?: 1,
                    csId = chunkStreamId,
                    message = message,
                    serializer = serializer
                )
            }
        } else { messageStreams.first() }

        messageStream.setMessage(message, serializer)
    }

    private suspend fun handshake() {
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
        state = ClientState.CONNECTION
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
