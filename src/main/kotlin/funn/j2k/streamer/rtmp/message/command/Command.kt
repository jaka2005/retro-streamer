package funn.j2k.streamer.rtmp.message.command

sealed class Command(
    val commandName: String,
    val transactionId: Int = 0
) {
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
    ) : Command("connect", transactionId = 1) {
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
    data class CreateStream(val streamId: Int) : Command("createStream")
    data class Play(val streamId: Int) : Command("play")
    data class Play2(val streamId: Int) : Command("play2")
    data class DeleteStream(val streamId: Int) : Command("deleteStream")
    data class Publish(val streamId: Int) : Command("publish")
    data class Seek(val streamId: Int, val position: Long) : Command("seek")
    data class Pause(val streamId: Int) : Command("pause")
}

fun Iterable<Command.Connect.AudioCodec>.toFlags(): Int = fold(0) { acc, codec -> acc or codec.flag }
fun Iterable<Command.Connect.VideoCodec>.toFlags(): Int = fold(0) { acc, codec -> acc or codec.flag }
