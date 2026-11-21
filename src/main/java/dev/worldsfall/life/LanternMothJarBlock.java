package dev.worldsfall.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A bottled Lantern Moth placed as a light (level 12); other moths are drawn to it. */
public class LanternMothJarBlock extends Block {
    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 9, 11);
    private static final DustParticleOptions GLOW = new DustParticleOptions(0xFF7FE6, 0.6f);

    public LanternMothJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.3 + random.nextDouble() * 0.4,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
        }
    }
}
