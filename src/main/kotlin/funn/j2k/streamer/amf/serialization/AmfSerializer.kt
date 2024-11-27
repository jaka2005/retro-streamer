package funn.j2k.streamer.amf.serialization

import kotlinx.datetime.Instant
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class AmfSerializer {
    private var storedCount: Int = 0
    private val storedObjects = hashMapOf<Int, Int>() // Object hash code -> Stored ID
    private var mapAsArray = false

    private fun registerObject(obj: Any?): Int {
        storedObjects[obj.hashCode()] = storedCount++
        return storedCount
    }

    private fun clearStoredObjects() {
        storedCount = 0
        storedObjects.clear()
    }

    private fun getStoredId(obj: Any?): Int? = storedObjects[obj.hashCode()]

    fun write(data: Any?, mapAsArray: Boolean = false): ByteArray {
        this.mapAsArray = mapAsArray
        clearStoredObjects()
        val result = ByteArrayOutputStream()
        DataOutputStream(result).apply {
            writeData(data)
            flush()
        }
        this.mapAsArray = false

        return result.toByteArray()
    }

    /**
     * Write given data to the output channel. Supported types are:
     * - [null] -> [AmfType.NULL]
     * - [Number] -> [AmfType.NUMBER]
     * - [Boolean] -> [AmfType.BOOLEAN]
     * - [String] -> [AmfType.STRING] (with ECMA-262 string length)
     * - [Char] -> [AmfType.STRING] (with ECMA-262 string length)
     * - [Instant] -> [AmfType.DATE]
     * - [Collection<*>] -> [AmfType.STRICT_ARRAY]
     * - [Map<*, *>] -> [AmfType.OBJECT] (or [AmfType.ECMA_ARRAY] if [mapAsArray] is true)
     *
     * @throws IllegalArgumentException if unsupported data type is given
     */
    private fun DataOutputStream.writeData(data: Any?) {
        when (data) {
            null ->
                writeByte(AmfType.NULL.ordinal)
            is Number -> {
                writeByte(AmfType.NUMBER.ordinal)
                writeDouble(data.toDouble())
            }
            is Boolean -> {
                writeByte(AmfType.BOOLEAN.ordinal)
                writeByte(if (data) 1 else 0)
            }
            is String -> {
                writeStringSmart(data)
            }
            is Char -> {
                writeByte(AmfType.STRING.ordinal)
                writeString(data.toString())
            }
            is Instant -> {
                writeByte(AmfType.DATE.ordinal)
                writeDouble(data.toEpochMilliseconds().toDouble())
                writeShort(0)
            }
            is Collection<*> -> {
                if (writeIfStored(data)) return
                registerObject(data)
                writeCollection(data)
            }
            is Map<*, *> -> {
                if (writeIfStored(data)) return
                registerObject(data)
                if (mapAsArray) {
                    writeEcmaArray(data)
                } else {
                    writeObject(data)
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported data type: $data")
            }
        }
    }

    private fun DataOutputStream.writeIfStored(data: Any): Boolean {
        val id = getStoredId(data) ?: return false

        writeByte(AmfType.REFERENCE.ordinal)
        writeShort(id)
        return true
    }

    private fun DataOutputStream.writeObject(map: Map<*, *>) {
        writeByte(AmfType.OBJECT.ordinal)

        map.forEach { (key, value) ->
            writeString(key.toString())
            writeData(value)
        }

        this.writeShort(UTF_EMPTY.toInt())
        writeByte(AmfType.OBJECT_END.ordinal)
    }

    private fun DataOutputStream.writeEcmaArray(map: Map<*, *>) {
        writeByte(AmfType.ECMA_ARRAY.ordinal)
        writeInt(0)

        map.forEach { (key, value) ->
            writeString(key.toString())
            writeData(value)
        }

        writeShort(UTF_EMPTY.toInt())
        writeByte(AmfType.OBJECT_END.ordinal)
    }

    private fun DataOutputStream.writeCollection(data: Collection<*>) {
            writeByte(AmfType.STRICT_ARRAY.ordinal)
        writeInt(data.size)

        data.forEach {
            writeData(it)
        }
    }

    // From: https://github.com/zerksud/amf-serializer/blob/9add53d392cce399e110d9494209ead9999b945c/src/com/exadel/flamingo/flex/messaging/amf/io/AMF0Serializer.java#L464
    private fun DataOutputStream.writeStringSmart(str: String): Int {
        val strlen = str.length
        var utflen = 0
        val charr = CharArray(strlen)
        var c: Int
        var count = 0

        str.toCharArray(charr, 0, 0, strlen)

        // check the length of the UTF-encoded string
        for (i in 0 until strlen) {
            c = charr[i].code
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++
            } else if (c > 0x07FF) {
                utflen += 3
            } else {
                utflen += 2
            }
        }

        /**
         * if utf-encoded String is < 64K, use the "String" data type, with a
         * two-byte prefix specifying string length; otherwise use the "Long String"
         * data type, withBUG#298 a four-byte prefix
         */
        val bytearr: ByteArray
        if (utflen <= 65535) {
            writeByte(AmfType.STRING.ordinal)
            bytearr = ByteArray(utflen + 2)
        } else {
            writeByte(AmfType.LONG_STRING.ordinal)
            bytearr = ByteArray(utflen + 4)
            bytearr[count++] = ((utflen ushr 24) and 0xFF).toByte()
            bytearr[count++] = ((utflen ushr 16) and 0xFF).toByte()
        }

        bytearr[count++] = ((utflen ushr 8) and 0xFF).toByte()
        bytearr[count++] = ((utflen ushr 0) and 0xFF).toByte()
        for (i in 0 until strlen) {
            c = charr[i].code
            if ((c >= 0x0001) && (c <= 0x007F)) {
                bytearr[count++] = c.toByte()
            } else if (c > 0x07FF) {
                bytearr[count++] = (0xE0 or ((c shr 12) and 0x0F)).toByte()
                bytearr[count++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                bytearr[count++] = (0x80 or ((c shr 0) and 0x3F)).toByte()
            } else {
                bytearr[count++] = (0xC0 or ((c shr 6) and 0x1F)).toByte()
                bytearr[count++] = (0x80 or ((c shr 0) and 0x3F)).toByte()
            }
        }

        write(bytearr)
        return utflen + 2
    }
}
