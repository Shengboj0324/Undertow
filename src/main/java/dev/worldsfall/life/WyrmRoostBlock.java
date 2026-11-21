package dev.worldsfall.life;

import dev.worldsfall.entity.Wyrm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Comparator;

/**
 * Wyrm Roost (proposal "The Wyrm grows a personality"): sets a tamed Wyrm's home. Use it with an empty hand and your
 * nearest free Wyrm settles there. While a Wyrm rests at its roost, Echoes cannot disguise and Lurkers cannot hide within
 * 24 blocks.
 */
public class WyrmRoostBlock extends Block {
    public static final int GUARD_RADIUS = 24, HOME_RADIUS = 8;
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 6, 16);

    public WyrmRoostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        Wyrm wyrm = server.getEntitiesOfClass(Wyrm.class, new AABB(pos).inflate(16), w -> w.isTame() && w.isOwnedBy(player) && !w.isPassenger()
            && !w.isVehicle()).stream().min(Comparator.comparingDouble(w -> w.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)))).orElse(null);
        if (wyrm == null) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.roost.no_wyrm"));
            return InteractionResult.FAIL;
        }
        wyrm.setRoost(pos);
        wyrm.snapTo(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, wyrm.getYRot(), 0);
        wyrm.setOrderedToSit(true);
        wyrm.setInSittingPose(true);
        player.sendOverlayMessage(Component.translatable("message.worldsfall.roost.settled", wyrm.getDisplayName()));
        return InteractionResult.SUCCESS;
    }

    /** True if a Wyrm resting at its roost guards {@code pos} (Echoes cannot disguise, Lurkers cannot hide). */
    public static boolean guarded(ServerLevel level, BlockPos pos) {
        int r = GUARD_RADIUS + HOME_RADIUS;
        return !level.getEntitiesOfClass(Wyrm.class, new AABB(pos).inflate(r), w -> w.isTame() && !w.roost().equals(BlockPos.ZERO)
            && w.roost().distSqr(pos) <= GUARD_RADIUS * GUARD_RADIUS && w.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(w.roost())) <= HOME_RADIUS * HOME_RADIUS
            && level.getBlockState(w.roost()).getBlock() instanceof WyrmRoostBlock).isEmpty();
    }
}
