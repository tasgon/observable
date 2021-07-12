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

class Profiler(val server: ServerLevel) {
    data class TimingData(var time: Long, var ticks: Int, var traces: Set<StackTraceElement>)

    var timingsMap = HashMap<Object, TimingData>()
    var notProcessing = true

    var processing
        get() = !notProcessing
        set(value) { notProcessing = !value }

    fun process(entity: Object, time: Long) {
        var timingInfo = timingsMap.getOrDefault(entity, TimingData(0, 0, HashSet()))
        timingInfo.time += time
        timingInfo.ticks++
    }

    fun startRunning(duration: Int? = null) {
        timingsMap.clear()
        processing = false
        duration?.let {
            val durMs = duration.toLong() * 1000L
            Observable.LOGGER.info("Starting profiler for $durMs ms")
            Timer("Profiler", false).schedule(durMs) {
                stopRunning()
            }
        }
    }

    fun stopRunning() {
        processing = false
        Observable.LOGGER.info("Profiler done, sending data")
        val data = ProfilingData(timingsMap)
        Observable.CHANNEL.rawChannel.sendToPlayers(server.players(), S2CPacket.ProfilingResult(data))
        Observable.LOGGER.info("Data transfer complete!")
    }
}