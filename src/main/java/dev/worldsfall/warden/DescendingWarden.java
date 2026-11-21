package dev.worldsfall.warden;

import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.BlockQueue;
import dev.worldsfall.world.PlayerPlaced;
import dev.worldsfall.world.sundering.UndercroftBuilder;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.TideData;
import dev.worldsfall.world.tide.TideState;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Descending Warden (Pact of Depths): the dragon drags the fight down through three layers. Rim (100–60 %, vanilla) →
 * it dives into the Descent → the Warden's Gallery beneath the island (60–30 %, low gravity, Lumen Hearts heal it, its
 * breath rises, it perches upside down on the ceiling) → it breaks out into the Drift and forces a High Tide (30–0 %),
 * hittable only when it breaches near an island edge. It always dies over the exit portal (vanilla death phase).
 */
public final class DescendingWarden {
    public static final int GALLERY_RX = 17, GALLERY_RY = 8, GALLERY_Y = 31;
    static final int GALLERY_CYCLE = 460, PERCH_AT = 300;
    static final String RISING_TAG = "worldsfall_rising_breath";

    private static int stageTicks;
    private static int breachTimer = 200, breachWindup = -1, breachRise = -1, breachHover = -1;
    private static Vec3 breachAt = Vec3.ZERO;

    private DescendingWarden() {}

    /** Centre of the Warden's Gallery: beyond the Undercroft chamber, under the far side of the island. */
    public static BlockPos galleryCenter(ServerLevel end) {
        UndercroftBuilder.Layout l = UndercroftBuilder.layout(Wardensong.descent(end));
        return l.chamberCenter().relative(l.inward(), UndercroftBuilder.CHAMBER_RADIUS + 5 + GALLERY_RX).atY(GALLERY_Y);
    }

    public static boolean inGallery(Vec3 p, BlockPos c) {
        double dx = (p.x - c.getX()) / GALLERY_RX, dy = (p.y - c.getY()) / GALLERY_RY, dz = (p.z - c.getZ()) / GALLERY_RX;
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    /** Unseals and furnishes the Gallery the first time a Pact of Depths is used in this world. */
    public static void ensureGallery(ServerLevel end, WardenData data) {
        if (data.galleryBuilt()) {
            spawnHearts(end, galleryCenter(end));
            return;
        }
        data.setGalleryBuilt();
        BlockPos c = galleryCenter(end);
        UndercroftBuilder.Layout l = UndercroftBuilder.layout(Wardensong.descent(end));
        BlockQueue q = new BlockQueue(end, 600);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState shell = WFBlocks.POLISHED_UNDERSHALE.get().defaultBlockState();
        BlockState floor = WFBlocks.UNDERSHALE.get().defaultBlockState();
        var rng = net.minecraft.util.RandomSource.create(c.asLong());
        for (int dx = -GALLERY_RX - 1; dx <= GALLERY_RX + 1; dx++) {
            for (int dz = -GALLERY_RX - 1; dz <= GALLERY_RX + 1; dz++) {
                for (int dy = -GALLERY_RY - 1; dy <= GALLERY_RY + 1; dy++) {
                    double e = sq(dx / (double) GALLERY_RX) + sq(dy / (double) GALLERY_RY) + sq(dz / (double) GALLERY_RX);
                    BlockPos p = c.offset(dx, dy, dz);
                    if (!carvable(end, p)) {
                        continue;
                    }
                    if (e <= 1.0 && dy > -GALLERY_RY + 1) {
                        q.set(p, air);
                    } else if (e <= 1.0) {
                        q.set(p, floor);
                    } else if (e <= 1.25) {
                        q.set(p, shell);
                    }
                }
            }
        }
        // the sealed passage from the Undercroft chamber
        BlockPos from = l.chamberCenter().relative(l.inward(), UndercroftBuilder.CHAMBER_RADIUS - 1).atY(UndercroftBuilder.FLOOR_Y + 1);
        Direction side = l.inward().getClockWise();
        for (int i = 0; i <= 8; i++) {
            BlockPos step = from.relative(l.inward(), i);
            for (int w = -2; w <= 2; w++) {
                for (int h = 0; h <= 4; h++) {
                    BlockPos p = step.relative(side, w).above(h);
                    if (carvable(end, p)) {
                        q.set(p, Math.abs(w) == 2 || h == 4 ? shell : air);
                    }
                }
                BlockPos f = step.relative(side, w).below();
                if (carvable(end, f)) {
                    q.set(f, floor);
                }
            }
        }
        // floating shelves near the ceiling (the melee window when the dragon perches upside down)
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + 0.3;
            BlockPos s = c.offset((int) Math.round(Math.cos(a) * 9), GALLERY_RY - 5, (int) Math.round(Math.sin(a) * 9));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    q.set(s.offset(x, 0, z), WFBlocks.ANCHOR_FRAME.get().defaultBlockState());
                }
            }
        }
        // Voidroot curtains and Lumen crystals from the ceiling, lamps on the floor
        for (int k = 0; k < 26; k++) {
            int x = rng.nextInt(GALLERY_RX * 2 - 6) - GALLERY_RX + 3, z = rng.nextInt(GALLERY_RX * 2 - 6) - GALLERY_RX + 3;
            double ceil = GALLERY_RY * Math.sqrt(Math.max(0, 1 - sq(x / (double) GALLERY_RX) - sq(z / (double) GALLERY_RX)));
            int top = (int) Math.floor(ceil) - 1;
            if (top < 3) {
                continue;
            }
            int len = 2 + rng.nextInt(5);
            BlockState hang = k % 3 == 0 ? WFBlocks.LUMEN_CRYSTAL.get().defaultBlockState() : WFBlocks.VOIDROOT.get().defaultBlockState();
            for (int i = 0; i < len; i++) {
                q.set(c.offset(x, top - i, z), hang);
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            q.set(c.offset((int) (Math.cos(a) * 13), -GALLERY_RY + 2, (int) (Math.sin(a) * 13)),
                WFBlocks.LUMEN_LAMP.get().defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        }
        q.then(() -> spawnHearts(end, c)).start();
    }

    private static boolean carvable(ServerLevel end, BlockPos p) {
        BlockState s = end.getBlockState(p);
        if (PlayerPlaced.isPlayerPlaced(end, p)) {
            return false;
        }
        return s.isAir() || s.is(Blocks.END_STONE) || s.is(WFBlocks.UNDERSHALE.get()) || s.is(WFBlocks.POLISHED_UNDERSHALE.get())
            || s.is(WFBlocks.VOIDROOT.get()) || s.is(WFBlocks.LUMEN_CRYSTAL.get()) || s.is(WFBlocks.ANCHOR_FRAME.get());
    }

    /** Four Lumen Hearts hang from the ceiling and heal the dragon the way End Crystals do. */
    static void spawnHearts(ServerLevel end, BlockPos c) {
        if (!end.getEntitiesOfClass(LumenHeart.class, new AABB(c).inflate(GALLERY_RX + 2)).isEmpty()) {
            return;
        }
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            double x = c.getX() + 0.5 + Math.cos(a) * 10, z = c.getZ() + 0.5 + Math.sin(a) * 10;
            double ceil = GALLERY_RY * Math.sqrt(Math.max(0, 1 - sq(10.0 / GALLERY_RX)));
            LumenHeart heart = WFEntities.LUMEN_HEART.get().create(end, EntitySpawnReason.EVENT);
            if (heart != null) {
                heart.snapTo(x, c.getY() + ceil - 3.5, z, 0, 0);
                end.addFreshEntity(heart);
            }
        }
    }

    static void tick(ServerLevel end, EnderDragon dragon, WardenData data) {
        float ratio = dragon.getHealth() / dragon.getMaxHealth();
        stageTicks++;
        risingBreath(end);
        switch (data.depthsStage()) {
            case WardenData.DEPTHS_RIM -> {
                if (end.getGameTime() - data.fightStart() < 5) {
                    ensureGallery(end, data);
                }
                if (ratio <= 0.6f) {
                    data.setDepthsStage(WardenData.DEPTHS_DIVE);
                    stageTicks = 0;
                    WardenFight.playAt(end, dragon.position(), WFSounds.EVENT_WARDEN_DIVE.get(), 8.0f, 1.0f);
                    for (ServerPlayer p : WardenFight.fighters(end)) {
                        p.sendOverlayMessage(Component.translatable("message.worldsfall.warden.dives"));
                    }
                }
            }
            case WardenData.DEPTHS_DIVE -> dive(end, dragon, data);
            case WardenData.DEPTHS_GALLERY -> gallery(end, dragon, data, ratio);
            case WardenData.DEPTHS_DRIFT -> drift(end, dragon, data);
            default -> { }
        }
    }

    // ---- the dive into the Descent; a ring of Tidewisps lights the way down
    private static void dive(ServerLevel end, EnderDragon dragon, WardenData data) {
        BlockPos top = Wardensong.descent(end);
        Vec3 over = new Vec3(top.getX() + 0.5, top.getY() + 18, top.getZ() + 0.5);
        WardenFight.steer(dragon, over);
        for (int i = 0; i < 6; i++) {
            double a = (end.getGameTime() * 0.15) + i * Math.PI / 3;
            end.sendParticles(WFParticles.TIDEWISP_TRAIL.get(), top.getX() + 0.5 + Math.cos(a) * 3, top.getY() - (stageTicks % 40),
                top.getZ() + 0.5 + Math.sin(a) * 3, 1, 0, 0, 0, 0);
        }
        if (dragon.position().distanceToSqr(over) < 10 * 10 || stageTicks > 400) {
            BlockPos c = galleryCenter(end);
            WardenFight.playAt(end, over, WFSounds.EVENT_WARDEN_DIVE.get(), 8.0f, 0.8f);
            end.sendParticles(ParticleTypes.REVERSE_PORTAL, over.x, over.y - 10, over.z, 120, 3, 8, 3, 0.1);
            dragon.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, dragon.getYRot(), 0);
            data.setDepthsStage(WardenData.DEPTHS_GALLERY);
            stageTicks = 0;
            ensureGallery(end, data);
        }
    }

    // ---- the Warden's Gallery: circling, rising breath, upside-down perches
    private static void gallery(ServerLevel end, EnderDragon dragon, WardenData data, float ratio) {
        BlockPos c = galleryCenter(end);
        int phase = stageTicks % GALLERY_CYCLE;
        if (phase < PERCH_AT) {
            dragon.setData(WFAttachments.DRAGON_POSE, 0);
            double a = stageTicks * 0.035;
            Vec3 target = new Vec3(c.getX() + 0.5 + Math.cos(a + 0.6) * 10, c.getY() + 1 + Math.sin(stageTicks * 0.05) * 2,
                c.getZ() + 0.5 + Math.sin(a + 0.6) * 10);
            WardenFight.steer(dragon, target);
            if (phase == 120 || phase == 220) {
                breathe(end, dragon, c);
            }
        } else {
            double ceil = c.getY() + GALLERY_RY - 3.5;
            float yaw = (float) (stageTicks * 0.7 % 360);
            WardenFight.hold(dragon, new Vec3(c.getX() + 0.5, ceil, c.getZ() + 0.5), yaw);
            dragon.setData(WFAttachments.DRAGON_POSE, 1);
            if (phase == PERCH_AT) {
                for (ServerPlayer p : WardenFight.fighters(end)) {
                    p.sendOverlayMessage(Component.translatable("message.worldsfall.warden.perches"));
                }
            }
            if (phase == GALLERY_CYCLE - 1) {
                dragon.setData(WFAttachments.DRAGON_POSE, 0);
                dragon.setNoAi(false);
            }
        }
        if (ratio <= 0.3f) {
            dragon.setData(WFAttachments.DRAGON_POSE, 0);
            dragon.setNoAi(false);
            enterDrift(end, dragon, data);
        }
    }

    /** In low gravity the breath rises and pools against the ceiling for twenty seconds. */
    private static void breathe(ServerLevel end, EnderDragon dragon, BlockPos c) {
        AreaEffectCloud cloud = new AreaEffectCloud(end, dragon.getX(), c.getY() - GALLERY_RY + 2, dragon.getZ());
        cloud.setOwner(dragon);
        cloud.setCustomParticle(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f));
        cloud.setRadius(3.5f);
        cloud.setDuration(400);
        cloud.setRadiusPerTick(0.004f);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
        cloud.addTag(RISING_TAG);
        end.addFreshEntity(cloud);
        WardenFight.playAt(end, dragon.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_SHOOT, 6.0f, 0.9f);
    }

    private static void risingBreath(ServerLevel end) {
        BlockPos c = galleryCenter(end);
        for (AreaEffectCloud cloud : end.getEntitiesOfClass(AreaEffectCloud.class, new AABB(c).inflate(GALLERY_RX + 4),
            x -> x.entityTags().contains(RISING_TAG))) {
            if (cloud.getY() < c.getY() + GALLERY_RY - 3) {
                cloud.setPos(cloud.getX(), cloud.getY() + 0.05, cloud.getZ());
            }
        }
    }

    // ---- the Drift: a forced High Tide; the dragon is only hittable when it breaches near an island edge
    private static void enterDrift(ServerLevel end, EnderDragon dragon, WardenData data) {
        data.setDepthsStage(WardenData.DEPTHS_DRIFT);
        stageTicks = 0;
        breachTimer = 160;
        breachWindup = breachRise = breachHover = -1;
        TideData tide = TideData.get(end.getServer());
        if (data.savedTide() == null) {
            data.setSavedTide(tide.state());
        }
        long now = end.getServer().overworld().getGameTime();
        TideCycle cycle = Tides.cycle(end);
        tide.set(tide.state().resume(now).jumpTo(now, cycle.phaseStart(TideCycle.Phase.HIGH) + 40).pause(now));
        WFNetwork.syncTidesToAll(end.getServer());
        double a = Math.atan2(galleryCenter(end).getZ(), galleryCenter(end).getX());
        double y = Tides.height(end) - 7;
        dragon.snapTo(Math.cos(a) * 85, y, Math.sin(a) * 85, dragon.getYRot(), 0);
        WardenFight.playAt(end, dragon.position(), WFSounds.EVENT_KING_TIDE_RISE.get(), 10.0f, 1.0f);
        for (ServerPlayer p : WardenFight.fighters(end)) {
            p.sendOverlayMessage(Component.translatable("message.worldsfall.warden.drift"));
            WardenFight.play(p, WFSounds.EVENT_WARDEN_BREACH.get(), 1.0f, 0.7f);
        }
    }

    private static void drift(ServerLevel end, EnderDragon dragon, WardenData data) {
        double tideY = Tides.height(end);
        keepAboveTide(end, tideY);
        if (breachHover >= 0) {
            if (breachHover == 90) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
            }
            end.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 2, dragon.getZ(), 4, 3, 1.5, 3, 0.02);
            if (--breachHover < 0) {
                WardenFight.playAt(end, dragon.position(), WFSounds.EVENT_WARDEN_DIVE.get(), 6.0f, 1.0f);
                breachTimer = 220 + end.getRandom().nextInt(80);
            }
            return;
        }
        if (breachRise >= 0) {
            WardenFight.steer(dragon, breachAt.add(0, 8, 0));
            if (--breachRise < 0) {
                breachHover = 90;
            }
            return;
        }
        if (breachWindup >= 0) {
            for (int i = 0; i < 8; i++) {
                end.sendParticles(WFParticles.TIDE_CREST.get(), breachAt.x + (end.getRandom().nextDouble() - 0.5) * 4, tideY + i * 1.5,
                    breachAt.z + (end.getRandom().nextDouble() - 0.5) * 4, 1, 0, 0.1, 0, 0);
            }
            if (--breachWindup < 0) {
                dragon.snapTo(breachAt.x, tideY - 12, breachAt.z, dragon.getYRot(), 0);
                WardenFight.playAt(end, breachAt, WFSounds.EVENT_WARDEN_BREACH.get(), 10.0f, 1.0f);
                end.sendParticles(ParticleTypes.END_ROD, breachAt.x, tideY + 1, breachAt.z, 120, 3, 1, 3, 0.25);
                breachRise = 30;
            }
            return;
        }
        // circling under the tide
        double a = stageTicks * 0.012;
        WardenFight.steer(dragon, new Vec3(Math.cos(a) * 85, tideY - 7, Math.sin(a) * 85));
        if (--breachTimer <= 0) {
            ServerPlayer target = pick(end);
            if (target != null) {
                breachAt = edgeNear(end, target);
                breachWindup = 40;
                WardenFight.playAt(end, breachAt, WFSounds.EVENT_WARDEN_DIVE_WARNING.get(), 6.0f, 0.9f);
            } else {
                breachTimer = 60;
            }
        }
    }

    /** A breach point within twelve blocks of solid ground, on the island's rim nearest the player. */
    private static Vec3 edgeNear(ServerLevel end, ServerPlayer p) {
        double a = Math.atan2(p.getZ(), p.getX());
        int lastSolid = 20;
        int lastTop = 60;
        for (int r = 20; r < 140; r += 2) {
            int x = Mth.floor(Math.cos(a) * r), z = Mth.floor(Math.sin(a) * r);
            int top = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top > end.getMinY() + 2) {
                lastSolid = r;
                lastTop = top;
            } else if (r - lastSolid > 6) {
                break;
            }
        }
        double r = lastSolid + 7;
        return new Vec3(Math.cos(a) * r, lastTop + 4, Math.sin(a) * r);
    }

    private static ServerPlayer pick(ServerLevel end) {
        var list = WardenFight.fighters(end).stream().filter(WardenFight::isFighter).toList();
        return list.isEmpty() ? null : list.get(end.getRandom().nextInt(list.size()));
    }

    /** Dragon knockback in the Drift cannot throw a player below the tide: they float down slowly and stop above it. */
    private static void keepAboveTide(ServerLevel end, double tideY) {
        for (ServerPlayer p : WardenFight.fighters(end)) {
            if (!WardenFight.isFighter(p) || p.onGround()) {
                continue;
            }
            if (p.getY() < tideY + 8 && p.getDeltaMovement().y < 0) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, true));
            }
            if (p.getY() < tideY + 1.5) {
                p.setDeltaMovement(p.getDeltaMovement().x, 0.25, p.getDeltaMovement().z);
                p.needsSync = true;
            }
        }
    }

    static boolean invulnerable(ServerLevel end, EnderDragon dragon, WardenData data) {
        return switch (data.depthsStage()) {
            case WardenData.DEPTHS_DIVE -> true;
            case WardenData.DEPTHS_DRIFT -> breachHover < 0 && dragon.getY() < Tides.height(end) + 1.0;
            default -> false;
        };
    }

    static void restoreTide(ServerLevel end, WardenData data) {
        TideState saved = data.savedTide();
        if (saved != null) {
            TideData.get(end.getServer()).set(saved.withCalmed(TideData.get(end.getServer()).state().calmed()));
            data.setSavedTide(null);
            WFNetwork.syncTidesToAll(end.getServer());
        }
        stageTicks = 0;
    }

    private static double sq(double v) {
        return v * v;
    }
}
