package dev.worldsfall.client.showcase;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Dev-only automated in-game capture (the §18.10 "picture" review). Enabled only by the JVM property
 * {@code -Dworldsfall.showcase=<output dir>}; it never registers in normal play. It creates a fresh world, walks a
 * list of {@link ShowcaseScenes scenes} (server-side setup, spectator camera, still shots and orbit frame sequences)
 * and writes PNGs that {@code tools/showcase/make_video.sh} turns into the showcase video.
 */
public final class ShowcaseDirector {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String PROPERTY = "worldsfall.showcase";
    private static final String LEVEL_NAME = "wf_showcase";
    private static final int SETTLE_MIN = 70;
    private static final int SETTLE_MAX = 900;
    private static final int DECORATE_WAIT = 50;

    private enum Phase { WAIT_TITLE, WAIT_WORLD, WARMUP, LOCATE, ARRIVE, DECORATE, SETTLE, STILLS, VIDEO, NEXT, DRAIN, DONE }

    private static @Nullable ShowcaseDirector instance;

    private final Path out;
    private final List<ShowcaseScenes.Scene> scenes;
    private final boolean video;
    private final boolean exit;
    private final ExecutorService writer = Executors.newFixedThreadPool(3);
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private Phase phase = Phase.WAIT_TITLE;
    private int sceneIndex = -1;
    private int ticks;
    private int frame;
    private int stillIndex;
    private ShowcaseScenes.@Nullable Plan plan;
    private @Nullable CompletableFuture<ShowcaseScenes.Plan> pending;

    private ShowcaseDirector(Path out, List<ShowcaseScenes.Scene> scenes, boolean video, boolean exit) {
        this.out = out;
        this.scenes = scenes;
        this.video = video;
        this.exit = exit;
    }

    /** Called from the client mod constructor; a no-op unless the showcase property is set. */
    public static void init() {
        String dir = System.getProperty(PROPERTY);
        if (dir == null || dir.isBlank()) {
            return;
        }
        List<ShowcaseScenes.Scene> all = ShowcaseScenes.all();
        String only = System.getProperty(PROPERTY + ".scenes", "");
        List<ShowcaseScenes.Scene> chosen = new ArrayList<>();
        if (only.isBlank()) {
            chosen.addAll(all);
        } else {
            List<String> ids = Arrays.stream(only.split(",")).map(String::trim).toList();
            for (ShowcaseScenes.Scene s : all) {
                if (ids.contains(s.id())) {
                    chosen.add(s);
                }
            }
        }
        boolean video = Boolean.parseBoolean(System.getProperty(PROPERTY + ".video", "true"));
        boolean exit = Boolean.parseBoolean(System.getProperty(PROPERTY + ".exit", "true"));
        instance = new ShowcaseDirector(Path.of(dir).toAbsolutePath(), chosen, video, exit);
        NeoForge.EVENT_BUS.register(instance);
        LOGGER.info("[showcase] enabled: {} scenes -> {}", chosen.size(), instance.out);
    }

    @SubscribeEvent
    public void onScreen(ScreenEvent.Init.Post event) {
        if (phase != Phase.WAIT_TITLE || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.renderDistance().set(16);
        mc.options.simulationDistance().set(10);
        mc.options.fov().set(62);
        mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); // no advancement toasts in HUD shots
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), LEVEL_NAME, "");
            LevelSettings settings = new LevelSettings(folder, GameType.SPECTATOR, LevelSettings.DifficultySettings.DEFAULT, true,
                WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(Long.getLong(PROPERTY + ".seed", 1_026_003L), true, false);
            phase = Phase.WAIT_WORLD;
            LOGGER.info("[showcase] creating world {}", folder);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options, WorldPresets::createNormalWorldDimensions, title);
        } catch (IOException e) {
            LOGGER.error("[showcase] could not create world", e);
            phase = Phase.DONE;
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        switch (phase) {
            case WAIT_TITLE, DONE -> { }
            case DRAIN -> {
                if (pendingWrites.get() == 0 || ++ticks > 600) {
                    finish(mc);
                }
            }
            case WAIT_WORLD -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.gui.screen() == null) {
                    if (!mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                    phase = Phase.WARMUP;
                    ticks = 0;
                }
            }
            case WARMUP -> {
                if (++ticks > 40) {
                    advance();
                }
            }
            case LOCATE -> {
                if (pending != null && pending.isDone()) {
                    plan = pending.getNow(null);
                    pending = null;
                    if (plan == null) {
                        LOGGER.warn("[showcase] scene {} could not be set up; skipping", scene().id());
                        advance();
                    } else {
                        phase = Phase.ARRIVE;
                        ticks = 0;
                    }
                }
            }
            case ARRIVE -> {
                holdCamera(mc, 0);
                if (++ticks >= DECORATE_WAIT && mc.player != null && mc.player.level().dimension() == plan().dimension()) {
                    MinecraftServer server = server(mc);
                    ShowcaseScenes.Plan p = plan();
                    pending = CompletableFuture.supplyAsync(() -> decorate(server, p), server);
                    phase = Phase.DECORATE;
                }
            }
            case DECORATE -> {
                holdCamera(mc, 0);
                if (pending != null && pending.isDone()) {
                    ShowcaseScenes.Plan refined = pending.getNow(null);
                    pending = null;
                    if (refined != null) {
                        plan = refined;
                    }
                    phase = Phase.SETTLE;
                    ticks = 0;
                }
            }
            case SETTLE -> {
                holdCamera(mc, 0);
                ticks++;
                if ((ticks >= SETTLE_MIN + plan().extraSettle() && mc.levelRenderer.hasRenderedAllSections()) || ticks >= SETTLE_MAX) {
                    phase = Phase.STILLS;
                    stillIndex = 0;
                    ticks = 0;
                }
            }
            case STILLS -> {
                ShowcaseScenes.Plan p = plan();
                holdCamera(mc, 0);
                if (++ticks % 12 == 0) {
                    if (stillIndex < p.stills().size()) {
                        logFrame(mc);
                        capture(mc, out.resolve("stills").resolve(scene().id() + (stillIndex == 0 ? "" : "_" + stillIndex) + ".png"));
                        stillIndex++;
                    } else {
                        phase = video && p.videoFrames() > 0 ? Phase.VIDEO : Phase.NEXT;
                        frame = 0;
                        ticks = 0;
                    }
                }
            }
            case VIDEO -> {
                ShowcaseScenes.Plan p = plan();
                holdCamera(mc, frame);
                if (frame > 0) {
                    capture(mc, out.resolve("frames").resolve(String.format(Locale.ROOT, "%02d_%s", ordinal(scene().id()), scene().id()))
                        .resolve(String.format(Locale.ROOT, "f_%04d.png", frame)));
                }
                if (++frame > p.videoFrames()) {
                    phase = Phase.NEXT;
                }
            }
            case NEXT -> {
                if (plan != null && plan.lens() && mc.player != null) {
                    mc.options.keyUse.setDown(false);
                    if (mc.gameMode != null) {
                        mc.gameMode.releaseUsingItem(mc.player);
                    }
                    MinecraftServer srv = server(mc);
                    java.util.UUID id = mc.player.getUUID();
                    srv.execute(() -> {
                        ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                        if (sp != null) {
                            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                    });
                    if (!mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                }
                if (plan != null && plan.cleanup() != null) {
                    MinecraftServer server = server(mc);
                    Runnable cleanup = plan.cleanup();
                    server.execute(cleanup);
                }
                advance();
            }
        }
    }

    private void advance() {
        sceneIndex++;
        plan = null;
        Minecraft mc = Minecraft.getInstance();
        if (sceneIndex >= scenes.size()) {
            // screenshot read-backs complete asynchronously on later frames; wait for every one before shutting down
            phase = Phase.DRAIN;
            ticks = 0;
            return;
        }
        ShowcaseScenes.Scene s = scene();
        LOGGER.info("[showcase] scene {}/{}: {}", sceneIndex + 1, scenes.size(), s.id());
        MinecraftServer server = server(mc);
        java.util.UUID id = mc.player == null ? null : mc.player.getUUID();
        pending = CompletableFuture.supplyAsync(() -> {
            ServerPlayer sp = id == null ? null : server.getPlayerList().getPlayer(id);
            if (sp == null) {
                return null;
            }
            try {
                sp.setGameMode(GameType.SPECTATOR);
                ShowcaseScenes.Plan p = s.setup().apply(new ShowcaseScenes.Ctx(server, sp));
                if (p != null) {
                    Vec3 c = p.camera();
                    float[] rot = ShowcaseScenes.look(c, p.target());
                    sp.teleportTo(server.getLevel(p.dimension()), c.x, c.y, c.z, Set.of(), rot[0], rot[1], true);
                }
                return p;
            } catch (RuntimeException e) {
                LOGGER.error("[showcase] setup failed for {}", s.id(), e);
                return null;
            }
        }, server);
        phase = Phase.LOCATE;
    }

    private ShowcaseScenes.@Nullable Plan decorate(MinecraftServer server, ShowcaseScenes.Plan p) {
        try {
            return p.decorate() == null ? p : p.decorate().apply(server, p);
        } catch (RuntimeException e) {
            LOGGER.error("[showcase] decorate failed for {}", scene().id(), e);
            return p;
        }
    }

    /** Keeps the spectator camera on the plan: video frames follow the path, stills use their fixed angles. */
    private void holdCamera(Minecraft mc, int t) {
        ShowcaseScenes.Plan p = plan;
        if (p == null || mc.player == null) {
            return;
        }
        if (p.lens() && phase != Phase.ARRIVE && phase != Phase.DECORATE) {
            // seen through the Void Lens: HUD on (the lens overlay is a HUD layer) and the local player scoped
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            // hold right-click the way a player would, so client and server both keep scoping
            mc.options.keyUse.setDown(true);
            if (!mc.player.isUsingItem() && mc.player.getMainHandItem().getItem() instanceof dev.worldsfall.item.VoidLensItem && mc.gameMode != null) {
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        Vec3 cam = p.camera();
        Vec3 target = p.target();
        if (phase == Phase.VIDEO) {
            if (p.path() != null) {
                cam = p.path().at(t, p);
            }
            if (p.targetPath() != null) {
                target = p.targetPath().at(t, p);
            }
        } else if (phase == Phase.STILLS && stillIndex < p.stills().size()) {
            cam = p.stills().get(stillIndex);
        }
        moveTo(mc, cam, target);
    }

    private static void moveTo(Minecraft mc, Vec3 cam, Vec3 target) {
        if (mc.player == null) {
            return;
        }
        float[] rot = ShowcaseScenes.look(cam, target);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setPos(cam.x, cam.y, cam.z);
        mc.player.setYRot(rot[0]);
        mc.player.setXRot(rot[1]);
        mc.player.setYHeadRot(rot[0]);
    }

    private void logFrame(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 c = mc.player.position();
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48))) {
            if (e.getType().builtInRegistryHolder().key().identifier().getNamespace().equals("worldsfall")) {
                sb.append(String.format(Locale.ROOT, " %s@(%.1f,%.1f,%.1f)", e.getType().builtInRegistryHolder().key().identifier().getPath(),
                    e.getX(), e.getY(), e.getZ()));
            }
        }
        LOGGER.info("[showcase] {} still {} cam=({},{},{}) entities:{}", scene().id(), stillIndex, String.format(Locale.ROOT, "%.1f", c.x),
            String.format(Locale.ROOT, "%.1f", c.y), String.format(Locale.ROOT, "%.1f", c.z), sb);
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
                LOGGER.error("[showcase] failed to write {}", file, e);
            } finally {
                pendingWrites.decrementAndGet();
            }
        }));
    }

    private void finish(Minecraft mc) {
        phase = Phase.DONE;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.MINUTES)) {
                LOGGER.warn("[showcase] {} frame writes still pending", pendingWrites.get());
            }
            Files.writeString(out.resolve("DONE"), "scenes=" + scenes.size() + "\n");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("[showcase] finish failed", e);
        }
        LOGGER.info("[showcase] complete -> {}", out);
        if (exit) {
            mc.stop();
        }
    }

    /** Position in the full scene list, so partial re-captures keep the same frame folder names. */
    private static int ordinal(String id) {
        List<ShowcaseScenes.Scene> all = ShowcaseScenes.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                return i;
            }
        }
        return 99;
    }

    private ShowcaseScenes.Scene scene() {
        return scenes.get(sceneIndex);
    }

    private ShowcaseScenes.Plan plan() {
        ShowcaseScenes.Plan p = plan;
        if (p == null) {
            throw new IllegalStateException("no plan");
        }
        return p;
    }

    private static MinecraftServer server(Minecraft mc) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s == null) {
            throw new IllegalStateException("showcase needs the integrated server");
        }
        return s;
    }

    static File gameDir() {
        return Minecraft.getInstance().gameDirectory;
    }
}
