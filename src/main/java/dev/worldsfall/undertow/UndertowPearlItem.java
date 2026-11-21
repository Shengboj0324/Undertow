package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Undertow Pearl: used in the End, it takes you to your most recent Void Capsule. */
public class UndertowPearlItem extends Item {
    public UndertowPearlItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Level.END) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.undertow_pearl.only_end"));
            return InteractionResult.FAIL;
        }
        GlobalPos target = sp.getData(WFAttachments.LAST_CAPSULE).orElse(null);
        if (target == null || target.dimension() != Level.END) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.undertow_pearl.no_capsule"));
            return InteractionResult.FAIL;
        }
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 50, 0.4, 0.8, 0.4, 0.05);
        Vec3 at = Vec3.atBottomCenterOf(target.pos()).add(0, 1.5, 0);
        sp.teleport(new TeleportTransition(server, at, net.minecraft.world.phys.Vec3.ZERO, sp.getYRot(), sp.getXRot(), TeleportTransition.DO_NOTHING));
        sp.resetFallDistance();
        server.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f);
        server.playSound(null, sp.blockPosition(), WFSounds.ITEM_VOID_ANCHOR_TRIGGER.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        player.getItemInHand(hand).consume(1, player);
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
        return InteractionResult.SUCCESS;
    }
}
