package dev.worldsfall.client.showcase;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.worldsfall.client.ClientWorldState;
import dev.worldsfall.client.expansion.LatticeScreen;
import dev.worldsfall.client.hud.WFHud;
import dev.worldsfall.life.WorldEventData;
import dev.worldsfall.life.WorldEvents;
import dev.worldsfall.nav.LatticeData;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.restoration.Restoration;
import dev.worldsfall.restoration.RestorationWorld;
import dev.worldsfall.undertow.ArchiveCodexItem;
import dev.worldsfall.undertow.SunkenArchive;
import dev.worldsfall.undertow.TideMaws;
import dev.worldsfall.undertow.VoidBell;
import dev.worldsfall.warden.Pact;
import dev.worldsfall.warden.Wardensong;
import dev.worldsfall.world.gen.structure.Blueprint;
import dev.worldsfall.world.gen.structure.OrreryGenerator;
import dev.worldsfall.world.sundering.UndercroftBuilder;
import dev.worldsfall.world.tide.Tides;
import dev.worldsfall.zenith.LeviathanDrifter;
import dev.worldsfall.zenith.OrrerySocketBlock;
import dev.worldsfall.zenith.SkyCurrents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Real-client playtest (enabled only by {@code -Dworldsfall.playtest=<output dir>}, e.g. from a launcher profile's JVM
 * arguments). Unlike the showcase it plays as a survival player in first person: it presses the real use, movement, jump
 * and sneak keys, clicks screens, pilots and rides, then checks what happened on both sides and writes a report plus a
 * frame sequence per scenario (turned into clips by {@code tools/showcase/make_video.sh}).
 */
public final class Playtest {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String PROPERTY = "worldsfall.playtest";

    private static @Nullable Playtest instance;

    private final Path out;
    private final List<Scenario> scenarios = new ArrayList<>();
    private final List<String> only;
    private final ExecutorService writer = Executors.newFixedThreadPool(3);
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private final List<Result> results = new ArrayList<>();
    private final Map<String, Integer> sounds = new TreeMap<>();
    private final Set<String> missingSounds = new java.util.TreeSet<>();
    private boolean started, done;
    private int scenarioIndex = -1, actIndex, actTicks, frame, drain;
    private @Nullable CompletableFuture<@Nullable String> pending;
    private @Nullable UUID playerId;

    private Playtest(Path out, List<String> only) {
        this.out = out;
        this.only = only;
    }

    public static void init() {
        // a JVM property, or (for launcher profiles, whose JVM arguments the launcher may rewrite) a
        // worldsfall-playtest.properties file in the game folder with "out=<dir>" and optional "scenarios=a,b"
        java.util.Properties props = new java.util.Properties();
        Path file = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("worldsfall-playtest.properties");
        if (Files.isRegularFile(file)) {
            try (var in = Files.newBufferedReader(file)) {
                props.load(in);
            } catch (IOException e) {
                LOGGER.warn("[playtest] could not read {}", file, e);
            }
        }
        String dir = System.getProperty(PROPERTY, props.getProperty("out", ""));
        if (dir == null || dir.isBlank()) {
            return;
        }
        String o = System.getProperty(PROPERTY + ".scenarios", props.getProperty("scenarios", ""));
        instance = new Playtest(Path.of(dir).toAbsolutePath(), o.isBlank() ? List.of() : Arrays.stream(o.split(",")).map(String::trim).toList());
        NeoForge.EVENT_BUS.register(instance);
        LOGGER.info("[playtest] enabled -> {}", instance.out);
    }

    // ================================================================== plumbing

    record Result(String scenario, String check, boolean pass, String detail) {}

    /** One step of a scenario; returns true when finished. */
    @FunctionalInterface
    interface Act {
        boolean tick(Minecraft mc, int t);
    }

    static final class Scenario {
        final String id;
        final boolean record;
        final CameraType camera;
        final List<Act> acts = new ArrayList<>();
        boolean aborted;
        int frames;

        Scenario(String id, boolean record, CameraType camera) {
            this.id = id;
            this.record = record;
            this.camera = camera;
        }
    }

    /** Server-side context handed to setup and check lambdas (always on the server thread). */
    record Srv(MinecraftServer server, ServerPlayer player) {
        ServerLevel end() {
            return level(Level.END);
        }

        ServerLevel level(net.minecraft.resources.ResourceKey<Level> key) {
            ServerLevel l = server.getLevel(key);
            if (l == null) {
                throw new IllegalStateException("missing " + key);
            }
            return l;
        }

        void cmd(String c) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
        }

        void tp(ServerLevel l, Vec3 p, float yaw, float pitch) {
            if (!player.hasEffect(MobEffects.RESISTANCE)) {
                protect(player);
            }
            player.teleportTo(l, p.x, p.y, p.z, Set.of(), yaw, pitch, true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }

        void hand(ItemStack stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.inventoryMenu.broadcastChanges();
        }
    }

    private final class Builder {
        final Scenario s;

        Builder(String id, boolean record, CameraType camera) {
            s = new Scenario(id, record, camera);
            if (only.isEmpty() || only.contains(id)) {
                scenarios.add(s);
            }
        }

        /** Runs on the server thread; a non-null return is a failure that aborts the scenario. */
        Builder server(String label, Function<Srv, @Nullable String> fn) {
            s.acts.add((mc, t) -> {
                if (t == 0) {
                    MinecraftServer srv = mc.getSingleplayerServer();
                    UUID id = playerId;
                    pending = CompletableFuture.supplyAsync(() -> {
                        ServerPlayer sp = srv == null || id == null ? null : srv.getPlayerList().getPlayer(id);
                        if (sp == null) {
                            return "no server player";
                        }
                        try {
                            return fn.apply(new Srv(srv, sp));
                        } catch (RuntimeException e) {
                            LOGGER.error("[playtest] {} / {} threw", s.id, label, e);
                            return label + " threw " + e;
                        }
                    }, srv);
                    return false;
                }
                CompletableFuture<@Nullable String> f = pending;
                if (f == null || !f.isDone()) {
                    return false;
                }
                String err = f.getNow("lost");
                pending = null;
                if (err != null) {
                    record(s.id, label, false, err);
                    s.aborted = true;
                }
                return true;
            });
            return this;
        }

        /** A server-side check: null = pass. Never aborts. */
        Builder check(String name, Function<Srv, @Nullable String> fn) {
            s.acts.add((mc, t) -> {
                if (t == 0) {
                    MinecraftServer srv = mc.getSingleplayerServer();
                    UUID id = playerId;
                    pending = CompletableFuture.supplyAsync(() -> {
                        ServerPlayer sp = srv == null || id == null ? null : srv.getPlayerList().getPlayer(id);
                        try {
                            return sp == null ? "no server player" : fn.apply(new Srv(srv, sp));
                        } catch (RuntimeException e) {
                            return "threw " + e;
                        }
                    }, srv);
                    return false;
                }
                CompletableFuture<@Nullable String> f = pending;
                if (f == null || !f.isDone()) {
                    return false;
                }
                String r = f.getNow("lost");
                pending = null;
                boolean pass = r == null || r.startsWith("ok");
                record(s.id, name, pass, r == null ? "" : r);
                return true;
            });
            return this;
        }

        /** Polls a server-side condition every 10 ticks (null or "ok..." = met); records the result. */
        Builder untilServer(String name, int timeout, Function<Srv, @Nullable String> fn) {
            final String[] last = {""};
            s.acts.add((mc, t) -> {
                if (pending != null) {
                    if (!pending.isDone()) {
                        return false;
                    }
                    String r = pending.getNow("lost");
                    pending = null;
                    if (r == null || r.startsWith("ok")) {
                        record(s.id, name, true, "after " + t + " ticks; " + (r == null ? "" : r));
                        return true;
                    }
                    last[0] = r;
                }
                if (t >= timeout) {
                    record(s.id, name, false, "timed out; " + last[0]);
                    return true;
                }
                if (t % 10 == 0) {
                    MinecraftServer srv = mc.getSingleplayerServer();
                    UUID id = playerId;
                    pending = CompletableFuture.supplyAsync(() -> {
                        ServerPlayer sp = srv == null || id == null ? null : srv.getPlayerList().getPlayer(id);
                        return sp == null ? "no server player" : fn.apply(new Srv(srv, sp));
                    }, srv);
                }
                return false;
            });
            return this;
        }

        /** Waits for a client-side condition; on timeout records a failure (and aborts if {@code fatal}). */
        Builder until(String name, int timeout, boolean fatal, Predicate<Minecraft> cond, Supplier<String> detail) {
            s.acts.add((mc, t) -> {
                if (cond.test(mc)) {
                    record(s.id, name, true, "after " + t + " ticks; " + detail.get());
                    return true;
                }
                if (t >= timeout) {
                    record(s.id, name, false, "timed out after " + timeout + " ticks; " + detail.get());
                    if (fatal) {
                        s.aborted = true;
                    }
                    return true;
                }
                return false;
            });
            return this;
        }

        /** Runs {@code each} every tick for {@code n} ticks (inputs held here are released at the end). */
        Builder during(int n, Consumer<Minecraft> each) {
            s.acts.add((mc, t) -> {
                if (t >= n) {
                    releaseKeys(mc);
                    return true;
                }
                each.accept(mc);
                return false;
            });
            return this;
        }

        /** Holds inputs every tick until the condition holds or the timeout passes. */
        Builder duringUntil(String name, int timeout, boolean fatal, Consumer<Minecraft> each, Predicate<Minecraft> cond, Supplier<String> detail) {
            s.acts.add((mc, t) -> {
                if (cond.test(mc)) {
                    releaseKeys(mc);
                    record(s.id, name, true, "after " + t + " ticks; " + detail.get());
                    return true;
                }
                if (t >= timeout) {
                    releaseKeys(mc);
                    record(s.id, name, false, "timed out after " + timeout + " ticks; " + detail.get());
                    if (fatal) {
                        s.aborted = true;
                    }
                    return true;
                }
                each.accept(mc);
                return false;
            });
            return this;
        }

        Builder client(Consumer<Minecraft> fn) {
            s.acts.add((mc, t) -> {
                fn.accept(mc);
                return true;
            });
            return this;
        }

        Builder clientCheck(String name, Function<Minecraft, @Nullable String> fn) {
            s.acts.add((mc, t) -> {
                String r = fn.apply(mc);
                record(s.id, name, r == null || r.startsWith("ok"), r == null ? "" : r);
                return true;
            });
            return this;
        }

        Builder wait(int n) {
            s.acts.add((mc, t) -> t >= n);
            return this;
        }

        /** Waits for the player to be in {@code dim} with its chunks drawn. */
        Builder arrive(net.minecraft.resources.ResourceKey<Level> dim) {
            return until("arrived in " + dim.identifier().getPath(), 600, true,
                mc -> mc.level != null && mc.level.dimension() == dim && mc.player != null && mc.player.tickCount > 5,
                () -> "")
                .until("chunks drawn", 400, false, mc -> mc.levelRenderer.hasRenderedAllSections(), () -> "")
                .wait(10);
        }

        /** Presses the use key once, the way a right-click does. */
        Builder use() {
            return client(mc -> KeyMapping.click(mc.options.keyUse.getKey()));
        }

        Builder face(Supplier<Vec3> target) {
            return client(mc -> look(mc, target.get()));
        }
    }

    private void record(String scenario, String check, boolean pass, String detail) {
        results.add(new Result(scenario, check, pass, detail));
        LOGGER.info("[playtest] {} {} / {} {}", pass ? "PASS" : "FAIL", scenario, check, detail);
    }

    private static void releaseKeys(Minecraft mc) {
        for (KeyMapping k : List.of(mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump,
            mc.options.keyShift, mc.options.keyUse, mc.options.keySprint)) {
            k.setDown(false);
        }
    }

    static void look(Minecraft mc, Vec3 target) {
        if (mc.player == null) {
            return;
        }
        float[] rot = ShowcaseScenes.look(mc.player.getEyePosition(), target);
        mc.player.setYRot(rot[0]);
        mc.player.setXRot(rot[1]);
        mc.player.setYHeadRot(rot[0]);
    }

    // ================================================================== events

    @SubscribeEvent
    public void onTitle(ScreenEvent.Init.Post event) {
        if (started || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.renderDistance().set(12);
        mc.options.simulationDistance().set(10);
        mc.options.fov().set(70);
        buildScenarios();
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "wf_playtest", "");
            LevelSettings settings = new LevelSettings(folder, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, true,
                WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(Long.getLong(PROPERTY + ".seed", 1_026_003L), true, false);
            LOGGER.info("[playtest] creating world {} with {} scenarios", folder, scenarios.size());
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options, WorldPresets::createNormalWorldDimensions, title);
        } catch (IOException e) {
            LOGGER.error("[playtest] could not create world", e);
            done = true;
        }
    }

    @SubscribeEvent
    public void onSound(PlaySoundEvent event) {
        if (event.getSound() == null) {
            return;
        }
        Identifier id = event.getSound().getIdentifier();
        if (id.getNamespace().equals("worldsfall")) {
            sounds.merge(id.getPath(), 1, Integer::sum);
            if (Minecraft.getInstance().getSoundManager().getSoundEvent(id) == null) {
                missingSounds.add(id.toString());
            }
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!started || done) {
            return;
        }
        if (drain > 0) {
            if (pendingWrites.get() == 0 || ++drain > 900) {
                finish(mc);
            }
            return;
        }
        if (scenarioIndex < 0) {
            if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.gui.screen() == null && mc.player.tickCount > 40) {
                playerId = mc.player.getUUID();
                nextScenario(mc);
            }
            return;
        }
        Scenario s = scenarios.get(scenarioIndex);
        if (s.aborted || actIndex >= s.acts.size()) {
            releaseKeys(mc);
            nextScenario(mc);
            return;
        }
        if (s.acts.get(actIndex).tick(mc, actTicks)) {
            actIndex++;
            actTicks = 0;
        } else {
            actTicks++;
        }
        if (s.record && mc.player != null) {
            capture(mc, out.resolve("frames").resolve(String.format(Locale.ROOT, "%02d_%s", scenarioIndex, s.id))
                .resolve(String.format(Locale.ROOT, "f_%04d.png", ++frame)));
            s.frames = frame;
        }
    }

    private void nextScenario(Minecraft mc) {
        scenarioIndex++;
        actIndex = 0;
        actTicks = 0;
        frame = 0;
        if (scenarioIndex >= scenarios.size()) {
            drain = 1;
            return;
        }
        Scenario s = scenarios.get(scenarioIndex);
        mc.options.setCameraType(s.camera);
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        LOGGER.info("[playtest] scenario {}/{}: {}", scenarioIndex + 1, scenarios.size(), s.id);
    }

    private void capture(Minecraft mc, Path file) {
        int width = mc.gameRenderer.mainRenderTarget().width;
        int downscale = width > 2600 ? 2 : 1;
        pendingWrites.incrementAndGet();
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), downscale, (NativeImage image) -> writer.execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("[playtest] failed to write {}", file, e);
            } finally {
                pendingWrites.decrementAndGet();
            }
        }));
    }

    private void finish(Minecraft mc) {
        done = true;
        writer.shutdown();
        try {
            writer.awaitTermination(2, TimeUnit.MINUTES);
            Files.createDirectories(out);
            Files.writeString(out.resolve("report.md"), report());
            Files.writeString(out.resolve("DONE"), "results=" + results.size() + "\n");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("[playtest] report failed", e);
        }
        LOGGER.info("[playtest] complete: {} checks, {} failed -> {}", results.size(), results.stream().filter(r -> !r.pass).count(), out);
        if (Boolean.parseBoolean(System.getProperty(PROPERTY + ".exit", "true"))) {
            mc.stop();
        }
    }

    private String report() {
        StringBuilder sb = new StringBuilder("# Worldsfall real-client playtest\n\n");
        sb.append("Client: ").append(net.minecraft.SharedConstants.getCurrentVersion().name()).append(", launched from the official launcher.\n\n");
        long failed = results.stream().filter(r -> !r.pass).count();
        sb.append(String.format(Locale.ROOT, "**%d checks, %d passed, %d failed**\n\n", results.size(), results.size() - failed, failed));
        sb.append("| Scenario | Check | Result | Detail |\n|---|---|---|---|\n");
        for (Result r : results) {
            sb.append("| ").append(r.scenario).append(" | ").append(r.check).append(" | ").append(r.pass ? "PASS" : "**FAIL**").append(" | ")
                .append(r.detail.replace("|", "/")).append(" |\n");
        }
        sb.append("\n## Scenarios\n\n");
        for (Scenario s : scenarios) {
            sb.append("- ").append(s.id).append(s.aborted ? " (aborted)" : "").append(s.record ? ", " + s.frames + " frames" : "").append('\n');
        }
        sb.append("\n## Worldsfall sounds heard\n\n");
        sounds.forEach((k, v) -> sb.append("- ").append(k).append(" ×").append(v).append('\n'));
        sb.append("\nMissing sound events: ").append(missingSounds.isEmpty() ? "none" : String.join(", ", missingSounds)).append('\n');
        return sb.toString();
    }

    // ================================================================== world helpers (server thread)

    static void pad(ServerLevel l, BlockPos c, int r, BlockState top) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                l.setBlockAndUpdate(c.offset(dx, 0, dz), top);
                l.setBlockAndUpdate(c.offset(dx, -1, dz), Blocks.END_STONE.defaultBlockState());
                for (int y = 1; y <= 5; y++) {
                    l.setBlockAndUpdate(c.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    static void protect(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, -1, 0, false, false));
    }

    static <T extends Entity> List<T> near(ServerLevel l, Class<T> type, Vec3 at, double r) {
        return l.getEntitiesOfClass(type, new AABB(at, at).inflate(r));
    }

    // ================================================================== the scenarios

    private void buildScenarios() {
        // shared state between steps of one scenario (written on the server thread, read after its future completes)
        final BlockPos gateA = new BlockPos(1600, 101, 1600), gateB = new BlockPos(1600, 101, 1660);
        final Vec3[] maw = {Vec3.ZERO};
        final double[] tide = {0};
        final int[] ids = {-1};
        final Vec3[] mark = {Vec3.ZERO};
        final SkyCurrents.Current[] current = {null};
        final BlockPos lens = new BlockPos(6200, 226, 600);
        final Vec3[] starSite = {new Vec3(1250, 80, 250)};

        // ---- arrival: into the End in survival, walking under the tide-lit sky
        new Builder("p_arrival", true, CameraType.FIRST_PERSON)
            .server("enter the End", s -> {
                protect(s.player());
                s.cmd("worldsfall tide set low");
                ServerLevel end = s.end();
                end.getChunk(0, 3);
                int y = end.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 6, 48);
                s.tp(end, new Vec3(6.5, Math.max(y, 50) + 1, 48.5), 180, 0);
                return null;
            })
            .arrive(Level.END)
            .clientCheck("tide synced to client", mc -> Tides.appliesTo(mc.level) ? "ok phase " + Tides.phase(mc.level).id : "tides not active client-side")
            .client(mc -> mark[0] = mc.player.position())
            .during(70, mc -> {
                mc.options.keyUp.setDown(true);
                mc.player.setXRot(-8);
            })
            .clientCheck("walked forward with W", mc -> {
                double d = mc.player.position().distanceTo(mark[0]);
                return d > 3 ? "ok moved " + fmt(d) + " blocks" : "moved only " + fmt(d);
            });

        // ---- the Gateway Lattice: attune two gateways with the Tuning Fork, sneak into one, pick the other in the screen
        new Builder("p_lattice", true, CameraType.FIRST_PERSON)
            .server("build two gateways", s -> {
                ServerLevel end = s.end();
                s.cmd("worldsfall tide set low");
                for (BlockPos g : List.of(gateA, gateB)) {
                    pad(end, g.below(), 6, Blocks.END_STONE_BRICKS.defaultBlockState());
                    end.setBlockAndUpdate(g.below(), Blocks.BEDROCK.defaultBlockState());
                    end.setBlockAndUpdate(g, Blocks.END_GATEWAY.defaultBlockState());
                }
                s.tp(end, Vec3.atBottomCenterOf(gateB).add(0, 0, -3), 0, 0);
                s.hand(new ItemStack(WFItems.TUNING_FORK.get()));
                return null;
            })
            .arrive(Level.END)
            .face(() -> Vec3.atCenterOf(gateB)).wait(3).use().wait(10)
            .check("Tuning Fork attunes gateway B", s -> LatticeData.get(s.server()).at(gateB) != null ? "ok " + LatticeData.get(s.server()).at(gateB).name()
                : "no node at B (crosshair on the gateway?)")
            .server("walk to gateway A", s -> {
                s.tp(s.end(), Vec3.atBottomCenterOf(gateA).add(0, 0, -3), 0, 0);
                return null;
            })
            .wait(10).face(() -> Vec3.atCenterOf(gateA)).wait(3).use().wait(10)
            .check("Tuning Fork attunes gateway A", s -> {
                LatticeData d = LatticeData.get(s.server());
                return d.at(gateA) != null && d.knows(s.player().getUUID(), gateA) && d.knows(s.player().getUUID(), gateB)
                    ? "ok lattice has " + d.size() + " nodes" : "A attuned=" + (d.at(gateA) != null);
            })
            .server("pearls", s -> {
                s.hand(ItemStack.EMPTY);
                s.player().getInventory().add(new ItemStack(Items.ENDER_PEARL, 3));
                return null;
            })
            .wait(5)
            .duringUntil("sneaking into an attuned gateway opens the Lattice", 80, true, mc -> {
                look(mc, Vec3.atCenterOf(gateA).add(0, -0.6, 0));
                mc.options.keyShift.setDown(true);
                mc.options.keyUp.setDown(true);
            }, mc -> mc.gui.screen() instanceof LatticeScreen, () -> "screen=" + (Minecraft.getInstance().gui.screen() == null ? "none"
                : Minecraft.getInstance().gui.screen().getClass().getSimpleName()))
            .clientCheck("vanilla gateway did not fire", mc -> mc.player.position().distanceTo(Vec3.atCenterOf(gateA)) < 4 ? "ok still at A"
                : "moved to " + mc.player.position())
            .wait(40)
            .client(mc -> {
                if (mc.gui.screen() instanceof LatticeScreen ls) {
                    int left = (ls.width - 176) / 2, top = (ls.height - 166) / 2;
                    ls.mouseClicked(new MouseButtonEvent(left + 12 + 40, top + 28 + 6, new MouseButtonInfo(0, 0)), false);
                }
            })
            .until("picking gateway B jumps there", 60, false, mc -> mc.player.position().distanceTo(Vec3.atCenterOf(gateB)) < 8,
                () -> "at " + Minecraft.getInstance().player.blockPosition())
            .check("a jump costs one pearl in survival", s -> {
                int n = s.player().getInventory().countItem(Items.ENDER_PEARL);
                return n == 2 ? "ok 2 left" : "pearls left " + n;
            })
            .wait(30);

        // ---- charts: fill an Empty Void Chart and hold it
        new Builder("p_chart", true, CameraType.FIRST_PERSON)
            .server("blank chart", s -> {
                s.hand(new ItemStack(WFItems.EMPTY_VOID_CHART.get()));
                return null;
            })
            .wait(5).client(mc -> mc.player.setXRot(10)).use().wait(10)
            .until("using the blank chart draws a Void Chart", 40, true, mc -> mc.player.getMainHandItem().get(DataComponents.MAP_ID) != null,
                () -> "hand=" + Minecraft.getInstance().player.getMainHandItem())
            .until("the chart's map data reaches the client", 60, false,
                mc -> mc.level.getMapData(mc.player.getMainHandItem().get(DataComponents.MAP_ID)) != null, () -> "")
            .during(80, mc -> mc.player.setXRot(55))
            .clientCheck("chart has decorations", mc -> {
                var data = mc.level.getMapData(mc.player.getMainHandItem().get(DataComponents.MAP_ID));
                if (data == null) {
                    return "no data";
                }
                int n = 0;
                for (var ignored : data.getDecorations()) {
                    n++;
                }
                return "ok " + n + " decorations, scale " + data.scale;
            });

        // ---- the Void Bell: set it down, charge it, board it, pilot it into an open Tide Maw at High Tide
        new Builder("p_bell_dive", true, CameraType.FIRST_PERSON)
            .server("find a maw, raise the tide", s -> {
                protect(s.player());
                ServerLevel end = s.end();
                s.cmd("worldsfall tide set high");
                List<Vec3> maws = TideMaws.mawsNear(end.getSeed(), 3000, 3000);
                if (maws.isEmpty()) {
                    return "no maw near (3000, 3000)";
                }
                maw[0] = maws.stream().min(java.util.Comparator.comparingDouble(m -> Math.hypot(m.x - 3000, m.z - 3000))).orElseThrow();
                tide[0] = Tides.height(end);
                BlockPos pad = BlockPos.containing(maw[0].x + 24, tide[0] + 2, maw[0].z);
                pad(end, pad, 4, Blocks.END_STONE_BRICKS.defaultBlockState());
                s.tp(end, Vec3.atBottomCenterOf(pad.above()), 90, 0);
                s.hand(new ItemStack(WFItems.VOID_BELL.get()));
                s.player().getInventory().add(new ItemStack(WFItems.STILLNESS_FRAGMENT.get(), 1));
                return null;
            })
            .arrive(Level.END)
            .clientCheck("maws are open at High Tide", mc -> TideMaws.open(mc.level) ? "ok tide " + fmt(Tides.height(mc.level)) : "maws closed")
            .face(() -> Vec3.atCenterOf(BlockPos.containing(maw[0].x + 21, tide[0] + 2, maw[0].z))).wait(3).use().wait(10)
            .check("the Void Bell item sets a bell down", s -> {
                var bells = near(s.end(), VoidBell.class, s.player().position(), 8);
                if (bells.isEmpty()) {
                    return "no bell placed";
                }
                ids[0] = bells.getFirst().getId();
                return "ok bell at " + bells.getFirst().blockPosition();
            })
            .server("fragment in hand", s -> {
                int slot = s.player().getInventory().findSlotMatchingItem(new ItemStack(WFItems.STILLNESS_FRAGMENT.get()));
                ItemStack frag = slot >= 0 ? s.player().getInventory().removeItemNoUpdate(slot) : ItemStack.EMPTY;
                s.hand(frag);
                return frag.isEmpty() ? "fragment missing" : null;
            })
            .wait(4).face(() -> entityCentre(ids[0])).wait(2).use().wait(10)
            .check("a Stillness Fragment charges it", s -> {
                Entity e = s.end().getEntity(ids[0]);
                return e instanceof VoidBell b && b.charge() > 0 ? "ok charge " + b.charge() / 1200 + " min" : "not charged";
            })
            .server("empty hand", s -> {
                s.hand(ItemStack.EMPTY);
                return null;
            })
            .wait(4).face(() -> entityCentre(ids[0])).wait(2).use()
            .until("right-click boards the bell", 40, true, mc -> mc.player.getVehicle() instanceof VoidBell, () -> "")
            .duringUntil("piloted into the open maw, the bell sinks into the Undertow", 900, true, mc -> {
                look(mc, new Vec3(maw[0].x, tide[0] - 2, maw[0].z));
                mc.options.keyUp.setDown(true);
            }, mc -> mc.level.dimension() == WFDimensions.THE_UNDERTOW, () -> "at " + Minecraft.getInstance().player.blockPosition())
            .until("still aboard after the descent", 60, false, mc -> mc.player.getVehicle() instanceof VoidBell, () -> "")
            .until("Undertow chunks drawn", 400, false, mc -> mc.levelRenderer.hasRenderedAllSections(), () -> "");

        // ---- the Undertow: drift through the haze toward the Sunken Archive, read a Codex aboard, rise back out
        new Builder("p_undertow", true, CameraType.FIRST_PERSON)
            .duringUntil("pilot toward the Sunken Archive", 260, false, mc -> {
                BlockPos c = SunkenArchive.centre(maw[0]);
                look(mc, Vec3.atCenterOf(c).add(0, 20, 0));
                mc.options.keyUp.setDown(true);
            }, mc -> mc.player.position().distanceTo(Vec3.atCenterOf(SunkenArchive.centre(maw[0])).add(0, 20, 0)) < 12,
                () -> "at " + Minecraft.getInstance().player.blockPosition())
            .check("the Undertow is alive (creatures near)", s -> {
                ServerLevel u = s.level(WFDimensions.THE_UNDERTOW);
                long n = u.getEntitiesOfClass(Entity.class, s.player().getBoundingBox().inflate(96),
                    e -> e.getType().builtInRegistryHolder().key().identifier().getNamespace().equals("worldsfall") && !(e instanceof VoidBell)).size();
                return n > 0 ? "ok " + n + " worldsfall creatures within 96" : "nothing near";
            })
            .check("the Sunken Archive stands", s -> {
                ServerLevel u = s.level(WFDimensions.THE_UNDERTOW);
                BlockPos c = SunkenArchive.centre(maw[0]);
                int shelves = 0;
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-20, -12, -20), c.offset(20, 20, 20))) {
                    if (u.getBlockState(p).is(WFBlocks.ARCHIVE_SHELF.get())) {
                        shelves++;
                    }
                }
                return shelves > 0 ? "ok " + shelves + " shelves" : "no shelves around " + c;
            })
            .server("a codex", s -> {
                s.hand(ArchiveCodexItem.of(2));
                return null;
            })
            .wait(5)
            .client(mc -> LOGGER.info("[playtest] codex diag: hand={} hit={} {} cooldown={} vehicle={}", mc.player.getMainHandItem(),
                mc.hitResult == null ? "null" : mc.hitResult.getType(),
                mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh ? eh.getEntity() : "",
                mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem()), mc.player.getVehicle()))
            .use()
            .wait(10)
            .check("the Codex counts as read on the server", s -> s.player().getData(dev.worldsfall.registry.WFAttachments.CODICES_READ).contains(2)
                ? "ok" : "read set " + s.player().getData(dev.worldsfall.registry.WFAttachments.CODICES_READ))
            .until("reading a Codex shows its record", 40, false, mc -> WFHud.currentLine().equals("codex.worldsfall.2"), WFHud::currentLine)
            .during(80, mc -> mc.player.setXRot(20))
            .duringUntil("rising past the ceiling returns to the End", 1400, false, mc -> {
                mc.player.setXRot(-12);
                mc.player.setYRot(mc.player.getYRot() + 0.25f);
                mc.options.keyJump.setDown(true);
            }, mc -> mc.level.dimension() == Level.END, () -> "y=" + fmt(Minecraft.getInstance().player.getY()))
            .until("still aboard on return", 40, false, mc -> mc.player.getVehicle() instanceof VoidBell, () -> "")
            .wait(40);

        // ---- the Zenith: stand on a Leviathan's back and be carried
        new Builder("p_leviathan", true, CameraType.FIRST_PERSON)
            .server("a Leviathan high over the Gap", s -> {
                s.player().stopRiding();
                s.cmd("worldsfall tide set low");
                ServerLevel end = s.end();
                Vec3 at = new Vec3(3600.5, 215, -3600.5);
                end.getChunk(BlockPos.containing(at));
                LeviathanDrifter lev = WFEntities.LEVIATHAN_DRIFTER.get().create(end, EntitySpawnReason.COMMAND);
                if (lev == null) {
                    return "could not create";
                }
                lev.snapTo(at.x, at.y, at.z, 0, 0);
                end.addFreshEntity(lev);
                ids[0] = lev.getId();
                AABB b = lev.getBoundingBox();
                s.tp(end, new Vec3(at.x, b.maxY + 0.05, at.z), 0, 10);
                return null;
            })
            .arrive(Level.END)
            .client(mc -> mark[0] = mc.player.position())
            .during(260, mc -> {
                if (mc.level.getEntity(ids[0]) instanceof LeviathanDrifter l) {
                    mc.player.setYRot(l.getYRot() + 70 + (float) Math.sin(mc.player.tickCount * 0.02) * 25);
                    mc.player.setXRot(18);
                    if (mc.player.tickCount % 10 == 0) {
                        AABB b = l.getBoundingBox();
                        LOGGER.info("[playtest] leviathan diag: carries={} onGround={} feetY={} topY={} dx={} dz={} v={} levV={}", l.carries(mc.player),
                            mc.player.onGround(), fmt(mc.player.getY()), fmt(b.maxY), fmt(mc.player.getX() - l.getX()), fmt(mc.player.getZ() - l.getZ()),
                            mc.player.getDeltaMovement(), l.getDeltaMovement());
                    }
                }
            })
            .clientCheck("standing on its back after 13 s", mc -> mc.level.getEntity(ids[0]) instanceof LeviathanDrifter l && l.carries(mc.player)
                ? "ok" : "fell off; y=" + fmt(mc.player.getY()))
            .clientCheck("carried along with it", mc -> {
                double d = Math.hypot(mc.player.getX() - mark[0].x, mc.player.getZ() - mark[0].z);
                return d > 15 ? "ok carried " + fmt(d) + " blocks" : "moved " + fmt(d);
            });

        // ---- Sky Currents: glide into one with an elytra
        new Builder("p_sky_current", true, CameraType.THIRD_PERSON_BACK)
            .server("an elytra at a current's mouth", s -> {
                ServerLevel end = s.end();
                for (int rx = 2; rx < 9 && current[0] == null; rx++) {
                    current[0] = SkyCurrents.currentOf(end.getSeed(), rx, 1);
                }
                if (current[0] == null) {
                    return "no current found";
                }
                s.player().setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                Vec3 a = current[0].points().get(1), b = current[0].points().get(2);
                float[] rot = ShowcaseScenes.look(a, b);
                s.tp(end, a.add(0, 3, 0), rot[0], 0);
                return null;
            })
            .arrive(Level.END)
            .client(mc -> mark[0] = Vec3.ZERO)
            .until("the client knows the nearby currents", 40, false, mc -> !dev.worldsfall.client.expansion.ZenithClient.currents().isEmpty(),
                () -> dev.worldsfall.client.expansion.ZenithClient.currents().size() + " currents")
            .server("back to the current's mouth", s -> {
                Vec3 a = current[0].points().get(1), b = current[0].points().get(2);
                float[] rot = ShowcaseScenes.look(a, b);
                s.tp(s.end(), a.add(0, 1.5, 0), rot[0], 0);
                return null;
            })
            .wait(3)
            .duringUntil("jump in the air opens the elytra", 40, true, mc -> mc.options.keyJump.setDown(mc.player.tickCount % 4 < 2),
                mc -> mc.player.isFallFlying(), () -> "")
            .during(200, mc -> {
                List<Vec3> pts = current[0].points();
                Vec3 p = mc.player.position();
                int best = 0;
                for (int i = 0; i < pts.size(); i++) {
                    if (pts.get(i).distanceToSqr(p) < pts.get(best).distanceToSqr(p)) {
                        best = i;
                    }
                }
                Vec3 ahead = pts.get(Math.min(best + 2, pts.size() - 1));
                look(mc, ahead);
                if (mc.player.getDeltaMovement().length() > mark[0].x) {
                    mark[0] = new Vec3(mc.player.getDeltaMovement().length(), 0, 0);
                }
            })
            .clientCheck("the glider caught the current", mc -> sounds.containsKey("event.sky_current.enter") ? "ok top speed " + fmt(mark[0].x * 20) + " m/s"
                : "never entered; top speed " + fmt(mark[0].x * 20) + " m/s");

        // ---- world events as a survival player on an island
        for (int ev : new int[] {WorldEvents.STARFALL, WorldEvents.ECLIPSE}) {
            String name = ev == WorldEvents.STARFALL ? "p_starfall" : "p_eclipse";
            new Builder(name, true, CameraType.FIRST_PERSON)
                .server("an island, the event", s -> {
                    s.player().setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    ServerLevel end = s.end();
                    s.cmd("worldsfall tide set low");
                    WorldEvents.stop(end);
                    BlockPos c = new BlockPos(1250, 80, 250);
                    pad(end, c, 14, Blocks.END_STONE.defaultBlockState());
                    s.tp(end, Vec3.atBottomCenterOf(c.above()), 45, -20);
                    return null;
                })
                .arrive(Level.END)
                .check("the event begins", s -> WorldEvents.start(s.end(), ev) ? "ok" : "start() refused")
                .until("the client hears of it", 60, false, mc -> ClientWorldState.get().worldEvent() == ev, () -> "client event "
                    + ClientWorldState.get().worldEvent())
                .during(ev == WorldEvents.STARFALL ? 60 : 30, mc -> mc.player.setXRot(-35))
                .check(ev == WorldEvents.STARFALL ? "a star is coming down" : "the sky has stilled", s -> {
                    var d = WorldEventData.get(s.server());
                    starSite[0] = Vec3.atCenterOf(d.site());
                    return d.current() == ev ? "ok site " + d.site() : "event " + d.current();
                })
                .during(ev == WorldEvents.STARFALL ? 260 : 220, mc -> {
                    if (ev == WorldEvents.STARFALL) {
                        look(mc, starSite[0].add(0, 18, 0));
                    } else {
                        mc.player.setYRot(mc.player.getYRot() + 0.6f);
                        mc.player.setXRot(-10);
                    }
                })
                .check(ev == WorldEvents.STARFALL ? "shards landed" : "memories walk", s -> {
                    var d = WorldEventData.get(s.server());
                    if (ev == WorldEvents.STARFALL) {
                        return d.placed().isEmpty() ? "nothing placed yet" : "ok " + d.placed().size() + " placed";
                    }
                    long echoes = near(s.end(), dev.worldsfall.entity.Echo.class, s.player().position(), 64).stream()
                        .filter(dev.worldsfall.entity.Echo::isMemory).count();
                    return echoes > 0 ? "ok " + echoes + " memories" : "no memories within 64";
                })
                .server("end it", s -> {
                    WorldEvents.stop(s.end());
                    return null;
                });
        }

        // ---- the Orrery: free each ring with a Spire Key at its tide
        new Builder("p_orrery", false, CameraType.FIRST_PERSON)
            .server("an Orrery", s -> {
                ServerLevel l = s.end();
                if (!l.getBlockState(lens).is(WFBlocks.ORRERY_LENS.get())) {
                    Blueprint bp = OrreryGenerator.build(lens, 0, net.minecraft.util.RandomSource.create(11));
                    BoundingBox box = bp.bounds();
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                            l.getChunk(cx, cz);
                            bp.place(l, new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15));
                        }
                    }
                }
                s.player().getInventory().add(new ItemStack(WFItems.SPIRE_KEY.get(), 3));
                BlockPos sock = OrreryGenerator.socketPos(lens, 0);
                s.tp(l, Vec3.atBottomCenterOf(sock.above()), 0, 90);
                return null;
            })
            .arrive(Level.END);
        Builder orr = new Builder("p_orrery_rings", true, CameraType.FIRST_PERSON);
        for (int ring = 0; ring < 3; ring++) {
            final int i = ring;
            orr.server("ring " + i + " at its tide", s -> {
                    s.cmd("worldsfall tide set " + OrrerySocketBlock.phaseFor(i).id);
                    BlockPos sock = OrreryGenerator.socketPos(lens, i);
                    s.tp(s.end(), Vec3.atBottomCenterOf(sock.above()), 0, 90);
                    s.hand(new ItemStack(WFItems.SPIRE_KEY.get()));
                    return null;
                })
                .wait(25).client(mc -> mc.player.setXRot(90)).wait(2).use().wait(12)
                .check("Spire Key frees ring " + i, s -> {
                    BlockState st = s.end().getBlockState(OrreryGenerator.socketPos(lens, i));
                    return st.hasProperty(OrrerySocketBlock.FREED) && st.getValue(OrrerySocketBlock.FREED) ? "ok" : "socket state " + st;
                })
                .wait(30);
        }
        orr.server("step back", s -> {
                Vec3 t = Vec3.atCenterOf(lens);
                s.tp(s.end(), t.add(-30, 6, -22), 0, 0);
                return null;
            })
            .wait(5)
            .during(260, mc -> look(mc, Vec3.atCenterOf(lens).add(0, 30, 0)))
            .clientCheck("a new star burns in the sky", mc -> ClientWorldState.get().orreryStars().isEmpty() ? "no orrery star on the client"
                : "ok " + ClientWorldState.get().orreryStars().size() + " star(s)");

        // ---- the Restoration: walk the restored archive hall under the mural, then look up at the Overmoon
        new Builder("p_restore_prep", false, CameraType.FIRST_PERSON)
            .server("restore the End, end the dragon", s -> {
                ServerLevel end = s.end();
                s.cmd("worldsfall tide set low");
                s.tp(end, new Vec3(40.5, 75, 30.5), 0, 0);
                return null;
            })
            .arrive(Level.END)
            .server("the dragon falls; the Sundering", s -> {
                for (var d : s.end().getDragons()) {
                    d.kill(s.end());
                }
                s.cmd("worldsfall sundering trigger");
                return null;
            })
            .untilServer("the Sundering opens the Undercroft", 4000, s -> dev.worldsfall.world.sundering.SunderingData.get(s.server()).done()
                ? "ok" : "sundering running")
            .server("restore the End", s -> {
                for (int i = 0; i < 40 && Restoration.fraction(s.server()) < 1.0; i++) {
                    Restoration.award(s.server(), s.player(), Restoration.Act.ORRERY_ALIGN, "playtest" + i);
                }
                RestorationWorld.noteCleared(s.server(), EnumSet.of(Pact.TIDE, Pact.SHARDS, Pact.DEPTHS, Pact.LONE));
                RestorationWorld.tick(s.end());
                return null;
            })
            .check("the dragon is gone", s -> s.end().getDragons().isEmpty() ? "ok" : s.end().getDragons().size() + " dragon(s)")
            .untilServer("the archive hall is carved", 1200, s -> {
                UndercroftBuilder.Layout l = UndercroftBuilder.layout(Wardensong.descent(s.end()));
                BlockPos room = RestorationWorld.archiveCentre(l);
                return s.end().getBlockState(room).isAir() && s.end().getBlockState(room.above()).isAir()
                    && s.end().getBlockState(RestorationWorld.muralOrigin(l)).is(WFBlocks.MURAL_PANEL.get()) ? "ok" : "not yet at " + room;
            })
            .wait(60);
        new Builder("p_restoration", true, CameraType.FIRST_PERSON)
            .server("into the archive hall", s -> {
                ServerLevel end = s.end();
                UndercroftBuilder.Layout l = UndercroftBuilder.layout(Wardensong.descent(end));
                BlockPos room = RestorationWorld.archiveCentre(l);
                Vec3 mural = Vec3.atCenterOf(room.relative(l.inward(), 5)).add(0, 1, 0);
                float[] rot = ShowcaseScenes.look(Vec3.atCenterOf(room).add(0, 1.6, 0), mural);
                s.tp(end, Vec3.atBottomCenterOf(room), rot[0], rot[1]);
                return null;
            })
            .arrive(Level.END)
            .wait(40)
            .clientCheck("the client knows the Restoration stage", mc -> ClientWorldState.get().restorationStage() >= 4
                ? "ok stage " + ClientWorldState.get().restorationStage() : "stage " + ClientWorldState.get().restorationStage())
            .during(160, mc -> {
                mc.options.keyUp.setDown(mc.player.tickCount % 80 < 20);
                mc.player.setYRot(mc.player.getYRot() + (mc.player.tickCount % 160 < 80 ? 0.35f : -0.35f));
            })
            .server("out under the sky", s -> {
                s.tp(s.end(), new Vec3(40.5, 75, 30.5), 135, -60);
                return null;
            })
            .wait(20)
            .during(160, mc -> look(mc, new Vec3(0.5, 300, 0.5)))
            .clientCheck("the Overmoon hangs over the island", mc -> ClientWorldState.get().overmoonRadius() > 0
                ? "ok radius " + fmt(ClientWorldState.get().overmoonRadius()) : "no Overmoon");
    }

    private static Vec3 entityCentre(int id) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        return e == null ? Vec3.ZERO : e.getBoundingBox().getCenter();
    }

    static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

}
