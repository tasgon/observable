package observable.client

import ProfilingData
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import observable.Observable
import org.lwjgl.opengl.GL11
import kotlin.math.roundToInt

object Overlay {
    data class Color(val r: Double, val g: Double, val b: Double, val a: Double) {

    }

    sealed class Entry(val color: Color) {
        companion object {
            fun getColor(rate: Double) = Color(
                (rate / 100.0).coerceIn(0.0, 1.0),
                ((100.0 - rate) / 100.0).coerceIn(0.0, 1.0),
                0.0,
                (rate / 100.0).coerceIn(0.0, 1.0)
            )
        }
        data class EntityEntry(val entity: Entity, val rate: Double) : Entry(getColor(rate))
        data class BlockEntityEntry(val blockEntity: BlockEntity, val rate: Double) : Entry(getColor(rate))

        operator fun component3() = color
    }

    var enabled = true
    var entities = ArrayList<Entry.EntityEntry>()
    var blockEntities = ArrayList<Entry.BlockEntityEntry>()
    lateinit var loc: Vec3

    val font: Font by lazy {
        Minecraft.getInstance().font
    }

    fun load(data: ProfilingData) {
        listOf(entities, blockEntities).forEach { it.clear() }
        var invalids = 0
        for (entry in data.entries) {
            when {
                entry.entity.entity != null -> entities.add(Entry.EntityEntry(entry.entity.entity, entry.rate))
                entry.entity.blockEntity != null -> blockEntities.add(Entry.BlockEntityEntry(entry.entity.blockEntity, entry.rate))
                else -> {
                    Observable.LOGGER.warn("Invalid Entry: ${entry.entity.classname}")
                    invalids++
                }
            }
        }
        if (invalids > 0) Observable.LOGGER.warn("$invalids invalid entries (${data.entries.size - invalids} remain)")
    }

    fun render(poseStack: PoseStack, partialTicks: Float) {
        if (!enabled) return

        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val bufSrc = Minecraft.getInstance().renderBuffers().bufferSource()
        loc = Minecraft.getInstance().player!!.position()

        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        poseStack.pushPose()

        camera.position.apply {
            poseStack.translate(-x, -y, -z)
        }

        for (entry in entities) {
            drawEntity(entry, poseStack, partialTicks, camera, bufSrc)
        }

        for (entry in blockEntities) {
            drawBlock(entry, poseStack, partialTicks, camera, bufSrc)
        }

        poseStack.popPose()
        bufSrc.endBatch()

        // Cleanup
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        RenderSystem.enableDepthTest()
    }

    inline fun drawEntity(entry: Entry.EntityEntry, poseStack: PoseStack,
                          partialTicks: Float, camera: Camera, bufSrc: MultiBufferSource) {
        poseStack.pushPose()

        val (entity, rate, color) = entry
        var text = "${entity.javaClass.simpleName}: ${(rate / 1000).roundToInt()} μs/t"
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
                poseStack.last().pose(), bufSrc, true, 0, 15728880)
        }

        poseStack.popPose()
    }

    inline fun drawBlock(entry: Entry.BlockEntityEntry, poseStack: PoseStack,
                         partialTicks: Float, camera: Camera, bufSrc: MultiBufferSource) {
        poseStack.pushPose()

        val (blockEntity, rate, color) = entry
        var text = "${(rate / 1000).roundToInt()} μs/t"
        var pos = blockEntity.blockPos

        val col: Int = -0x1
        val opacity = 0x00FFFFFF;
        pos.apply {
            poseStack.translate(x + 0.5, y + 0.5, z + 0.5)
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-0.025F, -0.025F, 0.025F)
            font.drawInBatch(text, -font.width(text).toFloat() / 2, 0F, col, false,
                poseStack.last().pose(), bufSrc, true, 0, 15728880)
        }

        poseStack.popPose()
    }
}