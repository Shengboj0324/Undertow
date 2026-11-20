package dev.worldsfall.block;

import dev.worldsfall.block.entity.HoardCacheBlockEntity;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Hoard Cache: the barter container (§6.3). The decorative crafted variant does not barter. */
public class HoardCacheBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 14, 15);
    private final boolean barters;

    public HoardCacheBlock(Properties properties, boolean barters) {
        super(properties);
        this.barters = barters;
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    public boolean barters() {
        return barters;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return barters ? new HoardCacheBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!barters || stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (HoardCacheBlockEntity.valueOf(stack) == 0) {
            if (level instanceof ServerLevel server) {
                server.playSound(null, pos, WFSounds.ENTITY_HOARDER_BARTER_REFUSE.get(), SoundSource.NEUTRAL, 1, 1);
                player.sendOverlayMessage(Component.translatable("message.worldsfall.hoard.refused"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HoardCacheBlockEntity cache) {
            HoardCacheBlockEntity.InsertResult result = cache.insert(server, sp, stack);
            for (ItemStack out : result.results()) {
                ItemEntity item = new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, out);
                item.setDeltaMovement(server.getRandom().nextGaussian() * 0.04, 0.22, server.getRandom().nextGaussian() * 0.04);
                server.addFreshEntity(item);
            }
            server.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.3);
            if (result.barters() > 0) {
                Progress.award(sp, "fair_trade");
            } else {
                sp.sendOverlayMessage(Component.translatable("message.worldsfall.hoard.value", cache.storedValue(),
                    HoardCacheBlockEntity.VALUE_PER_BARTER));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Rummaging the Cache without a recent barter angers the Hoard (§8.5.1). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!barters) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HoardCacheBlockEntity cache) {
            if (cache.recentlyBartered(server.getGameTime())) {
                sp.sendOverlayMessage(Component.translatable("message.worldsfall.hoard.friendly"));
            } else {
                cache.anger(server, sp);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
