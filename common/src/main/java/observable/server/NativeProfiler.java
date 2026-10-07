package observable.server;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class NativeProfiler {
    private static BiConsumer<TickingBlockEntity, Level> blockEntityTicker = (be, lvl) -> be.tick();
    private static EntityTicker entityTicker = (e, method) -> method.accept(e);
    private static BlockTicker blockTicker = BlockBehaviour.BlockStateBase::tick;
    private static FluidTicker fluidTicker = FluidState::tick;

    public static void setBlockEntityTicker(BiConsumer<TickingBlockEntity, Level> ticker) {
        blockEntityTicker = ticker;
    }

    public static void setEntityTicker(EntityTicker ticker) {
        entityTicker = ticker;
    }

    public static void setBlockTicker(BlockTicker ticker) {
        blockTicker = ticker;
    }

    public static void setFluidTicker(FluidTicker ticker) {
        fluidTicker = ticker;
    }

    public static void tickBlockEntity(TickingBlockEntity be, Level level) {
        blockEntityTicker.accept(be, level);
    }

    public static void tickEntity(Entity entity, Consumer<Entity> tickMethod) {
        entityTicker.tick(entity, tickMethod);
    }

    public static void tickBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        blockTicker.tick(state, level, pos, random);
    }

    public static void tickFluid(FluidState state, ServerLevel level, BlockPos pos, BlockState blockState) {
        fluidTicker.tick(state, level, pos, blockState);
    }

    public interface EntityTicker {
        void tick(Entity entity, Consumer<Entity> method);
    }

    public interface BlockTicker {
        void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);
    }

    public interface FluidTicker {
        void tick(FluidState state, ServerLevel level, BlockPos pos, BlockState blockState);
    }
}
