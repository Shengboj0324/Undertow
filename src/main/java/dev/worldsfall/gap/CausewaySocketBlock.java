package dev.worldsfall.gap;

import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A socket at each end of a Causeway gap: set a Causeway Keystone in it to reassemble the missing span. */
public class CausewaySocketBlock extends Block {
    public static final BooleanProperty USED = BooleanProperty.create("used");
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 10, 14);

    public CausewaySocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(USED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(USED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(WFItems.CAUSEWAY_KEYSTONE.get()) || state.getValue(USED)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && Causeway.restore(server, pos, sp)) {
            stack.consume(1, sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(USED) && random.nextInt(3) == 0) {
            level.addParticle(WFParticles.SOCKET_SPARK.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.7,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
        }
    }
}
