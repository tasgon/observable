package observable.forge

import observable.Observable.init
import me.shedaniel.architectury.platform.forge.EventBuses
import me.shedaniel.architectury.utils.GameInstance
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import observable.Observable

@Mod(Observable.MOD_ID)
class ObservableForge {
    init {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(Observable.MOD_ID, FMLJavaModLoadingContext.get().modEventBus)
        FMLJavaModLoadingContext.get().modEventBus.addListener(this::onClientInit)
        init()
    }

    fun onClientInit(ev: FMLClientSetupEvent) {
        Observable.clientInit()
        MinecraftForge.EVENT_BUS.register(ForgeClientHooks)
    }
}