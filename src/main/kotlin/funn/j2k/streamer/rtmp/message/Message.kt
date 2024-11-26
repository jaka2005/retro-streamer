package funn.j2k.streamer.rtmp.message

import funn.j2k.streamer.amf.AmfPacket
import funn.j2k.streamer.rtmp.message.command.Command

sealed class Message {
    sealed class ProtocolControlMessage : Message()

    data class SetChunkSize(val size: Int) : ProtocolControlMessage()
    data class Abort(val streamId: Int) : ProtocolControlMessage()
    data class Acknowledgement(val sequenceNumber: Int) : ProtocolControlMessage()
    data class WindowAcknowledgementSize(val size: Int) : ProtocolControlMessage()
    data class SetPeerBandwidth(val size: Int, val type: LimitType) : ProtocolControlMessage() {
        enum class LimitType {
            HARD, SOFT, DYNAMIC
        }
    }

    data class UserControlMessage(val eventType: EventType, val data: ByteArray) : Message() {
        enum class EventType {
            STREAM_BEGIN,
            STREAM_EOF,
            STREAM_DRY,
            SET_BUFFER_LENGTH,
            STREAM_IS_RECORDED,
            PING_REQUEST,
            PING_RESPONSE
        }
    }

    data class CommandMessage(val command: Command) : Message()
    data class DataMessage(val data: ByteArray) : Message()
    data class SharedObjectMessage(val data: AmfPacket) : Message()
    data class AudioMessage(val data: ByteArray) : Message()
    data class VideoMessage(val data: ByteArray) : Message()
    data class AggregateMessage(val data: Message) : Message()
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
    AGGREGATE_MESSAGE(22)
}
