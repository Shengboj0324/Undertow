package dev.worldsfall.zenith;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.nav.Charts;
import dev.worldsfall.nav.StructureSearch;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFMapDecorations;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.restoration.Restoration;
import dev.worldsfall.util.WFUtil;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.gen.structure.OrreryGenerator;
import dev.worldsfall.world.reassembly.Reassembly;
import dev.worldsfall.world.reassembly.ReassemblyPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Orrery (proposal "The Orrery"). A Spire Key set in a socket during its ring's tide phase frees that ring, which flies
 * into alignment (Reassembly). With all three aligned the lens focuses: the player receives a Star Chart, a new permanent
 * star appears in the End sky for everyone, and the Orrery reveals the Overmoon.
 */
public final class Orreries extends SavedData {
    public static final int CHART_HALF = 1024, CHART_SCALE = 4;

    private record Snapshot(Map<String, Integer> freed, List<Long> completed) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("freed", Map.of()).forGetter(Snapshot::freed),
            Codec.LONG.listOf().optionalFieldOf("completed", List.of()).forGetter(Snapshot::completed)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<Orreries> CODEC = Snapshot.CODEC.xmap(Orreries::new, Orreries::snapshot);
    public static final SavedDataType<Orreries> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "orreries"),
        Orreries::new, CODEC);

    private final Map<Long, Integer> freed = new HashMap<>();
    private final List<Long> completed = new ArrayList<>();

    public Orreries() {}

    private Orreries(Snapshot s) {
        s.freed.forEach((k, v) -> freed.put(Long.parseLong(k), v));
        completed.addAll(s.completed);
    }

    private Snapshot snapshot() {
        Map<String, Integer> f = new HashMap<>();
        freed.forEach((k, v) -> f.put(Long.toString(k), v));
        return new Snapshot(f, List.copyOf(completed));
    }

    public static Orreries get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The completed Orreries (their lens positions), one permanent star in the sky each. */
    public static List<Long> stars(MinecraftServer server) {
        return List.copyOf(get(server).completed);
    }

    public int freedMask(BlockPos lens) {
        return freed.getOrDefault(lens.asLong(), 0);
    }

    // ------------------------------------------------------------------ freeing rings

    /** The Orrery lens near a socket, or null. */
    public static @Nullable BlockPos lensNear(ServerLevel level, BlockPos socket) {
        for (BlockPos p : BlockPos.betweenClosed(socket.offset(-6, 0, -6), socket.offset(6, OrreryGenerator.PLATFORM_DROP + 1, 6))) {
            if (level.getBlockState(p).is(WFBlocks.ORRERY_LENS.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    /** Frees ring {@code ring}: its jammed blocks fly into alignment. Returns false if this Orrery cannot be found. */
    public static boolean freeRing(ServerLevel level, BlockPos socket, int ring, ServerPlayer player) {
        BlockPos lens = lensNear(level, socket);
        if (lens == null) {
            return false;
        }
        Orreries data = get(level.getServer());
        int mask = data.freedMask(lens);
        if ((mask & (1 << ring)) != 0) {
            return false;
        }
        data.freed.put(lens.asLong(), mask | (1 << ring));
        data.setDirty();
        List<BlockPos> jammed = OrreryGenerator.ring(lens, ring, false).stream()
            .filter(p -> isRingBlock(level.getBlockState(p))).toList();
        List<BlockPos> aligned = OrreryGenerator.ring(lens, ring, true);
        List<ReassemblyPlan.Target> targets = new ArrayList<>();
        for (int k = 0; k < aligned.size(); k++) {
            targets.add(new ReassemblyPlan.Target(aligned.get(k), OrreryGenerator.ringBlock(k)));
        }
        level.playSound(null, lens, WFSounds.BLOCK_ORRERY_TURN.get(), SoundSource.BLOCKS, 6.0f, 0.8f + ring * 0.15f);
        ReassemblyPlan plan = new ReassemblyPlan(targets, jammed, List.of(), Optional.empty(), List.of());
        Reassembly.start(level, socket, plan, WFBlocks.HALO_GLASS.get().defaultBlockState(), () -> settle(level, lens, ring, player));
        Progress.award(player, "orrery_ring");
        return true;
    }

    static boolean isRingBlock(BlockState s) {
        return s.is(WFBlocks.ORRERY_RING.get()) || s.is(WFBlocks.HALO_GLASS.get());
    }

    /** After the flight: any blocks the Reassembly did not carry are set directly, and stragglers of the jammed ring vanish. */
    private static void settle(ServerLevel level, BlockPos lens, int ring, ServerPlayer player) {
        List<BlockPos> aligned = OrreryGenerator.ring(lens, ring, true);
        for (int k = 0; k < aligned.size(); k++) {
            BlockPos p = aligned.get(k);
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, OrreryGenerator.ringBlock(k), Block.UPDATE_ALL);
            }
        }
        for (BlockPos p : OrreryGenerator.ring(lens, ring, false)) {
            if (isRingBlock(level.getBlockState(p)) && !aligned.contains(p)) {
                level.removeBlock(p, false);
            }
        }
        level.playSound(null, lens, WFSounds.BLOCK_ORRERY_ALIGN.get(), SoundSource.BLOCKS, 6.0f, 0.9f + ring * 0.1f);
        if (get(level.getServer()).freedMask(lens) == 0b111) {
            focus(level, lens, player);
        }
    }

    // ------------------------------------------------------------------ the lens focuses

    static void focus(ServerLevel level, BlockPos lens, ServerPlayer player) {
        Orreries data = get(level.getServer());
        if (data.completed.contains(lens.asLong())) {
            return;
        }
        data.completed.add(lens.asLong());
        data.setDirty();
        BlockState state = level.getBlockState(lens);
        if (state.is(WFBlocks.ORRERY_LENS.get())) {
            level.setBlock(lens, state.setValue(OrreryLensBlock.FOCUSED, true), Block.UPDATE_ALL);
        }
        level.playSound(null, lens, WFSounds.BLOCK_ORRERY_ALIGN.get(), SoundSource.BLOCKS, 12.0f, 0.6f);
        java.util.Set<ServerPlayer> witnesses = new java.util.LinkedHashSet<>();
        witnesses.add(player);
        level.players().stream().filter(p -> p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(lens)) < 48 * 48).forEach(witnesses::add);
        for (ServerPlayer p : witnesses) {
            WFUtil.give(p, starChart(level, lens));
            Progress.award(p, "the_orrery");
            for (int i = 0; i < 3; i++) {
                p.sendSystemMessage(Component.translatable("message.worldsfall.orrery.overmoon." + i).withStyle(net.minecraft.ChatFormatting.ITALIC));
            }
        }
        Restoration.award(level.getServer(), player, Restoration.Act.ORRERY_ALIGN, "orrery" + lens.asLong());
        WFNetwork.syncRestorationToAll(level.getServer());
    }

    /** A Star Chart: a pre-drawn Void Chart 2,048 blocks across marking every Driftwreck, Fallen Spire, Hoard and End City in it. */
    public static ItemStack starChart(ServerLevel level, BlockPos center) {
        ItemStack chart = Charts.create(level, center.getX(), center.getZ(), CHART_SCALE, true, "star");
        mark(level, chart, center, "driftwreck", "driftwreck", WFMapDecorations.WRECK);
        mark(level, chart, center, "fallen_spire", "fallen_spire", WFMapDecorations.SPIRE);
        mark(level, chart, center, "hoard", "hoard", WFMapDecorations.HOARD);
        for (ChunkPos c : StructureSearch.startsIn(level, Identifier.withDefaultNamespace("end_cities"), Identifier.withDefaultNamespace("end_city"),
            center, CHART_HALF)) {
            MapItemSavedData.addTargetDecoration(chart, new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ()), "city_" + c.pack(), WFMapDecorations.CITY);
        }
        MapItemSavedData.addTargetDecoration(chart, center, "orrery", WFMapDecorations.ORRERY);
        return chart;
    }

    private static void mark(ServerLevel level, ItemStack chart, BlockPos center, String set, String structure, Holder<MapDecorationType> icon) {
        for (ChunkPos c : StructureSearch.startsIn(level, Identifier.fromNamespaceAndPath(Worldsfall.MODID, set),
            Identifier.fromNamespaceAndPath(Worldsfall.MODID, structure), center, CHART_HALF)) {
            MapItemSavedData.addTargetDecoration(chart, new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ()), structure + "_" + c.pack(), icon);
        }
    }
}
