package observable.client

import dev.architectury.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
enum class RenderMode {
    CUBES,
    WIREFRAME,
}

@Serializable
data class ConfigData(
    var isOverlayEnabled: Boolean = true,
    var renderMode: RenderMode = RenderMode.CUBES,
    var profileDuration: Int = 30,
    var silenceJoinMessage: Boolean = true,
)

object ClientConfig {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    private val configFile by lazy {
        Platform.getConfigFolder().resolve("observable.json")
    }

    var data = ConfigData()
        private set

    fun load() {
        if (configFile.exists()) {
            try {
                val content = configFile.readText()
                data = json.decodeFromString<ConfigData>(content)
            } catch (e: Exception) {
                observable.Observable.LOGGER.error("Failed to load config: ${e.message}")
            }
        } else {
            save()
        }
    }

    fun save() {
        try {
            configFile.parent.createDirectories()
            val content = json.encodeToString(data)
            configFile.writeText(content)
        } catch (e: Exception) {
            observable.Observable.LOGGER.error("Failed to save config: ${e.message}")
        }
    }

    fun cycleRenderMode() {
        data.renderMode =
            when (data.renderMode) {
                RenderMode.CUBES -> RenderMode.WIREFRAME
                RenderMode.WIREFRAME -> RenderMode.CUBES
            }
        save()
    }
}
