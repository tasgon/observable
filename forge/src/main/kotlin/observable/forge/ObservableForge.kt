package observable.forge

import observable.Observable.init
import me.shedaniel.architectury.platform.forge.EventBuses
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import observable.Observable

@Mod(Observable.MOD_ID)
class ObservableForge {
    init {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(Observable.MOD_ID, FMLJavaModLoadingContext.get().modEventBus)
        init()
    }
}