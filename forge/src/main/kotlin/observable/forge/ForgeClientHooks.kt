package observable.forge

import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import observable.client.Overlay

object ForgeClientHooks {
    @SubscribeEvent
    fun onRender(ev: RenderLevelStageEvent) {
        if (ev.stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Overlay.render(ev.poseStack, ev.partialTick, ev.projectionMatrix)
        }
    }
}
