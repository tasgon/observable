package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import observable.net.C2SPacket
import kotlin.math.roundToInt

class ProfileScreen : Screen(TranslatableComponent("screen.observable.profile")) {
    sealed class Action {
        companion object {
            val DEFAULT = NewProfile(30)
        }
        data class NewProfile(var duration: Int) : Action()
        data class TPSProfilerRunning(val endTime: Long) : Action()
        object TPSProfilerCompleted : Action()

        val statusMsg get() = when (this) {
            is NewProfile -> "Duration (scroll): $duration seconds"
            is TPSProfilerRunning -> "Running for another %.1f seconds"
                .format(((endTime - System.nanoTime()).toDouble() / 1e9).coerceAtLeast(0.0) )
            is TPSProfilerCompleted -> "Profiling finished, please wait..."
        }
    }

    var action: Action = Action.DEFAULT
    lateinit var startBtn: Button
    lateinit var fpsBtn: Button
    lateinit var resultsBtn: Button
    lateinit var overlayBtn: BetterCheckbox

    val fpsText = TranslatableComponent("text.observable.profile_fps")
    val unimplementedText = TranslatableComponent("text.observable.unimplemented")

    override fun init() {
        super.init()

        startBtn = addButton(Button(
            0, height / 2 - 28, 100, 20, TranslatableComponent("text.observable.profile_tps")
        ) {
            val duration = (action as Action.NewProfile).duration
            Observable.CHANNEL.sendToServer(C2SPacket.InitTPSProfile(duration))
        })
        startBtn.active = action is Action.NewProfile

        startBtn.x = width / 2 - startBtn.width - 4

        fpsBtn = addButton(Button(width / 2 + 4, startBtn.y, startBtn.width, startBtn.height,
                fpsText) { }) as Button
        fpsBtn.active = false
        resultsBtn = addButton(Button(startBtn.x, startBtn.y + startBtn.height + 16,
                fpsBtn.x + fpsBtn.width - startBtn.x, 20, TranslatableComponent("text.observable.results")) {
            Minecraft.getInstance().setScreen(ResultsScreen())
        })
        overlayBtn = addButton(BetterCheckbox(resultsBtn.x, resultsBtn.y + resultsBtn.height + 4, resultsBtn.width,
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
    }

    override fun isPauseScreen() = false

    override fun render(poseStack: PoseStack, i: Int, j: Int, f: Float) {
        GuiComponent.drawCenteredString(poseStack, this.font, action.statusMsg,
            width / 2, startBtn.y - this.font.lineHeight - 4, 0xFFFFFF)

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