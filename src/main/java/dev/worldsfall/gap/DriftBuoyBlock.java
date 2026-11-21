package dev.worldsfall.gap;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drift Buoy: a floating lantern placed in midair (no other block within 3), glowing at level 10. Skates perch on it, so
 * players mark routes across the Gap before they own a Gateway Compass. It pops (and drops itself) if the tide reaches it.
 */
public class DriftBuoyBlock extends Block {
    private static final VoxelShape SHAPE = box(4, 0, 4, 12, 12, 12);

    public DriftBuoyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Placed only in open air: no other block within 3. */
    public static boolean openAir(LevelReader level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
            if (!p.equals(pos) && !level.getBlockState(p).isAir()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return openAir(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            Tracked.get(server).add(pos);
            server.scheduleTick(pos, this, 40);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Tracked.get(level).remove(pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Tides.appliesTo(level) && Tides.isBelowTide(level, pos.getY() + 0.5)) {
            level.playSound(null, pos, WFSounds.BLOCK_DRIFT_BUOY_POP.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.destroyBlock(pos, true);
            return;
        }
        level.scheduleTick(pos, this, 40);
    }

    /** Buoys a Skate can see from {@code pos}, nearest first. */
    public static @Nullable BlockPos nearest(ServerLevel level, BlockPos pos, int range) {
        BlockPos best = null;
        double bestD = range * range;
        for (BlockPos p : Tracked.get(level).positions) {
            double d = p.distSqr(pos);
            if (d < bestD && level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof DriftBuoyBlock) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static final class Tracked extends SavedData {
        public static final com.mojang.serialization.Codec<Tracked> CODEC = BlockPos.CODEC.listOf().xmap(Tracked::new, t -> List.copyOf(t.positions));
        public static final SavedDataType<Tracked> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "drift_buoys"),
            Tracked::new, CODEC);
        final Set<BlockPos> positions = new HashSet<>();

        public Tracked() {}

        private Tracked(List<BlockPos> list) {
            positions.addAll(list);
        }

        public static Tracked get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(TYPE);
        }

        void add(BlockPos p) {
            if (positions.add(p.immutable())) {
                setDirty();
            }
        }

        void remove(BlockPos p) {
            if (positions.remove(p)) {
                setDirty();
            }
        }
    }
}
