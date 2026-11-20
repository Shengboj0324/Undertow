package dev.worldsfall.block;

import dev.worldsfall.world.fracture.FractureTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The swirling crimson-black Rift surface. Stepping in carries the entity (and a ridden Skate) across (§13.2). */
public class FractureRiftBlock extends Block {
    private static final VoxelShape SHAPE = box(0, 6, 0, 16, 12, 16);

    public FractureRiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean precise) {
        if (level instanceof ServerLevel server && entity.canUsePortal(false) && !entity.isPassenger()) {
            FractureTravel.cross(server, entity);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(dev.worldsfall.registry.WFParticles.SHARD_SPARK.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.8,
                pos.getZ() + random.nextDouble(), 0, 0.05 + random.nextDouble() * 0.05, 0);
        }
    }
}
