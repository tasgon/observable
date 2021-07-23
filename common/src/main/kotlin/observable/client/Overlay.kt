package observable.client

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import observable.Observable
import kotlin.math.roundToInt

object Overlay {
    data class Color(val r: Int, val g: Int, val b: Int, val a: Int)

    sealed class Entry(val color: Color) {
        companion object {
            fun getColor(rate: Double) = Color(
                (rate / 100.0 * 255).roundToInt().coerceIn(0, 255),
                ((100.0 - rate) / 100.0 * 255).roundToInt().coerceIn(0, 255),
                0,
                (rate / 100.0).roundToInt().coerceIn(0, 255)
            )
        }
        data class EntityEntry(val entity: Entity, val rate: Double) : Entry(getColor(rate))
        data class BlockEntry(val pos: BlockPos, val rate: Double) : Entry(getColor(rate / 1000.0))

        operator fun component3() = color
    }

    var enabled = true
    var entities = ArrayList<Entry.EntityEntry>()
    var blocks = ArrayList<Entry.BlockEntry>()
    lateinit var loc: Vec3

    val font: Font by lazy { Minecraft.getInstance().font }
    @Suppress("INACCESSIBLE_TYPE")
    private val renderType: RenderType by lazy {
        RenderType.create("heat", DefaultVertexFormat.POSITION_COLOR, 7, 256,
            RenderType.CompositeState.builder()
                .setTextureState(RenderStateShard.TextureStateShard())
                .setDepthTestState(RenderStateShard.DepthTestStateShard("always", 519))
                .setTransparencyState(
                    RenderStateShard.TransparencyStateShard("translucent_transparency",
                        {
                            RenderSystem.enableBlend()
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE)
                        }
                    ) {
                        RenderSystem.disableBlend()
                        RenderSystem.defaultBlendFunc()
                    }).createCompositeState(true))
    }

    fun load() {
        val data = Observable.RESULTS ?: return
        val level = Minecraft.getInstance().level ?: return
        val levelLocation = level.dimension().location()
        listOf(entities, blocks).forEach { it.clear() }
        val positions = HashMap<BlockPos, Entry.BlockEntry>()
        data.entities[levelLocation]?.forEach { entry ->
            level.getEntity(entry.obj)?.let {
                entities.add(Entry.EntityEntry(it, entry.rate))
            }
        }
        data.blocks[levelLocation]?.forEach { entry ->
            blocks.add(Entry.BlockEntry(entry.obj, entry.rate))
        }
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
        val (entity, rate, color) = entry
        if (entity.removed || (entity == Minecraft.getInstance().player
            && entity.deltaMovement.lengthSqr() > .05)) return

        poseStack.pushPose()
        var text = "${(rate / 1000).roundToInt()} μs/t"
        var pos = entity.position()
        if (entity.isAlive) pos = pos.add(with(entity.deltaMovement) {
            Vec3(x, y.coerceAtLeast(0.0), z)
        }.scale(partialTicks.toDouble()))
        else text += " [X]"

        val col: Int = -0x1
        pos.apply {
            poseStack.translate(x, y + entity.bbHeight + 0.33, z)
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-0.025F, -0.025F, 0.025F)
            font.drawInBatch(text, -font.width(text).toFloat() / 2, 0F, col, false,
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

        val (pos, rate, color) = entry
        val text = "${(rate / 1000).roundToInt()} μs/t"

        val col: Int = -0x1
        val opacity = 0x00FFFFFF;
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