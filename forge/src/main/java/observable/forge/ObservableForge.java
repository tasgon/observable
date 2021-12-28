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
        var patchLoader = System.getenv("O_PATCH_LOADER");
        if (Objects.equals(patchLoader, "true")) {
            var loader = ((ModuleClassLoader)this.getClass().getClassLoader());
            loader.setFallbackClassLoader(new HackyLoader());
        }

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

    // Until McModLauncher/modlauncher#78 gets dealt with, we have to
    // monkey patch our own classloader to make things work
    public static class HackyLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // Try them all till one of them works
            try {
                try {
                    return ClassLoader.getPlatformClassLoader().loadClass(name);
                } catch (ClassNotFoundException e) {
                    return com.google.gson.Gson.class.getClassLoader().loadClass(name);
                }
            } catch (ClassNotFoundException e) {
                return ClassLoader.getSystemClassLoader().loadClass(name);
            }
        }
    }
}