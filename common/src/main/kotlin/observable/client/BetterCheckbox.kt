package observable.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.network.chat.Component

fun betterCheckbox(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    component: Component,
    default: Boolean,
    callback: ((Boolean) -> Unit),
) = Checkbox
    .builder(component, Minecraft.getInstance().font)
    .pos(x, y)
    .maxWidth(width)
    .selected(default)
    .onValueChange {
        _,
        bl,
        ->
        callback(bl)
    }.build()
