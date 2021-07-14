package observable.client

import ProfilingData
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
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

    var enabled = false
    var entities = ArrayList<Entry.EntityEntry>()
    var blockEntities = ArrayList<Entry.BlockEntityEntry>()
    lateinit var loc: Vec3

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

    fun render(poseStack: PoseStack, partialTicks: Float, camera: Camera) {
        if (!enabled) return

        loc = Minecraft.getInstance().player!!.position()

        RenderSystem.disableDepthTest()

        for (entry in entities) {
            drawEntity(entry, poseStack, partialTicks, camera)
        }

        for (entry in blockEntities) {
            drawBlock(entry, partialTicks)
        }

        // Cleanup
        RenderSystem.enableDepthTest()
    }

    inline fun drawEntity(entry: Entry.EntityEntry, poseStack: PoseStack, partialTicks: Float, camera: Camera) {
        poseStack.pushPose()

        val (entity, rate, color) = entry
        var text = "${(rate / 1000).roundToInt()} us/t"
        var pos = entity.position()
        if (entity.isAlive) pos = pos.add(entity.deltaMovement.scale(partialTicks.toDouble()))
        else text += " [X]"

        poseStack.mulPose(camera.rotation())
        poseStack.scale(-0.025F, -0.025F, 0.025F)
        val col: Int = -0x1
        pos.apply {
            poseStack.translate(x, y, z)
            Minecraft.getInstance().font.draw(poseStack, text, 0F, 0F, col)
        }

        poseStack.popPose()
    }

    inline fun drawBlock(entry: Entry.BlockEntityEntry, partialTicks: Float) {
    }
}