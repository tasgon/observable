package observable.client

import ProfilingData
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import observable.Observable

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
        object InvalidEntry : Entry(getColor(Double.MAX_VALUE))
    }

    var enabled = false
    var entities = ArrayList<Entry.EntityEntry>()
    var blockEntities = ArrayList<Entry.BlockEntityEntry>()

    fun load(data: ProfilingData) {
        listOf(entities, blockEntities).forEach { it.clear() }
        for (entry in data.entries) {
            if (entry.entity.entity != null) entities.add(Entry.EntityEntry(entry.entity.entity, entry.rate))
            else if (entry.entity.blockEntity != null) blockEntities.add(Entry.BlockEntityEntry(entry.entity.blockEntity, entry.rate))
            else Observable.LOGGER.warn("Invalid Entry: ${entry.entity.classname}")
        }
    }

    fun render(partialTicks: Float) {
        Observable.RESULTS?.let {
            for (entry in it.entries) {
            }
        }
    }

    inline fun drawEntity() {

    }
}