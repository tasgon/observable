package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.WindowFlag
import imgui.cStr
import imgui.classes.Context
import imgui.dsl
import imgui.impl.gl.ImplGL3
import imgui.impl.glfw.ImplGlfw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import observable.net.C2SPacket
import uno.glfw.GlfwWindow
import kotlin.math.roundToInt

class ResultsScreen : Screen(TranslatableComponent("screens.observable.results")) {
    companion object {
        lateinit var implGlfw: ImplGlfw
        lateinit var implGL3: ImplGL3

        val keyBuf = HashSet<Int>()

        var inited = false
            private set

        fun initCtx(reinit: Boolean = false) {
            if (inited && !reinit) return

            imgui.MINECRAFT_BEHAVIORS = true
            val window = GlfwWindow.from(Minecraft.getInstance().window.window)
            window.makeContextCurrent()
            Context()
            implGlfw = ImplGlfw(window, false, null)
            implGL3 = ImplGL3()

            inited = true
        }
    }

    var resultsOpen
        get() = true
        set(i) {}

    val filterBuf = ByteArray(256)
    val filterText get() = filterBuf.cStr

    override fun init() {
        ResultsScreen.initCtx(true)

        Observable.LOGGER.info("Init results")
        super.init()
    }

    override fun charTyped(c: Char, i: Int): Boolean {
        if (ImGui.io.wantTextInput) ImGui.io.addInputCharacter(c)

        return super.charTyped(c, i)
    }

    override fun mouseScrolled(d: Double, e: Double, amount: Double): Boolean {
        if (ImGui.io.wantCaptureMouse) ImGui.io.mouseWheel = amount.toFloat()

        return super.mouseScrolled(d, e, amount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, mods: Int): Boolean {
        if (ImGui.io.wantCaptureKeyboard && keyCode != 256) {
            ImGui.io.keysDown[keyCode] = true
            keyBuf.add(keyCode)
        }

        return super.keyPressed(keyCode, scanCode, mods)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, mods: Int): Boolean {
        ImGui.io.keysDown[keyCode] = false
        keyBuf.remove(keyCode)

        return super.keyReleased(keyCode, scanCode, mods)
    }

    override fun onClose() {
        keyBuf.forEach { ImGui.io.keysDown[it] = false }
        keyBuf.clear()

        super.onClose()
    }

    override fun isPauseScreen() = false

    override fun render(poseStack: PoseStack?, i: Int, j: Int, f: Float) {
        super.render(poseStack, i, j, f)

        implGL3.newFrame()
        implGlfw.newFrame()

        ImGui.newFrame()

        ImGui.showDemoWindow(booleanArrayOf(true))

        with(dsl) {
            window("Results", ::resultsOpen, WindowFlag.None.i) {
                ImGui.inputText("Filter", filterBuf)
                ImGui.columns(3, "resCol", false)
                ImGui.setColumnWidth(0, ImGui.windowWidth * .7F)
                Observable.RESULTS?.let { data ->
                    data.entries
                        .filter { it.entity.asAny != null }
                        .filter { filterText in it.entity.classname.lowercase() }.forEach {
                            treeNode(it.entity.classname) {

                            }
                            ImGui.nextColumn()

                            ImGui.text("${(it.rate / 1000).roundToInt()} us/t")
                            ImGui.nextColumn()
                            it.entity.entity?.let { entity ->
                                withId(entity) {
                                    button("Visit") {
                                        val pos = with(entity.position()) {
                                            BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())
                                        }
                                        Observable.LOGGER.info("Requesting teleport to $pos")
                                        Observable.CHANNEL.sendToServer(C2SPacket.RequestTeleport(null, pos))
                                    }
                                }
                            }
                            ImGui.nextColumn()
                        }
                }
                ImGui.columns(1)
            }
        }

        ImGui.render()

        implGL3.renderDrawData(ImGui.drawData!!)
    }
}