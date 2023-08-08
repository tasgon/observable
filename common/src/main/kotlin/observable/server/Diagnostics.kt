package observable.server

import dev.architectury.injectables.targets.ArchitecturyTarget
import dev.architectury.platform.Platform
import kotlinx.serialization.json.*
import net.minecraft.SystemReport
import observable.Observable

fun Profiler.getDiagnostics(): JsonObject {
    val duration = System.currentTimeMillis() - startTime

    return buildJsonObject {
        put("user", player?.gameProfile?.id?.toString())
        put("start", startTime)
        put("duration", duration)
        put("minecraftVersion", Platform.getMinecraftVersion())
        put("modLoader", ArchitecturyTarget.getCurrentTarget())
        put("observableVersion", Platform.getMod(Observable.MOD_ID).version)
        put(
            "additionalDiagnostics",
            buildJsonObject {
                put("System Report", SystemReport().toLineSeparatedString())
                put(
                    "Mods",
                    Platform.getMods().joinToString("\n") { mod ->
                        "'${mod.name}' (version: ${mod.version})"
                    }
                )
            }
        )
    }
}
