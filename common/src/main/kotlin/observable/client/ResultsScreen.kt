package observable.client

import ChunkMap
import com.mojang.blaze3d.vertex.PoseStack
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.cStr
import imgui.classes.Context
import imgui.dsl
import imgui.impl.gl.ImplBestGL
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
        lateinit var implGL: ImplBestGL

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
            implGL = ImplBestGL()

            inited = true
        }
    }

    val filterBuf = ByteArray(256)
    val filterText get() = filterBuf.cStr

    sealed class ResultsEntry(val type: String, val rate: Double, val ticks: Int) {
        class EntityEntry(val id: Int, type: String, rate: Double, ticks: Int) : ResultsEntry(type, rate, ticks)
        class BlockEntry(val pos: BlockPos, type: String, rate: Double, ticks: Int) : ResultsEntry(type, rate, ticks)

        fun requestTP(level: ResourceLocation) {
            val req = C2SPacket.RequestTeleport(level,
                (this as? EntityEntry)?.id, (this as? BlockEntry)?.pos)
            Observable.CHANNEL.sendToServer(req)
        }
    }

    data class TypeTimingsEntry(val type: String, val rate: Double, val ticks: Int)

    var ticks = 0
    var entryMap = HashMap<ResourceLocation, List<ResultsEntry>>()
    var dimTimingsMap = HashMap<ResourceLocation, Double>()
    lateinit var typeTimingsMap: List<TypeTimingsEntry>
    lateinit var chunkMap: ChunkMap

    fun loadData() {
        entryMap.clear()
        dimTimingsMap.clear()
        val norm = Settings.normalized
        Observable.RESULTS?.let { data ->
            ticks = data.ticks

            (data.entities.keys + data.blocks.keys).forEach {
                val list: MutableList<ResultsEntry> = mutableListOf()
                data.entities[it]?.map {
                    ResultsEntry.EntityEntry(it.obj, it.type,
                        it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0),
                        if (norm) ticks else it.ticks)
                }?.let { list += it }
                data.blocks[it]?.map {
                    ResultsEntry.BlockEntry(it.obj, it.type,
                        it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0),
                        if (norm) ticks else it.ticks)
                }?.let { list += it }

                entryMap[it] = list.sortedByDescending {
                    it.rate
                }
                dimTimingsMap[it] = list.sumOf { it.rate }
            }

            typeTimingsMap = entryMap.values.flatten().groupBy { it.type }.mapValues { (_, value) ->
                Pair(value.sumOf { it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0) },
                    if (norm) value.size * ticks else value.sumOf { it.ticks })
            }.toList().map {
                val (rate, t) = it.second
                TypeTimingsEntry(it.first, rate, t)
            }.sortedByDescending {
                it.rate
            }

            chunkMap = data.chunks
        }
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

        implGL.newFrame()
        implGlfw.newFrame()
        ImGui.newFrame()

        val size = implGlfw.window.size
        val startingPos = Vec2(100, 100)
        val indivSize = Vec2((size.x - 300) / 2, (size.y - 300) / 2)


        with(dsl) {
            ImGui.setNextWindowPos(startingPos, Cond.Once)
            ImGui.setNextWindowSize(indivSize, Cond.Once)
            window("Individual Results ($ticks ticks processed)", null) {
                ImGui.inputText("Filter", filterBuf)
                entryMap.forEach { (dim, vals) ->
                    val filtered = vals.filter { filterBuf.cStr.lowercase() in it.type.lowercase()
                            && it.rate >= Settings.minRate
                    }
                    collapsingHeader("$dim -- ${(dimTimingsMap[dim]!! / 1000).roundToInt()} us/t" +
                            " (${filtered.size} items)") {
                        ImGui.columns(3, "resCol", false)
                        ImGui.setColumnWidth(0, ImGui.windowWidth * .5F)

                        filtered.forEach {
                            ImGui.text(it.type)
                            ImGui.nextColumn()
                            ImGui.text("${(it.rate / 1000).roundToInt()} us/t (${it.ticks} ticks)")
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

            ImGui.setNextWindowPos(Vec2(startingPos.x, startingPos.y + indivSize.y + 100))
            ImGui.setNextWindowSize(indivSize, Cond.Once)
            window("Chunks", null) {
                chunkMap.forEach { (dim, chunks) ->
                    collapsingHeader("$dim -- ${(dimTimingsMap[dim]!! / 1000).roundToInt()} us/t" +
                            " (${chunks.size} items)") {
                        ImGui.columns(3, "chunkCol", false)
                        ImGui.setColumnWidth(0, ImGui.windowWidth * .5F)

                        chunks.forEach {
                            val (pos, rate) = it
                            ImGui.text("${pos.x}, ${pos.z}")
                            ImGui.nextColumn()
                            ImGui.text("${(rate / 1000).roundToInt()} us/t")
                            ImGui.nextColumn()
                            withId(it) {
                                button("Visit") {
                                    Observable.CHANNEL.sendToServer(C2SPacket.RequestTeleport(dim,
                                        null, BlockPos(pos.x * 16, 100, pos.z * 16)))
                                }
                            }
                            ImGui.nextColumn()
                        }

                        ImGui.columns(1)
                    }
                }
            }

            ImGui.setNextWindowPos(Vec2(startingPos.x + indivSize.x + 100, startingPos.y))
            ImGui.setNextWindowSize(indivSize, Cond.Once)
            window("Aggregated Results", null) {
                ImGui.columns(2, "aggResCol", false)
                ImGui.setColumnWidth(0, ImGui.windowWidth * .65F)
                typeTimingsMap.forEach { (type, rate, ticks) ->
                    ImGui.text(type)
                    ImGui.nextColumn()
                    ImGui.text("${(rate / 1000).roundToInt()} us/t ($ticks ticks)")
                    ImGui.nextColumn()
                }
                ImGui.columns(1)
            }

            ImGui.setNextWindowPos(Vec2(startingPos.x + indivSize.x + 100, startingPos.y + indivSize.y + 100))
            ImGui.setNextWindowSize(indivSize, Cond.Once)
            window("Settings", null) {
                with(Settings) {
                    try {
                        ImGui.inputInt("Minimum rate (ns/t)", ::minRate)
                        ImGui.inputInt("Maximum block text distance (m)", ::maxBlockDist)
                        ImGui.inputInt("Maximum entity text distance (m)", ::maxEntityDist)
                        ImGui.checkbox("Normalize results", ::normalized)
                    } catch (e: Exception) {
                        ImGui.text("Error updating settings:\n\t${e.javaClass.name}\n\t\t${e.message}")
                    }
                }
            }
        }

        ImGui.render()

        implGL.renderDrawData(ImGui.drawData!!)
    }
}