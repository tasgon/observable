package observable.client

object Settings {
    var minRate: Int = 0
        set(v) {
            field = v
            Overlay.loadSync()
        }

    var maxDist: Int = 2048
        @Synchronized set
}