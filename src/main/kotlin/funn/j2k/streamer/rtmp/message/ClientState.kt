package funn.j2k.streamer.rtmp.message

enum class ClientState {
    START,
    CONNECTION,
    IDLE,
    STREAMING,
    CLOSED
}