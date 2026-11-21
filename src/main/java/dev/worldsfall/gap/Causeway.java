package dev.worldsfall.gap;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.restoration.Restoration;
import dev.worldsfall.world.BlockQueue;
import dev.worldsfall.world.PlayerPlaced;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.reassembly.Reassembly;
import dev.worldsfall.world.reassembly.ReassemblyPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Pilgrim's Causeway (proposal "The Gap and the main island"): the wreck of the purpur road the Ascendancy walked from
 * the centre to the outer islands, about 1,000 blocks long in 18–24 floating segments. Each gap has a socket at both ends;
 * a Causeway Keystone reassembles the missing span out of the void below. Three larger segments are waystations (a Skate
 * roost, a Hoard Cache, a Tide Gauge). The last span turns every lantern from magenta to calm silver.
 */
public final class Causeway {
    public static final int START_R = 172, END_R = 1090;
    public static final int HALF_WIDTH = 2;
    private static final int BUILD_RANGE = 150;

    private Causeway() {}

    // ------------------------------------------------------------------ planning

    public static void ensurePlanned(ServerLevel end, CausewayData data) {
        if (data.planned()) {
            return;
        }
        double angle;
        BlockPos spire = end.findNearestMapStructure(WFTags.Structures.FALLEN_SPIRES, BlockPos.ZERO, 180, false);
        if (spire != null) {
            angle = Math.atan2(spire.getZ(), spire.getX());
        } else {
            angle = RandomSource.create(end.getSeed() ^ 0xCA05E7L).nextDouble() * Math.PI * 2;
        }
        data.plan(angle, plan(RandomSource.create(end.getSeed() ^ 0x5EA5A11L)));
    }

    /** 18–24 segments of 10–30 blocks with 20–50 block gaps, three of them waystations. */
    public static List<CausewayData.Segment> plan(RandomSource rng) {
        List<int[]> raw = new ArrayList<>();
        int r = START_R;
        int i = 0;
        while (r < END_R) {
            int len = 10 + rng.nextInt(21);
            int y = 64 + (int) Math.round(Math.sin(i * 0.7) * 3) + rng.nextInt(3) - 1;
            raw.add(new int[] {r, Math.min(END_R + 20, r + len), y});
            r += len + 20 + rng.nextInt(31);
            i++;
        }
        int n = raw.size();
        List<CausewayData.Segment> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            boolean way = k == n / 4 || k == n / 2 || k == (3 * n) / 4;
            int[] s = raw.get(k);
            out.add(new CausewayData.Segment(s[0], Math.max(s[1], way ? s[0] + 16 : s[1]), s[2], way));
        }
        return out;
    }

    /** Block position at radius {@code r} along the bearing, {@code lat} blocks to the side. */
    public static BlockPos at(double angle, double r, double lat, int y) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return BlockPos.containing(c * r - s * lat, y, s * r + c * lat);
    }

    // ------------------------------------------------------------------ lazy building

    public static void tick(ServerLevel end) {
        if (!WFConfig.get(WFConfig.SERVER.causeway, true) || end.getGameTime() % 40 != 7) {
            return;
        }
        CausewayData data = CausewayData.get(end.getServer());
        if (end.players().isEmpty()) {
            return;
        }
        ensurePlanned(end, data);
        List<CausewayData.Segment> segs = data.segments();
        for (ServerPlayer p : end.players()) {
            double pr = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
            if (pr > END_R + 300) {
                continue;
            }
            for (int i = 0; i < segs.size(); i++) {
                if (data.built(i)) {
                    continue;
                }
                CausewayData.Segment s = segs.get(i);
                BlockPos mid = at(data.angle(), (s.r0() + s.r1()) / 2.0, 0, s.y());
                if (p.blockPosition().distSqr(mid) < BUILD_RANGE * BUILD_RANGE && end.isLoaded(at(data.angle(), s.r0(), 0, s.y()))
                    && end.isLoaded(at(data.angle(), s.r1(), 0, s.y()))) {
                    data.markBuilt(i);
                    build(end, data, i, s);
                }
            }
        }
    }

    private static boolean free(ServerLevel end, BlockPos p) {
        BlockState st = end.getBlockState(p);
        return (st.isAir() || st.canBeReplaced() || st.is(Blocks.END_STONE)) && !PlayerPlaced.isPlayerPlaced(end, p);
    }

    static void build(ServerLevel end, CausewayData data, int index, CausewayData.Segment s) {
        double a = data.angle();
        RandomSource rng = RandomSource.create(end.getSeed() ^ (index * 0x9E3779B97F4A7C15L));
        BlockQueue q = new BlockQueue(end, 500);
        BlockState lantern = lantern(data.calm());
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (double r = s.r0(); r <= s.r1(); r += 0.5) {
            boolean nearEnd = r - s.r0() < 2.5 || s.r1() - r < 2.5;
            for (double lat = -HALF_WIDTH; lat <= HALF_WIDTH; lat += 0.5) {
                BlockPos p = at(a, r, lat, s.y());
                if (nearEnd && rng.nextInt(3) == 0) {
                    continue; // ragged broken ends
                }
                blocks.putIfAbsent(p, deck(rng));
                // the keel: hanging rubble under the deck, deepest in the middle
                int keel = (int) Math.max(0, 3 - Math.abs(lat) - (nearEnd ? 2 : 0)) + (rng.nextInt(4) == 0 ? 1 : 0);
                for (int k = 1; k <= keel; k++) {
                    blocks.putIfAbsent(p.below(k), k == keel ? WFBlocks.PURPUR_RUBBLE.get().defaultBlockState()
                        : rng.nextInt(3) == 0 ? WFBlocks.DRIFTSTONE.get().defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
                }
            }
        }
        // railing posts every 4 blocks; lanterns on every other post
        for (int r = s.r0() + 2; r <= s.r1() - 2; r += 4) {
            for (int side = -1; side <= 1; side += 2) {
                BlockPos base = at(a, r, side * (HALF_WIDTH + 1), s.y() + 1);
                if (rng.nextInt(5) == 0) {
                    continue; // a toppled post
                }
                blocks.put(base.below(), Blocks.PURPUR_BLOCK.defaultBlockState());
                blocks.put(base, WFBlocks.CRACKED_PURPUR_PILLAR.get().defaultBlockState());
                if (((r - s.r0()) / 4) % 2 == 0) {
                    blocks.put(base.above(), lantern);
                }
            }
        }
        if (s.waystation()) {
            waystation(end, a, s, blocks, rng);
        }
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            if (free(end, e.getKey())) {
                q.set(e.getKey(), e.getValue());
            }
        }
        // sockets at both ends of every gap
        List<CausewayData.Segment> segs = data.segments();
        if (index < segs.size() - 1 && !data.restored(index)) {
            q.set(at(a, s.r1() - 1, 0, s.y() + 1), WFBlocks.CAUSEWAY_SOCKET.get().defaultBlockState());
        }
        if (index > 0 && !data.restored(index - 1)) {
            q.set(at(a, s.r0() + 1, 0, s.y() + 1), WFBlocks.CAUSEWAY_SOCKET.get().defaultBlockState());
        }
        q.then(() -> {
            if (s.waystation()) {
                populateWaystation(end, a, s);
            }
        }).start();
    }

    private static BlockState deck(RandomSource rng) {
        int k = rng.nextInt(10);
        return k < 4 ? WFBlocks.WEATHERED_PURPUR.get().defaultBlockState() : k < 8 ? Blocks.PURPUR_BLOCK.defaultBlockState()
            : k < 9 ? Blocks.PURPUR_PILLAR.defaultBlockState() : WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState();
    }

    static BlockState lantern(boolean calm) {
        return WFBlocks.CAUSEWAY_LANTERN.get().defaultBlockState().setValue(CausewayLanternBlock.CALM, calm);
    }

    /** A wider platform: a Skate roost, a Hoard Cache and a Tide Gauge on a pedestal. */
    private static void waystation(ServerLevel end, double a, CausewayData.Segment s, Map<BlockPos, BlockState> blocks, RandomSource rng) {
        double mid = (s.r0() + s.r1()) / 2.0;
        for (int dr = -7; dr <= 7; dr++) {
            for (int dl = -7; dl <= 7; dl++) {
                if (dr * dr + dl * dl > 49) {
                    continue;
                }
                BlockPos p = at(a, mid + dr, dl, s.y());
                blocks.put(p, dr * dr + dl * dl > 36 ? Blocks.PURPUR_BLOCK.defaultBlockState() : WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState());
                for (int k = 1; k <= 2 + rng.nextInt(3) - (int) (Math.sqrt(dr * dr + dl * dl) / 3); k++) {
                    blocks.putIfAbsent(p.below(k), WFBlocks.DRIFTSTONE.get().defaultBlockState());
                }
            }
        }
        // the roost: a perch of pillars around a pile of Chorus Pods
        BlockPos roost = at(a, mid - 3, -3, s.y() + 1);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            blocks.put(roost.relative(d, 2), WFBlocks.ROOST_POST.get().defaultBlockState());
        }
        blocks.put(roost, WFBlocks.CHORUS_POD.get().defaultBlockState());
        // the Hoard Cache and the Tide Gauge on a pedestal
        blocks.put(at(a, mid + 3, -3, s.y() + 1), WFBlocks.HOARD_CACHE.get().defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        BlockPos gauge = at(a, mid + 3, 3, s.y() + 1);
        blocks.put(gauge, WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState());
        blocks.put(gauge.above(), WFBlocks.TIDE_GAUGE.get().defaultBlockState());
    }

    private static void populateWaystation(ServerLevel end, double a, CausewayData.Segment s) {
        double mid = (s.r0() + s.r1()) / 2.0;
        BlockPos roost = at(a, mid - 3, -3, s.y() + 1);
        for (int k = 0; k < 2; k++) {
            var skate = WFEntities.VOID_SKATE.get().create(end, EntitySpawnReason.STRUCTURE);
            if (skate != null) {
                skate.snapTo(roost.getX() + 0.5 + k * 3, roost.getY() + 3, roost.getZ() + 0.5, end.getRandom().nextFloat() * 360, 0);
                end.addFreshEntity(skate);
            }
        }
        var hoarder = WFEntities.HOARDER.get().create(end, EntitySpawnReason.STRUCTURE);
        if (hoarder != null) {
            BlockPos cache = at(a, mid + 3, -3, s.y() + 1);
            hoarder.snapTo(cache.getX() + 1.5, cache.getY(), cache.getZ() + 1.5, 0, 0);
            hoarder.setHome(cache);
            hoarder.setPersistenceRequired();
            end.addFreshEntity(hoarder);
        }
    }

    // ------------------------------------------------------------------ restoring a gap

    /** The gap a socket belongs to (the one ending or starting at its radius). */
    static Optional<Integer> gapAt(CausewayData data, BlockPos socket) {
        List<CausewayData.Segment> segs = data.segments();
        double r = socket.getX() * Math.cos(data.angle()) + socket.getZ() * Math.sin(data.angle());
        for (int g = 0; g < segs.size() - 1; g++) {
            if (Math.abs(r - (segs.get(g).r1() - 1)) < 2.5 || Math.abs(r - (segs.get(g + 1).r0() + 1)) < 2.5) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    /** A Causeway Keystone was set in a socket. */
    public static boolean restore(ServerLevel end, BlockPos socket, ServerPlayer player) {
        CausewayData data = CausewayData.get(end.getServer());
        Optional<Integer> gap = gapAt(data, socket);
        if (gap.isEmpty() || !data.markRestored(gap.get(), player.getGameProfile().name())) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.causeway.already"));
            return false;
        }
        int g = gap.get();
        CausewayData.Segment a = data.segments().get(g), b = data.segments().get(g + 1);
        double ang = data.angle();
        List<ReassemblyPlan.Target> targets = new ArrayList<>();
        List<BlockPos> lanterns = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        RandomSource rng = RandomSource.create(g * 31L + 7);
        for (double r = a.r1() - 1; r <= b.r0() + 1; r += 0.5) {
            double t = (r - a.r1()) / Math.max(1.0, b.r0() - a.r1());
            int y = (int) Math.round(Mth.lerp(Mth.clamp(t, 0, 1), a.y(), b.y()));
            for (double lat = -HALF_WIDTH; lat <= HALF_WIDTH; lat += 0.5) {
                BlockPos p = at(ang, r, lat, y);
                if (seen.add(p)) {
                    targets.add(new ReassemblyPlan.Target(p, deck(rng)));
                }
            }
        }
        for (double r = a.r1() + 2; r <= b.r0() - 2; r += 4) {
            double t = (r - a.r1()) / Math.max(1.0, b.r0() - a.r1());
            int y = (int) Math.round(Mth.lerp(t, a.y(), b.y())) + 1;
            for (int side = -1; side <= 1; side += 2) {
                BlockPos post = at(ang, r, side * (HALF_WIDTH + 1), y);
                if (seen.add(post)) {
                    targets.add(new ReassemblyPlan.Target(post, WFBlocks.CRACKED_PURPUR_PILLAR.get().defaultBlockState()));
                    lanterns.add(post.above());
                }
            }
        }
        double mr = (a.r1() + b.r0()) / 2.0;
        int my = (int) Math.round((a.y() + b.y()) / 2.0);
        BlockPos plaque = at(ang, mr, HALF_WIDTH + 1, my + 1);
        // the sockets of this gap are spent
        for (BlockPos sp : List.of(at(ang, a.r1() - 1, 0, a.y() + 1), at(ang, b.r0() + 1, 0, b.y() + 1))) {
            if (end.getBlockState(sp).is(WFBlocks.CAUSEWAY_SOCKET.get())) {
                end.setBlockAndUpdate(sp, WFBlocks.CAUSEWAY_SOCKET.get().defaultBlockState().setValue(CausewaySocketBlock.USED, true));
            }
        }
        end.playSound(null, socket, WFSounds.BLOCK_CAUSEWAY_RESTORE.get(), SoundSource.BLOCKS, 3.0f, 1.0f);
        String name = player.getGameProfile().name();
        ReassemblyPlan plan = new ReassemblyPlan(targets, List.of(), lanterns, Optional.empty(), List.of());
        Reassembly.start(end, socket, plan, lantern(data.calm()), () -> {
            placePlaque(end, plaque, ang, name);
            Restoration.award(end.getServer(), player, Restoration.Act.CAUSEWAY_SPAN, "gap" + g);
            if (data.restoredCount() >= data.gaps()) {
                completeAll(end, data, player);
            }
        });
        player.sendOverlayMessage(Component.translatable("message.worldsfall.causeway.restoring", g + 1, data.gaps()));
        return true;
    }

    /** A small plaque on the railing with the restorer's name (a text display, so it reads from both sides). */
    private static void placePlaque(ServerLevel end, BlockPos at, double angle, String name) {
        Direction facing = Direction.fromYRot(Math.toDegrees(angle) - 90 + 180);
        if (free(end, at)) {
            end.setBlockAndUpdate(at, WFBlocks.CAUSEWAY_PLAQUE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        }
        String cmd = String.format(java.util.Locale.ROOT,
            "summon minecraft:text_display %.2f %.2f %.2f {billboard:\"vertical\",text:{\"text\":\"%s\",\"color\":\"#E8C9F2\"},"
                + "background:0,shadow:1b,see_through:0b,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                + "translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},Tags:[\"worldsfall_plaque\"]}",
            at.getX() + 0.5, at.getY() + 1.15, at.getZ() + 0.5, name.replace("\"", ""));
        end.getServer().getCommands().performPrefixedCommand(end.getServer().createCommandSourceStack().withLevel(end).withSuppressedOutput(), cmd);
    }

    /** The last span: every lantern turns calm silver and everyone nearby earns The Long Walk. */
    private static void completeAll(ServerLevel end, CausewayData data, ServerPlayer finisher) {
        data.setCalm();
        double a = data.angle();
        for (CausewayData.Segment s : data.segments()) {
            for (int r = s.r0() - 60; r <= s.r1() + 2; r++) {
                for (int side = -1; side <= 1; side += 2) {
                    for (int dy = -4; dy <= 6; dy++) {
                        BlockPos p = at(a, r, side * (HALF_WIDTH + 1), s.y() + dy);
                        if (end.isLoaded(p) && end.getBlockState(p).is(WFBlocks.CAUSEWAY_LANTERN.get())) {
                            end.setBlockAndUpdate(p, end.getBlockState(p).setValue(CausewayLanternBlock.CALM, true));
                        }
                    }
                }
            }
        }
        for (ServerPlayer p : end.getPlayers(pl -> pl.distanceToSqr(Vec3.atCenterOf(finisher.blockPosition())) < 256 * 256)) {
            Progress.award(p, "the_long_walk");
            p.sendSystemMessage(Component.translatable("message.worldsfall.causeway.complete"));
        }
    }
}
