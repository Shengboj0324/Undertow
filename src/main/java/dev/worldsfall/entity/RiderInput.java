package dev.worldsfall.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

/** Side-agnostic access to a rider's raw key state (forward/jump/shift...). */
public final class RiderInput {
    /** Installed by the client entrypoint; returns the local player's key presses. */
    public static volatile Function<Player, Input> clientProvider = p -> Input.EMPTY;

    private RiderInput() {}

    public static Input of(Player player) {
        if (player instanceof ServerPlayer sp) {
            return sp.getLastClientInput();
        }
        return clientProvider.apply(player);
    }
}
