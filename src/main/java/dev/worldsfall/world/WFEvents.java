package dev.worldsfall.world;

import dev.worldsfall.block.HoardCacheBlock;
import dev.worldsfall.block.entity.HoardCacheBlockEntity;
import dev.worldsfall.command.WFCommands;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.SkateJourneys;
import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.fluid.StillwaterEffects;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.world.fracture.FractureArena;
import dev.worldsfall.world.fracture.FractureData;
import dev.worldsfall.world.gravity.GravityZones;
import dev.worldsfall.world.gravity.StableFields;
import dev.worldsfall.world.reassembly.Reassembly;
import dev.worldsfall.world.sundering.Sundering;
import dev.worldsfall.world.tide.TideEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Optional;

/** Game-bus glue for the world systems. */
public final class WFEvents {
    private WFEvents() {}

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Reassembly.tick(level);
        BlockQueue.tickAll(level);
        if (level.dimension() == Level.END) {
            TideEffects.tick(level);
            Sundering.tick(level);
            NaturalSpawners.tickEnd(level);
        } else if (level.dimension() == WFDimensions.THE_FRACTURE) {
            FractureArena.tickDecay(level);
            FractureEnvironment.tick(level);
        }
    }

    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }
        StillwaterEffects.tick(entity);
        if (entity instanceof LivingEntity living && (entity.tickCount + entity.getId()) % 5 == 0) {
            boolean relevant = level.dimension() == Level.END || level.dimension() == WFDimensions.THE_FRACTURE;
            if (relevant || GravityZones.hasModifier(living)) {
                GravityZones.Zone zone = GravityZones.update(level, living);
                if (living instanceof ServerPlayer sp && zone == GravityZones.Zone.LIGHT && entity.tickCount % 40 == 0) {
                    Progress.award(sp, "underneath");
                }
                if (zone == GravityZones.Zone.WELL && !living.onGround()) {
                    living.push(0, 0.02, 0);
                }
                if (level.dimension() == Level.END && !living.onGround() && living.getDeltaMovement().y < -0.5
                    && StableFields.isBelowField(level, living.getX(), living.getY(), living.getZ())
                    && !living.hasEffect(MobEffects.SLOW_FALLING)) {
                    living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
                }
            }
            if (entity instanceof ServerPlayer sp && level.dimension() == Level.END && entity.tickCount % 10 == 0) {
                markGateways(sp);
            }
        }
    }

    private static void markGateways(ServerPlayer sp) {
        BlockPos p = sp.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(2, 3, 2))) {
            if (sp.level().getBlockState(q).is(Blocks.END_GATEWAY)) {
                var used = new java.util.HashSet<>(sp.getData(WFAttachments.USED_GATEWAYS));
                if (used.add(q.immutable())) {
                    sp.setData(WFAttachments.USED_GATEWAYS, used);
                }
            }
        }
    }

    @SubscribeEvent
    public static void livingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity instanceof EnderDragon && level.dimension() == Level.END) {
            boolean killedBefore = level.getDragonFight() != null && level.getDragonFight().hasPreviouslyKilledDragon();
            Sundering.onDragonDeath(level);
            WFConfig.EggFragments mode = WFConfig.get(WFConfig.SERVER.eggFragments, WFConfig.EggFragments.SERVERS);
            boolean dedicated = level.getServer().isDedicatedServer();
            if (killedBefore && (mode == WFConfig.EggFragments.ALWAYS || (mode == WFConfig.EggFragments.SERVERS && dedicated))) {
                ItemEntity frag = new ItemEntity(level, 0.5, 70, 0.5, new ItemStack(WFItems.DRAGON_EGG_FRAGMENT.get()));
                frag.setUnlimitedLifetime();
                level.addFreshEntity(frag);
            }
        }
        if (entity instanceof ServerPlayer player) {
            VoidAnchorHandler.onDeath(player, event.getSource());
        }
    }

    @SubscribeEvent
    public static void livingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().getBooleanOr("worldsfall_anchored", false)) {
            // Anchor already moved everything it saves; remaining drops (cracked anchor) fall normally.
            event.getDrops().removeIf(item -> item.getItem().isEmpty());
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (sp.getPersistentData().getBooleanOr("worldsfall_anchored", false)) {
                sp.getPersistentData().remove("worldsfall_anchored");
                Progress.award(sp, "anchored");
            }
            WFNetwork.syncPlayer(sp);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            WFNetwork.syncPlayer(sp);
            if (sp.level().dimension() == Level.END) {
                Sundering.onPlayerEnterEnd(sp, sp.level());
            }
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            WFNetwork.syncPlayer(sp);
            if (event.getTo() == Level.END) {
                Sundering.onPlayerEnterEnd(sp, sp.level());
                Hints.show(sp, "end_arrival");
            }
        }
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        PlayerPlaced.mark(level, pos, true);
        BlockState placed = event.getPlacedBlock();
        if (level.dimension() == Level.END && placed.getBlock().asItem() != net.minecraft.world.item.Items.AIR) {
            sp.setData(WFAttachments.LAST_PLACED, Optional.of(placed.getBlock().defaultBlockState()));
        }
        if (level.dimension() == WFDimensions.THE_FRACTURE && WFConfig.get(WFConfig.SERVER.bossBlockDecay, true)
            && !FractureData.get(level.getServer()).defeated()
            && !level.getEntitiesOfClass(TheUnmade.class, new AABB(pos).inflate(128), TheUnmade::isActive).isEmpty()) {
            FractureArena.scheduleDecay(level, pos);
        }
    }

    @SubscribeEvent
    public static void breakBlock(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer sp)) {
            return;
        }
        PlayerPlaced.mark(level, event.getPos(), false);
        if (level.dimension() == Level.END && !sp.isCreative()) {
            for (BlockPos p : BlockPos.betweenClosed(event.getPos().offset(-10, -5, -10), event.getPos().offset(10, 5, 10))) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof HoardCacheBlock cache && cache.barters() && level.getBlockEntity(p) instanceof HoardCacheBlockEntity be) {
                    if (!be.recentlyBartered(level.getGameTime())) {
                        be.anger(level, sp);
                    }
                    break;
                }
            }
        }
        if (level.dimension() == WFDimensions.THE_FRACTURE && !FractureData.get(level.getServer()).defeated()
            && (event.getState().is(WFBlocks.NULL_OBSIDIAN.get()) || event.getState().is(WFBlocks.FRACTURE_GLASS.get())) && !sp.isCreative()) {
            event.setCanceled(true);
        }
    }

    /** Dismounting a flying Skate needs a perch or a deliberate double-tap (§8.2). */
    @SubscribeEvent
    public static void mount(EntityMountEvent event) {
        if (event.getEntityBeingMounted() instanceof VoidSkate skate && event.getEntityMounting() instanceof ServerPlayer sp) {
            if (event.isMounting()) {
                SkateJourneys.onMount(sp, skate);
                Hints.show(sp, "skate_ride");
                return;
            }
            boolean allowed = skate.isPerched() || skate.onGround() || sp.getPersistentData().getBooleanOr("worldsfall_allow_dismount", false)
                || !sp.isAlive() || skate.isRemoved() || !skate.isAlive();
            sp.getPersistentData().remove("worldsfall_allow_dismount");
            if (!allowed) {
                event.setCanceled(true);
            } else {
                SkateJourneys.onDismount(sp);
            }
        }
    }

    @SubscribeEvent
    public static void effectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().is(MobEffects.LEVITATION.unwrapKey().orElseThrow())
            && event.getEntity().hasEffect(WFEffects.STEADINESS)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void explosionKnockback(ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof LivingEntity living && living.hasEffect(WFEffects.STEADINESS)) {
            event.setKnockbackVelocity(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    @SubscribeEvent
    public static void pearl(EntityTeleportEvent.EnderPearl event) {
        Player p = event.getPlayer();
        if (p.level().dimension() == WFDimensions.THE_FRACTURE) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 0));
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        WFCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Reassembly.finishAll();
        BlockQueue.finishAll();
        StableFields.clear(false);
    }
}
