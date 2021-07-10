package observable.forge;

import me.shedaniel.architectury.platform.forge.EventBuses;
import observable.Observable;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Observable.MOD_ID)
public class ObservableForge {
    public ObservableForge() {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(Observable.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        Observable.init();
    }
}
