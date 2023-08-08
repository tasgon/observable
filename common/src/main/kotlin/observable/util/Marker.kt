package observable.util

import java.io.File

class Marker(path: String) {
    private val file = File(path)

    private val exists get() = file.exists()

    fun mark(block: () -> Unit) {
        if (!exists) {
            block()
            file.createNewFile()
        }
    }
}
