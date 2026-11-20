package dev.worldsfall.util;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Small helpers shared across systems. */
public final class WFUtil {
    private WFUtil() {}

    /** Gives a stack to the player, dropping whatever does not fit. */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** Plays a sound only for one player, at their position. */
    public static void playTo(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source, player.getX(), player.getY(), player.getZ(),
            volume, pitch, player.getRandom().nextLong()));
    }

    /** Marks an entity's velocity for syncing to clients after a server-side push. */
    public static void syncMotion(Entity entity) {
        entity.needsSync = true;
    }
}
