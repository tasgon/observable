package observable.forge

import java.util.function.Supplier
import java.util.function.BiPredicate
import observable.Observable.init
import me.shedaniel.architectury.platform.forge.EventBuses
import me.shedaniel.architectury.utils.GameInstance
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.ExtensionPoint.DISPLAYTEST
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.network.FMLNetworkConstants.IGNORESERVERONLY
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

        // Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(DISPLAYTEST) {
            Pair.of(Supplier { IGNORESERVERONLY }, BiPredicate { _, _, -> true})
        }

        init()
    }

    fun onClientInit(ev: FMLClientSetupEvent) {
        Observable.clientInit()
        MinecraftForge.EVENT_BUS.register(ForgeClientHooks)
    }
}