package funn.j2k.streamer

import funn.j2k.streamer.amf.serialization.AmfDeserializer
import funn.j2k.streamer.amf.serialization.AmfSerializer
import funn.j2k.streamer.rtmp.Controller
import funn.j2k.streamer.rtmp.message.Message.Command
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
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

    val controller = Controller(input, output)
    launch(Dispatchers.IO) {
        controller.handshake()
        val message = Command.Connect(
            "retro-streamer",
            "rtmp://localhost:1935/retro-streamer",
            "rtmp://localhost:1935/retro-streamer"
        )
        println("connecting")
        controller.sendMessage(message)
        while (true) {
            controller.receive()
        }
        println("connected")
    }
}
