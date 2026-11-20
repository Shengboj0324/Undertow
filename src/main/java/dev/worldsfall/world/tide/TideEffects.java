package dev.worldsfall.world.tide;

import dev.worldsfall.entity.Tidewisp;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.gravity.StableFields;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side tide ticking (§5.1, §5.3): phase-change sync and sounds, the per-entity Y comparison every
 * 10 ticks (Void Erosion, item destruction after 2 s), and Tidewisp gathering before Rising.
 */
public final class TideEffects {
    public static final String EROSION_TICKS_KEY = "worldsfall_erosion_ticks";
    public static final String BELOW_TICKS_KEY = "worldsfall_below_tide";
    private static TideCycle.Phase lastPhase;
    private static boolean lastStirring;

    private TideEffects() {}

    public static void tick(ServerLevel end) {
        long time = end.getGameTime();
        TideCycle.Phase phase = Tides.phase(end);
        boolean stirring = Tides.isStirring(end);
        if (lastPhase != null && (phase != lastPhase || stirring != lastStirring)) {
            WFNetwork.syncTidesToAll(end.getServer());
            onPhaseChange(end, phase, stirring && !lastStirring);
        }
        lastPhase = phase;
        lastStirring = stirring;
        if (time % 600 == 0) {
            WFNetwork.syncTidesToAll(end.getServer());
        }
        if (time % 10 == 0) {
            checkEntities(end);
        }
        if (stirring && time % 100 == 0) {
            spawnTidewisps(end);
        }
    }

    private static void onPhaseChange(ServerLevel end, TideCycle.Phase phase, boolean stirStarted) {
        for (ServerPlayer p : end.players()) {
            if (stirStarted) {
                dev.worldsfall.util.WFUtil.playTo(p, WFSounds.EVENT_TIDE_STIR.get(), SoundSource.AMBIENT, 0.9f, 1.0f);
            } else if (phase == TideCycle.Phase.RISING) {
                dev.worldsfall.util.WFUtil.playTo(p, WFSounds.EVENT_TIDE_RISE.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
            } else if (phase == TideCycle.Phase.FALLING) {
                dev.worldsfall.util.WFUtil.playTo(p, WFSounds.EVENT_TIDE_FALL.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
            }
        }
    }

    /** Whether this entity is protected from Erosion (§5.3): Stable Field, riding a Skate, Drake ward, tag. */
    public static boolean isProtected(LivingEntity e) {
        if (e.typeHolder().is(WFTags.Entities.EROSION_IMMUNE) || e instanceof VoidSkate) {
            return true;
        }
        if (e.getVehicle() instanceof VoidSkate) {
            return true;
        }
        if (StableFields.isInside(e.level(), e.getX(), e.getY(), e.getZ())) {
            return true;
        }
        return e instanceof Player p && Wyrm.wardsPlayer(p);
    }

    static void checkEntities(ServerLevel end) {
        double tide = Tides.height(end);
        for (Entity entity : end.getAllEntities()) {
            if (entity instanceof ItemEntity item) {
                handleItem(item, tide);
                continue;
            }
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            var data = living.getPersistentData();
            boolean below = living.getY() < tide;
            if (below && !isProtected(living) && !(living instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                data.putInt(EROSION_TICKS_KEY, data.getIntOr(EROSION_TICKS_KEY, 0) + 10);
                living.addEffect(new MobEffectInstance(WFEffects.VOID_EROSION, 30, 0, true, true, true));
                if (end.getGameTime() % 30 == 0) {
                    end.sendParticles(WFParticles.EROSION_STATIC.get(), living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                        4, living.getBbWidth() * 0.4, living.getBbHeight() * 0.4, living.getBbWidth() * 0.4, 0.0);
                    if (living instanceof ServerPlayer sp) {
                        dev.worldsfall.util.WFUtil.playTo(sp, WFSounds.EVENT_EROSION_STATIC.get(), SoundSource.PLAYERS, 0.5f, 1.0f);
                    }
                }
            } else if (data.getIntOr(EROSION_TICKS_KEY, 0) != 0) {
                data.putInt(EROSION_TICKS_KEY, 0);
            }
        }
    }

    /** Items below the plane are destroyed after 2 s, like the vanilla void (§5.1). */
    private static void handleItem(ItemEntity item, double tide) {
        var data = item.getPersistentData();
        if (item.getY() < tide) {
            int t = data.getIntOr(BELOW_TICKS_KEY, 0) + 10;
            if (t >= 40) {
                item.discard();
            } else {
                data.putInt(BELOW_TICKS_KEY, t);
            }
        } else if (data.contains(BELOW_TICKS_KEY)) {
            data.remove(BELOW_TICKS_KEY);
        }
    }

    /** Before Rising, Tidewisps gather near island edges around players (§8.7, §17.1). */
    static void spawnTidewisps(ServerLevel end) {
        for (ServerPlayer p : end.players()) {
            int nearby = end.getEntitiesOfClass(Tidewisp.class, p.getBoundingBox().inflate(32)).size();
            if (nearby >= 12) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                BlockPos pos = p.blockPosition().offset(end.getRandom().nextInt(25) - 12, end.getRandom().nextInt(9) - 2, end.getRandom().nextInt(25) - 12);
                if (end.getBlockState(pos).isAir()) {
                    Tidewisp w = WFEntities.TIDEWISP.get().create(end, EntitySpawnReason.NATURAL);
                    if (w != null) {
                        w.snapTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0);
                        end.addFreshEntity(w);
                    }
                }
            }
        }
    }
}
