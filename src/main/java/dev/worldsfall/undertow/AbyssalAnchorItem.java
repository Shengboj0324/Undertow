package dev.worldsfall.undertow;

import dev.worldsfall.item.VoidAnchorItem;

/**
 * Abyssal Anchor (proposal "Rewards: reach, not power"): an upgraded Void Anchor that is not consumed. After it seals a
 * Void Capsule it recharges for twenty minutes (an item cooldown), during which an ordinary anchor is used instead.
 */
public class AbyssalAnchorItem extends VoidAnchorItem {
    public static final int RECHARGE = 24000;

    public AbyssalAnchorItem(Properties properties) {
        super(properties, false);
    }
}
