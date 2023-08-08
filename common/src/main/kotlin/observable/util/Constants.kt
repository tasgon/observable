package observable.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component

const val MOD_URL = "https://o.tas.sh/"
val MOD_URL_COMPONENT: Component = Component.literal(MOD_URL)
    .withStyle(ChatFormatting.UNDERLINE)
    .withStyle {
        it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, MOD_URL))
    }
