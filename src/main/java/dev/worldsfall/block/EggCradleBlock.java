package dev.worldsfall.block;

import dev.worldsfall.block.entity.EggCradleBlockEntity;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Egg Cradle (§6.3, §10.2): a ring altar with four Lumen posts that light one by one. */
public class EggCradleBlock extends BaseEntityBlock {
    public static final IntegerProperty POSTS = IntegerProperty.create("posts", 0, 4);
    public static final BooleanProperty EGG = BooleanProperty.create("egg");
    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 6, 16), box(0, 6, 0, 3, 13, 3), box(13, 6, 0, 16, 13, 3),
        box(0, 6, 13, 3, 13, 16), box(13, 6, 13, 16, 13, 16));

    public EggCradleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POSTS, 0).setValue(EGG, false));
    }

    public static int light(BlockState state) {
        return Math.min(15, state.getValue(POSTS) * 3 + (state.getValue(EGG) ? 3 : 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POSTS, EGG);
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
        return new EggCradleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, WFBlockEntities.EGG_CRADLE.get(), EggCradleBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EggCradleBlockEntity cradle)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.is(Items.DRAGON_EGG)) {
            if (!level.isClientSide() && cradle.placeDragonEgg()) {
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(WFItems.SLUMBERING_EGG.get())) {
            if (!level.isClientSide()) {
                var data = stack.get(dev.worldsfall.registry.WFComponents.WYRM_DATA.get());
                if (data != null && cradle.placeSlumberingEgg(data.copyTag())) {
                    stack.consume(1, player);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(WFItems.GLOW_GLAND.get())) {
            if (player instanceof ServerPlayer sp) {
                switch (cradle.feed(sp)) {
                    case FED, COMPLETE -> stack.consume(1, sp);
                    case TOO_SOON -> sp.sendOverlayMessage(Component.translatable("message.worldsfall.cradle.too_soon",
                        cradle.ticksUntilNextFeed() / 20));
                    case WRONG_STAGE -> sp.sendOverlayMessage(Component.translatable("message.worldsfall.cradle.not_ready"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof EggCradleBlockEntity cradle) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.cradle.stage." + cradle.stage().getSerializedName(),
                cradle.glands(), EggCradleBlockEntity.GLANDS_REQUIRED));
        }
        return InteractionResult.SUCCESS;
    }
}
