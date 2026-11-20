package dev.worldsfall.client.screen;

import dev.worldsfall.menu.SatchelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The Satchel grid: vanilla container art, 1 or 2 rows (§9.5). */
public class SatchelScreen extends AbstractContainerScreen<SatchelMenu> {
    private static final Identifier BG = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final int rows;

    public SatchelScreen(SatchelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + menu.rows() * 18);
        this.rows = menu.rows();
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int xo = (width - imageWidth) / 2;
        int yo = (height - imageHeight) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, BG, xo, yo, 0.0F, 0.0F, imageWidth, rows * 18 + 17, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, xo, yo + rows * 18 + 17, 0.0F, 126.0F, imageWidth, 96, 256, 256);
    }
}
