package dev.worldsfall.warden;

import dev.worldsfall.entity.Hoarder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** NeoForge events of the Warden Cycle; the End's level tick calls {@link WardenFight#tick}. */
public final class WardenEvents {
    private WardenEvents() {}

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.END || event.loadedFromDisk()) {
            return;
        }
        Entity e = event.getEntity();
        if (e instanceof EnderDragon dragon && !(dragon instanceof ElderWarden)) {
            WardenFight.onDragonSpawned(level, dragon);
        } else if (e instanceof EndCrystal crystal && !(crystal instanceof LumenHeart) && WardenRelease.refusesCrystal(level, crystal)
            && !dev.worldsfall.config.WFConfig.get(dev.worldsfall.config.WFConfig.SERVER.dragonRespawnAfterRelease, false)) {
            event.setCanceled(true);
            level.addFreshEntity(new ItemEntity(level, crystal.getX(), crystal.getY() + 0.5, crystal.getZ(), new ItemStack(Items.END_CRYSTAL)));
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.END && event.getEntity() instanceof EndCrystal crystal
            && crystal.getRemovalReason() == Entity.RemovalReason.KILLED) {
            WardenFight.onCrystalRemoved(level, crystal);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon && dragon.level() instanceof ServerLevel level && level.dimension() == Level.END) {
            WardenFight.onDragonDeath(level, dragon);
        }
    }

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon && dragon.level() instanceof ServerLevel level && level.dimension() == Level.END
            && WardenFight.ignoresDamage(level, dragon, event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void knockback(LivingKnockBackEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && level.dimension() == Level.END) {
            float scale = WardenFight.knockbackScale(level, event.getEntity());
            if (scale != 1.0f) {
                event.setStrength(event.getStrength() * scale);
            }
        }
    }

    @SubscribeEvent
    public static void grief(LivingDestroyBlockEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon && dragon.level() instanceof ServerLevel level && !WardenFight.mayGrief(level, dragon)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void dragonTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EnderDragon dragon && !(dragon instanceof ElderWarden) && dragon.level() instanceof ServerLevel level
            && level.dimension() == Level.END && WardenData.get(level.getServer()).has(Pact.LONE)
            && dragon.getUUID().equals(WardenData.get(level.getServer()).dragon())) {
            PactBehaviours.loneWardenMove(dragon);
        }
    }

    /** Lumen Hearts shatter into light instead of exploding like End Crystals. */
    @SubscribeEvent
    public static void invulnerability(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() instanceof LumenHeart heart && heart.level() instanceof ServerLevel level && !event.isInvulnerable()) {
            event.setInvulnerable(true);
            heart.shatter(level, event.getSource());
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        ElderWarden elder = target instanceof ElderWarden w ? w
            : target instanceof EnderDragonPart part && part.parentMob instanceof ElderWarden w2 ? w2 : null;
        if (elder != null && event.getEntity() instanceof ServerPlayer player) {
            elder.offer(player, event.getHand());
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Hoarder h && PactBehaviours.isThief(h) && h.level() instanceof ServerLevel level) {
            PactBehaviours.onThiefDeath(level, h, event.getDrops());
        }
    }

    /** Endermen (and Echoes) cannot teleport within 32 blocks of a Warden's Lantern. */
    @SubscribeEvent
    public static void enderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntity().level() instanceof ServerLevel level && (event.getEntity() instanceof Enderman || event.getEntity() instanceof dev.worldsfall.entity.EnderCaste)
            && WardensLanternBlock.guarded(level, event.getTargetX(), event.getTargetY(), event.getTargetZ())) {
            event.setCanceled(true);
        }
    }
}
