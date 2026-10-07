package observable.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

const val MOD_URL = "https://observable.tas.sh/"

val MOD_URL_COMPONENT: Component = Component.literal(MOD_URL)
    .withStyle(ChatFormatting.UNDERLINE)
    .withStyle {
        it.withClickEvent(clickOpenUrl(MOD_URL))
    }

fun clickOpenUrl(url: String) = net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(url))
fun clickOpenFile(path: String) = net.minecraft.network.chat.ClickEvent.OpenFile(path)
fun clickRunCommand(cmd: String) = net.minecraft.network.chat.ClickEvent.RunCommand(cmd)

object Constants {
    fun load() {}
}
