package observable.client

import ChunkMap
import com.mojang.blaze3d.vertex.PoseStack
import glm_.vec2.Vec2
import imgui.*
import imgui.classes.Context
import imgui.impl.gl.ImplBestGL
import imgui.impl.glfw.ImplGlfw
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import observable.Observable
import observable.net.C2SPacket
import uno.glfw.GlfwWindow
import java.io.File
import java.lang.Exception
import java.lang.Float.max
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.min
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

    var filterBuf = ByteArray(256)

    sealed class ResultsEntry(val type: String, val rate: Double, val ticks: Int, val traces: ProfilingData.SerializedTraceMap) {
        class EntityEntry(val id: Int, type: String, rate: Double,
                          ticks: Int, traces: ProfilingData.SerializedTraceMap) : ResultsEntry(type, rate, ticks, traces)
        class BlockEntry(val pos: BlockPos, type: String, rate: Double,
                         ticks: Int, traces: ProfilingData.SerializedTraceMap) : ResultsEntry(type, rate, ticks, traces)

        fun requestTP(level: ResourceLocation) {
            val req = C2SPacket.RequestTeleport(level,
                (this as? EntityEntry)?.id, (this as? BlockEntry)?.pos)
            Observable.CHANNEL.sendToServer(req)
        }
    }

    data class TypeTimingsEntry(val type: String, val rate: Double, val ticks: Int)

    var ticks = 0
    var individualListingOffset = 0
    val NUM_ITEMS = 100
    var entryMap = HashMap<ResourceLocation, List<ResultsEntry>>()
    var filterMap: Map<ResourceLocation, List<ResultsEntry>> = mapOf()
    var dimTimingsMap = HashMap<ResourceLocation, Double>()
    lateinit var typeTimingsMap: List<TypeTimingsEntry>
    lateinit var chunkMap: ChunkMap

    var fontScale: Float = Minecraft.getInstance().window.let {
        if (it.isFullscreen) it.width.toFloat() / 1920.0f
        else 1.0f
    }
        set(v) { if (v > 0.01f) field = max(v, 0.5f) }

    var exception: Exception? = null
    var exceptionOpen: Boolean
        get() = exception != null
        set(v) { if (!v) exception = null }

    fun loadData() {
        entryMap.clear()
        dimTimingsMap.clear()
        val norm = ClientSettings.normalized
        Observable.RESULTS?.let { data ->
            ticks = data.ticks

            (data.entities.keys + data.blocks.keys).forEach {
                val list: MutableList<ResultsEntry> = mutableListOf()
                data.entities[it]?.map {
                    ResultsEntry.EntityEntry(it.obj, it.type,
                        it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0),
                        if (norm) ticks else it.ticks, it.traces)
                }?.let { list += it }
                data.blocks[it]?.map {
                    ResultsEntry.BlockEntry(it.obj, it.type,
                        it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0),
                        if (norm) ticks else it.ticks, it.traces)
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

            applyMapFilter()
        }
        individualListingOffset = 0
    }

    fun applyMapFilter() {
        filterMap = entryMap.map { (key, list) ->
            key to list.filter {
                filterBuf.cStr.lowercase() in it.type.lowercase()
                        && it.rate >= ClientSettings.minRate
            }
        }.toMap()
        individualListingOffset = 0
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
        if (ImGui.io.wantCaptureKeyboard && keyCode != 256
            && ImGui.io.keysDown.indices.contains(keyCode)) {
            ImGui.io.keysDown[keyCode] = true
            keyBuf.add(keyCode)
        }

        return super.keyPressed(keyCode, scanCode, mods)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, mods: Int): Boolean {
        if (ImGui.io.keysDown.indices.contains(keyCode)) {
            ImGui.io.keysDown[keyCode] = false
            keyBuf.remove(keyCode)
        }

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

        try {
            doRender(i, j, f)
        } catch (e: Exception) {
            e.printStackTrace()
            val mc = Minecraft.getInstance()
            mc.player?.chat(TranslatableComponent("text.observable.error", e.message).string)
            mc.player?.chat(TranslatableComponent("text.observable.report").string)
            mc.screen = null
        }
    }

    fun renderTrace(traceMap: ProfilingData.SerializedTraceMap, max: Int) {
        for (child in traceMap.children) {
            val open = ImGui.treeNode(child.methodName)
            ImGui.nextColumn()
            ImGui.text("%.2f%%", child.count.toFloat() / max * 100F)
            ImGui.nextColumn()
            if (open) {
                renderTrace(child, max)
                ImGui.treePop()
            }
        }
    }

    fun exportToFile() {
        val dir = File("observable_profiles")
        val sdf = SimpleDateFormat("yyyy-MM-dd--HH.mm.ss")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${sdf.format(Date())}.json")
        file.printWriter().use {
            it.println(Json {
                prettyPrint = true
            }.encodeToString(Observable.RESULTS))
        }

        val link = TextComponent(file.name).withStyle(ChatFormatting.UNDERLINE).withStyle {
            it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, dir.absolutePath))
        }

        GameInstance.getClient().gui.chat.addMessage(TranslatableComponent("text.observable.profile_saved", link))
    }

    inline fun doRender(i: Int, j: Int, f: Float) {
        implGL.newFrame()
        implGlfw.newFrame()
        ImGui.newFrame()

        val size = implGlfw.window.size
        val startingPos = Vec2(100, 100)
        val indivSize = Vec2((size.x - 300) / 2, (size.y - 300) / 2)

        with(dsl) {
            try {
                ImGui.setNextWindowPos(startingPos, Cond.Once)
                ImGui.setNextWindowSize(indivSize, Cond.Once)
                window("Individual Results ($ticks ticks processed)", null) {
                    ImGui.setWindowFontScale(fontScale)
                    ImGui.columns(2, "searchCol")
                    if (ImGui.inputText("Filter", filterBuf)) { applyMapFilter() }
                    ImGui.nextColumn()
                    button("Export to file") { exportToFile() }
                    ImGui.nextColumn()
                    ImGui.columns(1)
                    filterMap.forEach { (dim, vals) ->
                        collapsingHeader(
                            "$dim -- ${(dimTimingsMap[dim]!! / 1000).roundToInt()} us/t" +
                                    " (${vals.size} items)"
                        ) {
                            if (individualListingOffset != 0) withId(dim) {
                                button("Move up") {
                                    individualListingOffset = (individualListingOffset - NUM_ITEMS)
                                        .coerceAtLeast(0)
                                }
                            }
                            ImGui.columns(3, "resCol", false)
                            ImGui.setColumnWidth(0, ImGui.windowWidth * .5F)

                            vals.subList(individualListingOffset,
                                min(vals.size, individualListingOffset + NUM_ITEMS)
                            ).forEach {
                                val open = ImGui.treeNode(it, it.type)
                                ImGui.nextColumn()
                                ImGui.text("${(it.rate / 1000).roundToInt()} us/t (${it.ticks} ticks)")
                                ImGui.nextColumn()
                                withId(it) {
                                    button("Visit") {
                                        it.requestTP(dim)
                                    }
                                }
                                ImGui.nextColumn()
                                if (open) {
                                    if (it.traces.children.isEmpty()) {
                                        ImGui.columns(1)
                                        ImGui.text("No traces caught")
                                    } else {
                                        ImGui.columns(2)
                                        renderTrace(it.traces, it.traces.count)
                                    }
                                    ImGui.treePop()
                                    ImGui.columns(3, "resCol", false)
                                }
                            }
                            ImGui.columns(1)
                            if (individualListingOffset != vals.size - NUM_ITEMS
                                && vals.size > NUM_ITEMS) withId(vals) {
                                button("Move down") {
                                    individualListingOffset = (individualListingOffset + NUM_ITEMS)
                                        .coerceAtMost(vals.size - NUM_ITEMS)
                                }
                            }
                        }
                    }
                }

                ImGui.setNextWindowPos(Vec2(startingPos.x, startingPos.y + indivSize.y + 100))
                ImGui.setNextWindowSize(indivSize, Cond.Once)
                window("Chunks", null) {
                    ImGui.setWindowFontScale(fontScale)
                    chunkMap.forEach { (dim, chunks) ->
                        collapsingHeader(
                            "$dim -- ${(dimTimingsMap[dim]!! / 1000).roundToInt()} us/t" +
                                    " (${chunks.size} items)"
                        ) {
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
                                        Observable.CHANNEL.sendToServer(
                                            C2SPacket.RequestTeleport(
                                                dim,
                                                null, BlockPos(pos.x * 16, 100, pos.z * 16)
                                            )
                                        )
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
                    ImGui.setWindowFontScale(fontScale)
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
                    ImGui.setWindowFontScale(fontScale)
                    with(ClientSettings) {
                        try {
                            ImGui.inputInt("Minimum rate (ns/t)", ::minRate)
                            ImGui.inputInt("Maximum block text distance (m)", ::maxBlockDist)
                            ImGui.inputInt("Maximum entity text distance (m)", ::maxEntityDist)
                            ImGui.dragFloat("Font scale", ::fontScale, vSpeed = 0.05f, vMin = 0.5f,
                                flags = SliderFlag.NoInput.i)
                            ImGui.checkbox("Normalize results", ::normalized)
                        } catch (e: Exception) {
                            ImGui.text("Error updating settings:\n\t${e.javaClass.name}\n\t\t${e.message}")
                        }
                    }
                }

                // TODO: fix this
//                window("Error", ::exceptionOpen) {
//                    ImGui.text("Error rendering gui")
//                    ImGui.text(exception?.stackTraceToString() ?: "")
//                }
            } catch (e: Exception) {
                exception = e
                e.printStackTrace()
            }
        }

        ImGui.render()

        implGL.renderDrawData(ImGui.drawData!!)
    }
}