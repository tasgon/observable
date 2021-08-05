package observable.forge

import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import observable.client.Overlay

object ForgeClientHooks {
    @SubscribeEvent
    fun onRender(ev: RenderWorldLastEvent) {
        Overlay.render(ev.matrixStack, ev.partialTicks, ev.projectionMatrix)
    }
}