package observable.server

import observable.Props
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Tagged stack trace sampler.
 */
class TaggedSampler(val thread: Thread) : Runnable {
    data class Sample(val target: Profiler.TimingData?, val trace: Array<StackTraceElement>)
    companion object var target: AtomicReference<Profiler.TimingData?> = AtomicReference(null)

    class SamplerProcessor(val entries: ArrayDeque<Sample>) : Runnable {
        @Volatile
        var running = true

        override fun run() {
            var sample: Sample?
            while (running) {
                val (target, trace) = entries.removeLastOrNull() ?: continue
                if (target != null) {
                }
            }
        }
    }

    var entries = ArrayDeque<Sample>()

    override fun run() {
        val interval = ServerSettings.traceInterval
        val deviation = ServerSettings.deviation
        while (!Props.notProcessing) {
            entries.addLast(Sample(target.get(), thread.stackTrace))
            Thread.sleep(interval + Random.nextLong(-deviation, deviation))
        }
    }

}