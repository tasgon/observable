package observable.server

import ProfilingData
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
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
        data.data.slice(0..5).withIndex().forEach { (idx, v) ->
            val (obj, timingData) = v
            Observable.LOGGER.info("$idx: ${obj.className} -- ${(timingData.rate * 1000).roundToInt()} us/t")
        }
        Observable.CHANNEL.rawChannel.sendToPlayers(server.players(), S2CPacket.ProfilingResult(data))
        Observable.LOGGER.info("Data transfer complete!")
    }
}