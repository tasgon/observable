package observable.forge

import net.minecraftforge.client.event.RenderLevelLastEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import observable.client.Overlay

object ForgeClientHooks {
    @SubscribeEvent
    fun onRender(ev: RenderLevelLastEvent) {
        Overlay.render(ev.poseStack, ev.partialTick, ev.projectionMatrix)
    }
}