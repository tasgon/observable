package observable.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import observable.Props;
import observable.server.NativeProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Redirect(method = "tickFluid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    public final void observable$onTickLiquid(FluidState state, ServerLevel level, BlockPos pos, BlockState blockState) {
        if (!Props.notProcessing.get()) {
            NativeProfiler.tickFluid(state, level, pos, blockState);
        } else {
            state.tick(level, pos, blockState);
        }
    }

    @Redirect(method = "tickBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    public final void observable$onTickBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Props.notProcessing.get()) {
            NativeProfiler.tickBlock(state, level, pos, random);
        } else {
            state.tick(level, pos, random);
        }
    }

    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    public final void observable$onTickNonPassenger(Entity entity) {
        if (!Props.notProcessing.get()) {
            NativeProfiler.tickEntity(entity, Entity::tick);
        } else {
            entity.tick();
        }
    }

    @Redirect(method = "tickPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    public final void observable$onTickPassenger(Entity entity) {
        if (!Props.notProcessing.get()) {
            NativeProfiler.tickEntity(entity, Entity::rideTick);
        } else {
            entity.rideTick();
        }
    }
}
