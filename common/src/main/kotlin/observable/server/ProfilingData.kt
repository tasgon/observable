@file:UseSerializers(
    EntitySerializer::class,
    ResourceLocationSerializer::class,
    BlockEntitySerializer::class,
    BlockPosSerializer::class
)

package observable.server

import dev.architectury.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.SystemReport
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import observable.Observable
import observable.net.*

fun getPosition(obj: Any?): BlockPos = when (obj) {
    is Entity -> BlockPos(obj.position())
    is BlockPos -> obj
    else -> BlockPos.ZERO
}

@Serializable
data class ProfilingData(
    val entities: Map<ResourceLocation, List<Entry>>,
    val blocks: Map<ResourceLocation, List<Entry>>,
    val traces: SerializedTraceMap?,
    val ticks: Int
) {
    companion object {
        fun create(
            entities: Map<Entity, Profiler.TimingData>,
            blocks: Map<ResourceKey<Level>, Map<BlockPos, Profiler.TimingData>>,
            ticks: Int,
            traceMap: TraceMap? = null
        ): ProfilingData {
            val entityEntries =
                entities.asIterable().groupBy { it.key.level.dimension().location() }.mapValues { (_, entries) ->
                    entries.map { (entity, data) ->
                        Entry(entity, Registry.ENTITY_TYPE.getKey(entity.type).toString(), data)
                    }
                }

            val blockEntries = blocks.map { (level, posMap) ->
                level.location() to posMap.map { (pos, data) ->
                    Entry(pos, data.name, data)
                }
            }.toMap()

            return ProfilingData(
                entityEntries,
                blockEntries,
                traceMap?.let { SerializedTraceMap.create(it) },
                ticks
            )
        }
    }

    @Serializable
    data class Entry(
        val entityId: Int? = null,
        val position: BlockPos,
        val type: String,
        val rate: Double,
        val ticks: Int,
        val traces: SerializedTraceMap
    ) {
        constructor(obj: Any, type: String, data: Profiler.TimingData) : this(
            (obj as? Entity)?.id,
            getPosition(obj),
            type,
            data.time.toDouble() / data.ticks.toDouble(),
            data.ticks,
            SerializedTraceMap.create(data.traces)
        )
    }

    @Serializable
    data class SerializedStackTrace(
        val classname: String,
        val fileName: String?,
        val lineNumber: Int,
        val methodName: String
    ) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }

    @Serializable
    data class SerializedTraceMap(
        val className: String,
        val methodName: String,
        val children: List<SerializedTraceMap>,
        val count: Int
    ) {
        companion object {
            fun create(traceMap: TraceMap): SerializedTraceMap {
                Remapper.transform(traceMap)

                return SerializedTraceMap(
                    traceMap.className,
                    traceMap.methodName,
                    traceMap.children.map { (_, map) ->
                        Remapper.transform(map)
                        SerializedTraceMap.create(map)
                    }.sortedByDescending { it.count },
                    traceMap.count
                )
            }
        }

        val classMethod get() = "$className.$methodName"
    }
}

@Serializable
data class DataWithDiagnostics(val data: ProfilingData, val diagnostics: JsonObject) {
    constructor(data: ProfilingData) : this(
        data,
        buildJsonObject {
            put("Observable Version", JsonPrimitive(Platform.getMod(Observable.MOD_ID).version))
            put(
                "System Report",
                buildJsonArray {
                    SystemReport().toLineSeparatedString().split(System.lineSeparator()).forEach { add(JsonPrimitive(it)) }
                }
            )
            put(
                "Mods",
                buildJsonArray {
                    Platform.getMods().forEach { mod ->
                        add(JsonPrimitive("${mod.name} ${mod.version}"))
                    }
                }
            )
        }
    )
}
