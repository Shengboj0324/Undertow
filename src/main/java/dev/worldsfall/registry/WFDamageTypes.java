package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Damage types (Appendix C); definitions are data (damage_type JSON). */
public final class WFDamageTypes {
    public static final ResourceKey<DamageType> VOID_EROSION = key("void_erosion");
    public static final ResourceKey<DamageType> LURKER_SNARE = key("lurker_snare");
    public static final ResourceKey<DamageType> WARDEN_SLAM = key("warden_slam");
    public static final ResourceKey<DamageType> UNMADE_SHARD = key("unmade_shard");
    public static final ResourceKey<DamageType> UNMADE_TENDRIL = key("unmade_tendril");
    public static final ResourceKey<DamageType> VOID_PULSE = key("void_pulse");

    private WFDamageTypes() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity cause) {
        return level.damageSources().source(type, direct, cause);
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type) {
        return level.damageSources().source(type);
    }
}
