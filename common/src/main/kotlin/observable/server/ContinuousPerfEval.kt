package observable.server

import me.shedaniel.architectury.event.events.TickEvent
import me.shedaniel.architectury.utils.GameInstance
import observable.Observable
import observable.net.S2CPacket

object ContinuousPerfEval {
    var lastTime = System.nanoTime()
    var idx: UInt = 0u
    val timings = LongArray(40)
    var lastNotified = Long.MIN_VALUE / 2L
    var started = false

    fun start() {
        if (started) return
        TickEvent.SERVER_POST.register {
            idx += 1u
            if (idx < 200u) return@register
            val time = System.nanoTime()
            timings[idx.toInt() % timings.size] = time - lastTime
            lastTime = time
            val tps = 1e9 / timings.average()
            val curMs = System.currentTimeMillis()

            if (tps < 16.0 && (curMs - lastNotified) > ServerSettings.notifyInterval) {
                Observable.LOGGER.info("Server running slow, notifying valid players")
                val playerList = GameInstance.getServer()!!.playerList
                Observable.CHANNEL.sendToPlayers(playerList.players.filter {
                    Observable.hasPermission(it)
                }, S2CPacket.ConsiderProfiling(tps))
                lastNotified = curMs
            }
        }
        started = true
    }
}