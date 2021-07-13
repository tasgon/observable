package observable.client

import ProfilingData
import com.mojang.blaze3d.systems.RenderSystem
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import observable.Observable
import org.lwjgl.opengl.GL11

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
        for (entry in data.entries) {
            if (entry.entity.entity != null) entities.add(Entry.EntityEntry(entry.entity.entity, entry.rate))
            else if (entry.entity.blockEntity != null) blockEntities.add(Entry.BlockEntityEntry(entry.entity.blockEntity, entry.rate))
            else Observable.LOGGER.warn("Invalid Entry: ${entry.entity.classname}")
        }
    }

    fun render(partialTicks: Float) {
        loc = Minecraft.getInstance().player!!.position()

        for (entry in entities) {
            drawEntity(entry, partialTicks)
        }

        for (entry in blockEntities) {

        }
    }

    inline fun drawEntity(entry: Entry.EntityEntry, partialTicks: Float) {
        val (entity, rate, color) = entry
        var pos = entity.position()
        if (entity.isAlive) pos = pos.add(entity.deltaMovement.scale(partialTicks.toDouble()))

        color.apply {
            GL11.glColor4d(r, g, b, a)
        }

        pos.apply {
            GL11.glVertex3d(x, y, z)
            GL11.glVertex3d(x, y + 1.5, z)
        }
    }

    inline fun drawBlock(entry: Entry.BlockEntityEntry, partialTicks: Float) {
    }
}