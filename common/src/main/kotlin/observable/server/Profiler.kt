package observable.server

import ProfilingData
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.TextComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import observable.Observable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

class Profiler(val server: ServerLevel) {
    data class TimingData(var time: Long, var ticks: Int, var traces: Set<StackTraceElement>)

    var timingsMap = HashMap<Object, TimingData>()
    var notProcessing = true
    var players = ArrayList<ServerPlayer>()

    fun process(entity: Object, time: Long) {
        val timingInfo = timingsMap.getOrPut(entity) { TimingData(0, 0, HashSet()) }
        timingInfo.time += time
        timingInfo.ticks++
    }

    fun startRunning(duration: Int? = null) {
        timingsMap.clear()
        notProcessing = false
        duration?.let {
            val durMs = duration.toLong() * 1000L
            Observable.LOGGER.info("Starting profiler for $durMs ms")
            Timer("Profiler", false).schedule(durMs) {
                stopRunning()
            }
        }
    }

    fun stopRunning() {
        notProcessing = true
        val data = ProfilingData(timingsMap)
        Observable.LOGGER.info("Profiler done, sending data (${data.data.size} (block)entities logged)")
        Observable.LOGGER.info("Found clients ${players.map { (it.name as TextComponent).text }}")
        Observable.CHANNEL.rawChannel.sendToPlayers(players, S2CPacket.ProfilingResult(data))
        Observable.LOGGER.info("Data transfer complete!")
    }
}