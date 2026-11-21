package dev.worldsfall.undertow;

import dev.worldsfall.life.LostThings;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.world.tide.TideEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/**
 * The Undertow's own rules, run from its level tick: Void Erosion everywhere outside a Void Bell's bubble at double speed
 * (a Potion of Tidesight halves it), the ceiling and floor that return you to the End, lost things drifting by, and the
 * creatures that live in the haze.
 */
public final class UndertowEvents {
    public static final int GLIMMER_CAP = 24, EEL_CAP = 3;

    private UndertowEvents() {}

    public static boolean isUndertow(net.minecraft.world.level.Level level) {
        return level.dimension() == WFDimensions.THE_UNDERTOW;
    }

    public static void tick(ServerLevel u) {
        for (Entity e : toList(u)) {
            if (e.isPassenger()) {
                continue;
            }
            if (e.getY() > UndertowTravel.CEILING_Y || e.getY() < UndertowTravel.FLOOR_Y) {
                UndertowTravel.exit(u, e);
            }
        }
        if (u.getGameTime() % 10 == 0) {
            erosion(u);
        }
        if (u.getGameTime() % 100 == 0) {
            populate(u);
        }
    }

    private static java.util.List<Entity> toList(ServerLevel u) {
        java.util.List<Entity> out = new ArrayList<>();
        u.getAllEntities().forEach(out::add);
        return out;
    }

    /** Outside the bubble the void eats twice as fast; Tidesight halves it back. */
    static void erosion(ServerLevel u) {
        for (Entity e : toList(u)) {
            if (!(e instanceof LivingEntity l) || !l.isAlive() || l instanceof VoidBell || TideEffects.isProtected(l) || VoidBell.sheltered(l)
                || l.typeHolder().is(dev.worldsfall.registry.WFTags.Entities.EROSION_IMMUNE)
                || l instanceof Player p && (p.isCreative() || p.isSpectator())) {
                if (e instanceof LivingEntity le) {
                    le.getPersistentData().putInt(TideEffects.EROSION_TICKS_KEY, 0);
                }
                continue;
            }
            var data = l.getPersistentData();
            int rate = l.hasEffect(WFEffects.TIDESIGHT) ? 10 : 20;
            data.putInt(TideEffects.EROSION_TICKS_KEY, data.getIntOr(TideEffects.EROSION_TICKS_KEY, 0) + rate);
            l.addEffect(new MobEffectInstance(WFEffects.VOID_EROSION, 30, l.hasEffect(WFEffects.TIDESIGHT) ? 0 : 1, true, true, true));
        }
    }

    /** Glimmer shoals, a few Tide Eels and Drowned Ascendants, and lost things drifting past each player. */
    static void populate(ServerLevel u) {
        RandomSource r = u.getRandom();
        for (ServerPlayer p : u.players()) {
            AABB near = p.getBoundingBox().inflate(48);
            if (u.getEntitiesOfClass(Glimmer.class, near).size() < GLIMMER_CAP) {
                Vec3 c = p.position().add(r.nextGaussian() * 16, r.nextGaussian() * 6, r.nextGaussian() * 16);
                int n = 5 + r.nextInt(6);
                for (int i = 0; i < n; i++) {
                    spawn(u, WFEntities.GLIMMER.get(), c.add(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()));
                }
            }
            if (r.nextInt(4) == 0 && u.getEntitiesOfClass(TideEel.class, near.inflate(16)).size() < EEL_CAP) {
                double a = r.nextDouble() * Math.PI * 2;
                spawn(u, WFEntities.TIDE_EEL.get(), p.position().add(Math.cos(a) * 40, r.nextGaussian() * 8, Math.sin(a) * 40));
            }
            if (r.nextInt(3) == 0) {
                ItemStack lost = LostThings.take(u.getServer());
                if (!lost.isEmpty()) {
                    double a = r.nextDouble() * Math.PI * 2;
                    ItemEntity it = new ItemEntity(u, p.getX() + Math.cos(a) * 20, p.getY() + r.nextGaussian() * 4, p.getZ() + Math.sin(a) * 20, lost);
                    it.setNoGravity(true);
                    it.setDeltaMovement(-Math.cos(a) * 0.03, 0, -Math.sin(a) * 0.03);
                    it.setUnlimitedLifetime();
                    u.addFreshEntity(it);
                }
            }
        }
    }

    private static <T extends Entity> void spawn(ServerLevel u, net.minecraft.world.entity.EntityType<T> type, Vec3 at) {
        T e = type.create(u, EntitySpawnReason.NATURAL);
        if (e != null && u.getBlockState(net.minecraft.core.BlockPos.containing(at)).isAir()) {
            e.snapTo(at.x, at.y, at.z, u.getRandom().nextFloat() * 360, 0);
            u.addFreshEntity(e);
        }
    }
}
