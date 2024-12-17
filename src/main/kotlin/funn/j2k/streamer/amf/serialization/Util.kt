package funn.j2k.streamer.amf.serialization

import io.ktor.utils.io.*
import java.io.DataOutputStream


internal fun DataOutputStream.writeString(string: String) {
    val bytes = string.encodeToByteArray()

    writeShort(bytes.size)
    write(bytes)
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
