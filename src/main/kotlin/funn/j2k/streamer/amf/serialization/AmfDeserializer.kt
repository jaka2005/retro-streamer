package funn.j2k.streamer.amf.serialization

import funn.j2k.streamer.amf.serialization.AmfType.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.datetime.Instant

// TODO: make it linear with ordinary stream
class AmfDeserializer {
    private val storedObjects = mutableListOf<Any?>()
    var bytesRead = 0
        private set

    fun reset() {
        storedObjects.clear()
        bytesRead = 0
    }

    suspend fun read(data: ByteArray): Any? {
        storedObjects.clear()
        val channel = ByteReadChannel(data)
        val result = channel.readData()
        bytesRead = channel.totalBytesRead.toInt()
        return result
    }

    private suspend fun ByteReadChannel.readData(): Any? {
        val type = AmfType.entries[readByte().toInt()]
        return when (type) {
            NUMBER -> readDouble()
            BOOLEAN -> readByte() != 0.toByte()
            STRING -> readString()
            OBJECT -> readObject()
            MOVIECLIP -> error("Movieclip not supported")
            NULL, UNDEFINED -> null
            REFERENCE -> storedObjects[readUShortAsInt()]
            ECMA_ARRAY -> {
                readInt()
                readObject()
            }
            OBJECT_END -> OBJECT_END
            STRICT_ARRAY -> readList()
            DATE -> {
                Instant.fromEpochMilliseconds(readDouble().toLong()).also {
                    readShort()
                }
            }
            LONG_STRING -> readLongString()
            UNSUPPORTED -> error("Unsupported type: $type")
            RECORDSET -> error("Recordset not supported")
            XML_DOCUMENT -> readLongString()
            TYPED_OBJECT -> {
                readString()
                readObject()
            }
            AVMPLUS_OBJECT -> error("AVM+ required by server")
        }
    }

    private suspend fun ByteReadChannel.readObject(): AmfObject {
        val map = mutableMapOf<String, Any?>()
        storedObjects.add(map)
        while (true) {
            val key = readString()
            val data = readData()

            if (data == OBJECT_END) break

            map[key] = data
        }
        return map
    }

    private suspend fun ByteReadChannel.readList(): List<Any?> {
        val list = mutableListOf<Any?>()
        storedObjects.add(list)
        (0..readInt()).forEach { _ ->
            list.add(readData())
        }
        return list
    }
}