package dev.worldsfall.undertow;

import dev.worldsfall.item.EchoTabletItem;
import dev.worldsfall.network.EchoTabletPayload;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Archive Shelf (proposal "Rewards"): a bookshelf that holds up to six Echo Tablets and Archive Codices and reads them
 * aloud in glyph text, one each time it is used. Sneak-use with an empty hand takes the last one back.
 */
public class ArchiveShelfBlock extends BaseEntityBlock {
    public ArchiveShelfBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArchiveShelfBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(WFItems.ECHO_TABLET.get()) && !stack.is(WFItems.ARCHIVE_CODEX.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArchiveShelfBlockEntity shelf) {
            if (!shelf.insert(stack.copyWithCount(1))) {
                player.sendOverlayMessage(Component.translatable("message.worldsfall.archive_shelf.full"));
                return InteractionResult.FAIL;
            }
            stack.shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0f, 0.9f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ArchiveShelfBlockEntity shelf) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            ItemStack out = shelf.takeLast();
            if (!out.isEmpty()) {
                dev.worldsfall.util.WFUtil.give(player, out);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0f, 0.9f);
            }
            return InteractionResult.SUCCESS;
        }
        ItemStack record = shelf.nextToRead();
        if (record.isEmpty()) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.archive_shelf.empty"));
            return InteractionResult.SUCCESS;
        }
        if (record.is(WFItems.ARCHIVE_CODEX.get())) {
            ArchiveCodexItem.read(sp, ArchiveCodexItem.index(record));
        } else {
            int idx = EchoTabletItem.index(record);
            WFNetwork.send(sp, new EchoTabletPayload(idx));
            Progress.readTablet(sp, idx);
            level.playSound(null, pos, WFSounds.BLOCK_ARCHIVE_SHELF_READ.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ArchiveShelfBlockEntity shelf ? shelf.count() * 15 / ArchiveShelfBlockEntity.SIZE : 0;
    }
}
