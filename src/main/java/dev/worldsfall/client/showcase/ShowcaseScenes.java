package dev.worldsfall.client.showcase;

import com.mojang.datafixers.util.Pair;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.block.entity.EggCradleBlockEntity;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.registry.WFBiomes;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.world.fracture.FractureArena;
import dev.worldsfall.world.gen.WFEndBiomeSource;
import dev.worldsfall.world.sundering.Sundering;
import dev.worldsfall.world.sundering.SunderingData;
import dev.worldsfall.world.tide.Tides;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The showcase scene list (one per §18.10 "picture"), all server-side setup run on the integrated server thread. */
public final class ShowcaseScenes {
    private ShowcaseScenes() {}

    public record Ctx(MinecraftServer server, ServerPlayer player) {
        ServerLevel end() {
            return level(Level.END);
        }

        ServerLevel level(ResourceKey<Level> key) {
            ServerLevel l = server.getLevel(key);
            if (l == null) {
                throw new IllegalStateException("missing level " + key);
            }
            return l;
        }
    }

    @FunctionalInterface
    public interface CamPath {
        Vec3 at(int frame, Plan plan);
    }

    @FunctionalInterface
    public interface Decorator {
        Plan apply(MinecraftServer server, Plan plan);
    }

    /**
     * A scene's camera plan. {@code stills.get(0)} is the arrival camera; further entries are extra still angles.
     * {@code path}/{@code targetPath} drive the video frames when present.
     */
    public record Plan(ResourceKey<Level> dimension, Vec3 camera, Vec3 target, List<Vec3> stills, int videoFrames, @Nullable CamPath path,
                       @Nullable CamPath targetPath, @Nullable Decorator decorate, @Nullable Runnable cleanup, int extraSettle, boolean lens) {
        static Plan at(ResourceKey<Level> dim, Vec3 camera, Vec3 target) {
            return new Plan(dim, camera, target, List.of(camera), 0, null, null, null, null, 0, false);
        }

        Plan decorate(Decorator d) {
            return new Plan(dimension, camera, target, stills, videoFrames, path, targetPath, d, cleanup, extraSettle, lens);
        }

        Plan frame(Vec3 newCamera, Vec3 newTarget) {
            List<Vec3> s = new ArrayList<>(stills);
            if (s.isEmpty()) {
                s.add(newCamera);
            } else {
                s.set(0, newCamera);
            }
            return new Plan(dimension, newCamera, newTarget, s, videoFrames, path, targetPath, decorate, cleanup, extraSettle, lens);
        }

        Plan stills(List<Vec3> extra) {
            List<Vec3> s = new ArrayList<>();
            s.add(camera);
            s.addAll(extra);
            return new Plan(dimension, camera, target, s, videoFrames, path, targetPath, decorate, cleanup, extraSettle, lens);
        }

        Plan video(int frames, @Nullable CamPath p, @Nullable CamPath tp) {
            return new Plan(dimension, camera, target, stills, frames, p, tp, decorate, cleanup, extraSettle, lens);
        }

        /** The scene is shown through a Void Lens: the director keeps the HUD on and the local player scoped. */
        Plan throughLens() {
            return new Plan(dimension, camera, target, stills, videoFrames, path, targetPath, decorate, cleanup, extraSettle, true);
        }

        Plan settle(int extra) {
            return new Plan(dimension, camera, target, stills, videoFrames, path, targetPath, decorate, cleanup, extra, lens);
        }

        /** Slow orbit around the target, starting at the still camera, sweeping {@code degrees} over the video. */
        Plan orbit(int frames, double degrees) {
            double sweep = Math.toRadians(degrees);
            return video(frames, (f, p) -> {
                Vec3 c = p.camera(), t = p.target();
                double r = Math.hypot(c.x - t.x, c.z - t.z);
                double a = Math.atan2(c.z - t.z, c.x - t.x) + sweep * ease(f / (double) frames);
                return new Vec3(t.x + Math.cos(a) * r, c.y, t.z + Math.sin(a) * r);
            }, null);
        }
    }

    public record Scene(String id, Function<Ctx, @Nullable Plan> setup) {}

    static double ease(double x) {
        return x * x * (3 - 2 * x);
    }

    /** Yaw/pitch (degrees) looking from {@code from} to {@code to}. */
    public static float[] look(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        return new float[] {yaw, pitch};
    }

    public static List<Scene> all() {
        List<Scene> base = List.of(
            new Scene("chorus_reach", c -> biomeScene(c, WFBiomes.CHORUS_REACH, 30, 8, (s, p) -> {
                ServerLevel l = c.end();
                spawnAround(l, WFEntities.CHORUS_GRAZER.get(), p.target(), 3, 5, false);
                return p;
            }).orbit(180, 70)),
            new Scene("stillwater_basins", c -> basinScene(c).orbit(160, 60)),
            new Scene("fallen_spires", c -> leaningSpireScene(c).orbit(200, 80)),
            new Scene("the_hoard", c -> structureScene(c, "hoard", 16, 6, (s, p) -> {
                spawnAround(c.end(), WFEntities.HOARDER.get(), p.target(), 2, 6, false);
                return p;
            }).orbit(160, 60)),
            new Scene("the_underside", c -> undersideScene(c).orbit(180, 90)),
            new Scene("the_drift", c -> driftScene(c).orbit(200, 50)),
            new Scene("void_skate", c -> studioScene(c, new BlockPos(0, 112, 760), false, (l, center) -> {
                VoidSkate a = spawn(l, WFEntities.VOID_SKATE.get(), center.add(0, 2.5, 0), 20, true);
                if (a != null) {
                    a.setSaddled(true);
                }
                VoidSkate b = spawn(l, WFEntities.VOID_SKATE.get(), center.add(7, 5, 6), 50, true);
                if (b != null) {
                    b.setVariant(1);
                }
                return new Vec3[] {center.add(-4.4, 3.7, -3.6), center.add(0.5, 2.6, 0.5)};
            }).orbit(220, 140)),
            new Scene("wardrobe", c -> studioScene(c, new BlockPos(760, 110, -760), true, ShowcaseScenes::wardrobe)
                .stills(List.of(new Vec3(760.5 - 1.6, 112.5, -759.5 + 3.3))).orbit(200, 70)),
            new Scene("creatures", c -> creatureLineup(c)),
            new Scene("void_lens", ShowcaseScenes::voidLensScene),
            new Scene("egg_cradle_wyrm", c -> studioScene(c, new BlockPos(760, 110, 0), true, (l, center) -> {
                BlockPos cradle = BlockPos.containing(center);
                l.setBlockAndUpdate(cradle, WFBlocks.EGG_CRADLE.get().defaultBlockState());
                if (l.getBlockEntity(cradle) instanceof EggCradleBlockEntity be) {
                    be.placeDragonEgg();
                    be.debugSetProgress(EggCradleBlockEntity.ATTUNE_TICKS - 40);
                }
                Wyrm h = spawn(l, WFEntities.WYRM.get(), center.add(3.5, 0, -1.5), 200, true);
                Wyrm f = spawn(l, WFEntities.WYRM.get(), center.add(6.5, 0, 1.5), 210, true);
                Wyrm d = spawn(l, WFEntities.WYRM.get(), center.add(-6, 0, 2.5), 160, true);
                if (h != null) h.setStage(Wyrm.Stage.HATCHLING);
                if (f != null) f.setStage(Wyrm.Stage.FLEDGLING);
                if (d != null) d.setStage(Wyrm.Stage.DRAKE);
                return new Vec3[] {center.add(1, 4.5, -11), center.add(0, 1.2, 0)};
            }).stills(List.of(new Vec3(763.5, 112.5, -3.5))).orbit(200, 100).settle(60)),
            new Scene("gravity_anchor", c -> studioScene(c, new BlockPos(-760, 110, 0), true, (l, center) -> {
                BlockPos a = BlockPos.containing(center);
                l.setBlockAndUpdate(a, WFBlocks.GRAVITY_ANCHOR.get().defaultBlockState());
                // §9.8: a stabilised walkway reaching out over the Drift from the anchored stage
                BlockState walk = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    Identifier.fromNamespaceAndPath(Worldsfall.MODID, "polished_driftstone")).defaultBlockState();
                for (int i = 8; i < 22; i++) {
                    for (int w = -1; w <= 1; w++) {
                        l.setBlockAndUpdate(a.offset(i, -1 + (i > 14 ? 1 : 0), w), walk);
                    }
                }
                return new Vec3[] {center.add(-6.5, 3.2, -6.5), center.add(1.5, 0.8, 0.5)};
            }).stills(List.of(new Vec3(-748.5, 116, -14))).orbit(160, 90)),
            new Scene("items", ShowcaseScenes::itemWall),
            new Scene("the_unmade", c -> {
                ServerLevel f = c.level(WFDimensions.THE_FRACTURE);
                FractureArena.ensureBuilt(f);
                FractureArena.ensureBoss(f);
                Vec3 core = Vec3.atCenterOf(FractureArena.CENTER).add(0, FractureArena.CORE_HEIGHT, 0);
                Vec3 cam = core.add(30, -4, 14);
                return Plan.at(WFDimensions.THE_FRACTURE, cam, core.add(0, -3, 0)).stills(List.of(core.add(-16, -10, 22))).orbit(240, 120).settle(80);
            }),
            new Scene("sundering", ShowcaseScenes::sunderingScene)
        );
        // the expansion's pictures (1.2 → 2.0) come after the Sundering, which several of them need
        List<Scene> all = new ArrayList<>(base);
        all.addAll(ExpansionScenes.all());
        return List.copyOf(all);
    }

    // ---- scene builders -------------------------------------------------------------------------------------

    private static Plan biomeScene(Ctx c, ResourceKey<Biome> biome, double dist, double height, Decorator extra) {
        ServerLevel end = c.end();
        tide(c, "low");
        Pair<BlockPos, Holder<Biome>> found = end.findClosestBiome3d(h -> h.is(biome), new BlockPos(1500, 64, 300), 6000, 32, 64);
        if (found == null) {
            throw new IllegalStateException("biome not found: " + biome.identifier());
        }
        BlockPos p = found.getFirst();
        Vec3 guess = new Vec3(p.getX() + 0.5, 80, p.getZ() + 0.5);
        return Plan.at(Level.END, guess.add(0, 40, -20), guess).decorate((s, plan) -> {
            Vec3 t = islandCenter(end, BlockPos.containing(guess), biome, 72);
            return extra.apply(s, frameIsland(end, plan, t, dist, height));
        });
    }

    private static Plan structureScene(Ctx c, String tag, double dist, double height, Decorator extra) {
        ServerLevel end = c.end();
        tide(c, "low");
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, tag));
        BlockPos p = end.findNearestMapStructure(key, new BlockPos(1500, 64, 300), 200, false);
        if (p == null) {
            throw new IllegalStateException("structure not found: " + tag);
        }
        Vec3 guess = new Vec3(p.getX() + 8, 80, p.getZ() + 8);
        return Plan.at(Level.END, guess.add(0, 40, -20), guess).decorate((s, plan) -> {
            Vec3 t = islandCenter(end, BlockPos.containing(guess), null, 40);
            return extra.apply(s, frameIsland(end, plan, t, dist, height));
        });
    }

    /**
     * §11.1's picture is the leaning tower, so search outward for a Fallen Spire whose start is the LEANING variant and
     * frame the tower's own bounds rather than the island.
     */
    private static Plan leaningSpireScene(Ctx c) {
        ServerLevel end = c.end();
        tide(c, "low");
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "fallen_spires"));
        net.minecraft.world.level.levelgen.structure.BoundingBox found = null;
        for (int ring = 0; ring < 24 && found == null; ring++) {
            double a = ring * 2.399963;
            int r = 2400 + ring * 260;
            BlockPos from = new BlockPos((int) (Math.cos(a) * r), 64, (int) (Math.sin(a) * r));
            BlockPos p = end.findNearestMapStructure(key, from, 60, false);
            if (p == null) {
                continue;
            }
            var chunk = end.getChunk(p.getX() >> 4, p.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
            for (var start : chunk.getAllStarts().values()) {
                for (var piece : start.getPieces()) {
                    if (piece instanceof dev.worldsfall.world.gen.structure.BlueprintPiece bp && "fallen_spire".equals(bp.generator())
                        && (bp.variant() & 7) == dev.worldsfall.world.gen.structure.SpireGenerator.LEANING) {
                        found = bp.getBoundingBox();
                    }
                }
            }
        }
        if (found == null) {
            return structureScene(c, "fallen_spires", 38, 11, (s, p) -> p);
        }
        var box = found;
        Vec3 t = new Vec3((box.minX() + box.maxX()) / 2.0, box.minY() + (box.maxY() - box.minY()) * 0.42, (box.minZ() + box.maxZ()) / 2.0);
        double span = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
        return Plan.at(Level.END, t.add(0, 40, -20), t).decorate((s, plan) -> frameIsland(end, plan, t, Math.max(30, span * 1.05), span * 0.22));
    }

    /** The §4.2 picture: look down across the heart-shaped lake toward the notch where the Stillfall leaves the island. */
    private static Plan basinScene(Ctx c) {
        ServerLevel end = c.end();
        tide(c, "low");
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "stillwater_basins"));
        BlockPos p = end.findNearestMapStructure(key, new BlockPos(1500, 64, 300), 200, false);
        if (p == null) {
            return biomeScene(c, WFBiomes.STILLWATER_BASINS, 28, 10, ShowcaseScenes::aimAtStillwater);
        }
        var chunk = end.getChunk(p.getX() >> 4, p.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
        dev.worldsfall.world.gen.structure.StillwaterBasinPiece basin = null;
        for (var start : chunk.getAllStarts().values()) {
            for (var piece : start.getPieces()) {
                if (piece instanceof dev.worldsfall.world.gen.structure.StillwaterBasinPiece b) {
                    basin = b;
                }
            }
        }
        if (basin == null) {
            return biomeScene(c, WFBiomes.STILLWATER_BASINS, 28, 10, ShowcaseScenes::aimAtStillwater);
        }
        BlockPos ctr = basin.center();
        int r = basin.radius();
        net.minecraft.core.Direction spill = basin.spill();
        Vec3 t = new Vec3(ctr.getX() + 0.5 + spill.getStepX() * r * 0.25, ctr.getY(), ctr.getZ() + 0.5 + spill.getStepZ() * r * 0.25);
        // stand behind the heart's lobes, opposite the spill, high enough to read the shape
        Vec3 cam = t.add(-spill.getStepX() * (r * 1.9 + 8), r * 0.95 + 8, -spill.getStepZ() * (r * 1.9 + 8));
        Vec3 side = t.add(spill.getStepZ() * (r * 1.7 + 10) + spill.getStepX() * r, r * 0.35 + 3, -spill.getStepX() * (r * 1.7 + 10) + spill.getStepZ() * r);
        return Plan.at(Level.END, cam, t).stills(List.of(side));
    }

    /** §9.6: a Driftwreck seen through the Void Lens, traced as a box with its distance, and the tide line with its height. */
    private static @Nullable Plan voidLensScene(Ctx c) {
        ServerLevel end = c.end();
        tide(c, "low");
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "driftwrecks"));
        BlockPos p = end.findNearestMapStructure(key, new BlockPos(2600, 64, 600), 200, false);
        if (p == null) {
            return null;
        }
        var chunk = end.getChunk(p.getX() >> 4, p.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
        net.minecraft.world.level.levelgen.structure.BoundingBox box = null;
        for (var start : chunk.getAllStarts().values()) {
            if (start.isValid() && end.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(start.getStructure()).is(key)) {
                box = start.getBoundingBox();
            }
        }
        if (box == null) {
            return null;
        }
        Vec3 wreck = Vec3.atCenterOf(box.getCenter());
        double tideY = Tides.height(end);
        Vec3 cam = wreck.add(-94, 16, -68);
        // at the lens's 10x zoom a wreck fills the view, so aim at its lower side edge: a bracket corner, the open Drift
        // and the tide line all land in the lens together
        Vec3 dir = new Vec3(wreck.x - cam.x, 0, wreck.z - cam.z).normalize();
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        double half = Math.abs(right.x) * box.getXSpan() / 2.0 + Math.abs(right.z) * box.getZSpan() / 2.0;
        double y = Math.max(box.minY() - 2, tideY + 3);
        Vec3 t = new Vec3(wreck.x + right.x * half, y, wreck.z + right.z * half);
        ServerPlayer sp = c.player();
        return Plan.at(Level.END, cam, t).throughLens().decorate((s, plan) -> {
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.getInventory().setSelectedSlot(0);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(dev.worldsfall.registry.WFItems.VOID_LENS.get()));
            return plan;
        }).orbit(100, 14).settle(40);
    }

    private static Plan undersideScene(Ctx c) {
        ServerLevel end = c.end();
        tide(c, "low");
        int midY = (WFEndBiomeSource.UNDERSIDE_MIN_Y + WFEndBiomeSource.UNDERSIDE_MAX_Y) / 2;
        Pair<BlockPos, Holder<Biome>> found = end.findClosestBiome3d(h -> h.is(WFBiomes.THE_UNDERSIDE), new BlockPos(1500, midY, 300), 6000, 32, 8);
        if (found == null) {
            throw new IllegalStateException("underside not found");
        }
        BlockPos p = found.getFirst();
        Vec3 guess = new Vec3(p.getX() + 0.5, midY, p.getZ() + 0.5);
        return Plan.at(Level.END, guess.add(0, -10, -12), guess).decorate((s, plan) -> {
            // find the densest ceiling near the biome sample: lowest solid block above the underside band
            BlockPos best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int dx = -40; dx <= 40; dx += 4) {
                for (int dz = -40; dz <= 40; dz += 4) {
                    int x = p.getX() + dx, z = p.getZ() + dz;
                    int top = end.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    if (top < 50) {
                        continue;
                    }
                    int ceil = firstSolidAbove(end, x, WFEndBiomeSource.UNDERSIDE_MIN_Y - 20, z, top);
                    int score = (top - ceil) - Math.abs(dx) / 4 - Math.abs(dz) / 4;
                    if (ceil > 0 && score > bestScore) {
                        bestScore = score;
                        best = new BlockPos(x, ceil, z);
                    }
                }
            }
            if (best == null) {
                return plan;
            }
            Vec3 t = Vec3.atCenterOf(best).add(0, 2, 0);
            spawn(end, WFEntities.UNDERSIDE_LURKER.get(), t.add(3, -6, 4), 30, false);
            Vec3 cam = t.add(-26, -11, 8);
            return plan.frame(cam, t.add(2, -2, 2)).stills(List.of(t.add(-14, -16, -10)));
        });
    }

    private static Plan driftScene(Ctx c) {
        ServerLevel end = c.end();
        tide(c, "high");
        Pair<BlockPos, Holder<Biome>> found = end.findClosestBiome3d(h -> h.is(WFBiomes.THE_DRIFT), new BlockPos(1500, 64, 300), 6000, 32, 64);
        BlockPos p = found == null ? new BlockPos(2200, 64, 400) : found.getFirst();
        double tide = Tides.height(end);
        Vec3 t = new Vec3(p.getX() + 0.5, tide + 4, p.getZ() + 0.5);
        Vec3 cam = t.add(-30, 10, -24);
        return Plan.at(Level.END, cam, t).decorate((s, plan) -> {
            double ty = Tides.height(end);
            Vec3 tt = new Vec3(t.x, ty + 4, t.z);
            spawn(end, WFEntities.VOID_SKATE.get(), tt.add(2, 3, 0), 40, false);
            spawn(end, WFEntities.VOID_SKATE.get(), tt.add(-6, 5, 8), 70, false);
            spawnAround(end, WFEntities.TIDEWISP.get(), tt.add(0, -1, 0), 4, 7, false);
            return plan.frame(tt.add(-30, 9, -24), tt);
        }).settle(40);
    }

    interface Studio {
        Vec3[] build(ServerLevel level, Vec3 center);
    }

    /** A small floating stage in open void (where vanilla never generates terrain), lit with lanterns. */
    static Plan studioScene(Ctx c, BlockPos origin, boolean floor, Studio studio) {
        tide(c, "rising");
        Vec3 center = Vec3.atBottomCenterOf(origin);
        return Plan.at(Level.END, center.add(-10, 6, -10), center).decorate((s, plan) -> {
            ServerLevel l = c.end();
            if (floor) {
                buildStage(l, origin);
            }
            Vec3[] cams = studio.build(l, center);
            return plan.frame(cams[0], cams[1]);
        });
    }

    /**
     * Armor trims (§9.11), an upgraded elytra (§9.7) and a Hatchling Wyrm riding a shoulder (§10.3), on mannequins,
     * with a Stillwater cauldron and a Stillglass screen (§6.2, §7.5) beside them.
     */
    private static Vec3[] wardrobe(ServerLevel l, Vec3 center) {
        var mats = l.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
        var pats = l.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
        record Look(net.minecraft.world.item.Item[] armor, String pattern, String material, double x, double z, float yaw, boolean wyrm) {}
        net.minecraft.world.item.Item[] iron = {net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.IRON_CHESTPLATE,
            net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.IRON_BOOTS};
        net.minecraft.world.item.Item[] diamond = {net.minecraft.world.item.Items.DIAMOND_HELMET, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
            net.minecraft.world.item.Items.DIAMOND_LEGGINGS, net.minecraft.world.item.Items.DIAMOND_BOOTS};
        net.minecraft.world.item.Item[] netherite = {net.minecraft.world.item.Items.NETHERITE_HELMET, net.minecraft.world.item.Items.NETHERITE_CHESTPLATE,
            net.minecraft.world.item.Items.NETHERITE_LEGGINGS, net.minecraft.world.item.Items.NETHERITE_BOOTS};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        List<Look> looks = List.of(
            new Look(iron, "spire", "lumen", -1.9, 0.2, 18, true),
            new Look(netherite, "drift", "still", 0.0, -0.4, 0, false),
            new Look(diamond, "undersong", "frill", 1.9, 0.2, -18, false));
        for (Look look : looks) {
            var m = spawn(l, net.minecraft.world.entity.EntityTypes.MANNEQUIN, center.add(look.x, 0, look.z), look.yaw, false);
            if (m == null) {
                continue;
            }
            var trim = new net.minecraft.world.item.equipment.trim.ArmorTrim(
                mats.getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.fromNamespaceAndPath(Worldsfall.MODID, look.material))),
                pats.getOrThrow(ResourceKey.create(Registries.TRIM_PATTERN, Identifier.fromNamespaceAndPath(Worldsfall.MODID, look.pattern))));
            for (int i = 0; i < 4; i++) {
                net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(look.armor[i]);
                st.set(net.minecraft.core.component.DataComponents.TRIM, trim);
                m.setItemSlot(slots[i], st);
            }
            if (look.wyrm) {
                m.setData(dev.worldsfall.registry.WFAttachments.WYRM_SHOULDERS, 1);
            }
        }
        // an upgraded elytra, shown from behind on a fourth mannequin
        var flyer = spawn(l, net.minecraft.world.entity.EntityTypes.MANNEQUIN, center.add(-3.4, 0, 1.0), 142, false);
        if (flyer != null) {
            flyer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                dev.worldsfall.item.ElytraUpgrade.GALE.applyTo(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA)));
            flyer.setData(dev.worldsfall.registry.WFAttachments.WYRM_SHOULDERS, 2);
        }
        BlockPos o = BlockPos.containing(center);
        l.setBlockAndUpdate(o.offset(3, 0, 1), WFBlocks.STILLWATER_CAULDRON.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        for (int y = 0; y < 3; y++) {
            for (int x = 2; x <= 4; x++) {
                l.setBlockAndUpdate(o.offset(x, y, -3), WFBlocks.STILLGLASS.get().defaultBlockState());
            }
        }
        // a shelf of trimmed armor icons (the item models carry the mod's trim materials too)
        String[][] icons = {{"iron_chestplate", "lumen", "spire"}, {"netherite_helmet", "still", "drift"}, {"diamond_leggings", "frill", "undersong"},
            {"leather_chestplate", "lumen", "drift"}, {"golden_boots", "frill", "spire"}, {"chainmail_chestplate", "still", "undersong"}};
        for (int i = 0; i < icons.length; i++) {
            String cmd = String.format(java.util.Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"minecraft:%s\",count:1,components:{\"minecraft:trim\":{material:\"worldsfall:%s\","
                    + "pattern:\"worldsfall:%s\"}}},item_display:\"gui\",Rotation:[0f,0f],brightness:{sky:15,block:15},"
                    + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.9f,0.9f,0.1f]}}",
                center.x - 2.6 + i * 1.04, center.y + 3.1, center.z - 2.4, icons[i][0], icons[i][1], icons[i][2]);
            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withLevel(l).withSuppressedOutput(), cmd);
        }
        return new Vec3[] {center.add(-0.9, 1.9, 4.9), center.add(-0.6, 1.5, -0.4)};
    }

    static void buildStage(ServerLevel l, BlockPos o) {
        BlockState top = WFBlocks.POLISHED_STILLSTONE.get().defaultBlockState();
        BlockState rim = WFBlocks.STILLSTONE_BRICKS.get().defaultBlockState();
        BlockState under = WFBlocks.STILLSTONE.get().defaultBlockState();
        int r = 7;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.5) {
                    continue;
                }
                l.setBlockAndUpdate(o.offset(dx, -1, dz), d > r ? rim : top);
                int depth = (int) Math.max(1, (r + 1.5 - d) * 1.4);
                for (int y = 2; y <= depth + 1; y++) {
                    l.setBlockAndUpdate(o.offset(dx, -y, dz), under);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            BlockPos post = o.offset((int) Math.round(Math.cos(a) * 6), 0, (int) Math.round(Math.sin(a) * 6));
            l.setBlockAndUpdate(post, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "stillstone_brick_wall")).defaultBlockState());
            l.setBlockAndUpdate(post.above(), WFBlocks.SPIRE_LANTERN.get().defaultBlockState());
        }
    }

    private static Plan creatureLineup(Ctx c) {
        BlockPos origin = new BlockPos(0, 110, -760);
        Vec3 center = Vec3.atBottomCenterOf(origin);
        return Plan.at(Level.END, center.add(0, 4, -14), center).decorate((s, plan) -> {
            ServerLevel l = c.end();
            buildStage(l, origin);
            buildStage(l, origin.offset(14, 0, 0));
            buildStage(l, origin.offset(-14, 0, 0));
            List<EntityType<? extends Mob>> row = List.of(WFEntities.CHORUS_GRAZER.get(), WFEntities.HOARDER.get(), WFEntities.SPIRE_WARDEN.get(),
                WFEntities.ECHO.get(), WFEntities.WILD_SHULKER.get(), WFEntities.SHARDLING.get(), WFEntities.TIDEWISP.get());
            double x0 = -15;
            for (int i = 0; i < row.size(); i++) {
                spawn(l, row.get(i), center.add(x0 + i * 5, 0, 0), 0, true);
            }
            Vec3 cam = center.add(x0, 3.2, -9);
            Vec3 end = center.add(x0 + (row.size() - 1) * 5, 3.2, -9);
            Plan framed = plan.frame(cam, center.add(x0, 1.4, 0)).stills(List.of(center.add(0, 9, -26)));
            int frames = 260;
            return framed.video(frames, (f, p) -> cam.lerp(end, ease(f / (double) frames)),
                (f, p) -> center.add(x0, 1.4, 0).lerp(center.add(-x0, 1.4, 0), ease(f / (double) frames)));
        }).settle(40);
    }

    private static Plan itemWall(Ctx c) {
        BlockPos origin = new BlockPos(-760, 110, -760);
        Vec3 center = Vec3.atBottomCenterOf(origin);
        String[] items = {"void_anchor", "rift_tether", "gateway_compass", "shulker_satchel", "void_lens", "spire_key", "rift_key",
            "echo_tablet", "heart_of_stillness", "slumbering_egg", "bottled_tidewisp", "gale_upgrade_template", "still_upgrade_template",
            "hover_upgrade_template", "spire_armor_trim_smithing_template", "drift_armor_trim_smithing_template",
            "undersong_armor_trim_smithing_template", "tide_sigil", "under_sigil", "spire_sigil", "music_disc_unmade"};
        return Plan.at(Level.END, center.add(0, 3, -9), center.add(0, 3, 0)).decorate((s, plan) -> {
            ServerLevel l = c.end();
            buildStage(l, origin);
            BlockState back = WFBlocks.POLISHED_STILLSTONE.get().defaultBlockState();
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = 0; dy <= 5; dy++) {
                    l.setBlockAndUpdate(origin.offset(dx, dy, 2), dy == 5 || Math.abs(dx) == 6 ? WFBlocks.STILLSTONE_BRICKS.get().defaultBlockState() : back);
                }
            }
            int perRow = 7;
            for (int i = 0; i < items.length; i++) {
                int col = i % perRow, rowI = i / perRow;
                double x = center.x - (col - (perRow - 1) / 2.0) * 1.7; // camera looks south: west is screen-right
                double y = center.y + 4.1 - rowI * 1.45;
                String cmd = String.format(java.util.Locale.ROOT,
                    "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"worldsfall:%s\",count:1},item_display:\"gui\",Rotation:[0f,0f],"
                        + "brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,0f,0f],scale:[1.25f,1.25f,0.1f]}}",
                    x, y, center.z + 1.45, items[i]);
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withLevel(l).withSuppressedOutput(), cmd);
            }
            Vec3 t = center.add(0, 2.65, 1.4);
            return plan.frame(center.add(0, 2.65, -6.6), t).video(140, (f, p) -> center.add(-2.5 + 5 * ease(f / 140.0), 2.9, -7.2), (f, p) -> t);
        });
    }

    private static Plan sunderingScene(Ctx c) {
        ServerLevel end = c.end();
        tide(c, "rising");
        int frames = 340;
        Vec3 rough = new Vec3(0.5, 110, 0.5);
        return Plan.at(Level.END, rough.add(60, 0, 40), rough).decorate((s, plan) -> {
            // the real trigger (§5.4): the dragon dies. Ending the fight also lifts its boss fog.
            var fight = end.getDragonFight();
            for (net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon : List.copyOf(end.getDragons())) {
                if (fight != null) {
                    fight.setDragonKilled(dragon);
                }
                dragon.discard();
            }
            SunderingData data = SunderingData.get(s);
            BlockPos d = Sundering.chooseDescent(end); // deterministic: the same spot start() will crack open
            if (d == null || data.done() || !Sundering.start(end, data)) {
                return plan;
            }
            Vec3 t = new Vec3(d.getX() + 0.5, d.getY() - 2, d.getZ() + 0.5);
            // look back across the crack toward the exit portal, from just outside the ring of spikes
            double a0 = Math.atan2(d.getZ(), d.getX());
            Vec3 cam = new Vec3(Math.cos(a0) * 72, t.y + 24, Math.sin(a0) * 72);
            return plan.frame(cam, t).video(frames, (f, p) -> {
                double e = ease(f / (double) frames);
                double a = a0 + Math.toRadians(-18 + 36 * e);
                double r = 72 - 14 * e;
                return new Vec3(Math.cos(a) * r, t.y + 24 - 8 * e, Math.sin(a) * r);
            }, null);
        }).settle(20);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    static void tide(Ctx c, String phase) {
        c.server().getCommands().performPrefixedCommand(c.server().createCommandSourceStack().withSuppressedOutput(), "worldsfall tide set " + phase);
    }

    /** Average solid surface around the best column near {@code guess} (optionally inside {@code biome}). */
    private static Vec3 islandCenter(ServerLevel l, BlockPos guess, @Nullable ResourceKey<Biome> biome, int radius) {
        double sx = 0, sy = 0, sz = 0, wsum = 0;
        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                int x = guess.getX() + dx, z = guess.getZ() + dz;
                int y = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (y < 30 || (biome != null && !l.getBiome(new BlockPos(x, y - 1, z)).is(biome))) {
                    continue;
                }
                double w = 1.0 / (1 + (dx * dx + dz * dz) / 900.0);
                sx += x * w;
                sy += y * w;
                sz += z * w;
                wsum += w;
            }
        }
        if (wsum == 0) {
            return new Vec3(guess.getX() + 0.5, 70, guess.getZ() + 0.5);
        }
        double cx = sx / wsum, cz = sz / wsum;
        int cy = l.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(cx), Mth.floor(cz));
        return new Vec3(cx, Math.max(cy, sy / wsum) + 1.5, cz);
    }

    /** Picks the least-obstructed of 12 azimuths at the given distance/height and frames the target. */
    private static Plan frameIsland(ServerLevel l, Plan plan, Vec3 target, double dist, double height) {
        Vec3 best = null;
        int bestHits = Integer.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            Vec3 cam = target.add(Math.cos(a) * dist, height, Math.sin(a) * dist);
            int hits = obstruction(l, cam, target) * 4 + (l.getBlockState(BlockPos.containing(cam)).isAir() ? 0 : 100);
            if (hits < bestHits) {
                bestHits = hits;
                best = cam;
            }
        }
        Vec3 cam = best == null ? target.add(dist, height, 0) : best;
        Vec3 alt = target.add((target.x - cam.x) * 0.75, height * 1.3, (target.z - cam.z) * 0.75);
        return plan.frame(cam, target).stills(List.of(alt));
    }

    private static int obstruction(ServerLevel l, Vec3 from, Vec3 to) {
        int hits = 0;
        Vec3 d = to.subtract(from);
        double len = d.length();
        for (double s = 0; s < len - 3; s += 0.75) {
            if (!l.getBlockState(BlockPos.containing(from.add(d.scale(s / len)))).isAir()) {
                hits++;
            }
        }
        return hits;
    }

    private static int firstSolidAbove(ServerLevel l, int x, int fromY, int z, int maxY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = Math.max(fromY, l.getMinY()); y < maxY; y++) {
            if (!l.getBlockState(m.set(x, y, z)).isAir()) {
                return y;
            }
        }
        return -1;
    }

    private static Plan aimAtStillwater(MinecraftServer server, Plan plan) {
        ServerLevel l = server.getLevel(Level.END);
        if (l == null) {
            return plan;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Vec3 t = plan.target();
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (int dx = -48; dx <= 48; dx += 2) {
            for (int dz = -48; dz <= 48; dz += 2) {
                int x = Mth.floor(t.x) + dx, z = Mth.floor(t.z) + dz;
                int y = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (y > 20 && l.getBlockState(m.set(x, y, z)).is(WFBlocks.STILLWATER.get())) {
                    sx += x;
                    sy += y;
                    sz += z;
                    n++;
                }
            }
        }
        if (n < 6) {
            return plan;
        }
        Vec3 lake = new Vec3(sx / n + 0.5, sy / n + 1, sz / n + 0.5);
        double r = Math.max(14, Math.sqrt(n) * 2.6);
        return frameIsland(l, plan, lake, r + 8, 5 + r * 0.2);
    }

    private static void spawnAround(ServerLevel l, EntityType<? extends Mob> type, Vec3 center, int count, double radius, boolean noAi) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + 0.4;
            double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            double yy = type == WFEntities.TIDEWISP.get() || y < 10 ? center.y + (i % 2) : y;
            spawn(l, type, new Vec3(x, yy, z), (float) Math.toDegrees(a) + 90, noAi);
        }
    }

    static <T extends Entity> @Nullable T spawn(ServerLevel l, EntityType<T> type, Vec3 pos, float yaw, boolean noAi) {
        T e = type.create(l, EntitySpawnReason.COMMAND);
        if (e == null) {
            return null;
        }
        e.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
        if (e instanceof Mob mob) {
            mob.setNoAi(noAi);
            if (noAi) {
                mob.setNoGravity(true); // posed showcase subjects hold their place (fliers would otherwise sink)
            }
            mob.setPersistenceRequired();
            mob.setYHeadRot(yaw);
            mob.yBodyRot = yaw;
        }
        l.addFreshEntity(e);
        return e;
    }

}
