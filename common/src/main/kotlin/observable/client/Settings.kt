package observable.client

object Settings {
    var minRate: Int = 0
        set(v) {
            field = v
            synchronized(Overlay) {
                Overlay.load()
            }
        }
}