package dev.worldsfall.item;

import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Gateway Compass (§9.4). Unbound: points to the nearest End Gateway the holder has not used. Use on a
 * Gateway to bind. Pointing reuses vanilla's lodestone tracker so the needle animates like a compass.
 */
public class GatewayCompassItem extends Item {
    public static final int SEARCH_CHUNKS = 12;

    public GatewayCompassItem(Properties properties) {
        super(properties);
    }

    public static boolean isBound(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(WFComponents.GATEWAY_BOUND.get()));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isBound(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.END_GATEWAY)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            ItemStack stack = context.getItemInHand();
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos)), false));
            stack.set(WFComponents.GATEWAY_BOUND.get(), true);
            server.playSound(null, pos, WFSounds.ITEM_GATEWAY_COMPASS_BIND.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player) || level.dimension() != Level.END || isBound(stack) || level.getGameTime() % 40 != 0) {
            return;
        }
        Set<BlockPos> used = player.getData(WFAttachments.USED_GATEWAYS);
        BlockPos nearest = findNearestGateway(level, player.blockPosition(), used);
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        Optional<GlobalPos> target = nearest == null ? Optional.empty() : Optional.of(GlobalPos.of(level.dimension(), nearest));
        if (current == null || !current.target().equals(target)) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(target, false));
        }
    }

    public static @Nullable BlockPos findNearestGateway(ServerLevel level, BlockPos from, Set<BlockPos> exclude) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx++) {
            for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof TheEndGatewayBlockEntity && !exclude.contains(be.getBlockPos())) {
                        double d = be.getBlockPos().distSqr(from);
                        if (d < bestDist) {
                            bestDist = d;
                            best = be.getBlockPos();
                        }
                    }
                }
            }
        }
        return best;
    }
}
