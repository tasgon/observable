package observable

import me.shedaniel.architectury.event.events.TickEvent

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