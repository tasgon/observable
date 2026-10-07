package observable.fabric

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import observable.client.ObservableClient
import observable.client.ProfilerBridge
import org.joml.Matrix4f

class Client : ClientModInitializer {
    override fun onInitializeClient() {
        ObservableClient.clientInit()
        registerRendering()
        registerHud()
    }

    private fun registerRendering() {
        LevelRenderEvents.END_MAIN.register { context ->
            renderWorld(context)
        }
    }

    private fun renderWorld(context: LevelRenderContext) {
        if (ProfilerBridge.getBridge() == null) return

        val cameraState = context.levelState().cameraRenderState
        val capturedStack = Matrix4f().identity()
        val mc = Minecraft.getInstance()
        val deltaTracker = mc.deltaTracker
        val collector = context.submitNodeCollector()

        ProfilerBridge.render(
            cameraState.projectionMatrix,
            capturedStack,
            cameraState.pos,
            capturedStack,
            deltaTracker,
            collector,
            cameraState,
            true, // FLUSH labels over translucent terrain
        )
    }

    private fun registerHud() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("observable", "overlay"),
            object : HudElement {
                override fun extractRenderState(
                    graphics: GuiGraphicsExtractor,
                    delta: DeltaTracker,
                ) {
                    if (ProfilerBridge.getBridge() != null) {
                        ProfilerBridge.renderHud(graphics, delta)
                    }
                }
            },
        )
    }
}
