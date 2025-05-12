package funn.j2k.streamer.rtmp

class Config {
    private var appName
}

inline fun config(block: Config.() -> Unit) = Config().also(block)
