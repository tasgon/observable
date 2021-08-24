package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import observable.net.C2SPacket
import kotlin.math.roundToInt

class ProfileScreen : Screen(TranslatableComponent("screen.observable.profile")) {
    sealed class Action {
        companion object {
            val DEFAULT = NewProfile(30)
            val UNAVAILABLE = ObservableStatus("text.observable.unavailable")
            val NO_PERMISSIONS = ObservableStatus("text.observable.no_permissions")
        }
        data class NewProfile(var duration: Int) : Action()
        data class TPSProfilerRunning(val endTime: Long) : Action()
        object TPSProfilerCompleted : Action()
        data class ObservableStatus(var text: String) : Action()

        val statusMsg get() = when (this) {
            is NewProfile -> "Duration (scroll): $duration seconds"
            is TPSProfilerRunning -> "Running for another %.1f seconds"
                .format(((endTime - System.currentTimeMillis()).toDouble() / 1e3).coerceAtLeast(0.0) )
            is TPSProfilerCompleted -> "Profiling finished, please wait..."
            is ObservableStatus -> TranslatableComponent(text).string
        }
    }

    var action: Action = Action.UNAVAILABLE
    var startBtn: Button? = null
    lateinit var fpsBtn: Button
    lateinit var resultsBtn: Button
    lateinit var overlayBtn: BetterCheckbox

    val fpsText = TranslatableComponent("text.observable.profile_fps")
    val unimplementedText = TranslatableComponent("text.observable.unimplemented")

    fun openLink(dest: String) {
        val mc = Minecraft.getInstance()
        mc.setScreen(ConfirmLinkScreen({ bl: Boolean ->
            if (bl) {
                Util.getPlatform().openUri(dest)
            }
            mc.setScreen(this)
        }, dest, true))
    }

    override fun init() {
        super.init()


        val startBtn = addRenderableWidget(Button(
            0, height / 2 - 28, 100, 20, TranslatableComponent("text.observable.profile_tps")
        ) {
            val duration = (action as Action.NewProfile).duration
            Observable.CHANNEL.sendToServer(C2SPacket.InitTPSProfile(duration))
        })
        startBtn.active = action is Action.NewProfile

        startBtn.x = width / 2 - startBtn.width - 4

        fpsBtn = addRenderableWidget(Button(width / 2 + 4, startBtn.y, startBtn.width, startBtn.height,
                fpsText) { }) as Button
        fpsBtn.active = false
        resultsBtn = addRenderableWidget(Button(startBtn.x, startBtn.y + startBtn.height + 16,
                fpsBtn.x + fpsBtn.width - startBtn.x, 20, TranslatableComponent("text.observable.results")) {
            Minecraft.getInstance().setScreen(ResultsScreen())
        })
        overlayBtn = addRenderableWidget(BetterCheckbox(resultsBtn.x, resultsBtn.y + resultsBtn.height + 4, resultsBtn.width,
            20, TranslatableComponent("text.observable.overlay"), Overlay.enabled) {
            if (it) synchronized(Overlay) {
                Overlay.load()
            }
            Overlay.enabled = it
        })

        if (Observable.RESULTS == null) {
            arrayOf(resultsBtn, overlayBtn).forEach {
                it.active = false
            }
        }

        val width = resultsBtn.width / 3 - 2
        val learnBtn = addRenderableWidget(Button(startBtn.x, overlayBtn.y + overlayBtn.height + 8,
            width, 20, TranslatableComponent("text.observable.docs")) {
            openLink("https://github.com/tasgon/observable/wiki")
        })
        val helpBtn = addRenderableWidget(Button(learnBtn.x + learnBtn.width + 4, learnBtn.y,
            width, 20, TranslatableComponent("text.observable.discord")) {
            openLink("https://discord.gg/sfPbb3b5tF")
        })
        val donateBtn = addRenderableWidget(Button(helpBtn.x + helpBtn.width + 4, helpBtn.y,
            width, 20, TranslatableComponent("text.observable.donate")) {
            openLink("https://github.com/tasgon/observable/wiki/Support-this-project")
        })

        this.startBtn = startBtn
        if (action == Action.UNAVAILABLE ||
            action == Action.NO_PERMISSIONS) Observable.CHANNEL.sendToServer(C2SPacket.RequestAvailability)
    }

    override fun isPauseScreen() = false

    override fun render(poseStack: PoseStack, i: Int, j: Int, f: Float) {
        GuiComponent.drawCenteredString(poseStack, this.font, action.statusMsg,
            width / 2, startBtn!!.y - this.font.lineHeight - 4, 0xFFFFFF)


        super.render(poseStack, i, j, f)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
        (action as? Action.NewProfile)?.apply {
            duration += f.roundToInt() * 5
            duration = this.duration.coerceIn(5, 60)
        }

        return super.mouseScrolled(d, e, f)
    }

    override fun mouseMoved(d: Double, e: Double) {
        fpsBtn.message = if (fpsBtn.isHovered) unimplementedText else fpsText

        super.mouseMoved(d, e)
    }
}