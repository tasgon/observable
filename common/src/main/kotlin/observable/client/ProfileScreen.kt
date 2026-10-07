package observable.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import observable.Observable
import observable.net.*
import observable.net.C2SPacket
import kotlin.math.roundToInt

class ProfileScreen : Screen(Component.translatable("screen.observable.profile")) {
    sealed class Action {
        companion object {
            val DEFAULT = NewProfile(30)
            val UNAVAILABLE = ObservableStatus("text.observable.unavailable")
            val NO_PERMISSIONS = ObservableStatus("text.observable.no_permissions")
        }

        data class NewProfile(var duration: Int) : Action()

        data class TPSProfilerRunning(val endTime: Long) : Action()

        data object TPSProfilerCompleted : Action()

        data class ObservableStatus(val text: String) : Action()

        data class Custom(val text: String) : Action()

        val statusMsg
            get() =
                when (this) {
                    is NewProfile -> "Duration (scroll): $duration seconds"
                    is TPSProfilerRunning ->
                        "Running for another %.1f seconds"
                            .format(
                                ((endTime - System.currentTimeMillis()).toDouble() / 1e3).coerceAtLeast(
                                    0.0
                                )
                            )
                    is TPSProfilerCompleted -> "Profiling finished, please wait..."
                    is ObservableStatus -> Component.translatable(text).string
                    is Custom -> text
                }
    }

    var action: Action = Action.UNAVAILABLE
        set(value) {
            field = value
            startBtn?.active = (value is Action.NewProfile)
        }
    var startBtn: Button? = null
    var modeBtn: Button? = null
    var clearBtn: Button? = null
    var settingsBtn: Button? = null
    var sample = false

    private fun openLink(dest: String) {
        val mc = Minecraft.getInstance()
        mc.setScreen(
            ConfirmLinkScreen(
                { bl: Boolean ->
                    if (bl) {
                        Util.getPlatform().openUri(java.net.URI(dest))
                    }
                    mc.setScreen(this)
                },
                dest,
                true
            )
        )
    }

    private fun button(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        component: Component,
        onPress: () -> Unit
    ): Button {
        val btn = Button.builder(component) { onPress() }.pos(x, y).size(width, height).build()
        return addRenderableWidget(btn)
    }

    override fun init() {
        super.init()

        val startBtn =
            button(
                0,
                height / 2 - 80,
                100,
                20,
                Component.translatable("text.observable.profile_tps")
            ) {
                val duration = (action as Action.NewProfile).duration
                Observable.CHANNEL.sendToServer(C2SPacket.InitTPSProfile(duration, sample))
            }
        this.startBtn = startBtn
        startBtn.active = action is Action.NewProfile
        startBtn.x = width / 2 - startBtn.width - 4

        val settingsBtn =
            button(
                width / 2 + 4,
                startBtn.y,
                startBtn.width,
                startBtn.height,
                Component.translatable("screen.observable.client_settings")
            ) {
                Minecraft.getInstance().setScreen(ClientSettingsGui())
            }
        this.settingsBtn = settingsBtn

        val samplerBtn =
            addRenderableWidget(
                BetterCheckbox(
                    startBtn.x,
                    startBtn.y + startBtn.height + 18,
                    settingsBtn.x + settingsBtn.width - startBtn.x,
                    20,
                    Component.translatable("text.observable.sampler"),
                    sample
                ) {
                    sample = it
                }
            )

        // Visual Options Section
        val modeBtn =
            button(
                startBtn.x,
                samplerBtn.y + samplerBtn.height + 22,
                settingsBtn.x + settingsBtn.width - startBtn.x,
                20,
                getModeMessage()
            ) {
                ClientConfig.cycleRenderMode()
                modeBtn?.message = getModeMessage()
            }
        this.modeBtn = modeBtn

        val clearBtn =
            button(
                startBtn.x,
                modeBtn.y + modeBtn.height + 4,
                modeBtn.width,
                20,
                Component.translatable("text.observable.clear")
            ) {
                ProfilerBridge.clear()
                Overlay.loadSync()
            }
        this.clearBtn = clearBtn

        val modeBottomY = clearBtn.y + clearBtn.height + 4
        val totalWidth = settingsBtn.x + settingsBtn.width - startBtn.x
        val smallWidth = (totalWidth - 8) / 3

        val learnBtn =
            button(
                startBtn.x,
                modeBottomY,
                smallWidth,
                20,
                Component.translatable("text.observable.docs")
            ) {
                openLink("https://github.com/tasgon/observable/wiki")
            }
        val helpBtn =
            button(
                learnBtn.x + learnBtn.width + 4,
                learnBtn.y,
                smallWidth,
                20,
                Component.translatable("text.observable.discord")
            ) {
                openLink("https://discord.gg/sfPbb3b5tF")
            }
        val donateBtn =
            button(
                helpBtn.x + helpBtn.width + 4,
                helpBtn.y,
                smallWidth,
                20,
                Component.translatable("text.observable.donate")
            ) {
                openLink("https://github.com/tasgon/observable/wiki/Support-this-project")
            }

        this.startBtn = startBtn
        Observable.CHANNEL.sendToServer(C2SPacket.RequestAvailability)
    }

    private fun getModeMessage(): Component {
        val mode =
            when (ClientConfig.data.renderMode) {
                RenderMode.CUBES -> "text.observable.render_mode.cubes"
                RenderMode.WIREFRAME -> "text.observable.render_mode.wireframe"
            }
        return Component.translatable("text.observable.render_mode").append(": ").append(Component.translatable(mode))
    }

    override fun isPauseScreen() = false

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (ObservableClient.KEY_OPEN_SETTINGS.matches(event)) {
            this.onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, i: Int, j: Int, f: Float) {
        super.extractRenderState(graphics, i, j, f)

        val msg = action.statusMsg
        val textX = width / 2 - this.font.width(msg) / 2
        val textY = startBtn!!.y - this.font.lineHeight - 6

        graphics.text(this.font, msg, textX, textY, 0xFFFFFFFF.toInt(), true)

        // Update button states
        clearBtn?.active = Observable.RESULTS != null
        modeBtn?.message = getModeMessage()

        // Category Headers
        val headerColor = -1 // White
        val samplerBtn =
            this.children().filterIsInstance<net.minecraft.client.gui.components.Checkbox>().firstOrNull {
                it.message == Component.translatable("text.observable.sampler")
            }
        samplerBtn?.let {
            val profilerHeader = Component.translatable("text.observable.category.profiler")
            graphics.text(this.font, profilerHeader, it.x, it.y - 14, headerColor, true)
            graphics.fill(
                it.x,
                it.y - 4,
                it.x + (settingsBtn?.x ?: 0) + (settingsBtn?.width ?: 0) - (startBtn?.x ?: 0),
                it.y - 3,
                0x40FFFFFF
            )
        }

        modeBtn?.let {
            val visualsHeader = Component.translatable("text.observable.category.visuals")
            graphics.text(this.font, visualsHeader, it.x, it.y - 14, headerColor, true)
            graphics.fill(it.x, it.y - 4, it.x + it.width, it.y - 3, 0x40FFFFFF)
        }

        // Help Hint under the bottom buttons
        clearBtn?.let {
            val hintY = it.y + it.height + 4 + 20 + 8
            val hintKeyOverlay = ObservableClient.KEY_TOGGLE_OVERLAY.translatedKeyMessage
            val hintKeySettings = ObservableClient.KEY_OPEN_SETTINGS.translatedKeyMessage
            val hintText =
                if (ObservableClient.KEY_TOGGLE_OVERLAY.isUnbound) {
                    Component.translatable("text.observable.help_unbound", hintKeySettings)
                } else {
                    Component.translatable("text.observable.help_toggle", hintKeyOverlay, hintKeySettings)
                }
            graphics.text(this.font, hintText, width / 2 - this.font.width(hintText) / 2, hintY, -1, true)
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        (action as? Action.NewProfile)?.apply {
            duration += (scrollY.roundToInt() * 5)
            duration = this.duration.coerceIn(5, 60)
            ClientConfig.data.profileDuration = this.duration
            ClientConfig.save()
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}
