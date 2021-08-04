package observable.client

import net.minecraft.client.Minecraft

object Settings {
    var minRate: Int = 0
        set(v) {
            field = v
            Overlay.loadSync()
        }

    var maxBlockDist: Int = 128
        @Synchronized set

    var maxEntityDist: Int = 2048
        @Synchronized set

    var normalized = false
        set(v) {
            field = v
            (Minecraft.getInstance().screen as? ResultsScreen)?.loadData()
            Overlay.loadSync()
        }

    var maxBlockCount: Int = 2000
    var maxEntityCount: Int = 2000
}