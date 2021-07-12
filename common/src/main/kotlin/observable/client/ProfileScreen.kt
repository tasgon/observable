package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import observable.server.C2SPacket
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt

class ProfileScreen : Screen(TranslatableComponent("screen,observable.profile")) {
    var duration: Int = 30
    var startBtn: Button? = null

    override fun init() {
        super.init()

        ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)
            .forEach { Observable.LOGGER.info(it.threadName) }

        var startBtn = addButton(Button(
            0, height / 2 - 28, 100, 20, TranslatableComponent("text.observable.profile_tps")
        ) {
            Observable.CHANNEL.rawChannel.sendToServer(C2SPacket.InitTPSProfile(duration))
        })

        startBtn.x = width / 2 - startBtn.width - 4

        var fpsBtn = addButton(Button(width / 2 + 4, startBtn.y, startBtn.width, startBtn.height, TranslatableComponent("text.observable.profile_fps")) { }) as Button
        fpsBtn.active = false
        var resultsBtn = addButton(Button(startBtn.x, startBtn.y + startBtn.height + 16, fpsBtn.x + fpsBtn.width - startBtn.x, 20, TranslatableComponent("text.observable.results")) { })
        var showBtn = addButton(BetterCheckbox(resultsBtn.x, resultsBtn.y + resultsBtn.height + 4, resultsBtn.width,
            20, TranslatableComponent("text.observable.overlay"), false) { })

        this.startBtn = startBtn
    }

    override fun render(poseStack: PoseStack, i: Int, j: Int, f: Float) {
        GuiComponent.drawCenteredString(poseStack, this.font, "Duration (scroll): $duration seconds",
            width / 2, startBtn!!.y - this.font.lineHeight - 4, 0xFFFFFF)

        super.render(poseStack, i, j, f)
    }

    override fun keyPressed(i: Int, j: Int, k: Int): Boolean {
        return super.keyPressed(i, j, k)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
        this.duration += f.roundToInt() * 5
        this.duration = this.duration.coerceIn(5, 60)
        return super.mouseScrolled(d, e, f)
    }
}