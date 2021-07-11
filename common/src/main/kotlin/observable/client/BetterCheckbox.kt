package observable.client

import net.minecraft.client.gui.components.Checkbox
import net.minecraft.network.chat.Component

class BetterCheckbox(x: Int, y: Int, width: Int, height: Int, component: Component, default: Boolean,
                     var callback: ((Boolean) -> Unit))
    : Checkbox(x, y, width, height, component, default) {
    override fun onPress() {
        super.onPress()
        callback(this.selected())
    }
}