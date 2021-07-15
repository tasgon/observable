package observable.server

import ProfilingData
import me.shedaniel.architectury.networking.NetworkManager
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.network.chat.TextComponent
import observable.Observable
import observable.net.S2CPacket
import java.util.*
import kotlin.concurrent.schedule

class Profiler {
    data class TimingData(var time: Long, var ticks: Int, var traces: Set<StackTraceElement>)\

    var timingsMap = HashMap<Any, TimingData>()
    var notProcessing = true
    var lastExec = 0L

    fun process(entity: Any, time: Long) {
        val timingInfo = timingsMap.getOrPut(entity) { TimingData(0, 0, HashSet()) }
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
        val data = ProfilingData(timingsMap)
        val players = GameInstance.getServer()!!.playerList.players
        Observable.LOGGER.info("Profiler done, sending data (${data.entries.size} (block)entities logged)")
        Observable.LOGGER.info("Found clients ${players.map { (it.name as TextComponent).text }}")
        Observable.CHANNEL.sendToPlayers(players, S2CPacket.ProfilingResult(data))
        Observable.LOGGER.info("Data transfer complete!")
    }
}