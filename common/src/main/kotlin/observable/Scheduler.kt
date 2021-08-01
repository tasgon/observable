package observable

import me.shedaniel.architectury.event.events.TickEvent

class Scheduler {
    companion object {
        val SERVER by lazy { Scheduler() }
    }

    init {
        TickEvent.SERVER_POST.register {
            queue.removeFirstOrNull()?.let { it() }
        }
    }

    val queue = ArrayDeque<() -> Unit>()

    fun enqueue(fn: () -> Unit) = synchronized(queue) { queue.addLast(fn) }
}