package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import dev.architectury.utils.GameInstance
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import observable.Observable
import observable.net.C2SPacket
import kotlin.math.roundToInt

class ProfileScreen : Screen(Component.translatable("screen.observable.profile")) {

    init {
        this.init()
    }

    sealed class Action {
        companion object {
            val DEFAULT = NewProfile(30)
            val UNAVAILABLE = ObservableStatus("text.observable.unavailable")
            val NO_PERMISSIONS = ObservableStatus("text.observable.no_permissions")
        }

        data class NewProfile(var duration: Int) : Action()

        data class TPSProfilerRunning(val endTime: Long) : Action()

        object TPSProfilerCompleted : Action()

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
    var startBtn: Button? = null
    var sample = false
    lateinit var overlayBtn: BetterCheckbox

    private fun openLink(dest: String) {
        val mc = Minecraft.getInstance()
        mc.setScreen(
            ConfirmLinkScreen(
                { bl: Boolean ->
                    if (bl) {
                        Util.getPlatform().openUri(dest)
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
                height / 2 - 48,
                100,
                20,
                Component.translatable("text.observable.profile_tps")
            ) {
                val duration = (action as Action.NewProfile).duration
                Observable.CHANNEL.sendToServer(C2SPacket.InitTPSProfile(duration, sample))
            }
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
                GameInstance.getClient().setScreen(ClientSettingsGui())
            }

        val samplerBtn =
            addRenderableWidget(
                BetterCheckbox(
                    startBtn.x,
                    startBtn.y + startBtn.height + 4,
                    settingsBtn.x + settingsBtn.width - startBtn.x,
                    20,
                    Component.translatable("text.observable.sampler"),
                    sample
                ) {
                    sample = it
                }
            )

        val longWidth = settingsBtn.x + settingsBtn.width - samplerBtn.x
        val smallWidth = longWidth / 3 - 2

        overlayBtn =
            addRenderableWidget(
                BetterCheckbox(
                    samplerBtn.x,
                    samplerBtn.y + samplerBtn.height + 4,
                    samplerBtn.width,
                    20,
                    Component.translatable("text.observable.overlay"),
                    Overlay.enabled
                ) {
                    if (it) {
                        synchronized(Overlay) { Overlay.load() }
                    }
                    Overlay.enabled = it
                }
            )

        if (Observable.RESULTS == null) {
            arrayOf(overlayBtn).forEach { it.active = false }
        }

        val learnBtn =
            button(
                startBtn.x,
                overlayBtn.y + overlayBtn.height + 8,
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
        if (action == Action.UNAVAILABLE || action == Action.NO_PERMISSIONS) {
            Observable.CHANNEL.sendToServer(C2SPacket.RequestAvailability)
        }
    }

    override fun isPauseScreen() = false

    override fun render(graphics: GuiGraphics, i: Int, j: Int, f: Float) {
        graphics.drawCenteredString(
            this.font,
            action.statusMsg,
            width / 2,
            startBtn!!.y - this.font.lineHeight - 4,
            0xFFFFFF
        )

        super.render(graphics, i, j, f)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
        (action as? Action.NewProfile)?.apply {
            duration += f.roundToInt() * 5
            duration = this.duration.coerceIn(5, 60)
        }

        return super.mouseScrolled(d, e, f)
    }
}
