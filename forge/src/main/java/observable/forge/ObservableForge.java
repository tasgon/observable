package observable.forge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import observable.Observable;
import static observable.Observable.init;
import observable.server.ModLoader;
import observable.server.Remapper;


@Mod(Observable.MOD_ID)
public class ObservableForge {
    public ObservableForge() {
        Remapper.modLoader = ModLoader.FORGE;

        init();

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            onClientInit();
        }
    }

    public void onClientInit() {
        Observable.clientInit();
        NeoForge.EVENT_BUS.register(ForgeClientHooks.INSTANCE);
    }
}
