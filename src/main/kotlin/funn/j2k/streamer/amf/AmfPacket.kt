package funn.j2k.streamer.amf

data class AmfPacket(
    val version: Int = 0,
    val headers: List<AmfHeader> = emptyList(),
    val bodies: List<AmfBody> = emptyList()
)