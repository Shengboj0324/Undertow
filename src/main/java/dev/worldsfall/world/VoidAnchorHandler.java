package dev.worldsfall.world;

import dev.worldsfall.entity.VoidCapsule;
import dev.worldsfall.item.VoidAnchorItem;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Void Anchor trigger (§9.2): on a void death (vanilla void or below the tide plane), consumes one anchor and
 * seals the inventory (all of it; a random half of stacks for a cracked anchor) plus 50% XP in a capsule.
 */
public final class VoidAnchorHandler {
    private VoidAnchorHandler() {}

    public static boolean isVoidDeath(ServerPlayer player, DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
            || source.is(dev.worldsfall.registry.WFDamageTypes.VOID_EROSION)
            || source.is(dev.worldsfall.registry.WFDamageTypes.VOID_PULSE)
            || (Tides.appliesTo(player.level()) && player.getY() < Tides.height(player.level()));
    }

    /** Returns true if the anchor triggered (the caller must then prevent normal drops). */
    public static boolean onDeath(ServerPlayer player, DamageSource source) {
        if (!isVoidDeath(player, source) || player.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)) {
            return false;
        }
        Inventory inv = player.getInventory();
        int anchorSlot = -1;
        boolean cracked = false;
        boolean abyssal = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof dev.worldsfall.undertow.AbyssalAnchorItem && !player.getCooldowns().isOnCooldown(s)) {
                // an Abyssal Anchor is preferred: it is not consumed, only spent for twenty minutes
                anchorSlot = i;
                cracked = false;
                abyssal = true;
                break;
            }
            if (s.getItem() instanceof VoidAnchorItem anchor && !(s.getItem() instanceof dev.worldsfall.undertow.AbyssalAnchorItem)) {
                if (anchorSlot < 0 || (cracked && !anchor.cracked())) {
                    anchorSlot = i;
                    cracked = anchor.cracked();
                }
            }
        }
        if (anchorSlot < 0) {
            return false;
        }
        ItemStack keep = ItemStack.EMPTY;
        if (abyssal) {
            keep = inv.getItem(anchorSlot).copy();
            inv.setItem(anchorSlot, ItemStack.EMPTY);
        } else {
            inv.getItem(anchorSlot).shrink(1);
        }
        ServerLevel level = player.level();
        List<ItemStack> sealed = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (!cracked || level.getRandom().nextBoolean()) {
                sealed.add(s.copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        int xp = player.totalExperience / 2;
        VoidCapsule capsule = WFEntities.VOID_CAPSULE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (capsule == null) {
            return false;
        }
        capsule.fill(player.getUUID(), sealed, xp);
        capsule.snapTo(player.getX(), VoidCapsule.restingY(level), player.getZ(), 0, 0);
        level.addFreshEntity(capsule);
        player.setData(WFAttachments.LAST_CAPSULE, Optional.of(net.minecraft.core.GlobalPos.of(level.dimension(), capsule.blockPosition())));
        if (abyssal) {
            inv.setItem(anchorSlot, keep);
            player.getCooldowns().addCooldown(keep, dev.worldsfall.undertow.AbyssalAnchorItem.RECHARGE);
        }
        level.playSound(null, capsule.blockPosition(), WFSounds.ITEM_VOID_ANCHOR_TRIGGER.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        player.setExperiencePoints(0);
        player.setExperienceLevels(0);
        player.getPersistentData().putBoolean("worldsfall_anchored", true);
        return true;
    }
}
