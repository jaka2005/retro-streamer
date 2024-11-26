package funn.j2k.streamer.amf.serialization

const val UNKNOWN_SIZE = -1 // (U32) -1 as unknown content length (in bytes)
const val UTF_EMPTY: Short = 0 // empty string

enum class AmfType {
    NUMBER,
    BOOLEAN,
    STRING,
    OBJECT,
    MOVIECLIP, // NOTE: is not supported and placed here only for readability
    NULL,
    UNDEFINED,
    REFERENCE,
    ECMA_ARRAY,
    OBJECT_END,
    STRICT_ARRAY,
    DATE,
    LONG_STRING,
    UNSUPPORTED,
    RECORDSET, // is also unsupported
    XML_DOCUMENT,
    TYPED_OBJECT,
    AVMPLUS_OBJECT
}
