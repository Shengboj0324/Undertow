package dev.worldsfall.world.gravity;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFBiomes;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gravity Zones (§5.2) via attribute modifiers, applied/removed as entities enter/leave. Evaluated every
 * 5 ticks per living entity in the End and the Fracture. The camera never flips (D4).
 */
public final class GravityZones {
    public enum Zone {
        NONE(1.0, 1.0), LIGHT(0.45, 0.3), WELL(0.15, 0.1), STABLE(1.0, 1.0), LENS(-0.3, 0.0);
        public final double gravity;
        public final double fallDamage;
        Zone(double gravity, double fallDamage) {
            this.gravity = gravity;
            this.fallDamage = fallDamage;
        }
    }

    public static final Identifier GRAVITY_MOD = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "gravity_zone");
    public static final Identifier FALL_MOD = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "gravity_zone_fall");
    public static final Identifier SAFE_FALL_MOD = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "gravity_zone_safe_fall");

    /** Temporary reversed-gravity lenses from The Unmade (§13.5). */
    private static final List<Lens> LENSES = new ArrayList<>();

    public record Lens(net.minecraft.resources.ResourceKey<Level> dim, Vec3 center, double radius, long expiresAt) {}

    private GravityZones() {}

    public static void addLens(Level level, Vec3 center, double radius, int ticks) {
        synchronized (LENSES) {
            LENSES.add(new Lens(level.dimension(), center, radius, level.getGameTime() + ticks));
        }
    }

    public static List<Lens> lenses() {
        synchronized (LENSES) {
            return List.copyOf(LENSES);
        }
    }

    /** True if this position is in the Underside: the biome, or (fallback §20.4) under a solid ceiling below the island body. */
    public static boolean isUnderside(LevelReader level, BlockPos pos) {
        Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(pos);
        if (biome.is(WFBiomes.THE_UNDERSIDE)) {
            return true;
        }
        if (!(level instanceof Level l) || l.dimension() != Level.END || pos.getY() < 5 || pos.getY() > 60) {
            return false;
        }
        long distSq = (long) pos.getX() * pos.getX() + (long) pos.getZ() * pos.getZ();
        if (distSq < 150L * 150L && !dev.worldsfall.world.sundering.SunderingData.isSunderedClient(l)) {
            return false;
        }
        // open air here, and a sturdy ceiling within 24 blocks above
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        for (int dy = 2; dy <= 24; dy++) {
            BlockState s = level.getBlockState(pos.above(dy));
            if (!s.isAir()) {
                return s.isSolid() || s.getBlock() instanceof dev.worldsfall.block.HangingCrystalBlock;
            }
        }
        return false;
    }

    public static Zone zoneAt(Level level, LivingEntity entity) {
        if (entity.typeHolder().is(WFTags.Entities.GRAVITY_IMMUNE)) {
            return Zone.NONE;
        }
        Vec3 p = entity.position();
        synchronized (LENSES) {
            Iterator<Lens> it = LENSES.iterator();
            while (it.hasNext()) {
                Lens lens = it.next();
                if (lens.expiresAt < level.getGameTime() - 200) {
                    it.remove();
                } else if (lens.dim == level.dimension() && lens.expiresAt >= level.getGameTime()
                    && p.distanceToSqr(lens.center) <= lens.radius * lens.radius) {
                    return Zone.LENS;
                }
            }
        }
        if (level.dimension() != Level.END) {
            return Zone.NONE;
        }
        if (StableFields.isInside(level, p.x, p.y, p.z)) {
            return Zone.STABLE;
        }
        BlockPos pos = entity.blockPosition();
        if (GravityWells.isInWell(level, p)) {
            return Zone.WELL;
        }
        return isUnderside(level, pos) ? Zone.LIGHT : Zone.NONE;
    }

    public static double gravityFor(Zone zone) {
        double g = zone == Zone.LIGHT ? WFConfig.get(WFConfig.SERVER.gravityUnderside, 0.45) : zone.gravity;
        if (WFConfig.get(WFConfig.SERVER.reducedFloatiness, false) && zone != Zone.LENS) {
            g = 1 - (1 - g) * 0.5;
        }
        return g;
    }

    /** Applies modifiers for the entity's current zone. Returns the zone. */
    public static Zone update(Level level, LivingEntity entity) {
        Zone zone = zoneAt(level, entity);
        apply(entity, zone);
        return zone;
    }

    public static void apply(LivingEntity entity, Zone zone) {
        double g = gravityFor(zone);
        setModifier(entity, Attributes.GRAVITY, GRAVITY_MOD, zone == Zone.NONE || zone == Zone.STABLE ? 0 : g - 1);
        setModifier(entity, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MOD, zone == Zone.NONE || zone == Zone.STABLE ? 0 : zone.fallDamage - 1);
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = inst.getModifier(id);
        if (amount == 0) {
            if (existing != null) {
                inst.removeModifier(id);
            }
            return;
        }
        if (existing != null && Math.abs(existing.amount() - amount) < 1e-6) {
            return;
        }
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static boolean hasModifier(LivingEntity entity) {
        AttributeInstance inst = entity.getAttribute(Attributes.GRAVITY);
        return inst != null && inst.getModifier(GRAVITY_MOD) != null;
    }
}
