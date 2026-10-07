package observable.forge

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import observable.client.ProfilerBridge

object ForgeClientHooks {
    @SubscribeEvent
    fun onRenderLevelStage(event: RenderLevelStageEvent.AfterLevel) {
        val mc = Minecraft.getInstance()
        val renderState = event.levelRenderState
        val cameraState = renderState.cameraRenderState
        // In AfterLevel, we use the AUTHORITATIVE modelViewMatrix from the event.
        // Because our common code (ProfilerBridge) subtracts cameraPos from every vertex,
        // we pass a ZERO camera position here so the common code does (pos - 0) = pos.
        // Then, the authoritative modelViewMatrix handles the world -> camera translation correctly.
        val zeroPos = Vec3(0.0, 0.0, 0.0)

        ProfilerBridge.render(
            null,
            event.modelViewMatrix,
            zeroPos,
            event.modelViewMatrix,
            mc.deltaTracker,
            null,
            cameraState,
            true, // FLUSH labels over water and particles
        )
    }

    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        ProfilerBridge.renderHud(event.guiGraphics, event.partialTick)
    }
}
