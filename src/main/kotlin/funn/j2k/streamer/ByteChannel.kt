package funn.j2k.streamer

import io.ktor.utils.io.*

interface Writeable {
    suspend fun write(output: ByteWriteChannel)
}

interface Readable {
    suspend fun read(input: ByteReadChannel)
}

suspend fun ByteWriteChannel.write(writeable: Writeable) {
    writeable.write(this)
}

suspend fun ByteReadChannel.read(readable: Readable) {
    readable.read(this)
}

suspend fun ByteWriteChannel.write24Bits(value: Int) {
    require(value in 0..0xFFFFFF) { "Value must fit in 24 bits" }
    writeShort((value shr 8).toShort())
    writeByte((value and 0xff).toByte())
}

suspend fun ByteReadChannel.read24Bits(): Int {
    val firstBits = readShort().toInt()
    val secondBits = readByte().toInt()

    return (firstBits shl 8) or secondBits
}
