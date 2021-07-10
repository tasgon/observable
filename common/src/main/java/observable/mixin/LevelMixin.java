package observable.mixin;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickableBlockEntity;
import observable.Observable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(Level.class)
public class LevelMixin {
    @Overwrite
    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        try {
            consumer.accept(entity);
        } catch (Throwable var6) {
            CrashReport crashReport = CrashReport.forThrowable(var6, "Ticking entity");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being ticked");
            entity.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TickableBlockEntity;tick()V"))
    public void redirectTick(TickableBlockEntity blockEntity) {
        blockEntity.tick();
    }
}
