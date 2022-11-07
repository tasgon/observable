package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import dev.architectury.utils.GameInstance
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import java.lang.NumberFormatException
import kotlin.reflect.KMutableProperty0

object ClientSettings {
    var minRate: Int = 0
        set(v) {
            field = v
            Overlay.loadSync()
        }

    var maxBlockDist: Int = 128
        @Synchronized set

    var maxEntityDist: Int = 2048
        @Synchronized set

    var normalized = true
        set(v) {
            field = v
            Overlay.loadSync()
        }

    var maxBlockCount: Int = 2000
    var maxEntityCount: Int = 2000
}

class ClientSettingsGui : Screen(TranslatableComponent("screen.observable.client_settings")) {
    private fun entry(y: Int, prop: KMutableProperty0<Int>) {
        val box = EditBox(
            GameInstance.getClient().font,
            width * 3 / 4,
            y,
            40,
            20,
            TextComponent("")
        )
        box.value = prop.get().toString()
        box.setFilter {
            try {
                Integer.parseInt(it)
                true
            } catch (e: NumberFormatException) {
                false
            }
        }
        box.setResponder {
            prop.set(Integer.parseInt(it))
        }
        addRenderableWidget(box)
    }

    val fields = listOf("maxBlockDist", "maxEntityDist", "maxEntityCount", "normalize").map {
        TranslatableComponent("text.observable.$it")
    }
    override fun init() {
        super.init()

        entry(20, ClientSettings::maxBlockDist)
        entry(40, ClientSettings::maxEntityDist)
        entry(60, ClientSettings::maxEntityCount)
        addRenderableWidget(
            BetterCheckbox(
                width * 3 / 4,
                90,
                width / 2,
                20,
                TextComponent(""),
                true
            ) {
                ClientSettings.normalized = it
            }
        )
    }

    override fun render(poseStack: PoseStack, i: Int, j: Int, f: Float) {
        for ((field, entry) in fields.zip(this.children())) {
            GuiComponent.drawCenteredString(
                poseStack,
                this.font,
                field,
                width / 4,
                (entry as AbstractWidget).y,
                0xFFFFFF
            )
        }

        super.render(poseStack, i, j, f)
    }
}
