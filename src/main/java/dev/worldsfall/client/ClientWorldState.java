package dev.worldsfall.client;

import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.registry.WFBiomes;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.world.fracture.FractureArena;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Per-frame client-side environment state feeding the sky, tide plane, fog and HUD. */
public final class ClientWorldState {
    private static final ClientWorldState INSTANCE = new ClientWorldState();
    private static final int GHOST_TICKS = 1200;

    private final Vector3f nebula = new Vector3f(0.29f, 0.16f, 0.48f);
    private float nebulaStrength = 0.8f;
    private float haze;
    private float tideGlow;
    private float calmed;
    private final double[] history = new double[GHOST_TICKS];
    private int historyCount;
    private int historyIndex;
    private float motion;
    private float brightness = 1f;
    private float bossPulse;
    private float bossCalm;
    private final Vector3f coreDir = new Vector3f(0, 1, 0);
    private float coreSize = 0.08f;
    private Vec3 camera = Vec3.ZERO;
    private float riseFlash;
    private TideCycle.Phase lastPhase = TideCycle.Phase.LOW;
    // expansion: restoration and world events, from RestorationPayload
    private int restorationStage;
    private float restorationFraction;
    private int worldEvent;
    private long worldEventStart;
    private java.util.List<Long> orreryStars = java.util.List.of();
    private float stars = 1f;

    private ClientWorldState() {}

    public static ClientWorldState get() {
        return INSTANCE;
    }

    public void acceptRestoration(dev.worldsfall.network.RestorationPayload p) {
        restorationStage = p.stage();
        restorationFraction = p.fraction();
        worldEvent = p.event();
        worldEventStart = p.eventStart();
        orreryStars = java.util.List.copyOf(p.orreryStars());
    }

    public int restorationStage() { return restorationStage; }
    public float restorationFraction() { return restorationFraction; }
    public int worldEvent() { return worldEvent; }
    public long worldEventStart() { return worldEventStart; }
    public java.util.List<Long> orreryStars() { return orreryStars; }
    /** 1 normally; fades to almost nothing over ten seconds of an Eclipse of Stillness. */
    public float starBrightness() { return stars; }

    public void tick(Minecraft mc) {
        float starTarget = worldEvent == dev.worldsfall.life.WorldEvents.ECLIPSE ? 0.05f : 1f;
        stars += (starTarget - stars) * 0.02f;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        camera = mc.gameRenderer.mainCamera().position();
        BlockPos pos = mc.player.blockPosition();
        Holder<Biome> biome = level.getBiome(pos);
        Vector3f target;
        float hazeTarget = 0;
        if (biome.is(WFBiomes.CHORUS_REACH)) {
            target = new Vector3f(0.46f, 0.26f, 0.62f);
        } else if (biome.is(WFBiomes.STILLWATER_BASINS)) {
            target = new Vector3f(0.13f, 0.22f, 0.45f);
        } else if (biome.is(WFBiomes.FALLEN_SPIRES)) {
            target = new Vector3f(0.42f, 0.10f, 0.28f);
        } else if (biome.is(WFBiomes.THE_HOARD)) {
            target = new Vector3f(0.24f, 0.24f, 0.28f);
        } else if (biome.is(WFBiomes.THE_UNDERSIDE)) {
            target = new Vector3f(0.10f, 0.32f, 0.33f);
            hazeTarget = 1;
        } else if (biome.is(WFBiomes.THE_DRIFT)) {
            target = new Vector3f(0.12f, 0.26f, 0.36f);
            hazeTarget = 0.6f;
        } else {
            target = new Vector3f(0.30f, 0.16f, 0.48f);
        }
        // blended over ~64 blocks of travel / a few seconds
        nebula.lerp(target, 0.02f);
        haze += (hazeTarget - haze) * 0.02f;
        if (Tides.appliesTo(level)) {
            double h = Tides.height(level);
            history[historyIndex] = h;
            historyIndex = (historyIndex + 1) % GHOST_TICKS;
            historyCount = Math.min(GHOST_TICKS, historyCount + 1);
            TideCycle.Phase phase = Tides.phase(level);
            motion = phase == TideCycle.Phase.RISING ? 1f : phase == TideCycle.Phase.FALLING ? -1f : 0f;
            if (phase == TideCycle.Phase.RISING && lastPhase != TideCycle.Phase.RISING) {
                riseFlash = 1f;
            }
            lastPhase = phase;
            tideGlow = (float) Math.min(1.0, 0.45 + 0.55 * Tides.cycle(level).normalized(Tides.effectiveTime(level)));
            calmed += ((Tides.state(level).calmed() ? 1f : 0f) - calmed) * 0.05f;
            brightness = 1f + (Tides.isStirring(level) ? 0.12f * (0.5f + 0.5f * (float) Math.sin(level.getGameTime() * 0.15)) : 0f);
        }
        riseFlash = Math.max(0, riseFlash - 0.02f);
        if (level.dimension() == WFDimensions.THE_FRACTURE) {
            TheUnmade boss = level.getEntitiesOfClass(TheUnmade.class, new AABB(FractureArena.CENTER).inflate(160)).stream().findFirst().orElse(null);
            Vec3 core = boss != null ? boss.position().add(0, 2, 0) : Vec3.atCenterOf(FractureArena.CENTER).add(0, FractureArena.CORE_HEIGHT, 0);
            Vec3 d = core.subtract(camera);
            double dist = Math.max(1, d.length());
            coreDir.set((float) (d.x / dist), (float) (d.y / dist), (float) (d.z / dist));
            coreSize = (float) Math.max(0.035, Math.min(0.6, Math.atan(2.2 / dist)));
            if (boss != null) {
                bossPulse = 0.5f + 0.5f * (float) Math.sin(level.getGameTime() * Math.PI * 2 / Math.max(8f, boss.pulsePeriod()));
                bossCalm += ((boss.phase() == TheUnmade.PHASE_FINALE ? 1f : 0f) - bossCalm) * 0.05f;
            } else {
                bossCalm += (1f - bossCalm) * 0.02f;
            }
        }
    }

    public float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return ((mc.level.getGameTime() % 72000L) + partial) / 20f;
    }

    public Vector3f nebulaTint() { return new Vector3f(nebula); }
    public float nebulaStrength() { return nebulaStrength; }
    public float tideGlow() { return tideGlow; }
    public float calmed() { return calmed; }
    public float underHaze() { return haze; }
    public Vector3f cameraPos() { return new Vector3f((float) camera.x, (float) camera.y, (float) camera.z); }
    public float tideMotion() { return motion; }
    public float tideBrightness() { return brightness; }
    public float bossPulse() { return bossPulse; }
    public float bossCalm() { return bossCalm; }
    public Vector3f coreDirection() { return new Vector3f(coreDir); }
    public float coreAngularSize() { return coreSize; }
    public float riseFlash() { return riseFlash; }

    /** Tide height 60 s ago (the ghost line, §5.1), or NaN when there is not enough history. */
    public double ghostLineY() {
        if (historyCount < GHOST_TICKS) {
            return Double.NaN;
        }
        return history[historyIndex];
    }

    public float ghostAlpha() {
        return 0.55f;
    }

    public void reset() {
        historyCount = 0;
        historyIndex = 0;
    }
}
