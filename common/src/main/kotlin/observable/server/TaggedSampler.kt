package observable.server

import observable.Props
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tagged stack trace sampler.
 */
class TaggedSampler(val thread: Thread) : Runnable {
    class Sample(val target: Profiler.TimingData?, val trace: Array<StackTraceElement>)

    class SamplerProcessor(val entries: ArrayDeque<Sample>) : Runnable {
        @Volatile
        var running = true

        override fun run() {
            var sample: Sample?
            while (running) {
                sample = entries.removeLastOrNull()
                if (sample != null) {

                }
            }
        }
    }

    var target: AtomicReference<Profiler.TimingData?> = AtomicReference(null)
    var entries = ArrayDeque<Sample>()

    override fun run() {
        while (!Props.notProcessing) {
            entries.addLast(Sample(target.get(), thread.stackTrace))
        }
    }

}