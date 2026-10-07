package observable.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component

const val MOD_URL = "https://observable.tas.sh/"
val MOD_URL_COMPONENT: Component =
    Component
        .literal(MOD_URL)
        .withStyle(ChatFormatting.UNDERLINE)
        .withStyle {
            it.withClickEvent(ClickEvent.OpenUrl(java.net.URI(MOD_URL)))
        }
