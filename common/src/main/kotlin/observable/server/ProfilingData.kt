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
data class ProfilingData(val entries: List<ProfilingData.Entry>) {
    constructor(data: Map<Any, Profiler.TimingData>) : this(data.map { (entity, data) ->
        Entry(entity, data)
    }.sortedByDescending {
        it.rate
    })

    @Serializable
    data class SerializedEntity(val entity: Entity?, val blockEntity: BlockEntity?, val classname: String) {
        constructor(obj: Any) : this(obj as? Entity, obj as? BlockEntity, obj.javaClass.name)
        val asAny get() = entity ?: blockEntity
    }

    @Serializable
    data class Entry(val entity: SerializedEntity, val rate: Double, val traces: List<SerializedStackTrace>) {
        constructor(obj: Any, data: Profiler.TimingData) : this(SerializedEntity(obj),
            data.time.toDouble() / data.ticks.toDouble(), data.traces.map { SerializedStackTrace(it) })
    }

    @Serializable
    data class SerializedStackTrace(val classname: String, val fileName: String?, val lineNumber: Int, val methodName: String) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }
}