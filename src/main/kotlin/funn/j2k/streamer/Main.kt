package funn.j2k.streamer

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// NOTE: using rtmp protocol
fun main(): Unit = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.IO)
    // TODO: read from config or args
    val socket = aSocket(selectorManager).tcp().connect("localhost",1935)

    val input = socket.openReadChannel()
    val output = socket.openWriteChannel(autoFlush = false)

    launch(Dispatchers.IO) {
        println("Handshaking...")
        handshake(input, output)
        println("Handshaked!")
//        sendMessage(TODO())
    }
}

const val RTMP_VERSION: Byte = 3

suspend fun handshake(input: ByteReadChannel, output: ByteWriteChannel) {
    output.writeByte(RTMP_VERSION) // c0 packet
    val c1 = ByteArray(1536) { 0 }
    output.writeFully(c1) // c1 packet
    output.flush()

    val s0 = input.readByte() // s0 packet

    check(s0 == RTMP_VERSION)

    val s1 = ByteArray(1536) // s1 packet
    input.readFully(s1)

    output.writeFully(s1)
    output.flush()

    val s2 = ByteArray(1536)
    input.readFully(s2)

    assert(c1.contentEquals(s2))
}

//suspend fun ByteWriteChannel.sendMessage(message: Message) {
//    val data = message.serialize()
//}
