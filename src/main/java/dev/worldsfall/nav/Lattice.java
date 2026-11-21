package dev.worldsfall.nav;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.network.LatticeMenuPayload;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gateway Lattice (proposal "Navigation"). A Tuning Fork attunes an End Gateway; restored End Ships (Moorings) join
 * on their own. Walking into an attuned gateway while sneaking opens a list of every node you have attuned or visited;
 * a jump costs one ender pearl and fails while the destination is under the tide. Without sneaking, gateways keep their
 * vanilla pairing.
 */
public final class Lattice {
    /** Players looking at the Lattice list: the gateway they stand in. Their vanilla gateway jump is held while they choose. */
    private static final Map<UUID, BlockPos> HOLDING = new HashMap<>();
    private static final String[] FIRST = {"Pale", "Lilac", "Hollow", "Quiet", "Drifting", "Low", "High", "Silver", "Violet", "Far",
        "Broken", "Still", "Glass", "Hanging", "Sunken", "Ashen"};
    private static final String[] SECOND = {"Reach", "Landing", "Crossing", "Rest", "Spire", "Shelf", "Gate", "Hollow", "Shore", "Steps",
        "Watch", "Anchorage", "Rise", "Mooring", "Stair", "Verge"};

    private Lattice() {}

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.latticeMaxNodes, 32) > 0;
    }

    public static String nameFor(BlockPos pos, int kind) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        String first = FIRST[(int) ((h >>> 8) & 15)];
        String second = kind == LatticeData.MOORING ? "Mooring" : SECOND[(int) ((h >>> 20) & 15)];
        return first + " " + second;
    }

    // ------------------------------------------------------------------ attuning

    static InteractionResult useFork(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.END_GATEWAY)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, WFSounds.ITEM_TUNING_FORK_STRIKE.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        LatticeData data = LatticeData.get(server.getServer());
        LatticeData.Node node = data.at(pos);
        if (node == null) {
            int max = WFConfig.get(WFConfig.SERVER.latticeMaxNodes, 32);
            if (data.size() >= max) {
                player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.full", max));
                return InteractionResult.FAIL;
            }
            node = new LatticeData.Node(pos.immutable(), LatticeData.GATEWAY, nameFor(pos, LatticeData.GATEWAY));
            data.add(node);
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.06);
        }
        data.learn(player.getUUID(), pos);
        player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.attuned", node.name(), data.size(),
            WFConfig.get(WFConfig.SERVER.latticeMaxNodes, 32)));
        Progress.award(player, "attuned");
        return InteractionResult.SUCCESS;
    }

    /** A restored End Ship joins the Lattice; false if the Lattice is full. */
    public static boolean addMooring(ServerLevel level, BlockPos helm, ServerPlayer by) {
        LatticeData data = LatticeData.get(level.getServer());
        if (data.size() >= WFConfig.get(WFConfig.SERVER.latticeMaxNodes, 32)) {
            return false;
        }
        data.add(new LatticeData.Node(helm.immutable(), LatticeData.MOORING, nameFor(helm, LatticeData.MOORING)));
        data.learn(by.getUUID(), helm);
        return true;
    }

    // ------------------------------------------------------------------ player tick

    /** Runs before the player's portal handling each tick, so an attuned gateway entered while sneaking does not fire. */
    public static void tickPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.END || !enabled()) {
            HOLDING.remove(player.getUUID());
            return;
        }
        LatticeData data = LatticeData.get(level.getServer());
        BlockPos held = HOLDING.get(player.getUUID());
        if (held != null && player.blockPosition().distSqr(held) > 9) {
            HOLDING.remove(player.getUUID());
            held = null;
        }
        var process = player.portalProcess;
        if (process != null && process.isSamePortal((Portal) Blocks.END_GATEWAY)) {
            BlockPos entry = process.getEntryPosition();
            LatticeData.Node node = data.at(entry);
            if (node != null && (player.isShiftKeyDown() || entry.equals(held))) {
                player.portalProcess = null;
                player.setPortalCooldown();
                if (!entry.equals(held)) {
                    data.learn(player.getUUID(), entry);
                    HOLDING.put(player.getUUID(), entry.immutable());
                    WFNetwork.send(player, menu(level, player, entry));
                }
            }
        }
        if (player.tickCount % 20 == 0) {
            for (LatticeData.Node n : data.nodes()) {
                if (!data.knows(player.getUUID(), n.pos()) && n.pos().distSqr(player.blockPosition()) < (n.kind() == LatticeData.MOORING ? 20 * 20 : 6 * 6)) {
                    data.learn(player.getUUID(), n.pos());
                    player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.learned", n.name()));
                }
            }
        }
    }

    public static LatticeMenuPayload menu(ServerLevel level, ServerPlayer player, BlockPos from) {
        LatticeData data = LatticeData.get(level.getServer());
        List<LatticeMenuPayload.Entry> entries = new ArrayList<>();
        List<LatticeData.Node> known = new ArrayList<>(data.knownBy(player.getUUID()));
        known.sort(Comparator.comparingDouble(n -> n.pos().distSqr(from)));
        for (LatticeData.Node n : known) {
            if (n.pos().equals(from)) {
                continue;
            }
            entries.add(new LatticeMenuPayload.Entry(n.pos(), n.kind(), n.name(), covered(level, n), skatePerched(level, n.pos())));
        }
        return new LatticeMenuPayload(from, entries);
    }

    static boolean covered(ServerLevel level, LatticeData.Node n) {
        return Tides.appliesTo(level) && Tides.isBelowTide(level, n.pos().getY() + 0.5);
    }

    static boolean skatePerched(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && !level.getEntitiesOfClass(VoidSkate.class, new AABB(pos).inflate(16), VoidSkate::isPerched).isEmpty();
    }

    // ------------------------------------------------------------------ travelling

    public static void jump(ServerPlayer player, BlockPos target) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.END) {
            return;
        }
        BlockPos from = HOLDING.get(player.getUUID());
        LatticeData data = LatticeData.get(level.getServer());
        LatticeData.Node node = data.at(target);
        if (from == null || node == null || !data.knows(player.getUUID(), target) || target.equals(from)) {
            return;
        }
        if (!stillStands(level, node)) {
            data.remove(target);
            player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.gone", node.name()));
            return;
        }
        if (covered(level, node)) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.covered", node.name()));
            return;
        }
        if (!player.getAbilities().instabuild) {
            int slot = player.getInventory().findSlotMatchingItem(Items.ENDER_PEARL.getDefaultInstance());
            if (slot < 0) {
                player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.no_pearl"));
                return;
            }
            player.getInventory().removeItem(slot, 1);
        }
        Vec3 arrival = arrival(level, node);
        level.playSound(null, player.blockPosition(), WFSounds.EVENT_LATTICE_JUMP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
        HOLDING.remove(player.getUUID());
        player.setPortalCooldown();
        player.teleportTo(level, arrival.x, arrival.y, arrival.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        if (arrival.y > node.pos().getY() - 0.5 && node.kind() == LatticeData.GATEWAY && !standable(level, BlockPos.containing(arrival))) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
        }
        level.playSound(null, BlockPos.containing(arrival), WFSounds.EVENT_LATTICE_JUMP.get(), SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.PORTAL, arrival.x, arrival.y + 1, arrival.z, 60, 0.4, 0.8, 0.4, 0.4);
        level.playSound(null, BlockPos.containing(arrival), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.0f);
        Progress.award(player, "lattice_jump");
    }

    static boolean stillStands(ServerLevel level, LatticeData.Node node) {
        if (!level.isLoaded(node.pos())) {
            level.getChunk(node.pos());
        }
        var state = level.getBlockState(node.pos());
        return node.kind() == LatticeData.GATEWAY ? state.is(Blocks.END_GATEWAY) : state.getBlock() instanceof DriftHelmBlock;
    }

    /** Where a traveller lands: the nearest standable spot within six blocks (and forty below), or beside the node. */
    static Vec3 arrival(ServerLevel level, LatticeData.Node node) {
        BlockPos base = node.pos();
        if (node.kind() == LatticeData.MOORING && standable(level, base.above())) {
            return Vec3.atBottomCenterOf(base.above());
        }
        @Nullable BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz < 4) {
                    continue;
                }
                for (int dy = 3; dy >= -40; dy--) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (standable(level, p)) {
                        double score = dx * dx + dz * dz + Math.abs(dy) * 2.0;
                        if (score < bestScore) {
                            bestScore = score;
                            best = p;
                        }
                        break;
                    }
                }
            }
        }
        return best != null ? Vec3.atBottomCenterOf(best) : Vec3.atBottomCenterOf(base.offset(2, 0, 0));
    }

    static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            && !level.getBlockState(feet).is(Blocks.END_GATEWAY) && !level.getBlockState(feet.above()).is(Blocks.END_GATEWAY);
    }

    /** For tests and commands: whether a player is currently choosing a destination. */
    public static boolean choosing(ServerPlayer player) {
        return HOLDING.containsKey(player.getUUID());
    }

    public static void release(UUID player) {
        HOLDING.remove(player);
    }
}
