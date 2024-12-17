package funn.j2k.streamer.amf.serialization

interface Serializable {
    fun serialize(serializer: AmfSerializer): ByteArray
}
