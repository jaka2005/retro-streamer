package funn.j2k.streamer.rtmp.message

import funn.j2k.streamer.amf.AmfPacket
import funn.j2k.streamer.amf.serialization.AmfSerializer
import funn.j2k.streamer.amf.serialization.Serializable
import java.nio.ByteBuffer

sealed class Message(val messageType: MessageType) : Serializable {
    sealed class ProtocolControlMessage(messageType: MessageType) : Message(messageType) {
        data class SetChunkSize(val size: Int) : ProtocolControlMessage(MessageType.SET_CHUNK_SIZE) {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                ByteBuffer.allocate(4).putInt(size).array()
        }

        data class Abort(val streamId: Int) : ProtocolControlMessage(MessageType.ABORT) {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                ByteBuffer.allocate(4).putInt(streamId).array()
        }

        data class Acknowledgement(
            val sequenceNumber: Int
        ) : ProtocolControlMessage(MessageType.ACKNOWLEDGEMENT) {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                ByteBuffer.allocate(4).putInt(sequenceNumber).array()
        }

        data class WindowAcknowledgementSize(
            val size: Int
        ) : ProtocolControlMessage(MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE) {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                ByteBuffer.allocate(4).putInt(size).array()
        }

        data class SetPeerBandwidth(
            val size: Int,
            val type: LimitType
        ) : ProtocolControlMessage(MessageType.SET_PEER_BANDWIDTH) {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                ByteBuffer.allocate(6).putInt(size).put(type.ordinal.toByte()).array()

            enum class LimitType {
                HARD, SOFT, DYNAMIC
            }
        }
    }

    sealed class UserControlMessage(
        val eventType: EventType,
    ) : Message(MessageType.USER_CONTROL_MESSAGE) {
        override fun serialize(serializer: AmfSerializer): ByteArray =
            ByteBuffer.allocate(2).putShort(eventType.ordinal.toShort()).array()

        enum class EventType {
            STREAM_BEGIN,
            STREAM_EOF,
            STREAM_DRY,
            SET_BUFFER_LENGTH,
            STREAM_IS_RECORDED,
            PING_REQUEST,
            PING_RESPONSE
        }


        data class StreamBegin(
            val streamId: Int
        ) : UserControlMessage(EventType.STREAM_BEGIN) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) + ByteBuffer.allocate(4).putInt(streamId).array()
            }
        }

        data class StreamEof(
            val streamId: Int
        ) : UserControlMessage(EventType.STREAM_EOF) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) + ByteBuffer.allocate(4).putInt(streamId).array()
            }
        }

        data class StreamDry(
            val streamId: Int
        ) : UserControlMessage(EventType.STREAM_DRY) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) + ByteBuffer.allocate(4).putInt(streamId).array()
            }
        }

        data class SetBufferLength(
            val streamId: Int,
            val bufferLength: Int
        ) : UserControlMessage(EventType.SET_BUFFER_LENGTH) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) +
                        ByteBuffer.allocate(8).putInt(streamId).putInt(bufferLength).array()
            }
        }

        data class StreamIsRecorded(
            val streamId: Int
        ) : UserControlMessage(EventType.STREAM_IS_RECORDED) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) + ByteBuffer.allocate(4).putInt(streamId).array()
            }
        }

        data class PingRequest(
            val timestamp: Int
        ) : UserControlMessage(EventType.PING_REQUEST) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) + ByteBuffer.allocate(4).putInt(timestamp).array()
            }
        }

        data class PingResponse(
            val timestamp: Int
        ) : UserControlMessage(EventType.PING_RESPONSE) {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                return super.serialize(serializer) + ByteBuffer.allocate(4).putInt(timestamp).array()
            }
        }
    }

    sealed class Command(
        val commandName: String,
    ) : Message(MessageType.COMMAND_MESSAGE) {
        open val transactionId: Int = 0

        override fun serialize(serializer: AmfSerializer): ByteArray =
            serializer.write(commandName) + serializer.write(transactionId)

        data class Connect(
            val app: String,
            val tcUrl: String,
            val swfUrl: String,
            val flashVer: String = "LNX 9,0,124,2",
            val fpad: Boolean = false,
            val audioCodecs: List<AudioCodec> = AudioCodec.entries,
            val videoCodecs: List<VideoCodec> = VideoCodec.entries,
            val videoFunction: Boolean = false,
            val pageUrl: String = "",
            val objectEncoding: ObjectEncoding = ObjectEncoding.AMF0
        ) : Command("connect") {
            override val transactionId: Int = 1
            override fun serialize(serializer: AmfSerializer): ByteArray {
                val commandObject = serializer.write(mapOf(
                    "app" to app,
                    "flashVer" to flashVer,
                    "swfUrl" to swfUrl,
                    "tcUrl" to tcUrl,
                    "fpad" to fpad,
                    "audioCodecs" to audioCodecs.toAFlags(),
                    "videoCodecs" to videoCodecs.toVFlags(),
                    "pageUrl" to pageUrl,
                    "videoFunction" to if (videoFunction) 1 else 0,
                    "objectEncoding" to objectEncoding.flag
                ))

                return super.serialize(serializer) + commandObject
            }


            enum class AudioCodec(val flag: Int) {
                NONE(0x0001),
                ADPCM(0x0002),
                MP3(0x0004),
                INTEL(0x0008),
                UNUSED(0x0010),
                NELLY8(0x0020),
                NELLY(0x0040),
                G711A(0x0080),
                G711U(0x0100),
                NELLY16(0x0200),
                AAC(0x0400),
                SPEEX(0x0800);
                companion object {
                    fun matchedValues(flag: Int): List<AudioCodec> = entries.filter { it.flag and flag != 0 }
                    fun from(flag: Int): AudioCodec? = entries.find { codec -> codec.flag == flag }
                }
            }

            enum class VideoCodec(val flag: Int) {
                UNUSED(0x0001),
                JPEG(0x0002),
                SORENSON(0x0004),
                HOMEBREW(0x0008),
                VP6(0x0010),
                VP6ALPHA(0x0020),
                HOMEBREWV(0x0040),
                H264(0x0080);

                companion object {
                    fun matchedValues(flag: Int): List<VideoCodec> = VideoCodec.entries.filter { it.flag and flag != 0 }
                    fun from(flag: Int): VideoCodec? = VideoCodec.entries.find { codec -> codec.flag == flag }
                }
            }

            enum class ObjectEncoding(val flag: Int) {
                AMF0(0),
                AMF3(3)
            }
        }
        data class CreateStream(override val transactionId: Int) : Command("createStream") {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                super.serialize(serializer) + serializer.write(null)
        }

        data class Play(
            override val transactionId: Int,
            val streamName: String,
            val startInSeconds: Int = START_STREAM_OR_RECORD,
            val duration: Int = PLAY_UNTIL_AVAILABLE,
            val reset: Boolean = false
        ) : Command("play") {
            override fun serialize(serializer: AmfSerializer): ByteArray =
                super.serialize(serializer) +
                        serializer.write(streamName) +
                        serializer.write(startInSeconds) +
                        serializer.write(duration) +
                        serializer.write(reset)

            companion object {
                // for startInSeconds param
                const val START_STREAM_OR_RECORD = -2
                const val START_STREAM = -1

                // for duration param
                const val PLAY_UNTIL_AVAILABLE = -1
            }
        }

        data class Play2(val streamId: Int, override val transactionId: Int) : Command("play2") {
            override fun serialize(serializer: AmfSerializer): ByteArray {
                TODO("Not yet implemented")
            }
        }

        data class DeleteStream(val streamId: Int) : Command("deleteStream") {
            override val transactionId: Int = 0

            override fun serialize(serializer: AmfSerializer): ByteArray =
                super.serialize(serializer) + serializer.write(null) + serializer.write(streamId)
        }

        data class Publish(val publishingName: String, val type: PublishingType) : Command("publish") {
            enum class PublishingType {
                LIVE, RECORD, APPEND
            }

            override val transactionId: Int = 0

            override fun serialize(serializer: AmfSerializer): ByteArray =
                super.serialize(serializer) + serializer.write(null) + serializer.write(publishingName) +
                        serializer.write(type.name.lowercase())
        }

        data class Seek(val position: Long) : Command("seek") {
            override val transactionId: Int = 0

            override fun serialize(serializer: AmfSerializer): ByteArray =
                super.serialize(serializer) + serializer.write(null) + serializer.write(position)
        }

        data class Pause(val setPause: Boolean = true, val position: Long) : Command("pause") {
            override val transactionId: Int = 0

            override fun serialize(serializer: AmfSerializer): ByteArray =
                super.serialize(serializer) + serializer.write(null) + serializer.write(setPause) +
                        serializer.write(position)
        }
    }

    data class DataMessage(val data: ByteArray) : Message(MessageType.DATA_MESSAGE) {
        override fun serialize(serializer: AmfSerializer): ByteArray = data

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DataMessage

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data class SharedObjectMessage(val data: AmfPacket) : Message(MessageType.SHARED_OBJECT_MESSAGE) {
        override fun serialize(serializer: AmfSerializer): ByteArray {
            TODO("Not yet implemented")
        }
    }

    data class AudioMessage(val data: ByteArray) : Message(MessageType.AUDIO_MESSAGE) {
        override fun serialize(serializer: AmfSerializer): ByteArray = data
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioMessage

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data class VideoMessage(val data: ByteArray) : Message(MessageType.VIDEO_MESSAGE) {
        override fun serialize(serializer: AmfSerializer): ByteArray = data

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as VideoMessage

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data class AggregateMessage(val data: Message) : Message(MessageType.AGGREGATE_MESSAGE) {
        override fun serialize(serializer: AmfSerializer): ByteArray {
            TODO("Not yet implemented")
        }
    }
}

enum class MessageType(val id: Int) {
    SET_CHUNK_SIZE(1),
    ABORT(2),
    ACKNOWLEDGEMENT(3),
    USER_CONTROL_MESSAGE(4),
    WINDOW_ACKNOWLEDGEMENT_SIZE(5),
    SET_PEER_BANDWIDTH(6),
    AUDIO_MESSAGE(8),
    VIDEO_MESSAGE(9),
    DATA_MESSAGE(18),
    SHARED_OBJECT_MESSAGE(19),
    COMMAND_MESSAGE(20),
    AGGREGATE_MESSAGE(22);
    companion object {
        fun from(id: Int): MessageType? = entries.find { it.id == id }
    }
}

fun Iterable<Message.Command.Connect.AudioCodec>.toAFlags(): Int = fold(0) { acc, codec -> acc or codec.flag }
fun Iterable<Message.Command.Connect.VideoCodec>.toVFlags(): Int = fold(0) { acc, codec -> acc or codec.flag }
