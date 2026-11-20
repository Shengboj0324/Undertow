package dev.worldsfall.item;

import net.minecraft.world.item.SpyglassItem;

/**
 * Void Lens (§9.6): an upgraded spyglass. While scoped, the client reveals Echoes, hidden Lurkers,
 * Driftwrecks within 128 blocks and the tide height (see client VoidLensOverlay).
 */
public class VoidLensItem extends SpyglassItem {
    public VoidLensItem(Properties properties) {
        super(properties);
    }
}
