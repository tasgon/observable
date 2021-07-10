package observable.fabric

import net.fabricmc.api.ModInitializer
import observable.Observable

class ObservableFabric : ModInitializer {
    override fun onInitialize() {
        Observable.init()
    }
}