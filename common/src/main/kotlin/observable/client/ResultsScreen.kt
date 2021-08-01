package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.cStr
import imgui.classes.Context
import imgui.dsl
import imgui.impl.gl.ImplGL3
import imgui.impl.glfw.ImplGlfw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import observable.Observable
import observable.net.C2SPacket
import uno.glfw.GlfwWindow
import java.lang.Exception
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

    var alwaysOpen
        get() = true
        set(i) {}

    val filterBuf = ByteArray(256)
    val filterText get() = filterBuf.cStr

    sealed class ResultsEntry(val type: String, val rate: Double) {
        class EntityEntry(val id: Int, type: String, rate: Double) : ResultsEntry(type, rate)
        class BlockEntry(val pos: BlockPos, type: String, rate: Double) : ResultsEntry(type, rate)

        fun requestTP(level: ResourceLocation) {
            val req = C2SPacket.RequestTeleport(level,
                (this as? EntityEntry)?.id, (this as? BlockEntry)?.pos)
            Observable.CHANNEL.sendToServer(req)
        }
    }

    var entryMap = HashMap<ResourceLocation, List<ResultsEntry>>()
    var dimTimingsMap = HashMap<ResourceLocation, Double>()
    lateinit var typeTimingsMap: List<Pair<String, Double>>

    fun loadData() {
        entryMap.clear()
        dimTimingsMap.clear()
        Observable.RESULTS?.let { data ->
            (data.entities.keys + data.blocks.keys).forEach {
                val list: MutableList<ResultsEntry> = mutableListOf()
                data.entities[it]?.map { ResultsEntry.EntityEntry(it.obj, it.type, it.rate) }?.let { list += it }
                data.blocks[it]?.map { ResultsEntry.BlockEntry(it.obj, it.type, it.rate) }?.let { list += it }

                entryMap[it] = list.sortedByDescending { it.rate }
                dimTimingsMap[it] = list.sumOf { it.rate }
            }
        }

        typeTimingsMap = entryMap.values.flatten().groupBy { it.type }.mapValues { (_, value) ->
            value.sumOf { it.rate }
        }.toList().sortedByDescending { it.second }
    }

    override fun init() {
        ResultsScreen.initCtx(true)

        loadData()

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

//        ImGui.showDemoWindow(booleanArrayOf(true))

        with(dsl) {
            window("Individual Results", ::alwaysOpen) {
                ImGui.inputText("Filter", filterBuf)
                val width = ImGui.windowWidth
                entryMap.forEach { (dim, vals) ->
                    val filtered = vals.filter { filterBuf.cStr.lowercase() in it.type.lowercase()
                            && it.rate > Settings.minRate
                    }
                    collapsingHeader("${dim.toString()} -- ${(dimTimingsMap[dim]!! / 1000).roundToInt()} us/t" +
                            " (${filtered.size} items)") {
                        ImGui.columns(3, "resCol", false)
                        ImGui.setColumnWidth(0, width * .5F)

                        filtered.forEach {
                            ImGui.text(it.type)
                            ImGui.nextColumn()
                            ImGui.text("${(it.rate / 1000).roundToInt()} us/t")
                            ImGui.nextColumn()
                            withId(it) {
                                button("Visit") {
                                    it.requestTP(dim)
                                }
                            }
                            ImGui.nextColumn()
                        }

                        ImGui.columns(1)
                    }
                }
            }

            window("Aggregated Results", ::alwaysOpen) {
                ImGui.columns(2, "aggResCol", false)
                ImGui.setColumnWidth(0, ImGui.windowWidth * .8F)
                typeTimingsMap.forEach { (type, rate) ->
                    ImGui.text(type)
                    ImGui.nextColumn()
                    ImGui.text("${(rate / 1000).roundToInt()} us/t")
                    ImGui.nextColumn()
                }
                ImGui.columns(1)
            }

            window("Settings", ::alwaysOpen) {
                with(Settings) {
                    try {
                        ImGui.inputInt("Minimum rate (ns/t)", ::minRate)
                        ImGui.inputInt("Maximum distance (m)", ::maxDist)
                    } catch (e: Exception) {
                        ImGui.text("Error updating settings:\n\t${e.javaClass.name}\n\t\t${e.message}")
                    }
                }
            }
        }

        ImGui.render()

        implGL3.renderDrawData(ImGui.drawData!!)
    }
}