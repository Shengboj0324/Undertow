package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.menu.SatchelMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WFMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Worldsfall.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<SatchelMenu>> SATCHEL = MENUS.register("shulker_satchel",
        () -> IMenuTypeExtension.create(SatchelMenu::new));

    private WFMenus() {}
}
