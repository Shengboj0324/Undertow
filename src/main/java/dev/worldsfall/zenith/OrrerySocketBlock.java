package dev.worldsfall.zenith;

import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An Orrery socket: a Spire Key set here during its ring's tide phase (Low, Rising or High) frees that ring. */
public class OrrerySocketBlock extends Block {
    public static final IntegerProperty RING = IntegerProperty.create("ring", 0, 2);
    public static final BooleanProperty FREED = BooleanProperty.create("freed");
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 12, 14);
    private static final TideCycle.Phase[] PHASES = {TideCycle.Phase.LOW, TideCycle.Phase.RISING, TideCycle.Phase.HIGH};

    public OrrerySocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RING, 0).setValue(FREED, false));
    }

    public static TideCycle.Phase phaseFor(int ring) {
        return PHASES[ring];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RING, FREED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(WFItems.SPIRE_KEY.get()) || state.getValue(FREED)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        int ring = state.getValue(RING);
        TideCycle.Phase need = phaseFor(ring);
        TideCycle.Phase now = Tides.appliesTo(server) ? Tides.phase(server) : need;
        if (now != need) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.orrery.wrong_phase",
                Component.translatable("tide.worldsfall." + need.id), Component.translatable("tide.worldsfall." + now.id)));
            return InteractionResult.FAIL;
        }
        if (Orreries.freeRing(server, pos, ring, sp)) {
            stack.consume(1, sp);
            server.setBlock(pos, state.setValue(FREED, true), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FREED) && random.nextInt(3) == 0) {
            level.addParticle(WFParticles.SOCKET_SPARK.get(), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }
}
