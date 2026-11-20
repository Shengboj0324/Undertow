package dev.worldsfall.item;

import net.minecraft.world.item.Item;

/**
 * Void Anchor (§9.2): carried in the inventory, it seals your belongings in a Void Capsule when you die in
 * the void or below the tide plane. The cracked version saves a random half of your stacks. Logic lives in
 * {@link dev.worldsfall.world.VoidAnchorHandler}.
 */
public class VoidAnchorItem extends Item {
    private final boolean cracked;

    public VoidAnchorItem(Properties properties, boolean cracked) {
        super(properties);
        this.cracked = cracked;
    }

    public boolean cracked() {
        return cracked;
    }
}
