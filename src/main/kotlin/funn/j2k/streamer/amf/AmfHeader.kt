package funn.j2k.streamer.amf

data class AmfHeader(
    val name: String,
    val required: Boolean = false,
    val data: Any?
)
