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
import observable.server.TaggedSampler;
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
    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        try {
            if (Props.notProcessing) consumer.accept(entity);
            else {
                if (Props.entityDepth < 0) Props.entityDepth = Thread.currentThread().getStackTrace().length - 1;
                if ((Object)this instanceof ServerLevel) {
                    Profiler.TimingData data = Observable.INSTANCE.getPROFILER().process(entity);
                    Props.currentTarget.set(data);
                    long start = System.nanoTime();
                    consumer.accept(entity);
                    data.setTime(System.nanoTime() - start + data.getTime());
                    Props.currentTarget.set(null);
                    data.setTicks(data.getTicks() + 1);
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
            if (Props.blockEntityDepth < 0) Props.blockEntityDepth = Thread.currentThread().getStackTrace().length - 1;
            if ((Object)this instanceof ServerLevel) {
                Profiler.TimingData data = Observable.INSTANCE.getPROFILER().processBlockEntity(blockEntity, (Level)(Object)this);
                Props.currentTarget.set(data);
                long start = System.nanoTime();
                blockEntity.tick();
                data.setTime(System.nanoTime() - start + data.getTime());
                Props.currentTarget.set(null);
                data.setTicks(data.getTicks() + 1);
            } else {
                blockEntity.tick();
            }
        }
    }
}
