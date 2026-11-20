package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.EchoTabletItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One creative tab, icon Lumen Crystal, ordered by biome then function (§18.9). */
public final class WFTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Worldsfall.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("worldsfall", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.worldsfall"))
        .icon(() -> new ItemStack(WFBlocks.LUMEN_CRYSTAL.get()))
        .displayItems((params, out) -> {
            for (var block : WFBlocks.itemBlocks()) {
                out.accept(block.get());
            }
            for (var item : WFItems.ordered()) {
                if (item.get() == WFItems.ECHO_TABLET.get()) {
                    for (int i = 1; i <= EchoTabletItem.FRAGMENTS; i++) {
                        out.accept(EchoTabletItem.of(i));
                    }
                } else {
                    out.accept(item.get());
                }
            }
            for (Item egg : WFItems.spawnEggs()) {
                out.accept(egg);
            }
        }).build());

    private WFTabs() {}
}
