package funn.j2k.streamer.rtmp

import funn.j2k.streamer.rtmp.message.Message.SetPeerBandwidth.LimitType
import kotlin.properties.Delegates

class Controller {
    private var windowAcknowledgementSize by Delegates.notNull<Int>()
    private var limitType = LimitType.HARD
    private var chunkSize = 128
    private var time = 0


}