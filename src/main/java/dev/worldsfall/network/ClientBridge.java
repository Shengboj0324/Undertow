package dev.worldsfall.network;

import net.minecraft.core.BlockPos;

import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/** Hooks the client installs so common payload handlers never reference client classes directly. */
public final class ClientBridge {
    public static volatile BiConsumer<BlockPos, Long> sunderingFx = (p, t) -> {};
    public static volatile IntConsumer echoTablet = i -> {};

    private ClientBridge() {}
}
