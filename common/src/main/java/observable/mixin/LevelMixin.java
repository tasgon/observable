package observable.mixin;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import observable.Observable;
import observable.Props;
import observable.server.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(Level.class)
public class LevelMixin {
    /**
     * @reason This is an overwrite to insert the monitoring code.
     * @author .
     */
    @Overwrite
    public <T extends Entity> void guardEntityTick(Consumer<Entity> consumer, T entity) {
        try {
            if (Props.notProcessing) consumer.accept(entity);
            else {
                if ((Object)this instanceof ServerLevel) {
                    long start = System.nanoTime();
                    consumer.accept(entity);
                    long end = System.nanoTime();
                    Observable.INSTANCE.getPROFILER().process(entity, end - start);
                } else {
                    consumer.accept(entity);
                }
            }
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Ticking entity");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being ticked");
            entity.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    public void redirectTick(TickingBlockEntity blockEntity) {
        if (Props.notProcessing) blockEntity.tick();
        else {
            if ((Object)this instanceof ServerLevel) {
                long start = System.nanoTime();
                blockEntity.tick();
                long end = System.nanoTime();
                Observable.INSTANCE.getPROFILER().processBlockEntity(blockEntity, end - start,
                        (Level)(Object)this);
            } else {
                blockEntity.tick();
            }
        }
    }
}
