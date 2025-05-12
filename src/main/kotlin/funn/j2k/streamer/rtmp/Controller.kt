package funn.j2k.streamer.rtmp

import funn.j2k.streamer.rtmp.message.Message

interface IController {
    fun onConnect()
    fun onCommand(message: Message.Command)
    fun onCommandResult(result: Map<String, Any>)
    fun onInit()
}

class Controller: IController {
    override fun onConnect() {
        TODO("Not yet implemented")
    }

    override fun onCommand(message: Message.Command) {
        TODO("Not yet implemented")
    }

    override fun onCommandResult(result: Map<String, Any>) {
        TODO("Not yet implemented")
    }

    override fun onInit() {
        TODO("Not yet implemented")
    }
}
