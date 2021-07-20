package observable.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import observable.Observable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Redirect(method = "tickLiquid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    public void onTickLiquid(FluidState state, Level level, BlockPos pos) {
        if (Observable.INSTANCE.getPROFILER().getNotProcessing()) state.tick(level, pos);
        else {
            long start = System.nanoTime();
            state.tick(level, pos);
            Observable.INSTANCE.getPROFILER().processFluid(state, pos, level, System.nanoTime() - start);
        }
    }

    @Redirect(method = "tickBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V"))
    public void onTickBlock(BlockState state, ServerLevel level, BlockPos pos, Random random) {
        if (Observable.INSTANCE.getPROFILER().getNotProcessing()) state.tick(level, pos, random);
        else {
            long start = System.nanoTime();
            state.tick(level, pos, random);
            Observable.INSTANCE.getPROFILER().processBlock(state, pos, level, System.nanoTime() - start);
        }
    }
}
