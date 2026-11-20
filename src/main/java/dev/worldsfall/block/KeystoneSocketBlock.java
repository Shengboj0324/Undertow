package dev.worldsfall.block;

import dev.worldsfall.block.entity.KeystoneSocketBlockEntity;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.world.reassembly.Reassembly;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Keystone Socket: insert a Spire Key to trigger a Reassembly (§6.3, §11.1). */
public class KeystoneSocketBlock extends BaseEntityBlock {
    public static final BooleanProperty USED = BooleanProperty.create("used");
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 14, 14);

    public KeystoneSocketBlock(Properties properties) {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeystoneSocketBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(WFItems.SPIRE_KEY.get()) || state.getValue(USED)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp
            && level.getBlockEntity(pos) instanceof KeystoneSocketBlockEntity socket) {
            stack.consume(1, sp);
            server.setBlock(pos, state.setValue(USED, true), Block.UPDATE_ALL);
            Reassembly.start(server, pos, socket.plan(), sp);
        }
        return InteractionResult.SUCCESS;
    }

    /** Sockets glow and spark, more strongly when a player holds a Spire Key nearby (§17.1). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(USED)) {
            return;
        }
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, false);
        boolean keyNear = near != null && (near.getMainHandItem().is(WFItems.SPIRE_KEY.get()) || near.getOffhandItem().is(WFItems.SPIRE_KEY.get()));
        int count = keyNear ? 4 : 1;
        for (int i = 0; i < count; i++) {
            if (keyNear || random.nextInt(3) == 0) {
                level.addParticle(WFParticles.SOCKET_SPARK.get(), pos.getX() + 0.5 + random.nextGaussian() * 0.2, pos.getY() + 0.9,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.2, 0, 0.03 + random.nextDouble() * 0.03, 0);
            }
        }
        if (keyNear && random.nextInt(30) == 0) {
            level.playLocalSound(pos, dev.worldsfall.registry.WFSounds.EVENT_SOCKET_HUM.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f, false);
        }
    }
}
