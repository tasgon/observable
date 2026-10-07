package observable.client

import com.mojang.blaze3d.vertex.*
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ChunkPos.containing
import net.minecraft.world.phys.Vec3
import observable.Observable
import observable.client.ObservableClient
import observable.server.ProfilingData
import org.joml.Matrix4fc
import kotlin.math.roundToInt

/**
 * High-level Client Controller for Profile Overlay and HUD.
 */
object Overlay : ClientBridge, WorldRenderer {
    override val settingsKey = ObservableClient.KEY_OPEN_SETTINGS
    override val overlayKey = ObservableClient.KEY_TOGGLE_OVERLAY

    data class RateEntry(
        val pos: BlockPos,
        val rate: Double,
    )

    object RenderColor {
        fun fromNanos(rateNanos: Double): PackedColor {
            val rateMicros = rateNanos / 1000.0
            val r = (rateMicros / 100.0 * 255).toInt().coerceIn(0, 255)
            val g = ((100.0 - rateMicros) / 100.0 * 255).toInt().coerceIn(0, 255)
            val a = (rateMicros / 100.0 * 255).toInt().coerceIn(20, 100)
            return PackedColor((a shl 24) or (r shl 16) or (g shl 8))
        }
    }

    @JvmInline
    value class PackedColor(
        val argb: Int,
    ) {
        val r get() = (argb shr 16) and 0xFF
        val g get() = (argb shr 8) and 0xFF
        val b get() = argb and 0xFF
        val a get() = (argb shr 24) and 0xFF
        val hex get() = argb or (0xFF shl 24)
    }

    sealed class Entry {
        data class EntityEntry(
            val entityId: Int,
            val rate: Double,
        ) : Entry() {
            val color = RenderColor.fromNanos(rate)
            val entity get() = Minecraft.getInstance().level?.getEntity(entityId)
        }
    }

    var entities: List<Entry.EntityEntry> = ArrayList()
    private var blockMap = emptyMap<ChunkPos, List<RateEntry>>()

    fun init() {}

    val font: Font by lazy { Minecraft.getInstance().font }

    override fun clear() {
        entities = emptyList()
        blockMap = emptyMap()
        Observable.RESULTS = null
    }

    private fun normalize(
        dataEntry: ProfilingData.Entry,
        totalTicks: Int,
        norm: Boolean,
    ): Double = dataEntry.rate * (if (norm) dataEntry.ticks.toDouble() / totalTicks else 1.0)

    fun load(lvl: ClientLevel? = null) {
        val data = Observable.RESULTS ?: return
        val level = lvl ?: Minecraft.getInstance().level ?: return
        val levelLocation = level.dimension().identifier()
        val totalTicks = data.ticks
        val norm = ClientSettings.normalized
        val minRate = ClientSettings.minRate

        entities =
            data.entities[levelLocation]
                ?.map { Entry.EntityEntry(it.entityId!!, normalize(it, totalTicks, norm)) }
                ?.filter { it.rate >= minRate }
                ?.sortedByDescending { it.rate }
                .orEmpty()

        val blocks =
            data.blocks[levelLocation]
                ?.map { RateEntry(it.position, normalize(it, totalTicks, norm)) }
                ?.filter { it.rate >= minRate }
                .orEmpty()

        synchronized(this) {
            blockMap = blocks.groupBy { ChunkPos.containing(it.pos) }
        }
    }

    fun loadSync(lvl: ClientLevel? = null) = synchronized(this) { this.load(lvl) }

    fun renderHud(
        graphics: GuiGraphicsExtractor,
        delta: DeltaTracker,
    ) {
        if (!ObservableClient.isOverlayEnabled || Observable.RESULTS == null) return
        val mc = Minecraft.getInstance()
        val partialTicks = delta.getGameTimeDeltaPartialTick(true)
        val cameraPos = mc.gameRenderer.mainCamera.position()

        synchronized(this) {
            val distLimit = ClientSettings.maxBlockDist
            val maxDistSq = distLimit * distLimit

            for ((i, entry) in entities.withIndex()) {
                if (i > (ClientSettings.maxEntityCount - 1)) break
                val entity = entry.entity ?: continue
                if (entity.isRemoved) continue
                val pos = entity.getPosition(partialTicks)
                val distSq = cameraPos.distanceToSqr(pos)
                if (distSq > maxDistSq.toDouble()) continue
            }
        }
    }

    override fun render(
        stack: Matrix4fc,
        bufferSource: MultiBufferSource,
        camera: Vec3,
        modelViewMatrix: Matrix4fc,
        delta: net.minecraft.client.DeltaTracker,
        collector: net.minecraft.client.renderer.SubmitNodeCollector?,
        cameraState: net.minecraft.client.renderer.state.level.CameraRenderState,
        flush: Boolean,
    ) {
        if (ProfilerBridge.isSettingsKeyClicked()) {
            ProfilerBridge.openProfileScreen()
        }
        if (ProfilerBridge.isOverlayKeyClicked()) {
            ObservableClient.isOverlayEnabled = !ObservableClient.isOverlayEnabled
        }
        if (ObservableClient.KEY_CYCLE_RENDER_MODE.consumeClick()) {
            ClientConfig.cycleRenderMode()
        }

        if (!ObservableClient.isOverlayEnabled) return
        val player = Minecraft.getInstance().player ?: return
        if (Observable.RESULTS == null) return

        val labels = mutableListOf<ProfilerBridge.LabelEntry>()
        val visibleBoxEntries = mutableListOf<RateEntry>()
        val distLimit = ClientSettings.maxBlockDist
        val maxDistSq = distLimit * distLimit
        val camPos =
            Minecraft
                .getInstance()
                .gameRenderer.mainCamera
                .position()
        val partialTicks = delta.getGameTimeDeltaPartialTick(true)

        synchronized(this) {
            // Collect Entities
            for ((i, entry) in entities.withIndex()) {
                if (i > (ClientSettings.maxEntityCount - 1)) break
                val entity = entry.entity ?: continue
                if (entity.isRemoved) continue
                val pos = entity.getPosition(partialTicks)
                val distSq = camPos.distanceToSqr(pos)
                if (distSq <= maxDistSq.toDouble()) {
                    val labelPos = Vec3(pos.x, pos.y + entity.bbHeight + 0.33, pos.z)
                    labels.add(ProfilerBridge.LabelEntry(labelPos, "${(entry.rate / 1000).roundToInt()} μs/t", entry.color.hex))
                }
            }

            // Collect Blocks
            val cpos = ChunkPos.containing(player.blockPosition())
            val chunkLimit = (distLimit / 16).coerceAtLeast(2)
            for (x in (cpos.x - chunkLimit)..(cpos.x + chunkLimit)) {
                for (z in (cpos.z - chunkLimit)..(cpos.z + chunkLimit)) {
                    blockMap[ChunkPos(x, z)]?.forEach { entry: RateEntry ->
                        if (entry.rate < 1000) return@forEach
                        val dx = entry.pos.x + 0.5 - camPos.x
                        val dy = entry.pos.y + 0.5 - camPos.y
                        val dz = entry.pos.z + 0.5 - camPos.z
                        if (dx * dx + dy * dy + dz * dz < maxDistSq.toDouble()) {
                            visibleBoxEntries.add(entry)
                            labels.add(
                                ProfilerBridge.LabelEntry(Vec3.atCenterOf(entry.pos), "${(entry.rate / 1000).roundToInt()} μs/t", -1),
                            ) // -1 is 0xFFFFFFFF
                        }
                    }
                }
            }
        }

        val bridgeEntries =
            visibleBoxEntries.map {
                val color = RenderColor.fromNanos(it.rate)
                ProfilerBridge.BlockEntry(it.pos, color.hex, color.a)
            }
        ProfilerBridge.drawWorldPass(stack, bufferSource, camPos, bridgeEntries, labels, collector, cameraState, flush)
    }
}
