package dev.worldsfall.client.expansion;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.nav.LatticeData;
import dev.worldsfall.network.LatticeJumpPayload;
import dev.worldsfall.network.LatticeMenuPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * The Gateway Lattice list (proposal "Navigation"): every node you have attuned or visited, nearest first, with its tide
 * state and whether a Skate is perched there. Picking one sends the jump; the server checks everything again.
 */
public class LatticeScreen extends Screen {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "textures/gui/lattice.png");
    private static final int W = 176, H = 166, ROW = 16, ROWS = 7, LIST_X = 12, LIST_Y = 28, LIST_W = 152;
    private static final int LILAC = 0xFFE3C9F2, GREY = 0xFF8C8398, TEAL = 0xFF3FC9C0, MAGENTA = 0xFFE34FD6, DIM = 0xFF5D5866;

    private final LatticeMenuPayload menu;
    private int scroll;
    private int left, top;

    public LatticeScreen(LatticeMenuPayload menu) {
        super(Component.translatable("gui.worldsfall.lattice.title"));
        this.menu = menu;
    }

    public static void open(LatticeMenuPayload menu) {
        Minecraft.getInstance().gui.setScreen(new LatticeScreen(menu));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, left, top, 0.0f, 0.0f, W, H, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, left + W / 2, top + 12, LILAC);
        List<LatticeMenuPayload.Entry> entries = menu.entries();
        if (entries.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.worldsfall.lattice.empty"), left + LIST_X + 4, top + LIST_Y + 8, LIST_W - 8, GREY);
        }
        var player = Minecraft.getInstance().player;
        for (int i = 0; i < ROWS && i + scroll < entries.size(); i++) {
            LatticeMenuPayload.Entry e = entries.get(i + scroll);
            int x = left + LIST_X, y = top + LIST_Y + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW - 1;
            if (hover && !e.covered()) {
                g.fill(x, y, x + LIST_W, y + ROW - 1, 0x40E34FD6);
            }
            g.text(font, e.kind() == LatticeData.MOORING ? "⚓" : "◆", x + 3, y + 4, e.covered() ? DIM : e.kind() == LatticeData.MOORING ? MAGENTA : TEAL);
            String name = font.plainSubstrByWidth(e.name(), 92);
            g.text(font, name, x + 14, y + 4, e.covered() ? DIM : LILAC);
            int dist = player == null ? 0 : (int) Math.sqrt(player.blockPosition().distSqr(e.pos()));
            String right = e.covered() ? Component.translatable("gui.worldsfall.lattice.covered").getString() : dist + " m";
            g.text(font, right, x + LIST_W - 4 - font.width(right), y + 4, e.covered() ? TEAL : GREY);
            if (e.skate() && !e.covered()) {
                g.text(font, "✦", x + 14 + font.width(name) + 4, y + 4, MAGENTA);
            }
            if (hover) {
                g.setTooltipForNextFrame(List.of(Component.literal(e.name()).getVisualOrderText(),
                    Component.translatable("gui.worldsfall.lattice.at", e.pos().getX(), e.pos().getY(), e.pos().getZ()).getVisualOrderText(),
                    Component.translatable(e.skate() ? "gui.worldsfall.lattice.skate" : "gui.worldsfall.lattice.no_skate").getVisualOrderText()),
                    mouseX, mouseY);
            }
        }
        if (entries.size() > ROWS) {
            int barH = ROWS * ROW;
            int knob = Math.max(10, barH * ROWS / entries.size());
            int ky = top + LIST_Y + (barH - knob) * scroll / Math.max(1, entries.size() - ROWS);
            g.fill(left + LIST_X + LIST_W + 2, top + LIST_Y, left + LIST_X + LIST_W + 4, top + LIST_Y + barH, 0x40FFFFFF);
            g.fill(left + LIST_X + LIST_W + 2, ky, left + LIST_X + LIST_W + 4, ky + knob, TEAL);
        }
        g.centeredText(font, Component.translatable("gui.worldsfall.lattice.cost"), left + W / 2, top + H - 20, GREY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<LatticeMenuPayload.Entry> entries = menu.entries();
        for (int i = 0; i < ROWS && i + scroll < entries.size(); i++) {
            int x = left + LIST_X, y = top + LIST_Y + i * ROW;
            if (event.x() >= x && event.x() < x + LIST_W && event.y() >= y && event.y() < y + ROW - 1) {
                LatticeMenuPayload.Entry e = entries.get(i + scroll);
                if (e.covered()) {
                    return true;
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                ClientPacketDistributor.sendToServer(new LatticeJumpPayload(e.pos()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, menu.entries().size() - ROWS));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
