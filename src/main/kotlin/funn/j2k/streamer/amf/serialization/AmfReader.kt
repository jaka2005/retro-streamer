package funn.j2k.streamer.amf.serialization

import funn.j2k.streamer.amf.AmfBody
import funn.j2k.streamer.amf.AmfHeader
import funn.j2k.streamer.amf.AmfPacket
import funn.j2k.streamer.amf.serialization.AmfType.*
import io.ktor.utils.io.*
import kotlinx.datetime.Instant

class AmfReader(private val input: ByteReadChannel) {
    private val storedObjects = mutableListOf<Any?>()

    suspend fun read(): AmfPacket {
        input.apply {
            val version = readUShortAsInt()
            val headerCount = readUShortAsInt()
            val headers = (0 until headerCount).map { readHeader() }
            val bodyCount = readUShortAsInt()
            val bodies = (0 until bodyCount).map { readBody() }
            return AmfPacket(version, headers, bodies)
        }
    }

    private suspend fun readHeader(): AmfHeader {
        input.apply {
            val name = readString()
            val required = readByte() != 0.toByte()

            storedObjects.clear()
            val data = readData()
            return AmfHeader(name, required, data)
        }
    }

    private suspend fun readBody(): AmfBody {
        input.apply {
            val target = readString()
            val response = readString()

            storedObjects.clear()
            val data = readData()
            return AmfBody(target, response, data)
        }
    }


    private suspend fun readData(): Any? {
        input.apply {
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
                    readString() // TODO: store type somewhere
                    readObject()
                }
                AVMPLUS_OBJECT -> error("AVM+ required by server")
            }
        }
    }

    private suspend fun readObject(): Map<String, Any?> {
        input.apply {
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
    }

    private suspend fun readList(): List<Any?> {
        val list = mutableListOf<Any?>()
        storedObjects.add(list)
        (0..input.readInt()).forEach { _ ->
            list.add(readData())
        }
        return list
    }
}