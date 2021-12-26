package observable

import me.shedaniel.architectury.event.events.TickEvent
import me.shedaniel.architectury.utils.GameInstance
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