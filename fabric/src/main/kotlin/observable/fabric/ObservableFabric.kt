package observable.fabric

import net.fabricmc.api.ModInitializer
import observable.Observable
import observable.server.ModLoader
import observable.server.Remapper

class ObservableFabric : ModInitializer {
    override fun onInitialize() {
        Remapper.modLoader = ModLoader.FABRIC
        Observable.init()
    }
}
