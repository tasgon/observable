package observable.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import observable.server.ProfilingData
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ProfileExporter {
    private val dir = File("observable_profiles")
    private val sdf = SimpleDateFormat("yyyy-MM-dd--HH.mm.ss")

    init {
        if (!dir.exists()) dir.mkdirs()
    }

    private val json = Json {
        prettyPrint = true
    }

    fun export(data: ProfilingData): Component {
        val file = File(dir, "${sdf.format(Date())}.json")
        file.printWriter().use {
            it.println(json.encodeToString(data))
        }

        val link = Component.literal(file.name).withStyle(ChatFormatting.UNDERLINE).withStyle {
            it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, dir.absolutePath))
        }

        return link
    }
}
