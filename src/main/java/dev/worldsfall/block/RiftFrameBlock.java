package dev.worldsfall.block;

import dev.worldsfall.block.entity.RiftFrameBlockEntity;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Fracture Rift Frame (§6.3, §11.4): insert the three Ascendant Sigils (Spire, Tide, Under) — or a Rift Key —
 * to open the Rift for 10 minutes. One player opening it lets the whole party through.
 */
public class RiftFrameBlock extends BaseEntityBlock {
    public static final IntegerProperty SIGILS = IntegerProperty.create("sigils", 0, 3);
    /** Bit flags of which sigil kinds are inserted, so each kind is required exactly once. */
    public static final IntegerProperty MASK = IntegerProperty.create("mask", 0, 7);

    public RiftFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIGILS, 0).setValue(MASK, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGILS, MASK);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftFrameBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, WFBlockEntities.RIFT_FRAME.get(), RiftFrameBlockEntity::serverTick);
    }

    public static int sigilBit(ItemStack stack) {
        if (stack.is(WFItems.SPIRE_SIGIL.get())) return 1;
        if (stack.is(WFItems.TIDE_SIGIL.get())) return 2;
        if (stack.is(WFItems.UNDER_SIGIL.get())) return 4;
        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(WFTags.Items.RIFT_OPENERS) || !(level.getBlockEntity(pos) instanceof RiftFrameBlockEntity frame)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (frame.isOpen()) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.rift.already_open"));
            return InteractionResult.CONSUME;
        }
        if (stack.is(WFItems.RIFT_KEY.get())) {
            stack.consume(1, sp);
            server.setBlock(pos, state.setValue(SIGILS, 3).setValue(MASK, 7), Block.UPDATE_ALL);
            frame.open(server);
            return InteractionResult.CONSUME;
        }
        int bit = sigilBit(stack);
        int mask = state.getValue(MASK);
        if (bit == 0 || (mask & bit) != 0) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.rift.sigil_present"));
            return InteractionResult.CONSUME;
        }
        stack.consume(1, sp);
        mask |= bit;
        int count = Integer.bitCount(mask);
        server.setBlock(pos, state.setValue(SIGILS, count).setValue(MASK, mask), Block.UPDATE_ALL);
        server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.6f + count * 0.15f);
        if (count == 3) {
            frame.open(server);
        }
        return InteractionResult.CONSUME;
    }
}
