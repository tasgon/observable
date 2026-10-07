package observable.server

import dev.architectury.platform.Platform
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.SystemReport
import observable.Observable

fun Profiler.getDiagnostics(): JsonObject {
    val duration = System.currentTimeMillis() - startTime

    val systemReport = SystemReport()
    if (!ServerSettings.includeJvmArgs) {
        systemReport.setDetail("JVM Flags", "<REDACTED>")
    }

    val modLoader =
        if (Platform.isNeoForge()) {
            "neoforge"
        } else if (Platform.isFabric()) {
            "fabric"
        } else {
            "unknown"
        }

    return buildJsonObject {
        put("user", player?.gameProfile?.id?.toString())
        put("start", startTime)
        put("duration", duration)
        put("minecraftVersion", Platform.getMinecraftVersion())
        put("modLoader", modLoader)
        put("observableVersion", Platform.getMod(Observable.MOD_ID).version)
        put(
            "additionalDiagnostics",
            buildJsonObject {
                put("System Report", systemReport.toLineSeparatedString())
                put(
                    "Mods",
                    Platform.getMods().joinToString("\n") { mod ->
                        "'${mod.name}' (version: ${mod.version})"
                    },
                )
            },
        )
    }
}
