package funn.j2k.streamer.amf

data class AmfBody(
    val target: String,
    val response: String,
    val data: Any?
)
