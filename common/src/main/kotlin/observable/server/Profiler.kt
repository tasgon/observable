package observable.server

import observable.server.ProfilingData
import dev.architectury.networking.NetworkManager
import dev.architectury.utils.GameInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.TickingBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import observable.Observable
import observable.Props
import observable.net.S2CPacket
import java.util.*
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

    var startingTicks: Int = 0

    fun process(entity: Entity) = timingsMap.getOrPut(entity) {
        TimingData(0, 0, TraceMap(entity::class))
    }

    fun processBlockEntity(blockEntity: TickingBlockEntity, level: Level) = blockTimingsMap.getOrPut(level.dimension()) {
        HashMap()
    }.getOrPut(blockEntity.pos) {
        TimingData(0, 0, TraceMap(blockEntity::class),
            blockEntity.type)
    }

    fun processBlock(blockState: BlockState, pos: BlockPos, level: Level) =
        blockTimingsMap.getOrPut(level.dimension()) { HashMap() }
            .getOrPut(pos) {
                TimingData(0, 0, TraceMap(blockState::class),
                    blockState.block.descriptionId)
            }

    fun processFluid(fluidState: FluidState, pos: BlockPos, level: Level) =
        blockTimingsMap.getOrPut(level.dimension()) { HashMap() }.getOrPut(pos) {
            TimingData(0, 0, TraceMap(fluidState::class),
                Registry.FLUID.getKey(fluidState.type).toString())
        }

    fun startRunning(duration: Int? = null, sample: Boolean = false, ctx: NetworkManager.PacketContext) {
        player = ctx.player as? ServerPlayer
        timingsMap.clear()
        blockTimingsMap.clear()
        serverTraceMap = TraceMap()
        val start = System.currentTimeMillis()
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

        val durMs = (duration?.toLong() ?: return) * 1000L
        Observable.CHANNEL.sendToPlayers(
            GameInstance.getServer()!!.playerList.players,
            S2CPacket.ProfilingStarted(start + durMs)
        )
        Observable.LOGGER.info("${(ctx.player.name as TextComponent).text} started profiler for $duration s")
        Timer("Profiler", false).schedule(durMs) {
            stopRunning()
        }
    }

    fun stopRunning() {
        val ticks: Int
        synchronized(Props.notProcessing) {
            notProcessing = true
            ticks = GameInstance.getServer()!!.tickCount - startingTicks
        }
        val players = player?.let { listOf(it) } ?: GameInstance.getServer()!!.playerList.players
        Observable.CHANNEL.sendToPlayers(players, S2CPacket.ProfilingCompleted)
        val data = ProfilingData.create(timingsMap, blockTimingsMap, ticks, serverTraceMap)
        Observable.LOGGER.info("Profiler ran for $ticks ticks, sending data")
        Observable.LOGGER.info("Sending to ${players.map { (it.name as TextComponent).text }}")
        Observable.CHANNEL.sendToPlayersSplit(players, S2CPacket.ProfilingResult(data))
        Observable.LOGGER.info("Data transfer complete!")
        GameInstance.getServer()?.playerList?.players?.filter { Observable.hasPermission(it) }?.let {
            Observable.CHANNEL.sendToPlayers(it, S2CPacket.ProfilerInactive)
        }
    }
}