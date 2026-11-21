package dev.worldsfall.client.showcase;

import dev.worldsfall.client.showcase.ShowcaseScenes.Ctx;
import dev.worldsfall.client.showcase.ShowcaseScenes.Plan;
import dev.worldsfall.client.showcase.ShowcaseScenes.Scene;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.gap.Causeway;
import dev.worldsfall.gap.CausewayData;
import dev.worldsfall.life.WorldEvents;
import dev.worldsfall.nav.Charts;
import dev.worldsfall.nav.DriftHelmBlock;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.restoration.Restoration;
import dev.worldsfall.restoration.RestorationWorld;
import dev.worldsfall.undertow.SunkenArchive;
import dev.worldsfall.undertow.VoidBell;
import dev.worldsfall.warden.Pact;
import dev.worldsfall.warden.Wardensong;
import dev.worldsfall.world.gen.ZenithShoalFeature;
import dev.worldsfall.world.gen.structure.Blueprint;
import dev.worldsfall.world.gen.structure.OrreryGenerator;
import dev.worldsfall.world.sundering.UndercroftBuilder;
import dev.worldsfall.zenith.Orreries;
import dev.worldsfall.zenith.SkyCurrents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Showcase pictures for the expansion (releases 1.2 → 2.0): each feature staged, framed and filmed. */
final class ExpansionScenes {
    private ExpansionScenes() {}

    static List<Scene> all() {
        return List.of(
            new Scene("x_warden_cycle", ExpansionScenes::wardenCycle),
            new Scene("x_elder_warden", ExpansionScenes::elderWarden),
            new Scene("x_causeway", ExpansionScenes::causeway),
            new Scene("x_cities_and_charts", ExpansionScenes::citiesAndCharts),
            new Scene("x_living_blocks", ExpansionScenes::livingBlocks),
            new Scene("x_starfall", c -> worldEvent(c, WorldEvents.STARFALL, 260)),
            new Scene("x_eclipse", c -> worldEvent(c, WorldEvents.ECLIPSE, 260)),
            new Scene("x_migration", c -> worldEvent(c, WorldEvents.MIGRATION, 30)),
            new Scene("x_zenith", ExpansionScenes::zenith),
            new Scene("x_orrery", c -> orrery(c, false)),
            new Scene("x_orrery_aligned", c -> orrery(c, true)),
            new Scene("x_undertow", ExpansionScenes::undertow),
            new Scene("x_items", ExpansionScenes::itemWall),
            new Scene("x_restoration_mural", c -> restoration(c, 0)),
            new Scene("x_trophy_hall", c -> restoration(c, 1)),
            new Scene("x_overmoon", c -> restoration(c, 2))
        );
    }

    // ------------------------------------------------------------------ 1.2 the Warden Cycle

    private static Plan wardenCycle(Ctx c) {
        return ShowcaseScenes.studioScene(c, new BlockPos(0, 120, 1500), true, (l, center) -> {
            EnderDragon dragon = ShowcaseScenes.spawn(l, EntityTypes.ENDER_DRAGON, center.add(0, 9, 4), 180, true);
            if (dragon != null) {
                dragon.setCustomName(net.minecraft.network.chat.Component.translatable("entity.worldsfall.warden_dragon", 7));
            }
            Pact[] pacts = {Pact.TIDE, Pact.SHARDS, Pact.HOARD, Pact.DEPTHS};
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                EndCrystal crystal = new EndCrystal(l, center.x + Math.cos(a) * 5.5, center.y, center.z + Math.sin(a) * 5.5);
                crystal.setData(WFAttachments.CRYSTAL_KIND, "pact:" + pacts[i].id());
                l.addFreshEntity(crystal);
            }
            ShowcaseScenes.spawn(l, WFEntities.LUMEN_HEART.get(), center.add(-3, 0, -4), 0, true);
            l.setBlockAndUpdate(BlockPos.containing(center.add(3, 0, -4)), WFBlocks.WARDENS_LANTERN.get().defaultBlockState());
            return new Vec3[] {center.add(-17, 7, -15), center.add(0, 5, 2)};
        }).orbit(200, 80).settle(40);
    }

    private static Plan elderWarden(Ctx c) {
        return ShowcaseScenes.studioScene(c, new BlockPos(-1500, 120, 0), true, (l, center) -> {
            var elder = ShowcaseScenes.spawn(l, WFEntities.ELDER_WARDEN.get(), center.add(0, 8, 0), 210, false);
            if (elder != null) {
                elder.setPerch(BlockPos.containing(center.add(0, 2, 0)));
            }
            Wyrm drake = ShowcaseScenes.spawn(l, WFEntities.WYRM.get(), center.add(-5, 0, -4), 40, true);
            if (drake != null) {
                drake.setStage(Wyrm.Stage.DRAKE);
                drake.setLook(Wyrm.LOOK_WARDENBORN, true);
                drake.setLook(Wyrm.LOOK_BARDED, true);
                drake.setLook(Wyrm.LOOK_BAGS, true);
            }
            return new Vec3[] {center.add(-16, 5, -14), center.add(0, 4, 0)};
        }).orbit(200, 70).settle(40);
    }

    // ------------------------------------------------------------------ 1.3 the Gap

    private static Plan causeway(Ctx c) {
        ServerLevel end = c.end();
        ShowcaseScenes.tide(c, "low");
        CausewayData data = CausewayData.get(c.server());
        Causeway.ensurePlanned(end, data);
        CausewayData.Segment seg = data.segments().get(1);
        double a = data.angle();
        BlockPos mid = Causeway.at(a, (seg.r0() + seg.r1()) / 2.0, 0, seg.y());
        Vec3 target = Vec3.atCenterOf(mid);
        Vec3 cam = Vec3.atCenterOf(Causeway.at(a, seg.r0() - 9, 7, seg.y() + 5));
        return Plan.at(Level.END, cam, target).decorate((s, plan) -> {
            for (int i = 0; i < 4; i++) {
                Causeway.tick(end);
            }
            ServerLevel l = c.end();
            // a Drift Buoy line marking the way across the next gap, with a Skate perched on one
            for (int k = 0; k < 3; k++) {
                BlockPos b = Causeway.at(a, seg.r1() + 8 + k * 10, -4, seg.y() + 2);
                l.setBlockAndUpdate(b, WFBlocks.DRIFT_BUOY.get().defaultBlockState());
            }
            VoidSkate sk = ShowcaseScenes.spawn(l, WFEntities.VOID_SKATE.get(), Vec3.atBottomCenterOf(Causeway.at(a, seg.r1() + 18, -4, seg.y() + 3)), 0, true);
            if (sk != null) {
                sk.setPerched(true);
            }
            return plan;
        }).orbit(220, 50).settle(60);
    }

    // ------------------------------------------------------------------ 1.4 cities and navigation

    private static Plan citiesAndCharts(Ctx c) {
        return ShowcaseScenes.studioScene(c, new BlockPos(1500, 120, -1500), true, (l, center) -> {
            BlockPos o = BlockPos.containing(center);
            // a purpur gallery wall with three charts framed on it
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    l.setBlockAndUpdate(o.offset(dx, dy, 5), dy == 4 || Math.abs(dx) == 5 ? Blocks.PURPUR_PILLAR.defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
                }
            }
            ItemStack[] charts = {Orreries.starChart(l, new BlockPos(2400, 64, 300)), Charts.cityChart(l, l.players().getFirst()),
                Charts.create(l, 2400, 300, 2, true, "void")};
            for (int i = 0; i < charts.length; i++) {
                if (charts[i] == null) {
                    continue;
                }
                ItemFrame frame = new ItemFrame(l, o.offset(-3 + i * 3, 2, 4), Direction.NORTH);
                frame.setItem(charts[i], false);
                l.addFreshEntity(frame);
            }
            ShowcaseScenes.spawn(l, WFEntities.SHULKER_ARCHIVIST.get(), center.add(-3, 0, 1), 0, true);
            l.setBlockAndUpdate(o.offset(3, 0, 1), WFBlocks.DRIFT_HELM.get().defaultBlockState().setValue(DriftHelmBlock.MOORED, true)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            l.setBlockAndUpdate(o.offset(5, 0, -1), WFBlocks.SHIPS_BELL.get().defaultBlockState());
            return new Vec3[] {center.add(0, 3.2, -6.5), center.add(0, 2, 3)};
        }).stills(List.of(new Vec3(1500.5 - 5, 123.5, -1500.5 - 4))).orbit(160, 50).settle(60);
    }

    // ------------------------------------------------------------------ 1.5 living systems

    private static Plan livingBlocks(Ctx c) {
        return ShowcaseScenes.studioScene(c, new BlockPos(-1500, 120, 1500), true, (l, center) -> {
            BlockPos o = BlockPos.containing(center);
            BlockState[] row = {WFBlocks.TIDE_BELL.get().defaultBlockState(), WFBlocks.GRAVITY_PLATE.get().defaultBlockState()
                .setValue(dev.worldsfall.life.GravityPlateBlock.POWERED, true), WFBlocks.COURIER_CACHE.get().defaultBlockState(),
                WFBlocks.ANCHOR_FRAME_LAMP.get().defaultBlockState(), WFBlocks.ANCHOR_FRAME_STAIRS.get().defaultBlockState(),
                WFBlocks.STARFALL_SHARD.get().defaultBlockState(), WFBlocks.TIDEWRACK.get().defaultBlockState(),
                WFBlocks.LANTERN_MOTH_JAR.get().defaultBlockState()};
            for (int i = 0; i < row.length; i++) {
                l.setBlockAndUpdate(o.offset(-7 + i * 2, 0, 2), row[i]);
            }
            l.setBlockAndUpdate(o.offset(-2, 0, -3), WFBlocks.WYRM_ROOST.get().defaultBlockState());
            Wyrm w = ShowcaseScenes.spawn(l, WFEntities.WYRM.get(), center.add(-1.5, 0.4, -2.5), 150, true);
            if (w != null) {
                w.setStage(Wyrm.Stage.FLEDGLING);
                w.setInSittingPose(true);
            }
            l.setBlockAndUpdate(o.offset(3, 0, -3), WFBlocks.SPIRE_LANTERN.get().defaultBlockState());
            l.setBlockAndUpdate(o.offset(4, 2, -4), WFBlocks.CAUSEWAY_LANTERN.get().defaultBlockState());
            for (int i = 0; i < 4; i++) {
                ShowcaseScenes.spawn(l, WFEntities.LANTERN_MOTH.get(), center.add(3 + Math.cos(i * 1.6) * 1.4, 1 + i * 0.3, -3 + Math.sin(i * 1.6) * 1.4),
                    i * 90, false);
            }
            return new Vec3[] {center.add(1, 4.5, -10.5), center.add(0, 0.8, 0)};
        }).orbit(200, 70).settle(40);
    }

    private static Plan worldEvent(Ctx c, int event, int settle) {
        ServerLevel end = c.end();
        ShowcaseScenes.tide(c, "low");
        WorldEvents.stop(end);
        Vec3 island = new Vec3(1250.5, 80, 250.5);
        Vec3 cam = island.add(-24, 22, -24);
        return Plan.at(Level.END, cam, island).decorate((s, plan) -> {
            // events choose a player who is really there; the camera is a spectator, so step in for the moment it starts
            c.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            WorldEvents.start(end, event);
            c.player().setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            var data = dev.worldsfall.life.WorldEventData.get(s);
            if (event == WorldEvents.STARFALL) {
                // look down on the landing site from above and to the side, so the streak comes in over the island
                Vec3 t = Vec3.atCenterOf(data.site());
                return plan.frame(t.add(-16, 24, -16), t);
            }
            if (event == WorldEvents.MIGRATION) {
                // bring the flock in at once and film it from ahead, as it passes
                dev.worldsfall.life.Migration.spawnFlock(end, data);
                var flock = end.getEntitiesOfClass(VoidSkate.class, new net.minecraft.world.phys.AABB(BlockPos.containing(cam)).inflate(200),
                    VoidSkate::isMigrating);
                if (!flock.isEmpty()) {
                    Vec3 centre = flock.stream().map(VoidSkate::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / flock.size());
                    Vec3 dir = flock.getFirst().migrationTarget().subtract(flock.getFirst().position()).multiply(1, 0, 1).normalize();
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    Vec3 eye = centre.add(dir.scale(36)).add(side.scale(18)).add(0, -6, 0);
                    return plan.frame(eye, centre.add(dir.scale(10)));
                }
            }
            if (event == WorldEvents.ECLIPSE) {
                // a low view across the island: the darkened sky above, memories walking on the island top in front of us
                Vec3 t = island.add(0, 2, 0);
                java.util.List<BlockPos> tops = new java.util.ArrayList<>();
                for (int r = 0; r < 40 && tops.size() < 4; r += 2) {
                    for (int k = 0; k < 8 && tops.size() < 4; k++) {
                        int x = (int) t.x + (int) (Math.cos(k * 0.785) * r), z = (int) t.z + (int) (Math.sin(k * 0.785) * r);
                        int y = end.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        BlockPos top = new BlockPos(x, y, z);
                        if (y > end.getMinY() + 20 && tops.stream().allMatch(o -> o.distSqr(top) > 9)) {
                            tops.add(top);
                        }
                    }
                }
                for (int i = 0; i < tops.size(); i++) {
                    var echo = WFEntities.ECHO.get().create(end, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    if (echo != null) {
                        echo.makeMemory();
                        echo.snapTo(tops.get(i).getX() + 0.5, tops.get(i).getY(), tops.get(i).getZ() + 0.5, 200 + i * 40, 0);
                        end.addFreshEntity(echo);
                    }
                }
                if (!tops.isEmpty()) {
                    Vec3 f = Vec3.atBottomCenterOf(tops.getFirst());
                    return plan.frame(f.add(-9, 5, -9), f.add(0, 1.5, 0));
                }
                return plan.frame(t.add(-12, 7, -12), t.add(0, 3, 0));
            }
            return plan;
        }).orbit(160, 40).settle(settle);
    }

    // ------------------------------------------------------------------ 1.6 the Zenith

    private static Plan zenith(Ctx c) {
        ServerLevel end = c.end();
        long seed = end.getSeed();
        SkyCurrents.Current current = null;
        for (int rx = 2; rx < 9 && current == null; rx++) {
            current = SkyCurrents.currentOf(seed, rx, 1);
        }
        Vec3 p = current == null ? new Vec3(3600, 200, 2300) : current.points().get(3);
        Vec3 cam = p.add(-34, 6, -30);
        return Plan.at(Level.END, cam, p).decorate((s, plan) -> {
            ServerLevel l = c.end();
            BlockPos shoal = BlockPos.containing(p.add(-6, -14, 10));
            new ZenithShoalFeature().place(l, l.getChunkSource().getGenerator(), RandomSource.create(7), shoal);
            ShowcaseScenes.spawn(l, WFEntities.LEVIATHAN_DRIFTER.get(), p.add(12, -8, 30), 200, true);
            for (int i = 0; i < 2; i++) {
                ShowcaseScenes.spawn(l, WFEntities.GALE_HARRIER.get(), p.add(-4 + i * 8, 10, 6), i * 120, false);
            }
            return plan.frame(cam, p.add(4, -6, 14));
        }).orbit(220, 60).settle(80);
    }

    private static Plan orrery(Ctx c, boolean align) {
        ServerLevel end = c.end();
        BlockPos lens = new BlockPos(6200, 226, 600);
        Vec3 target = Vec3.atCenterOf(lens);
        Vec3 cam = target.add(-42, 12, -30);
        return Plan.at(Level.END, cam, target).decorate((s, plan) -> {
            ServerLevel l = c.end();
            if (!l.getBlockState(lens).is(WFBlocks.ORRERY_LENS.get())) {
                Blueprint bp = OrreryGenerator.build(lens, 0, RandomSource.create(11));
                BoundingBox box = bp.bounds();
                for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                    for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                        l.getChunk(cx, cz);
                        bp.place(l, new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15));
                    }
                }
            }
            if (align) {
                for (int i = 0; i < 3; i++) {
                    Orreries.freeRing(l, OrreryGenerator.socketPos(lens, i), i, c.player());
                }
                // the new star: look past the aligned machine toward where it now burns
                return plan.frame(target.add(-46, 4, 8), target.add(30, 40, 0));
            }
            return plan;
        }).orbit(220, 90).settle(align ? 520 : 60);
    }

    // ------------------------------------------------------------------ 2.0 the Undertow and the Restoration

    private static Plan undertow(Ctx c) {
        ServerLevel u = c.level(WFDimensions.THE_UNDERTOW);
        Vec3 maw = new Vec3(3000, 0, 3000);
        BlockPos centre = SunkenArchive.centre(maw);
        Vec3 target = Vec3.atCenterOf(centre).add(0, 2, 0);
        Vec3 cam = target.add(-30, 16, -26);
        return Plan.at(WFDimensions.THE_UNDERTOW, cam, target).decorate((s, plan) -> {
            SunkenArchive.ensure(u, maw);
            VoidBell bell = ShowcaseScenes.spawn(u, WFEntities.VOID_BELL.get(), target.add(-10, 8, -6), 40, false);
            if (bell != null) {
                bell.addCharge(VoidBell.CHARGE_PER_FRAGMENT);
                bell.setLamp(true);
            }
            for (int i = 0; i < 2; i++) {
                ShowcaseScenes.spawn(u, WFEntities.TIDE_EEL.get(), target.add(-10 + i * 3, 10, -3), i * 120, false);
            }
            for (int i = 0; i < 12; i++) {
                ShowcaseScenes.spawn(u, WFEntities.GLIMMER.get(), target.add(6 + Math.cos(i) * 2, 6 + Math.sin(i * 1.3), -8 + Math.sin(i) * 2), i * 30, false);
            }
            return plan;
        }).orbit(240, 90).settle(80);
    }

    private static Plan restoration(Ctx c, int view) {
        ServerLevel end = c.end();
        if (Restoration.fraction(c.server()) < 1.0) {
            for (int i = 0; i < 40; i++) {
                Restoration.award(c.server(), c.player(), Restoration.Act.ORRERY_ALIGN, "showcase" + i);
            }
        }
        RestorationWorld.noteCleared(c.server(), java.util.EnumSet.of(Pact.TIDE, Pact.SHARDS, Pact.DEPTHS, Pact.LONE));
        UndercroftBuilder.Layout l = UndercroftBuilder.layout(Wardensong.descent(end));
        if (view == 2) {
            ShowcaseScenes.tide(c, "low");
            Vec3 cam = new Vec3(40.5, 75, 30.5);
            return Plan.at(Level.END, cam, new Vec3(0.5, 300, 0.5)).stills(List.of(new Vec3(-30.5, 78, 60.5))).settle(200);
        }
        BlockPos room = view == 0 ? RestorationWorld.archiveCentre(l) : RestorationWorld.trophyCentre(l);
        Direction side = l.inward().getClockWise();
        Vec3 target = Vec3.atCenterOf(room).add(0, 1.5, 0);
        Vec3 cam = view == 0 ? Vec3.atCenterOf(room.relative(l.inward(), -4).relative(side, -3)).add(0, 2.2, 0)
            : Vec3.atCenterOf(room.relative(side, -5).relative(l.inward(), -2)).add(0, 2.5, 0);
        Vec3 look = view == 0 ? target.add(l.inward().getStepX() * 5, 1, l.inward().getStepZ() * 5) : target;
        return Plan.at(Level.END, cam, look).decorate((s, plan) -> {
            RestorationWorld.tick(end);
            dev.worldsfall.world.BlockQueue.tickAll(end);
            return plan;
        }).settle(260);
    }

    private static Plan itemWall(Ctx c) {
        BlockPos origin = new BlockPos(-760, 110, 760);
        Vec3 center = Vec3.atBottomCenterOf(origin);
        String[] items = {"pact_crystal_tide", "pact_crystal_depths", "stillness_crystal", "dragon_scale", "wyrm_barding", "wardenborn_seal",
            "music_disc_warden", "causeway_keystone", "drift_buoy", "tuning_fork", "city_chart", "empty_void_chart", "mirrorfish", "driftsilt",
            "eclipse_dust", "courier_token", "harrier_plume", "leviathan_barnacle", "current_vane", "music_disc_ladders", "void_bell", "archive_codex",
            "abyssal_anchor", "undertow_pearl", "null_pigment", "music_disc_undertow", "bottled_lantern_moth", "halo_glass"};
        return Plan.at(Level.END, center.add(0, 3, -9), center.add(0, 3, 0)).decorate((s, plan) -> {
            ServerLevel l = c.end();
            ShowcaseScenes.buildStage(l, origin);
            BlockState back = WFBlocks.POLISHED_SKYSTONE.get().defaultBlockState();
            for (int dx = -7; dx <= 7; dx++) {
                for (int dy = 0; dy <= 6; dy++) {
                    l.setBlockAndUpdate(origin.offset(dx, dy, 2), dy == 6 || Math.abs(dx) == 7 ? WFBlocks.SKYSTONE_BRICKS.get().defaultBlockState() : back);
                }
            }
            int perRow = 7;
            for (int i = 0; i < items.length; i++) {
                int col = i % perRow, rowI = i / perRow;
                double x = center.x - (col - (perRow - 1) / 2.0) * 1.7;
                double y = center.y + 5.0 - rowI * 1.45;
                String cmd = String.format(java.util.Locale.ROOT,
                    "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"worldsfall:%s\",count:1},item_display:\"gui\",Rotation:[0f,0f],"
                        + "brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,0f,0f],scale:[1.25f,1.25f,0.1f]}}",
                    x, y, center.z + 1.45, items[i]);
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withLevel(l).withSuppressedOutput(), cmd);
            }
            Vec3 t = center.add(0, 3.0, 1.4);
            return plan.frame(center.add(0, 3.0, -7.2), t).video(140, (f, p) -> center.add(-2.5 + 5 * ShowcaseScenes.ease(f / 140.0), 3.2, -7.8), (f, p) -> t);
        });
    }
}
