package dev.worldsfall.restoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.undertow.MuralPanelBlock;
import dev.worldsfall.warden.Pact;
import dev.worldsfall.warden.Wardensong;
import dev.worldsfall.world.BlockQueue;
import dev.worldsfall.world.PlayerPlaced;
import dev.worldsfall.world.fracture.FractureData;
import dev.worldsfall.world.sundering.SunderingData;
import dev.worldsfall.world.sundering.UndercroftBuilder;
import dev.worldsfall.zenith.LeviathanDrifter;
import dev.worldsfall.zenith.ZenithSpawning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The visible Restoration of the End (proposal "The five stages"), run from the End's level tick. Everything here changes
 * the world at runtime near players, never by regenerating chunks, and never touches player-placed blocks.
 * <ol>
 *   <li>First Light (20%): lanterns flicker on in un-restored Fallen Spires; the Undercroft's Archive hall unseals.</li>
 *   <li>Green Shoots (40%): Pale Loam spreads on its own in the Midlands; small Grazer herds appear on the Highlands; the
 *       Skate stable opens.</li>
 *   <li>Steady Water (60%): Low Tide lasts half again as long and King Tides come half as often ({@link RestorationEffects}).</li>
 *   <li>Lanterns of the Ascendancy (80%): Sky Currents glow brighter, a constellation appears, Skate Migrations come twice
 *       as often, and the trophy hall opens.</li>
 *   <li>Stillness (100%): the Overmoon swells over the main island and Leviathans gather around it.</li>
 * </ol>
 * The Restoration Mural in the Archive hall fills one panel per 5%.
 */
public final class RestorationWorld extends SavedData {
    public static final int MURAL_W = 5, MURAL_H = 4, INTERVAL = 100;
    public static final String TROPHY_TAG = "worldsfall_trophy";

    private record Snapshot(int rooms, List<Long> litSpires, List<String> pacts, int muralLit, int trophyHash) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("rooms", 0).forGetter(Snapshot::rooms),
            Codec.LONG.listOf().optionalFieldOf("lit_spires", List.of()).forGetter(Snapshot::litSpires),
            Codec.STRING.listOf().optionalFieldOf("pacts_cleared", List.of()).forGetter(Snapshot::pacts),
            Codec.INT.optionalFieldOf("mural_lit", -1).forGetter(Snapshot::muralLit),
            Codec.INT.optionalFieldOf("trophy_hash", 0).forGetter(Snapshot::trophyHash)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<RestorationWorld> CODEC = Snapshot.CODEC.xmap(RestorationWorld::new, RestorationWorld::snapshot);
    public static final SavedDataType<RestorationWorld> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "restoration_world"),
        RestorationWorld::new, CODEC);

    /** Room bits. */
    public static final int ARCHIVE_HALL = 1, STABLE = 2, TROPHY_HALL = 4;

    private int rooms;
    private final Set<Long> litSpires = new HashSet<>();
    private final Set<Pact> pacts = EnumSet.noneOf(Pact.class);
    private int muralLit = -1;
    private int trophyHash;

    public RestorationWorld() {}

    private RestorationWorld(Snapshot s) {
        rooms = s.rooms;
        litSpires.addAll(s.litSpires);
        s.pacts.forEach(id -> {
            Pact p = Pact.byId(id);
            if (p != null) {
                pacts.add(p);
            }
        });
        muralLit = s.muralLit;
        trophyHash = s.trophyHash;
    }

    private Snapshot snapshot() {
        return new Snapshot(rooms, List.copyOf(litSpires), pacts.stream().map(Pact::id).toList(), muralLit, trophyHash);
    }

    public static RestorationWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean hasRoom(int bit) {
        return (rooms & bit) != 0;
    }

    public Set<Pact> pactsCleared() {
        return EnumSet.copyOf(pacts.isEmpty() ? EnumSet.noneOf(Pact.class) : pacts);
    }

    /** Called when a Pact fight is won, so the trophy hall can show its glyphs. */
    public static void noteCleared(MinecraftServer server, Set<Pact> cleared) {
        RestorationWorld data = get(server);
        if (data.pacts.addAll(cleared)) {
            data.setDirty();
        }
    }

    static void onStage(MinecraftServer server, int stage) {
        ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
        if (end != null) {
            tick(end, true);
        }
    }

    public static void tick(ServerLevel end) {
        tick(end, false);
    }

    private static void tick(ServerLevel end, boolean now) {
        if (!now && end.getGameTime() % INTERVAL != 41) {
            return;
        }
        MinecraftServer server = end.getServer();
        int stage = RestorationEffects.stage(server);
        if (stage <= 0 || !SunderingData.get(server).done()) {
            return;
        }
        RestorationWorld data = get(server);
        UndercroftBuilder.Layout l = UndercroftBuilder.layout(Wardensong.descent(end));
        if (stage >= 1 && !data.hasRoom(ARCHIVE_HALL)) {
            data.rooms |= ARCHIVE_HALL;
            data.setDirty();
            buildArchiveHall(end, l);
        }
        if (stage >= 2 && !data.hasRoom(STABLE)) {
            data.rooms |= STABLE;
            data.setDirty();
            buildStable(end, l);
        }
        if (stage >= 4 && !data.hasRoom(TROPHY_HALL)) {
            data.rooms |= TROPHY_HALL;
            data.setDirty();
            buildTrophyHall(end, l);
        }
        updateMural(end, l, data);
        if (data.hasRoom(TROPHY_HALL)) {
            updateTrophies(end, l, data);
        }
        RandomSource r = end.getRandom();
        for (ServerPlayer p : end.players()) {
            if (stage >= 1) {
                lightSpire(end, p, data, r);
            }
            if (stage >= 2) {
                greenShoots(end, p, r);
            }
        }
        if (stage >= 5) {
            gatherLeviathans(end);
        }
    }

    // ------------------------------------------------------------------ the Undercroft's new rooms

    private static Direction side(UndercroftBuilder.Layout l) {
        return l.inward().getClockWise();
    }

    /** The Archive hall's centre: beside the Rift Chamber, through a short passage. */
    public static BlockPos archiveCentre(UndercroftBuilder.Layout l) {
        return l.chamberCenter().relative(side(l), UndercroftBuilder.CHAMBER_RADIUS + 10).atY(UndercroftBuilder.FLOOR_Y + 1);
    }

    public static BlockPos stableCentre(UndercroftBuilder.Layout l) {
        return l.chamberCenter().relative(side(l).getOpposite(), UndercroftBuilder.CHAMBER_RADIUS + 10).atY(UndercroftBuilder.FLOOR_Y + 1);
    }

    public static BlockPos trophyCentre(UndercroftBuilder.Layout l) {
        return archiveCentre(l).relative(side(l), 17);
    }

    /** The mural's lower-left panel and the direction its rows run. */
    static BlockPos muralOrigin(UndercroftBuilder.Layout l) {
        return archiveCentre(l).relative(l.inward(), 5).relative(side(l).getOpposite(), 2).above(1);
    }

    static boolean carvable(ServerLevel end, BlockPos p) {
        if (PlayerPlaced.isPlayerPlaced(end, p)) {
            return false;
        }
        BlockState s = end.getBlockState(p);
        return s.isAir() || s.is(Blocks.END_STONE) || s.is(Blocks.OBSIDIAN) || s.is(WFBlocks.UNDERSHALE.get()) || s.is(WFBlocks.POLISHED_UNDERSHALE.get())
            || s.is(WFBlocks.UNDERSHALE_BRICKS.get()) || s.is(WFBlocks.VOIDROOT.get()) || s.is(WFBlocks.LUMEN_CRYSTAL.get());
    }

    /** Carves a room (and the passage to it from {@code from}): floor, walls and ceiling, air inside. */
    private static void room(ServerLevel end, BlockQueue q, BlockPos from, Direction dir, BlockPos centre, int halfLong, int halfWide, int height,
                             BlockState floor, BlockState wall) {
        BlockState air = Blocks.AIR.defaultBlockState();
        Direction across = dir.getClockWise();
        for (int i = 0; i <= from.distManhattan(centre) - halfLong; i++) {
            BlockPos step = from.relative(dir, i);
            for (int w = -2; w <= 2; w++) {
                for (int h = -1; h <= 4; h++) {
                    BlockPos p = step.relative(across, w).above(h);
                    if (carvable(end, p)) {
                        q.set(p, h == -1 ? floor : Math.abs(w) == 2 || h == 4 ? wall : air);
                    }
                }
            }
        }
        for (int a = -halfLong - 1; a <= halfLong + 1; a++) {
            for (int b = -halfWide - 1; b <= halfWide + 1; b++) {
                for (int h = -1; h <= height; h++) {
                    BlockPos p = centre.relative(dir, a).relative(across, b).above(h);
                    if (!carvable(end, p)) {
                        continue;
                    }
                    boolean shell = Math.abs(a) == halfLong + 1 || Math.abs(b) == halfWide + 1 || h == height;
                    q.set(p, h == -1 ? floor : shell ? wall : air);
                }
            }
        }
    }

    private static void buildArchiveHall(ServerLevel end, UndercroftBuilder.Layout l) {
        BlockQueue q = new BlockQueue(end, 500);
        Direction s = side(l);
        BlockPos c = archiveCentre(l);
        BlockPos from = l.chamberCenter().relative(s, UndercroftBuilder.CHAMBER_RADIUS - 1).atY(c.getY());
        room(end, q, from, s, c, 6, 5, 7, WFBlocks.ARCHIVE_TILES.get().defaultBlockState(), WFBlocks.UNDERSHALE_BRICKS.get().defaultBlockState());
        // shelves along both long walls, lanterns overhead, the mural on the far wall
        for (int a = -5; a <= 5; a += 2) {
            for (int b : new int[] {-5, 5}) {
                for (int h = 0; h <= 1; h++) {
                    q.set(c.relative(s, a).relative(l.inward(), b).above(h), WFBlocks.ARCHIVE_SHELF.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, b < 0 ? l.inward() : l.inward().getOpposite()));
                }
            }
        }
        for (int a = -4; a <= 4; a += 4) {
            q.set(c.relative(s, a).above(6), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        BlockPos m = muralOrigin(l);
        for (int x = 0; x < MURAL_W; x++) {
            for (int y = 0; y < MURAL_H; y++) {
                q.set(m.relative(s, x).above(y), WFBlocks.MURAL_PANEL.get().defaultBlockState());
            }
        }
        q.set(c.relative(l.inward().getOpposite(), 3), Blocks.LECTERN.defaultBlockState());
        q.start();
    }

    private static void buildStable(ServerLevel end, UndercroftBuilder.Layout l) {
        BlockQueue q = new BlockQueue(end, 500);
        Direction s = side(l).getOpposite();
        BlockPos c = stableCentre(l);
        BlockPos from = l.chamberCenter().relative(s, UndercroftBuilder.CHAMBER_RADIUS - 1).atY(c.getY());
        room(end, q, from, s, c, 6, 5, 8, WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState(), WFBlocks.DRIFTSTONE_BRICKS.get().defaultBlockState());
        for (int a = -4; a <= 4; a += 4) {
            for (int b : new int[] {-3, 3}) {
                q.set(c.relative(s, a).relative(l.inward(), b), WFBlocks.ROOST_POST.get().defaultBlockState());
            }
        }
        q.set(c.relative(s, 5), WFBlocks.WYRM_ROOST.get().defaultBlockState());
        q.set(c.above(7), WFBlocks.ANCHOR_FRAME_LAMP.get().defaultBlockState());
        q.then(() -> {
            // harness racks: harnesses hung on the walls
            for (int a = -3; a <= 3; a += 3) {
                BlockPos wall = c.relative(s, a).relative(l.inward(), 6).above(2);
                var frame = new net.minecraft.world.entity.decoration.ItemFrame(end, wall.relative(l.inward().getOpposite()), l.inward().getOpposite());
                frame.setItem(new ItemStack(WFItems.SKATE_HARNESS.get()), false);
                end.addFreshEntity(frame);
            }
        });
        q.start();
    }

    private static void buildTrophyHall(ServerLevel end, UndercroftBuilder.Layout l) {
        BlockQueue q = new BlockQueue(end, 500);
        Direction s = side(l);
        BlockPos c = trophyCentre(l);
        BlockPos from = archiveCentre(l).relative(s, 6);
        room(end, q, from, s, c, 6, 6, 8, WFBlocks.POLISHED_STILLSTONE.get().defaultBlockState(), WFBlocks.STILLSTONE_BRICKS.get().defaultBlockState());
        for (BlockPos p : pedestals(l)) {
            q.set(p, WFBlocks.CHISELED_STILLSTONE.get().defaultBlockState());
        }
        q.set(c.above(7), WFBlocks.HALO_GLASS.get().defaultBlockState());
        for (int a = -4; a <= 4; a += 4) {
            for (int b : new int[] {-3, 3}) {
                q.set(c.relative(s, a).relative(l.inward(), b).above(7), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        q.start();
    }

    public static List<BlockPos> pedestals(UndercroftBuilder.Layout l) {
        Direction s = side(l);
        BlockPos c = trophyCentre(l);
        List<BlockPos> out = new ArrayList<>();
        for (int a = -4; a <= 4; a += 2) {
            for (int b : new int[] {-4, 4}) {
                out.add(c.relative(s, a).relative(l.inward(), b));
            }
        }
        return out;
    }

    /** What the trophy hall shows: the dragon, The Unmade, the Release, and a glyph for each Pact the server has won. */
    static List<ItemStack> trophies(ServerLevel end, RestorationWorld data) {
        List<ItemStack> out = new ArrayList<>();
        var fight = end.getDragonFight();
        if (fight != null && fight.hasPreviouslyKilledDragon()) {
            out.add(new ItemStack(Items.DRAGON_HEAD));
        }
        if (FractureData.get(end.getServer()).defeated()) {
            out.add(new ItemStack(WFItems.HEART_OF_STILLNESS.get()));
        }
        if (dev.worldsfall.warden.WardenData.get(end.getServer()).released()) {
            out.add(new ItemStack(WFItems.WARDENBORN_SEAL.get()));
        }
        for (Pact p : data.pactsCleared()) {
            out.add(new ItemStack(WFItems.glyph(p)));
        }
        return out;
    }

    private static void updateTrophies(ServerLevel end, UndercroftBuilder.Layout l, RestorationWorld data) {
        BlockPos c = trophyCentre(l);
        if (!end.isLoaded(c)) {
            return;
        }
        List<ItemStack> shown = trophies(end, data);
        int hash = shown.stream().map(s -> s.getItem().toString()).toList().hashCode();
        if (hash == data.trophyHash && !end.getEntities((Entity) null, new AABB(c).inflate(12), e -> e.entityTags().contains(TROPHY_TAG)).isEmpty()) {
            return;
        }
        data.trophyHash = hash;
        data.setDirty();
        for (Entity e : end.getEntities((Entity) null, new AABB(c).inflate(12), e -> e.entityTags().contains(TROPHY_TAG))) {
            e.discard();
        }
        List<BlockPos> stands = pedestals(l);
        for (int i = 0; i < Math.min(shown.size(), stands.size()); i++) {
            Display.ItemDisplay d = new Display.ItemDisplay(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, end);
            BlockPos p = stands.get(i).above();
            d.snapTo(p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 0, 0);
            d.getSlot(0).set(shown.get(i));
            d.entityTags().add(TROPHY_TAG);
            end.addFreshEntity(d);
        }
    }

    private static void updateMural(ServerLevel end, UndercroftBuilder.Layout l, RestorationWorld data) {
        if (!data.hasRoom(ARCHIVE_HALL)) {
            return;
        }
        int lit = (int) Math.floor(Restoration.fraction(end.getServer()) * MURAL_W * MURAL_H + 1e-9);
        BlockPos m = muralOrigin(l);
        if (lit == data.muralLit || !end.isLoaded(m)) {
            return;
        }
        Direction s = side(l);
        int k = 0, found = 0;
        for (int y = 0; y < MURAL_H; y++) {
            for (int x = 0; x < MURAL_W; x++) {
                BlockPos p = m.relative(s, x).above(y);
                BlockState st = end.getBlockState(p);
                if (st.is(WFBlocks.MURAL_PANEL.get())) {
                    found++;
                    if (st.getValue(MuralPanelBlock.LIT) != k < lit) {
                        end.setBlock(p, st.setValue(MuralPanelBlock.LIT, k < lit), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    }
                }
                k++;
            }
        }
        // only remember the count once the whole mural stands (its panels are placed gradually)
        if (found == MURAL_W * MURAL_H) {
            data.muralLit = lit;
            data.setDirty();
        }
    }

    /** How many mural panels are lit for a fraction (one per 5%). */
    public static int panelsFor(double fraction) {
        return (int) Math.floor(Math.min(1.0, fraction) * MURAL_W * MURAL_H + 1e-9);
    }

    // ------------------------------------------------------------------ out in the End

    /** First Light: a few lanterns come on in each un-restored Fallen Spire a player walks into. */
    private static void lightSpire(ServerLevel end, ServerPlayer p, RestorationWorld data, RandomSource r) {
        StructureStart start = end.structureManager().getStructureWithPieceAt(p.blockPosition(), WFTags.Structures.FALLEN_SPIRES);
        if (!start.isValid() || !data.litSpires.add(start.getChunkPos().pack())) {
            return;
        }
        data.setDirty();
        int placed = 0;
        for (int i = 0; i < 300 && placed < 5; i++) {
            StructurePiece piece = start.getPieces().get(r.nextInt(start.getPieces().size()));
            var box = piece.getBoundingBox();
            BlockPos q = new BlockPos(box.minX() + r.nextInt(box.getXSpan()), box.minY() + r.nextInt(box.getYSpan()), box.minZ() + r.nextInt(box.getZSpan()));
            BlockState below = end.getBlockState(q.below());
            if (end.isLoaded(q) && end.getBlockState(q).isAir() && (below.is(WFBlocks.WEATHERED_PURPUR.get()) || below.is(Blocks.PURPUR_BLOCK))
                && !PlayerPlaced.isPlayerPlaced(end, q.below())) {
                end.setBlockAndUpdate(q, WFBlocks.SPIRE_LANTERN.get().defaultBlockState());
                placed++;
            }
        }
    }

    /** Green Shoots: Pale Loam takes root on open end stone in the Midlands, and Grazers wander onto the Highlands. */
    private static void greenShoots(ServerLevel end, ServerPlayer p, RandomSource r) {
        int x = p.getBlockX() + r.nextInt(97) - 48, z = p.getBlockZ() + r.nextInt(97) - 48;
        if (end.hasChunk(x >> 4, z >> 4)) {
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos top = new BlockPos(x, y, z);
            if (end.getBiome(top).is(net.minecraft.world.level.biome.Biomes.END_MIDLANDS) && end.getBlockState(top).is(Blocks.END_STONE)
                && end.getBlockState(top.above()).isAir() && !PlayerPlaced.isPlayerPlaced(end, top)) {
                end.setBlockAndUpdate(top, WFBlocks.PALE_LOAM.get().defaultBlockState());
            }
        }
        if (r.nextInt(40) == 0 && end.getBiome(p.blockPosition()).is(net.minecraft.world.level.biome.Biomes.END_HIGHLANDS)
            && end.getEntitiesOfClass(dev.worldsfall.entity.ChorusGrazer.class, p.getBoundingBox().inflate(64)).size() < 3) {
            for (int i = 0; i < 3; i++) {
                int gx = p.getBlockX() + r.nextInt(41) - 20, gz = p.getBlockZ() + r.nextInt(41) - 20;
                if (!end.hasChunk(gx >> 4, gz >> 4)) {
                    continue;
                }
                int gy = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                if (gy > end.getMinY() + 8 && Math.abs(gy - p.getY()) < 24) {
                    var g = WFEntities.CHORUS_GRAZER.get().create(end, EntitySpawnReason.NATURAL);
                    if (g != null) {
                        g.snapTo(gx + 0.5, gy, gz + 0.5, r.nextFloat() * 360, 0);
                        end.addFreshEntity(g);
                    }
                }
            }
        }
    }

    /** Stillness: three Leviathans circle high above the main island, under the Overmoon. */
    private static void gatherLeviathans(ServerLevel end) {
        if (end.players().stream().noneMatch(p -> p.getX() * p.getX() + p.getZ() * p.getZ() < 700 * 700)) {
            return;
        }
        List<LeviathanDrifter> circling = end.getEntitiesOfClass(LeviathanDrifter.class, new AABB(-800, 0, -800, 800, 320, 800),
            LeviathanDrifter::circlesOvermoon);
        for (int i = circling.size(); i < 3; i++) {
            LeviathanDrifter l = ZenithSpawning.spawnLeviathan(end, new Vec3(Math.cos(i * 2.1) * 140, 215, Math.sin(i * 2.1) * 140), end.getRandom());
            if (l != null) {
                l.snapTo(Math.cos(i * 2.1) * 140, 212 + i * 6, Math.sin(i * 2.1) * 140, l.getYRot(), 0);
                l.circleOvermoon(i * 2.1);
            }
        }
    }
}
