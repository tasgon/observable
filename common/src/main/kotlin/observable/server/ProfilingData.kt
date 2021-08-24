@file:UseSerializers(EntitySerializer::class, ResourceLocationSerializer::class,
        BlockEntitySerializer::class, BlockPosSerializer::class)

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import observable.net.BlockEntitySerializer
import observable.net.BlockPosSerializer
import observable.net.EntitySerializer
import observable.net.ResourceLocationSerializer
import observable.server.Profiler
import observable.server.TraceMap
import kotlin.math.roundToInt

@Serializable
data class ProfilingData(val entities: Map<ResourceLocation, List<Entry<Int>>>,
                         val blocks: Map<ResourceLocation, List<Entry<BlockPos>>>,
                         val chunks: ChunkMap, val ticks: Int) {
    companion object {
        fun create(entities: Map<Entity, Profiler.TimingData>,
                   blocks: Map<ResourceKey<Level>, Map<BlockPos, Profiler.TimingData>>,
                   ticks: Int): ProfilingData {
            val chunks = ChunkMapBuilder()

            val entityEntries = entities.map { (entity, data) ->
                chunks.tick(entity, data)
                Entry(entity, Registry.ENTITY_TYPE.getKey(entity.type).toString(), data)
            }.groupBy { it.obj.level.dimension().location() }.mapValues { (_, entry) ->
                entry.map { Entry(it.obj.id, it.type, it.rate, it.ticks, it.traces) }
            }

            val blockEntries = blocks.map { (level, posMap) ->
                level.location() to posMap.map { (pos, data) ->
                    chunks.tick(level, pos, data)
                    Entry(pos, data.name, data)
                }
            }.toMap()

            return ProfilingData(entityEntries, blockEntries, chunks.build(ticks), ticks)
        }
    }

    @Serializable
    data class SerializedChunkPos(val x: Int, val z: Int) {
        constructor(pos: ChunkPos) : this(pos.x, pos.z)
        constructor(pos: BlockPos) : this(ChunkPos(pos))
        constructor(pos: Vec3) : this(pos.x.roundToInt() / 16, pos.z.roundToInt() / 16)
    }
    @Serializable
    data class SerializedChunkEntry(var time: Long) {
        constructor() : this(0L)
    }

    class ChunkMapBuilder {
        val data = HashMap<ResourceLocation, HashMap<SerializedChunkPos, SerializedChunkEntry>>()

        fun tick(entity: Entity, timings: Profiler.TimingData) {
            val entry = data.getOrPut(entity.level.dimension().location()) { HashMap() }
                .getOrPut(SerializedChunkPos(entity.blockPosition())) { SerializedChunkEntry() }
            entry.time += timings.time
        }

        fun tick(levelKey: ResourceKey<Level>, pos: BlockPos, timings: Profiler.TimingData) {
            val entry = data.getOrPut(levelKey.location()) { HashMap() }
                .getOrPut(SerializedChunkPos(pos)) { SerializedChunkEntry() }
            entry.time += timings.time
        }

        fun build(ticks: Int): ChunkMap = data.toMap().mapValues { (_, value) ->
            value.toList().map { (pos, entry) ->
                Pair(pos, entry.time.toDouble() / ticks)
            }.sortedByDescending { it.second }
        }
    }

    @Serializable
    data class Entry<T>(val obj: T, val type: String, val rate: Double,
                        val ticks: Int, val traces: SerializedTraceMap) {
        constructor(obj: T, type: String, data: Profiler.TimingData) : this(obj, type,
            data.time.toDouble() / data.ticks.toDouble(), data.ticks, SerializedTraceMap(data.traces))
    }

    @Serializable
    data class SerializedStackTrace(val classname: String, val fileName: String?,
                                    val lineNumber: Int, val methodName: String) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }

    @Serializable
    data class SerializedTraceMap(val classname: String, val methodName: String,
                                  val children: List<SerializedTraceMap>, val count: Int) {
        constructor(traceMap: TraceMap) : this(traceMap.className, traceMap.classMethod,
            traceMap.children.map { (_, map) ->
                SerializedTraceMap(map)
            }, traceMap.count)
    }
}

typealias ChunkMap = Map<ResourceLocation, List<Pair<ProfilingData.SerializedChunkPos, Double>>>