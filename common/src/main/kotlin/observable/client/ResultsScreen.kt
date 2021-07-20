package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.classes.Context
import imgui.impl.gl.ImplGL3
import imgui.impl.glfw.ImplGlfw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import uno.glfw.GlfwWindow

class ResultsScreen : Screen(TranslatableComponent("screens.observable.results")) {
    companion object {
        val implGlfw: ImplGlfw
        val implGL3: ImplGL3

        init {
            imgui.MINECRAFT_BEHAVIORS = true
            val window = GlfwWindow.from(Minecraft.getInstance().window.window)
            window.makeContextCurrent()
            Context()
            implGlfw = ImplGlfw(window, false, null)
            implGL3 = ImplGL3()
        }
    }

    var open = true

    override fun init() {
        Observable.LOGGER.info("Init results")
        super.init()
    }

    override fun render(poseStack: PoseStack?, i: Int, j: Int, f: Float) {
        super.render(poseStack, i, j, f)

        implGL3.newFrame()
        implGlfw.newFrame()

        ImGui.newFrame()

        ImGui.showDemoWindow(::open)

        ImGui.render()

        implGL3.renderDrawData(ImGui.drawData!!)
    }
}