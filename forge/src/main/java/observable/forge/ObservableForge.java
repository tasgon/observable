package observable.forge;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import observable.Observable;
import observable.client.ObservableClient;

@Mod(Observable.MOD_ID)
public class ObservableForge {
    public ObservableForge(IEventBus bus) {
        Observable.init();

        if (Platform.getEnvironment() == Env.CLIENT) {
            ObservableClient.clientInit();
            NeoForge.EVENT_BUS.register(ForgeClientHooks.INSTANCE);
        }
    }
}
