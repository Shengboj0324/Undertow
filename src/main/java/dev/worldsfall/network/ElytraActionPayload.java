package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.ElytraUpgrade;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: an elytra upgrade fired (§9.7). Movement is client-authoritative; the server validates the
 * upgrade, charges durability (Gale 3 per boost, Still 1 per second, Hover 2 per hover) and plays effects.
 */
public record ElytraActionPayload(int action) implements CustomPacketPayload {
    public static final int GALE = 0, STILL_SECOND = 1, HOVER = 2;
    public static final Type<ElytraActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "elytra_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElytraActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ElytraActionPayload::action, ElytraActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ElytraUpgrade upgradeOf(ItemStack elytra) {
        return elytra.is(Items.ELYTRA) ? elytra.get(WFComponents.ELYTRA_UPGRADE.get()) : null;
    }

    public static void handle(ElytraActionPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp) || !sp.isFallFlying()) {
            return;
        }
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        ElytraUpgrade up = upgradeOf(chest);
        ElytraUpgrade needed = switch (p.action) {
            case GALE -> ElytraUpgrade.GALE;
            case STILL_SECOND -> ElytraUpgrade.STILL;
            default -> ElytraUpgrade.HOVER;
        };
        if (up != needed) {
            return;
        }
        int cost = switch (p.action) {
            case GALE -> 3;
            case STILL_SECOND -> 1;
            default -> 2;
        };
        chest.hurtAndBreak(cost, sp, EquipmentSlot.CHEST);
        var level = sp.level();
        switch (p.action) {
            case GALE -> {
                level.playSound(null, sp.blockPosition(), WFSounds.ITEM_ELYTRA_GALE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                level.sendParticles(WFParticles.SKATE_WAKE.get(), sp.getX(), sp.getY() + 0.5, sp.getZ(), 24, 0.6, 0.3, 0.6, 0.15);
            }
            case STILL_SECOND -> {
                level.playSound(null, sp.blockPosition(), WFSounds.ITEM_ELYTRA_STILL.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
                level.sendParticles(WFParticles.STILL_RING.get(), sp.getX(), sp.getY() + 0.5, sp.getZ(), 1, 0, 0, 0, 0);
            }
            default -> {
                level.playSound(null, sp.blockPosition(), WFSounds.ITEM_ELYTRA_HOVER.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
                level.sendParticles(WFParticles.UP_MOTE.get(), sp.getX(), sp.getY() - 0.2, sp.getZ(), 12, 0.4, 0.2, 0.4, 0.01);
            }
        }
        Progress.award(sp, "wings_revised");
    }
}
