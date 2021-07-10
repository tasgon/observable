package observable.fabric;

import observable.Observable;
import net.fabricmc.api.ModInitializer;

public class ObservableFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Observable.init();
    }
}
