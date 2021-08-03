@file:UseSerializers(EntitySerializer::class, ResourceLocationSerializer::class,
        BlockEntitySerializer::class, BlockPosSerializer::class)

import it.unimi.dsi.fastutil.Hash
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import observable.net.BlockEntitySerializer
import observable.net.BlockPosSerializer
import observable.net.EntitySerializer
import observable.net.ResourceLocationSerializer
import observable.server.Profiler
import kotlin.math.roundToInt

//typealias LevelMap<T> = Map<>

@Serializable
data class ProfilingData(val entities: Map<ResourceLocation, List<Entry<Int>>>,
                         val blocks: Map<ResourceLocation, List<Entry<BlockPos>>>, val ticks: Int) {
    companion object {
        fun create(entities: Map<Entity, Profiler.TimingData>,
                   blocks: Map<ResourceKey<Level>, Map<BlockPos, Profiler.TimingData>>,
                   ticks: Int): ProfilingData {

            val entityEntries = entities.map { (entity, data) ->
                Entry(entity, Registry.ENTITY_TYPE.getKey(entity.type).toString(), data)
            }.groupBy { it.obj.level.dimension().location() }.mapValues { (_, entry) ->
                entry.map { Entry(it.obj.id, it.type, it.rate, it.ticks, it.traces) }
            }

            val blockEntries = blocks.map { (level, posMap) ->
                level.location() to posMap.map { Entry(it.key, it.value.name, it.value) }
            }.toMap()

            return ProfilingData(entityEntries, blockEntries, ticks)
        }
    }

//    @Serializable
//    data class ChunkMap(var data: Map<ResourceLocation, Map<ChunkPos, ChunkEntry>>) {
//        @Serializable
//        data class ChunkPos(val x: Int, val z: Int) {
//            constructor(pos: Vec3) : this(pos.x.roundToInt() / 16, pos.z.roundToInt() / 16)
//            constructor(pos: BlockPos) : this(pos.x / 16, pos.z / 16)
//        }
//        @Serializable
//        data class ChunkEntry(val time: Double, val ticks: Int)
//
//        fun tick(entity: Entity, timings: Profiler.TimingData) {
//            val entry = data.getOrPut(entity.level.dimension().location()) { HashMap() }
//                .getOrPut(ChunkPos(entity.position())) { ChunkEntry(0.0, 0) }
//            entry.time += timings.time
//        }
//    }
//
//    class ChunkMapBuilder {
//        val data = HashMap<ResourceLocation, HashMap<ChunkMap.ChunkEntry.ChunkPos, ChunkEntry>>
//    }

    @Serializable
    data class Entry<T>(val obj: T, val type: String, val rate: Double,
                        val ticks: Int, val traces: List<SerializedStackTrace>) {
        constructor(obj: T, type: String, data: Profiler.TimingData) : this(obj, type,
            data.time.toDouble() / data.ticks.toDouble(), data.ticks, data.traces.map { SerializedStackTrace(it) })
    }

    @Serializable
    data class SerializedStackTrace(val classname: String, val fileName: String?,
                                    val lineNumber: Int, val methodName: String) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }
}