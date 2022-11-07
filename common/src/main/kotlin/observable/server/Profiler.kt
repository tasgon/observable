package observable.server

import dev.architectury.utils.GameInstance
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.network.chat.*
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.TickingBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import observable.Observable
import observable.Props
import observable.net.S2CPacket
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.schedule
import kotlin.random.Random

inline val StackTraceElement.classMethod get() = "${this.className} + ${this.methodName}"

class Profiler {
    data class TimingData(var time: Long, var ticks: Int, var traces: TraceMap, var name: String = "")

    var timingsMap = HashMap<Entity, TimingData>()
    lateinit var serverTraceMap: TraceMap
    lateinit var serverThread: Thread
    lateinit var samplerThread: Thread

    // TODO: consider splitting out block entity timings
//    var blockEntityTimingsMap = HashMap<BlockEntity, TimingData>()
    var blockTimingsMap = HashMap<ResourceKey<Level>, HashMap<BlockPos, TimingData>>()
    var notProcessing
        get() = Props.notProcessing
        set(v) {
            Props.notProcessing = v
        }

    var player: ServerPlayer? = null
    var startTime: Long = 0
    var startingTicks: Int = 0

    fun process(entity: Entity) = timingsMap.getOrPut(entity) {
        TimingData(0, 0, TraceMap(entity::class))
    }

    fun processBlockEntity(blockEntity: TickingBlockEntity, level: Level) = blockTimingsMap.getOrPut(level.dimension()) {
        HashMap()
    }.getOrPut(blockEntity.pos) {
        TimingData(
            0,
            0,
            TraceMap(blockEntity::class),
            blockEntity.type
        )
    }

    fun processBlock(blockState: BlockState, pos: BlockPos, level: Level) =
        blockTimingsMap.getOrPut(level.dimension()) { HashMap() }
            .getOrPut(pos) {
                TimingData(
                    0,
                    0,
                    TraceMap(blockState::class),
                    blockState.block.descriptionId
                )
            }

    fun processFluid(fluidState: FluidState, pos: BlockPos, level: Level) =
        blockTimingsMap.getOrPut(level.dimension()) { HashMap() }.getOrPut(pos) {
            TimingData(
                0,
                0,
                TraceMap(fluidState::class),
                Registry.FLUID.getKey(fluidState.type).toString()
            )
        }

    fun startRunning(sample: Boolean = false) {
        timingsMap.clear()
        blockTimingsMap.clear()
        serverTraceMap = TraceMap()
        startTime = System.currentTimeMillis()
        synchronized(Props.notProcessing) {
            notProcessing = false
            startingTicks = GameInstance.getServer()!!.tickCount
        }
        if (sample) {
            samplerThread = Thread(TaggedSampler(serverThread))
            samplerThread.start()

            Thread {
                while (!Props.notProcessing) {
                    val interval = ServerSettings.traceInterval.toLong()
                    val deviation = ServerSettings.deviation.toLong()
                    serverTraceMap.add(serverThread.stackTrace.reversed().iterator())
                    Thread.sleep(interval + Random.nextLong(-deviation, deviation))
                }
            }.start()
        }
    }

    fun runWithDuration(player: ServerPlayer?, duration: Int, sample: Boolean, onComplete: (Component) -> Unit) {
        this.player = player
        startRunning(sample)
        val durMs = duration.toLong() * 1000L
        Observable.CHANNEL.sendToPlayers(
            GameInstance.getServer()!!.playerList.players,
            S2CPacket.ProfilingStarted(startTime + durMs)
        )
        Timer("Profiler", false).schedule(durMs) {
            val result = stopRunning()
            onComplete(result)
        }
    }

    fun uploadProfile(data: ProfilingData, diagnostics: JsonObject): Component {
        Observable.LOGGER.info("Attempting to upload profile")
        val serialized = Json.encodeToString(DataWithDiagnostics(data, diagnostics))

        return try {
            val conn = URL("https://observable.tas.sh/v1/add").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true

            GZIPOutputStream(conn.outputStream).bufferedWriter(Charsets.UTF_8).use { it.write(serialized) }

            val profileURL = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Observable.LOGGER.info("Profile uploaded to $profileURL")
            val link = Component.literal(profileURL).withStyle(ChatFormatting.UNDERLINE).withStyle {
                it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, profileURL))
            }

            Component.translatable("text.observable.profile_uploaded", link)
        } catch (e: Exception) {
            e.printStackTrace()
            Component.translatable("text.observable.upload_failed")
        }
    }

    fun stopRunning(): Component {
        val diagnostics = getDiagnostics()
        val ticks: Int
        synchronized(Props.notProcessing) {
            notProcessing = true
            ticks = GameInstance.getServer()!!.tickCount - startingTicks
        }
        val players = player?.let { listOf(it) } ?: listOf()
        Observable.CHANNEL.sendToPlayers(players, S2CPacket.ProfilingCompleted)
        val data = ProfilingData.create(timingsMap, blockTimingsMap, ticks, serverTraceMap)
        Observable.LOGGER.info("Profiler ran for $ticks ticks, sending data")
        Observable.LOGGER.info("Sending to ${players.map { it.gameProfile.name }}")
        Observable.CHANNEL.sendToPlayersSplit(players, S2CPacket.ProfilingResult(data))
        val result = uploadProfile(data, diagnostics)
        Observable.LOGGER.info("Data transfer complete!")
        GameInstance.getServer()?.playerList?.players?.filter { Observable.hasPermission(it) }?.let {
            Observable.CHANNEL.sendToPlayers(it, S2CPacket.ProfilerInactive)
        }
        return result
    }
}
