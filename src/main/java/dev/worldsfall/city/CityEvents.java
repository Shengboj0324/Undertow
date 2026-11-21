package dev.worldsfall.city;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.effect.SinkingEffect;
import dev.worldsfall.nav.Lattice;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/** Game events for End Cities and navigation (release 1.4): the Lattice hold, Archivist spawning, Sinking Bullets. */
public final class CityEvents {
    /** Chance that a city has an Archivist. */
    public static final double ARCHIVIST_CHANCE = 0.65;

    private CityEvents() {}

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Lattice.tickPlayer(player);
            if (player.tickCount % 100 == 37 && player.level() instanceof ServerLevel level && level.dimension() == Level.END) {
                visitCity(level, player);
            }
        }
    }

    /** The first time a player stands in an End City, it may gain a Shulker Archivist in one of its rooms. */
    static void visitCity(ServerLevel level, ServerPlayer player) {
        StructureStart start = Cities.cityAt(level, player.blockPosition());
        if (start == null) {
            return;
        }
        long key = start.getChunkPos().pack();
        CityData data = CityData.get(level.getServer());
        if (data.seen(key)) {
            return;
        }
        RandomSource rng = RandomSource.create(level.getSeed() ^ key * 0x2545F4914F6CDD1DL);
        if (rng.nextDouble() >= ARCHIVIST_CHANCE) {
            data.markSeen(key);
            return;
        }
        List<StructurePiece> floors = start.getPieces().stream().filter(p -> Cities.FLOORS.contains(Cities.template(level, p))).toList();
        if (floors.isEmpty()) {
            data.markSeen(key);
            return;
        }
        if (spawnArchivist(level, floors.get(rng.nextInt(floors.size())).getBoundingBox(), rng)) {
            data.markSeen(key);
        }
    }

    static boolean spawnArchivist(ServerLevel level, BoundingBox room, RandomSource rng) {
        for (int i = 0; i < 60; i++) {
            BlockPos p = new BlockPos(room.minX() + 2 + rng.nextInt(Math.max(1, room.getXSpan() - 4)), room.minY() + 1 + rng.nextInt(Math.max(1, room.getYSpan() - 2)),
                room.minZ() + 2 + rng.nextInt(Math.max(1, room.getZSpan() - 4)));
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                continue;
            }
            ShulkerArchivist a = WFEntities.SHULKER_ARCHIVIST.get().create(level, EntitySpawnReason.STRUCTURE);
            if (a == null) {
                return false;
            }
            a.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            level.addFreshEntity(a);
            return true;
        }
        return false;
    }

    /** At High Tide, City Shulkers fire Sinking Bullets: a hit pulls you down instead of up. */
    @SubscribeEvent
    public static void bulletImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ShulkerBullet bullet) || !(bullet.level() instanceof ServerLevel level)
            || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)
            || !sinkingNow(level)) {
            return;
        }
        event.setCanceled(true);
        Entity owner = bullet.getOwner();
        DamageSource source = bullet.damageSources().mobProjectile(bullet, owner instanceof LivingEntity l ? l : null);
        if (target.hurtServer(level, source, 4.0f)) {
            target.addEffect(new MobEffectInstance(WFEffects.SINKING, SinkingEffect.BULLET_TICKS), owner != null ? owner : bullet);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, bullet.getX(), bullet.getY(), bullet.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(dev.worldsfall.registry.WFParticles.DOWNDRAFT_DUST.get(), target.getX(), target.getY() + 1, target.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
        bullet.playSound(SoundEvents.SHULKER_BULLET_HIT, 1.0f, 0.7f);
        bullet.discard();
    }

    public static boolean sinkingNow(ServerLevel level) {
        return level.dimension() == Level.END && Tides.appliesTo(level) && Tides.phase(level) == TideCycle.Phase.HIGH
            && WFConfig.get(WFConfig.SERVER.sinkingBullets, true);
    }
}
