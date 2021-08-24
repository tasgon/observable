package observable.server

import observable.Observable
import observable.Props
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Tagged stack trace sampler.
 */
class TaggedSampler(val thread: Thread) : Runnable {
    data class Sample(val target: Profiler.TimingData?, val trace: Array<StackTraceElement>)

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
        Observable.LOGGER.info("Started sampler thread")
        val interval = ServerSettings.traceInterval
        val deviation = ServerSettings.deviation
        var trace: Array<StackTraceElement>
        var target: Profiler.TimingData
        while (!Props.notProcessing) {
            target = Props.currentTarget.get() ?: continue
            trace = thread.stackTrace
            target.traces.add(trace.toList())
            Thread.sleep(interval + Random.nextLong(-deviation, deviation))
        }
    }

}