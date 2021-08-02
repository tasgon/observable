package observable.client

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import glm_.pow
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import observable.Observable
import kotlin.math.roundToInt

object Overlay {
    data class Color(val r: Int, val g: Int, val b: Int, val a: Int) {
        companion object {
            fun fromNanos(rateNanos: Double): Color {
                val micros = rateNanos / 1000.0
                return Color(micros)
            }
        }
        constructor(rateMicros: Double) : this(
            (rateMicros / 100.0 * 255).roundToInt().coerceIn(0, 255),
            ((100.0 - rateMicros) / 100.0 * 255).roundToInt().coerceIn(0, 255),
            0,
            (rateMicros / 100.0 * 255 + 25).roundToInt().coerceIn(0, 255)
        )
    }

    sealed class Entry() {
        data class EntityEntry(val entityId: Int, val rate: Double) : Entry() {
            val entity get() = Minecraft.getInstance().level?.getEntity(entityId)
        }
        data class BlockEntry(val pos: BlockPos, val rate: Double, val color: Color = Color.fromNanos(rate)) : Entry()
    }

    var enabled = true
    var entities: List<Entry.EntityEntry> = ArrayList()
    var blocks: List<Entry.BlockEntry> = ArrayList()
    lateinit var loc: Vec3

    val font: Font by lazy { Minecraft.getInstance().font }
    @Suppress("INACCESSIBLE_TYPE")
    private val renderType: RenderType get() {
        return RenderType.create("heat", DefaultVertexFormat.POSITION_COLOR, 7, 256,
            RenderType.CompositeState.builder()
                .setTextureState(RenderStateShard.TextureStateShard())
                .setDepthTestState(RenderStateShard.DepthTestStateShard("always", 519))
                .setTransparencyState(
                    RenderStateShard.TransparencyStateShard("translucent_transparency",
                        {
                            RenderSystem.enableBlend()
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
                        }
                    ) {
                        RenderSystem.disableBlend()
                        RenderSystem.defaultBlendFunc()
                    }).createCompositeState(true))
    }

    fun load(lvl: ClientLevel? = null) {
        val data = Observable.RESULTS ?: return
        val level = lvl ?: Minecraft.getInstance().level ?: return
        val levelLocation = level.dimension().location()
        val ticks = data.ticks
        val norm = Settings.normalized
        entities = data.entities[levelLocation]?.map {
            Entry.EntityEntry(it.obj, it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0))
        }?.filter { it.rate >= Settings.minRate }.orEmpty()

        blocks = data.blocks[levelLocation]?.map {
            Entry.BlockEntry(it.obj, it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0))
        }?.filter { it.rate >= Settings.minRate }.orEmpty()
    }

    inline fun loadSync(lvl: ClientLevel? = null) = synchronized(this) {
        this.load(lvl)
    }

    fun render(poseStack: PoseStack, partialTicks: Float) {
        if (!enabled) return

        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val bufSrc = Minecraft.getInstance().renderBuffers().bufferSource()

        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        poseStack.pushPose()

        camera.position.apply {
            poseStack.translate(-x, -y, -z)
        }

        synchronized(this) {
            for (entry in blocks) {
                if (camera.blockPosition.distSqr(entry.pos) > Settings.maxDist.pow(2)) continue
                drawBlockOutline(entry, poseStack, camera, bufSrc)
                drawBlock(entry, poseStack, camera, bufSrc)
            }

            for (entry in entities) {
                drawEntity(entry, poseStack, partialTicks, camera, bufSrc)
            }
        }

        poseStack.popPose()
        bufSrc.endBatch()

        // Cleanup
        RenderSystem.enableDepthTest()
    }

    inline fun drawEntity(entry: Entry.EntityEntry, poseStack: PoseStack,
                          partialTicks: Float, camera: Camera, bufSrc: MultiBufferSource) {
        val rate = entry.rate
        val entity = entry.entity ?: return
        if (entity.removed || (entity == Minecraft.getInstance().player
            && entity.deltaMovement.lengthSqr() > .01)) return

        poseStack.pushPose()
        var text = "${(rate / 1000).roundToInt()} μs/t"
        var pos = entity.position()
        if (camera.position.distanceTo(pos) > Settings.maxDist) return
        if (entity.isAlive) pos = pos.add(with(entity.deltaMovement) {
            Vec3(x, y.coerceAtLeast(0.0), z)
        }.scale(partialTicks.toDouble()))
        else text += " [X]"

        val c = Color.fromNanos(rate)
        val r = if (c.r > c.g) 0xFFu else (255 * c.r / c.g).toUInt()
        val g = if (c.g > c.r) 0xFFu else (255 * c.g / c.r).toUInt()
        val col: UInt = (r shl 16) or (g shl 8) or (0xFFu shl 24)
        pos.apply {
            poseStack.translate(x, y + entity.bbHeight + 0.33, z)
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-0.025F, -0.025F, 0.025F)
            font.drawInBatch(text, -font.width(text).toFloat() / 2, 0F, col.toInt(), false,
                poseStack.last().pose(), bufSrc, true, 0, 0xF000F0)
        }

        poseStack.popPose()
    }

    private inline fun drawBlockOutline(entry: Entry.BlockEntry, poseStack: PoseStack,
                                        camera: Camera, bufSrc: MultiBufferSource) {
        val (pos, _, color) = entry
        val buf = bufSrc.getBuffer(renderType)

        poseStack.pushPose()

        pos.apply {
            poseStack.translate(x.toDouble(), y.toDouble(), z.toDouble())
        }
        val mat = poseStack.last().pose()
        color.apply {
            buf.vertex(mat, 0F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 0F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 0F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 0F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 1F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 1F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 0F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 1F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 1F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 1F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 1F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 0F).color(r, g, b, a).endVertex()
        }

        poseStack.popPose()
    }

    private inline fun drawBlock(entry: Entry.BlockEntry, poseStack: PoseStack,
                                 camera: Camera, bufSrc: MultiBufferSource) {
        poseStack.pushPose()

        val (pos, rate) = entry
        val text = "${(rate / 1000).roundToInt()} μs/t"

        val col: Int = -0x1
        pos.apply {
            poseStack.translate(x + 0.5, y + 0.5, z + 0.5)
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-0.025F, -0.025F, 0.025F)
            font.drawInBatch(text, -font.width(text).toFloat() / 2, 0F, col, false,
                poseStack.last().pose(), bufSrc, true, 0, 0xF000F0)
        }

        poseStack.popPose()
    }
}