package dev.worldsfall.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bottled Tidewisp placed as a light (level 9); it pulses faster before a tide change (§8.7). */
public class TidewispJarBlock extends Block {
    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 9, 11);

    public TidewispJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean stirring = dev.worldsfall.world.tide.Tides.appliesTo(level) && dev.worldsfall.world.tide.Tides.isStirring(level);
        if (random.nextInt(stirring ? 2 : 6) == 0) {
            level.addParticle(dev.worldsfall.registry.WFParticles.TIDEWISP_TRAIL.get(), pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, 0, 0.01, 0);
        }
    }
}
