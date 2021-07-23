@file:UseSerializers(EntitySerializer::class, ResourceLocationSerializer::class,
        BlockEntitySerializer::class, BlockPosSerializer::class)

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import observable.net.BlockEntitySerializer
import observable.net.BlockPosSerializer
import observable.net.EntitySerializer
import observable.net.ResourceLocationSerializer
import observable.server.Profiler

//typealias LevelMap<T> = Map<>

@Serializable
data class ProfilingData(val entities: Map<ResourceLocation, List<Entry<Int>>>,
                         val blocks: Map<ResourceLocation, List<Entry<BlockPos>>>) {
    constructor(entities: Map<Entity, Profiler.TimingData>,
                blocks: Map<ResourceKey<Level>, Map<BlockPos, Profiler.TimingData>>,
                custom: Boolean = true
    ) : this(entities.map { (entity, data) ->
        Entry(entity, Registry.ENTITY_TYPE.getKey(entity.type).toString(), data)
    }.groupBy { it.obj.level.dimension().location() }
        .mapValues {
            it.value.map { Entry(it.obj.id, it.type, it.rate, it.traces) }
        }, blocks.map {
        it.key.location() to it.value.map { Entry(it.key, it.value.name, it.value) }
    }.toMap())

    @Serializable
    data class Entry<T>(val obj: T, val type: String, val rate: Double, val traces: List<SerializedStackTrace>) {
        constructor(obj: T, type: String, data: Profiler.TimingData) : this(obj, type,
            data.time.toDouble() / data.ticks.toDouble(), data.traces.map { SerializedStackTrace(it) })
    }

    @Serializable
    data class SerializedStackTrace(val classname: String, val fileName: String?, val lineNumber: Int, val methodName: String) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }
}