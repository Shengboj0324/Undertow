package dev.worldsfall.life;

import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A Starfall shard cluster: pale gold-white crystals that landed from the sky. Mine it for Lumen, Halo Glass or a tablet. */
public class StarfallShardBlock extends Block {
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 13, 14);

    public StarfallShardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }


    @Override
    public void playerDestroy(net.minecraft.server.level.ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity be,
                              ItemStack tool) {
        super.playerDestroy(level, player, pos, state, be, tool);
        Progress.award(player, "starstruck");
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(WFParticles.STARFALL_SPARK.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.4 + random.nextDouble() * 0.6,
                pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
