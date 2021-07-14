package observable.fabric

import com.mojang.math.Quaternion
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import observable.Observable
import observable.client.Overlay
import observable.client.ProfileScreen

class Client : ClientModInitializer {
    override fun onInitializeClient() {
        Observable.clientInit()

        WorldRenderEvents.LAST.register {
            Overlay.render(poseStack = it.matrixStack(), partialTicks = it.tickDelta(), camera = it.camera())
        }
    }
}