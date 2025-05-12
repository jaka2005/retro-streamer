package funn.j2k.streamer

import funn.j2k.streamer.amf.serialization.AmfDeserializer
import funn.j2k.streamer.amf.serialization.AmfSerializer
import funn.j2k.streamer.rtmp.Client
import funn.j2k.streamer.rtmp.Controller
import funn.j2k.streamer.rtmp.message.Message.Command
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// NOTE: using rtmp protocol
fun main(): Unit = runBlocking {
//    val selectorManager = SelectorManager(Dispatchers.IO)
//    // TODO: read from config or args
//    val socket = aSocket(selectorManager).tcp().connect("localhost",1935)
//
//    val input = socket.openReadChannel()
//    val output = socket.openWriteChannel(autoFlush = false)
//
//    val client = Client(Controller(), input, output)

    val serializer = AmfSerializer()
    val deserializer = AmfDeserializer()
    launch(Dispatchers.IO) {
        val message = Command.Connect(
            "retro-streamer",
            "rtmp://localhost:1935/retro-streamer",
            "rtmp://localhost:1935/retro-streamer"
        )
//        client.run()
        println(deserializer.read(serializer.write(mapOf(
            "123" to 123,
            "124" to mapOf(
                "1234" to 1234,
                "1244" to 1244
            ),
            "125" to "125",
        )) + ByteArray(120) { 0 }))

    }
}
