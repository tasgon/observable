package observable.server

import com.mojang.brigadier.arguments.BoolArgumentType.bool
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import dev.architectury.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

val configFile: Path by lazy { Platform.getConfigFolder().resolve("observable.json") }
val ServerSettings by lazy { loadSettings() }

val TypeMap = mapOf(Integer.TYPE to { integer() }, java.lang.Boolean.TYPE to { bool() })

@Serializable
data class ServerSettingsData(
    var traceInterval: Int = 3,
    var deviation: Int = 1,
    var notifyInterval: Int = 120 * 60 * 1000,
    var allPlayersAllowed: Boolean = false,
    var allowedPlayers: MutableSet<String> = mutableSetOf(),
) {
    fun sync() = configFile.writeText(Json.encodeToString(this))
}

fun loadSettings(): ServerSettingsData {
    if (!configFile.exists()) {
        val settings = ServerSettingsData()
        configFile.writeText(Json.encodeToString(settings))
        return settings
    }
    return Json.decodeFromString(configFile.readText())
}
