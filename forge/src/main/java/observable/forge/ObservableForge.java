package observable.forge;

import cpw.mods.cl.ModuleClassLoader;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import observable.Observable;
import static observable.Observable.init;
import observable.server.ModLoader;
import observable.server.Remapper;

import java.util.Objects;

@Mod(Observable.MOD_ID)
public class ObservableForge {
    public ObservableForge() {
        Remapper.modLoader = ModLoader.FORGE;
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(Observable.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientInit);

        init();
    }

    public void onClientInit(FMLClientSetupEvent ev) {
        Observable.clientInit();
        MinecraftForge.EVENT_BUS.register(ForgeClientHooks.INSTANCE);
    }
}