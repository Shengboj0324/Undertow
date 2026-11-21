package dev.worldsfall.life;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.config.WFConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's log of lost things (proposal "A layer below: the Undertow"): items that fell into the End's void are
 * remembered (up to a cap, oldest forgotten first). Tide fishing very rarely brings one back; in the Undertow they wash up.
 */
public final class LostThings extends SavedData {
    public static final float FISHING_CHANCE = 0.01f;

    public static final com.mojang.serialization.Codec<LostThings> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
        .xmap(LostThings::new, l -> List.copyOf(l.items));
    public static final SavedDataType<LostThings> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "lost_things"),
        LostThings::new, CODEC);

    private final List<ItemStack> items = new ArrayList<>();

    public LostThings() {}

    private LostThings(List<ItemStack> list) {
        list.stream().filter(s -> !s.isEmpty()).forEach(items::add);
    }

    public static LostThings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int size() {
        return items.size();
    }

    public void remember(ItemStack stack) {
        int cap = WFConfig.get(WFConfig.SERVER.lostThingsCap, 500);
        if (cap <= 0 || stack.isEmpty()) {
            return;
        }
        items.add(stack.copy());
        while (items.size() > cap) {
            items.removeFirst();
        }
        setDirty();
    }

    /** Takes one remembered thing at random (empty if none). */
    public static ItemStack take(MinecraftServer server) {
        LostThings data = get(server);
        if (data.items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = data.items.remove(server.overworld().getRandom().nextInt(data.items.size()));
        data.setDirty();
        return s;
    }

    /** An item about to fall out of the End's world is remembered instead of simply vanishing. */
    public static void onItemTick(ItemEntity item) {
        Level level = item.level();
        if (!level.isClientSide() && level.dimension() == Level.END && item.getY() < level.getMinY() - 60 && !item.isRemoved()) {
            get(level.getServer()).remember(item.getItem());
            item.discard();
        }
    }
}
