package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.world.entity.Entity

class EntityList(minecraft: Minecraft?, x: Int, y: Int, width: Int, height: Int, m: Int) : ObjectSelectionList<EntityList.EntryBase>(minecraft, x, y,
    width,
    height,
    m
) {
//    class EntityEntry() : EntryBase() {
//
//    }
    @Environment(EnvType.CLIENT)
    class EntryBase(val mc: Minecraft, val entity: Entity) : ObjectSelectionList.Entry<EntryBase>() {
    override fun render(
        poseStack: PoseStack,
        i: Int,
        j: Int,
        k: Int,
        l: Int,
        m: Int,
        n: Int,
        o: Int,
        bl: Boolean,
        f: Float
    ) {
        mc.font.draw(poseStack, entity.javaClass.name, (k + 32 + 3).toFloat(), (j + 1).toFloat(), 0xFFFFFF);
    }
}
}