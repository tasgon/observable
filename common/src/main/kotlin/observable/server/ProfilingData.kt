@file:UseSerializers(EntitySerializer::class,
        BlockEntitySerializer::class)

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import observable.net.BlockEntitySerializer
import observable.net.BlockPosSerializer
import observable.net.EntitySerializer
import observable.server.Profiler


@Serializable
data class ProfilingData(val data: List<Pair<SerializedEntity, SerializedTimingData>>) {
    constructor(data: Map<Object, Profiler.TimingData>) : this(data.map { (entity, data) ->
        Pair(SerializedEntity(entity as? Entity, entity as? BlockEntity), SerializedTimingData(data))
    }.sortedByDescending {
        it.second.rate
    })

    sealed class EntityType {
    }

    @Serializable
    data class SerializedEntity(val entity: Entity?, val blockEntity: BlockEntity?) {
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