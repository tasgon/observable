package observable.forge

import java.util.function.Supplier
import java.util.function.BiPredicate
import observable.Observable.init
import dev.architectury.platform.forge.EventBuses
import dev.architectury.utils.GameInstance
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.IExtensionPoint.DisplayTest
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.network.NetworkConstants.IGNORESERVERONLY
import observable.Observable
import observable.server.ModLoader
import observable.server.Remapper
import org.apache.commons.lang3.tuple.Pair

@Mod(Observable.MOD_ID)
class ObservableForge {
    init {
        Remapper.modLoader = ModLoader.FORGE
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