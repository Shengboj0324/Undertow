package dev.worldsfall.block;

import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.TideData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * The Still Throne (§11.4): placing the Heart of Stillness calms the tides world-wide (High Tide 36 → 20,
 * tide colour teal → silver-blue). Taking it back restores normal tides.
 */
public class StillThroneBlock extends Block {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 14, 16);

    public StillThroneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (state.getValue(FILLED) || !stack.is(WFItems.HEART_OF_STILLNESS.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            stack.consume(1, sp);
            server.setBlock(pos, state.setValue(FILLED, true), Block.UPDATE_ALL);
            setCalm(server, true);
            server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 0.7f);
            Progress.award(sp, "calm_waters");
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(FILLED)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, state.setValue(FILLED, false), Block.UPDATE_ALL);
            dev.worldsfall.util.WFUtil.give(player, new ItemStack(WFItems.HEART_OF_STILLNESS.get()));
            setCalm(server, false);
            server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.7f);
        }
        return InteractionResult.SUCCESS;
    }

    private static void setCalm(ServerLevel level, boolean calm) {
        TideData data = TideData.get(level.getServer());
        data.set(data.state().withCalmed(calm));
        WFNetwork.syncTidesToAll(level.getServer());
    }
}
