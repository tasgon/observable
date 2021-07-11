package observable.fabric

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import observable.Observable
import observable.client.ProfileScreen

class Client : ClientModInitializer {
    override fun onInitializeClient() {
        Observable.clientInit()
    }
}