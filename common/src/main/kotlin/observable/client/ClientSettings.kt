package observable.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen
import net.minecraft.network.chat.Component
import observable.client.ObservableClient
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

    var renderMode: RenderMode
        get() = ClientConfig.data.renderMode
        set(v) {
            ClientConfig.data.renderMode = v
            ClientConfig.save()
        }

    var silenceJoinMessage: Boolean
        get() = ClientConfig.data.silenceJoinMessage
        set(v) {
            ClientConfig.data.silenceJoinMessage = v
            ClientConfig.save()
        }
}

object KeyBindDeepLink {
    private var ticks = 0

    fun arm() {
        ticks = 40
    }

    fun tick() {
        if (ticks <= 0) return
        ticks--
        val screen =
            Minecraft.getInstance().screen as? KeyBindsScreen ?: run {
                ticks = 0
                return
            }

        // Tier 1: Modded search box
        screen.children().filterIsInstance<EditBox>().firstOrNull()?.let {
            screen.setFocused(it)
            it.setValue("category:Observable")
            ticks = 0
            return
        }

        // Tier 2: Vanilla scroll
        try {
            val target = Component.translatable("key.category.observable.main").string
            val all = mutableListOf<net.minecraft.client.gui.components.events.GuiEventListener>()

            fun walk(h: net.minecraft.client.gui.components.events.ContainerEventHandler) {
                h.children().forEach {
                    all.add(it)
                    (it as? net.minecraft.client.gui.components.events.ContainerEventHandler)?.let(::walk)
                }
            }
            walk(screen)
            val list = all.filterIsInstance<net.minecraft.client.gui.components.AbstractSelectionList<*>>().firstOrNull() ?: return
            val entry =
                list.children().find { e ->
                    (e as? net.minecraft.client.gui.components.events.ContainerEventHandler)
                        ?.children()
                        ?.filterIsInstance<AbstractWidget>()
                        ?.any { it.message.string.equals(target, true) } == true
                } ?: return
            net.minecraft.client.gui.components.AbstractSelectionList::class.java.declaredMethods
                .find { it.name == "centerScrollOn" }
                ?.also { it.isAccessible = true }
                ?.invoke(list, entry)
            ticks = 0
        } catch (_: Throwable) {
        }
    }
}

class ClientSettingsGui : Screen(Component.translatable("screen.observable.client_settings")) {
    val fields =
        listOf("maxBlockDist", "maxEntityDist", "maxEntityCount", "normalize", "silence_join").map {
            Component.translatable("text.observable.$it")
        }

    override fun init() {
        super.init()

        val leftX = width / 2 - 110
        val rightX = width / 2 + 50
        val widgetW = 60
        val startY = height / 2 - 100

        entry(startY, ClientSettings::maxBlockDist, rightX, widgetW)
        entry(startY + 20, ClientSettings::maxEntityDist, rightX, widgetW)
        entry(startY + 40, ClientSettings::maxEntityCount, rightX, widgetW)
        addRenderableWidget(
            BetterCheckbox(rightX, startY + 60, 20, 20, Component.literal(""), ClientSettings.normalized) {
                ClientSettings.normalized = it
            }
        )
        addRenderableWidget(
            BetterCheckbox(rightX, startY + 80, 20, 20, Component.literal(""), ClientSettings.silenceJoinMessage) {
                ClientSettings.silenceJoinMessage = it
            }
        )

        // Footer Buttons - Vertical with 24px gap
        val footerY = startY + 120
        button(width / 2 - 100, footerY, 200, 20, Component.translatable("controls.keybinds")) {
            KeyBindDeepLink.arm()
            Minecraft.getInstance().setScreen(KeyBindsScreen(this, Minecraft.getInstance().options))
        }

        button(width / 2 - 100, footerY + 24, 200, 20, Component.translatable("gui.back")) {
            Minecraft.getInstance().setScreen(ObservableClient.PROFILE_SCREEN)
        }
    }

    private fun button(x: Int, y: Int, width: Int, height: Int, component: Component, onPress: () -> Unit) {
        val btn = net.minecraft.client.gui.components.Button.builder(component) { onPress() }.pos(x, y).size(width, height).build()
        addRenderableWidget(btn)
    }

    private fun entry(y: Int, prop: KMutableProperty0<Int>, x: Int, w: Int) {
        val box = EditBox(Minecraft.getInstance().font, x, y, w, 20, Component.literal(""))
        box.value = prop.get().toString()
        box.setResponder {
            try {
                prop.set(Integer.parseInt(it))
            } catch (e: NumberFormatException) {
            }
        }
        addRenderableWidget(box)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, i: Int, j: Int, f: Float) {
        super.extractRenderState(graphics, i, j, f)

        val font = Minecraft.getInstance().font
        val rightX = width / 2 + 50
        val startY = height / 2 - 100

        // Title
        graphics.text(font, title, width / 2 - font.width(title) / 2, startY - 22, -1, true)
        graphics.fill(width / 2 - 110, startY - 12, width / 2 + 110, startY - 11, 0x40FFFFFF)

        // Settings Labels
        val labelX = rightX - 10

        fun drawLabel(key: String, y: Int) {
            val component = Component.translatable(key)
            graphics.text(font, component, labelX - font.width(component), y + 6, -1, true)
        }

        drawLabel("text.observable.maxBlockDist", startY)
        drawLabel("text.observable.maxEntityDist", startY + 20)
        drawLabel("text.observable.maxEntityCount", startY + 40)
        drawLabel("text.observable.normalize", startY + 60)
        drawLabel("text.observable.silence_join", startY + 80)
    }
}
