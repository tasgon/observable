package observable.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.TickingBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import observable.Observable
import observable.Props
import observable.net.*
import observable.net.S2CPacket
import java.net.HttpURLConnection
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.schedule

class Profiler {
    var timingsMap = HashMap<Entity, NativeTimingData>()
    var blockTimingsMap = HashMap<ResourceKey<Level>, HashMap<BlockPos, NativeTimingData>>()
    private val lock = Any()

    var notProcessing: Boolean
        get() = Props.notProcessing.get()
        set(v) = Props.notProcessing.set(v)

    var player: ServerPlayer? = null
    var startTime: Long = 0
    var startingTicks: Int = 0

    fun process(entity: Entity) = timingsMap.getOrPut(entity) { NativeTimingData(0, 0, "", TraceMap(entity::class)) }

    fun processBlockEntity(blockEntity: TickingBlockEntity, level: Level) = blockTimingsMap
        .getOrPut(level.dimension()) { HashMap() }
        .getOrPut(blockEntity.pos) {
            NativeTimingData(0, 0, blockEntity.type, TraceMap(blockEntity.javaClass.name))
        }

    fun processBlock(state: BlockState, pos: BlockPos, level: Level) = blockTimingsMap
        .getOrPut(level.dimension()) { HashMap() }
        .getOrPut(pos) {
            NativeTimingData(0, 0, BuiltInRegistries.BLOCK.getKey(state.block).toString(), TraceMap(state.block::class))
        }

    fun processFluid(state: FluidState, pos: BlockPos, level: Level) = blockTimingsMap
        .getOrPut(level.dimension()) { HashMap() }
        .getOrPut(pos) {
            NativeTimingData(0, 0, BuiltInRegistries.FLUID.getKey(state.type).toString(), TraceMap(state.type::class))
        }

    fun startRunning(sample: Boolean = false) {
        timingsMap.clear()
        blockTimingsMap.clear()
        startTime = System.currentTimeMillis()
        synchronized(lock) {
            notProcessing = false
            startingTicks = Observable.SERVER_INSTANCE?.tickCount ?: 0
        }
        if (sample) {
            // Start the background sampler thread
            val thread = Thread(TaggedSampler(Thread.currentThread()))
            thread.name = "Observable-Sampler"
            thread.isDaemon = true
            thread.start()
        }
    }

    fun runWithDuration(
        player: ServerPlayer?,
        duration: Int,
        sample: Boolean
    ) {
        this.player = player
        startRunning(sample)
        val durMs = duration.toLong() * 1000L
        Observable.CHANNEL.sendToPlayers(
            Observable.SERVER_INSTANCE!!.playerList.players,
            S2CPacket.ProfilingStarted(System.currentTimeMillis() + durMs),
        )
        Timer("Profiler", false).schedule(durMs) {
            Observable.SERVER_INSTANCE?.execute {
                stopRunning()
            }
        }
    }

    fun uploadProfile(data: ProfilingData, diagnostics: JsonObject): String? {
        if (ServerSettings.uploadURL.isEmpty()) {
            Observable.LOGGER.info("uploadURL not set, skipping upload")
            return null
        }

        Observable.LOGGER.info("Attempting to upload profile")
        val serialized = Json.encodeToString(DataWithDiagnostics(data, diagnostics))

        return try {
            val conn = java.net.URI.create(ServerSettings.uploadURL).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true

            Observable.LOGGER.info("Writing ${String.format("%.2f", serialized.length / 1000.0)}kb")
            GZIPOutputStream(conn.outputStream).bufferedWriter(Charsets.UTF_8).use {
                it.write(serialized)
            }

            val profileURL = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Observable.LOGGER.info("Profile uploaded to $profileURL")

            profileURL
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopRunning() {
        val diagnostics = getDiagnostics()
        val ticks: Int
        synchronized(lock) {
            notProcessing = true
            ticks = (Observable.SERVER_INSTANCE?.tickCount ?: 0) - startingTicks
            Props.currentTarget.set(null)
        }

        val playerList = player?.let { listOf(it) } ?: listOf()
        Observable.CHANNEL.sendToPlayers(playerList, S2CPacket.ProfilingCompleted)

        val rootTraceMap = TraceMap("Server", "all")
        timingsMap.values.forEach { rootTraceMap.merge(it.traces as TraceMap) }
        blockTimingsMap.values.forEach { posMap ->
            posMap.values.forEach { rootTraceMap.merge(it.traces as TraceMap) }
        }

        val data = ProfilingData.create(timingsMap, blockTimingsMap, ticks, rootTraceMap)
        Observable.RESULTS = data

        Observable.LOGGER.info("Profiler ran for $ticks ticks, sending data")

        val link = uploadProfile(data, diagnostics)
        Observable.CHANNEL.sendToPlayers(playerList, S2CPacket.ProfilingResult(data, link))

        Observable.LOGGER.info("Data transfer complete!")

        Observable.SERVER_INSTANCE
            ?.playerList
            ?.players
            ?.filter { Observable.hasPermission(it) }
            ?.let { Observable.CHANNEL.sendToPlayers(it, S2CPacket.ProfilerInactive) }
    }

    fun init() {
        NativeProfiler.setBlockEntityTicker { ticker, level ->
            if (notProcessing) {
                ticker.tick()
                return@setBlockEntityTicker
            }
            val timing = processBlockEntity(ticker, level)
            Props.currentTarget.set(timing)
            val start = System.nanoTime()
            ticker.tick()
            val end = System.nanoTime()
            Props.currentTarget.set(null)

            timing.apply {
                time += (end - start)
                ticks++
            }
        }
        NativeProfiler.setEntityTicker { entity, tickMethod ->
            if (notProcessing) {
                tickMethod.accept(entity)
                return@setEntityTicker
            }
            val timing = process(entity)
            Props.currentTarget.set(timing)
            val start = System.nanoTime()
            tickMethod.accept(entity)
            val end = System.nanoTime()
            Props.currentTarget.set(null)

            timing.apply {
                time += (end - start)
                ticks++
            }
        }
        NativeProfiler.setBlockTicker { state, level, pos, random ->
            if (notProcessing) {
                state.tick(level, pos, random)
                return@setBlockTicker
            }
            val timing = processBlock(state, pos, level)
            Props.currentTarget.set(timing)
            val start = System.nanoTime()
            state.tick(level, pos, random)
            val end = System.nanoTime()
            Props.currentTarget.set(null)

            timing.apply {
                time += (end - start)
                ticks++
            }
        }
        NativeProfiler.setFluidTicker { state, level, pos, blockState ->
            if (notProcessing) {
                state.tick(level, pos, blockState)
                return@setFluidTicker
            }
            val timing = processFluid(state, pos, level)
            Props.currentTarget.set(timing)
            val start = System.nanoTime()
            state.tick(level, pos, blockState)
            val end = System.nanoTime()
            Props.currentTarget.set(null)

            timing.apply {
                time += (end - start)
                ticks++
            }
        }
    }
}
