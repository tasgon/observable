package observable.client

import net.minecraft.client.Minecraft

object Settings {
    var minRate: Int = 0
        set(v) {
            field = v
            Overlay.loadSync()
        }

    var maxDist: Int = 2048
        @Synchronized set

    var normalized = false
        set(v) {
            field = v
            (Minecraft.getInstance().screen as? ResultsScreen)?.loadData()
            Overlay.loadSync()
        }
}