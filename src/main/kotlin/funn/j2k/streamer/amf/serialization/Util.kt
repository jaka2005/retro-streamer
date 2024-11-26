package funn.j2k.streamer.amf.serialization

import io.ktor.utils.io.*


// TODO: make compatible with long-string and separate from utf-8
internal suspend fun ByteWriteChannel.writeString(string: String) {
    val bytes = string.encodeToByteArray()

    writeShort(bytes.size)
    writeFully(bytes)
}

internal suspend fun ByteReadChannel.readString(): String {
    val size = readUShortAsInt()
    return readPacket(size).readText()
}

internal suspend fun ByteReadChannel.readLongString(): String {
    val size = readInt()
    return readPacket(size).readText()
}

internal suspend fun ByteReadChannel.readUShort() = readShort().toUShort()

internal suspend fun ByteReadChannel.readUShortAsInt() = readUShort().toInt()
