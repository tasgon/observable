package observable

import dev.architectury.event.events.common.TickEvent
import dev.architectury.utils.GameInstance
import observable.net.S2CPacket
import observable.server.ServerSettings

class Scheduler {
    val queue = ArrayDeque<() -> Unit>()


    companion object {
        val SERVER by lazy { Scheduler() }
    }

    init {
        TickEvent.SERVER_POST.register {
            queue.removeFirstOrNull()?.let { it() }
        }
    }

    fun enqueue(fn: () -> Unit) = synchronized(queue) { queue.addLast(fn) }
}