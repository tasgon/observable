package observable.server

import com.mojang.brigadier.arguments.BoolArgumentType.bool
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import kotlinx.serialization.Serializable

val ServerSettings = ServerSettingsData()

val TypeMap = mapOf(Integer.TYPE to { integer() }, java.lang.Boolean.TYPE to { bool() })

@Serializable
data class ServerSettingsData(
    var traceInterval: Int = 3,
    var deviation: Int = 1,
    var notifyInterval: Int = 120 * 60 * 1000
)
