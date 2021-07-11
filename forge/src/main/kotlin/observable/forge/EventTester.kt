package observable.forge

import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import observable.Observable

object EventTester {
    var ticks = 0
    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (ticks % 20 == 0) Observable.LOGGER.info("$ticks ticks")
        ticks++
    }
}