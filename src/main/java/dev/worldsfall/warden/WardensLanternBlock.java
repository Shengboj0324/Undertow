package dev.worldsfall.warden;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Warden's Lantern (Dragon Scale use): within 32 blocks, Endermen and Echoes cannot teleport and Echoes do not spawn —
 * for builders in the outer End. Lantern positions are tracked per dimension so the check is a short list scan.
 */
public class WardensLanternBlock extends LanternBlock {
    public static final int RADIUS = 32;

    public WardensLanternBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            Tracked.get(server).add(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Tracked.get(level).remove(pos);
    }

    /** True if a Warden's Lantern stands within 32 blocks. */
    public static boolean guarded(ServerLevel level, double x, double y, double z) {
        for (BlockPos p : Tracked.get(level).positions) {
            if (p.distToCenterSqr(x, y, z) <= RADIUS * RADIUS) {
                if (level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof WardensLanternBlock)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    public static final class Tracked extends SavedData {
        public static final com.mojang.serialization.Codec<Tracked> CODEC = BlockPos.CODEC.listOf().xmap(Tracked::new, t -> List.copyOf(t.positions));
        public static final SavedDataType<Tracked> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "wardens_lanterns"),
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
