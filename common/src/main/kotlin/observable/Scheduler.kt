package observable

import dev.architectury.event.events.common.TickEvent

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
