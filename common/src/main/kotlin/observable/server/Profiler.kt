package observable.server

import ProfilingData
import me.shedaniel.architectury.networking.NetworkManager
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import observable.Observable
import observable.net.S2CPacket
import java.util.*
import kotlin.concurrent.schedule

class Profiler {
    data class TimingData(var time: Long, var ticks: Int, var traces: Set<StackTraceElement>, var name: String = "")

    var timingsMap = HashMap<Entity, TimingData>()
    // TODO: consider splitting out block entity timings
//    var blockEntityTimingsMap = HashMap<BlockEntity, TimingData>()
    var blockTimingsMap = HashMap<ResourceKey<Level>, HashMap<BlockPos, TimingData>>()
    var notProcessing = true

    fun process(entity: Entity, time: Long) {
        val timingInfo = timingsMap.getOrPut(entity) { TimingData(0, 0, HashSet()) }
        timingInfo.time += time
        timingInfo.ticks++
    }

    fun processBlock(blockState: BlockState, pos: BlockPos, level: Level, time: Long) {
        val blockMap = blockTimingsMap.getOrPut(level.dimension()) { HashMap() }
        val timingInfo = blockMap.getOrPut(pos) { TimingData(0, 0, HashSet(), blockState.block.name.string) }
        timingInfo.time += time
        timingInfo.ticks++
    }

    fun processBlockEntity(blockEntity: BlockEntity, time: Long) {
        if (blockEntity.level == null) {
            Observable.LOGGER.warn("Block entity at ${blockEntity.blockPos} has no associated dimension")
            return
        }

        val blockMap = blockTimingsMap.getOrPut(blockEntity.level!!.dimension()) { HashMap() }
        val timingInfo = blockMap.getOrPut(blockEntity.blockPos) {
            TimingData(0, 0, HashSet(), blockEntity.blockState.block.name.string)
        }
        timingInfo.time += time
        timingInfo.ticks++
    }

    fun processFluid(blockState: FluidState, pos: BlockPos, level: Level, time: Long) {
        val blockMap = blockTimingsMap.getOrPut(level.dimension()) { HashMap() }
        val timingInfo = blockMap.getOrPut(pos) { TimingData(0, 0, HashSet(), blockState.type.toString()) }
        timingInfo.time += time
        timingInfo.ticks++
    }

    fun startRunning(duration: Int? = null, ctx: NetworkManager.PacketContext) {
        timingsMap.clear()
        val start = System.nanoTime()
        notProcessing = false
        duration?.let {
            val durMs = duration.toLong() * 1000L
            Observable.CHANNEL.sendToPlayers(GameInstance.getServer()!!.playerList.players,
                S2CPacket.ProfilingStarted(start + durMs * 1000000L))
            Observable.LOGGER.info("${(ctx.player.name as TextComponent).text} started profiler for $duration s")
            Timer("Profiler", false).schedule(durMs) {
                stopRunning()
            }
        }
    }

    fun stopRunning() {
        notProcessing = true
        val data = ProfilingData(timingsMap, blockTimingsMap)
        val players = GameInstance.getServer()!!.playerList.players
        Observable.LOGGER.info("Profiler done, sending data (${data.entities.size} (block)entities logged)")
        Observable.LOGGER.info("Found clients ${players.map { (it.name as TextComponent).text }}")
        Observable.CHANNEL.sendToPlayers(players, S2CPacket.ProfilingResult(data))
        Observable.LOGGER.info("Data transfer complete!")
    }
}