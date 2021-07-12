import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import observable.server.BlockPosSerializer
import observable.server.Profiler

@Serializable
data class ProfilingData(val data: List<Pair<SerializedEntity, SerializedTimingData>>) {
    constructor(data: Map<Object, Profiler.TimingData>) : this(data.map { (entity, data) ->
        val id = (entity as? Entity)?.let { it.id }
        val pos = (entity as? BlockEntity)?.let { it.blockPos }

        Pair(SerializedEntity(entity.javaClass.name, id, pos), SerializedTimingData(data))
    }.sortedByDescending {
        it.second.rate
    })

    @Serializable(with = BlockPosSerializer::class)
    data class SerializedEntity(val className: String, val entityId: Int?, val pos: BlockPos?) {
    }

    @Serializable
    data class SerializedStackTrace(val className: String, val fileName: String?, val lineNumber: Int, val methodName: String) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }

    @Serializable
    data class SerializedTimingData(val rate: Double, val traces: List<SerializedStackTrace>) {
        constructor(timingData: Profiler.TimingData) :
                this (timingData.time.toDouble() / timingData.ticks.toDouble(),
                    timingData.traces.map { SerializedStackTrace(it) })
    }
}