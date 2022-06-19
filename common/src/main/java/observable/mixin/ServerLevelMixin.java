package observable.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import observable.Observable;
import observable.Props;
import observable.server.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Redirect(method = "tickFluid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    public final void onTickLiquid(FluidState state, Level level, BlockPos pos) {
        if (Props.notProcessing) state.tick(level, pos);
        else {
            if (Props.fluidDepth < 0) Props.fluidDepth = Thread.currentThread().getStackTrace().length - 1;
            Profiler.TimingData data = Observable.INSTANCE.getPROFILER().processFluid(state, pos, level);
            Props.currentTarget.set(data);
            long start = System.nanoTime();
            state.tick(level, pos);
            data.setTime(System.nanoTime() - start + data.getTime());
            Props.currentTarget.set(null);
            data.setTicks(data.getTicks() + 1);
        }
    }

    @Redirect(method = "tickBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    public final void onTickBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Props.notProcessing) state.tick(level, pos, random);
        else {
            if (Props.blockDepth < 0) Props.blockDepth = Thread.currentThread().getStackTrace().length - 1;
            Profiler.TimingData data = Observable.INSTANCE.getPROFILER().processBlock(state, pos, level);
            Props.currentTarget.set(data);
            long start = System.nanoTime();
            state.tick(level, pos, random);
            data.setTime(System.nanoTime() - start + data.getTime());
            Props.currentTarget.set(null);
            data.setTicks(data.getTicks() + 1);
        }
    }
}
