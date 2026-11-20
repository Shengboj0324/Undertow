package dev.worldsfall.client.hud;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.ClientWorldState;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.fluid.StillwaterEffects;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * HUD (§18.9): the Skate stamina arc right of the crosshair, the optional tide indicator under the hotbar,
 * the Echo Tablet glyph line, and gentle screen-edge effects (tide rise vignette, erosion desaturation,
 * Stillwater underwater vignette) that can be switched off (§17.4).
 */
public final class WFHud {
    private static int tabletIndex;
    private static long tabletStart = -1;
    private static final String GLYPHS = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ";

    private WFHud() {}

    public static void register(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, id("screen_effects"), WFHud::screenEffects);
        e.registerAbove(id("screen_effects"), id("void_lens"), VoidLensOverlay::render);
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, id("skate_stamina"), WFHud::stamina);
        e.registerAbove(VanillaGuiLayers.HOTBAR, id("tide_indicator"), WFHud::tide);
        e.registerAbove(VanillaGuiLayers.TITLE, id("echo_tablet"), WFHud::tablet);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Worldsfall.MODID, path);
    }

    public static void showTablet(int index) {
        tabletIndex = index;
        tabletStart = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ stamina arc (slim teal arc, fades when full)
    private static float staminaAlpha;

    private static void stamina(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (Minecraft.getInstance().gui.hud.isHidden()) {
            return; // F1 hides the mod HUD like the vanilla one
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !(p.getVehicle() instanceof VoidSkate skate)) {
            staminaAlpha = Math.max(0, staminaAlpha - 0.05f);
            return;
        }
        float frac = skate.stamina() / VoidSkate.MAX_STAMINA;
        float want = frac < 0.995f ? 1f : 0f;
        staminaAlpha += (want - staminaAlpha) * 0.1f;
        if (staminaAlpha < 0.02f) {
            return;
        }
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int r = 14;
        int segments = 28;
        int lit = Math.round(segments * frac);
        for (int i = 0; i < segments; i++) {
            double a = Math.toRadians(-60 + 120.0 * i / (segments - 1));
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy - (int) Math.round(Math.sin(a) * r);
            int base = i < lit ? ARGB.color((int) (230 * staminaAlpha), 58, 224, 208) : ARGB.color((int) (90 * staminaAlpha), 20, 40, 48);
            g.fill(x, y, x + 2, y + 1, base);
        }
    }

    // ------------------------------------------------------------------ tide indicator
    private static void tide(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (Minecraft.getInstance().gui.hud.isHidden()) {
            return; // F1 hides the mod HUD like the vanilla one
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !Tides.appliesTo(mc.level)) {
            return;
        }
        WFConfig.HudMode mode = WFConfig.get(WFConfig.CLIENT.tideHud, WFConfig.HudMode.AUTO);
        boolean holding = holdsTideItem(p.getMainHandItem()) || holdsTideItem(p.getOffhandItem());
        if (mode == WFConfig.HudMode.OFF || (mode == WFConfig.HudMode.AUTO && !holding)) {
            return;
        }
        TideCycle cycle = Tides.cycle(mc.level);
        long t = Tides.effectiveTime(mc.level);
        TideCycle.Phase phase = Tides.phase(mc.level);
        float level = (float) cycle.normalized(t);
        int x = g.guiWidth() / 2 - 60, y = g.guiHeight() - 52;
        // tiny wave icon with a fill level
        g.fill(x, y, x + 14, y + 10, ARGB.color(170, 10, 14, 22));
        int fillH = Math.max(1, Math.round(8 * level));
        g.fill(x + 1, y + 9 - fillH, x + 13, y + 9, ARGB.color(230, 58, 224, 208));
        g.fill(x + 1, y + 9 - fillH, x + 13, y + 10 - fillH, ARGB.color(255, 184, 255, 247));
        long secs = cycle.ticksUntilNextPhase(t) / 20;
        Component label = Component.translatable("message.worldsfall.tide.phase", Component.translatable("tide.worldsfall." + phase.id),
            String.format("%d:%02d", secs / 60, secs % 60));
        g.text(mc.font, label, x + 18, y + 1, ARGB.color(255, 184, 255, 247), true);
    }

    private static boolean holdsTideItem(ItemStack s) {
        return s.is(WFItems.GATEWAY_COMPASS.get()) || s.is(WFItems.VOID_LENS.get()) || s.is(WFBlocks.TIDE_GAUGE.get().asItem());
    }

    // ------------------------------------------------------------------ Echo Tablet line: glyphs resolve into letters over 1 s, fade after 6 s
    private static void tablet(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (Minecraft.getInstance().gui.hud.isHidden()) {
            return; // F1 hides the mod HUD like the vanilla one
        }
        if (tabletStart < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long age = System.currentTimeMillis() - tabletStart;
        if (age > 7500) {
            tabletStart = -1;
            return;
        }
        String text = Component.translatable("tablet.worldsfall." + tabletIndex).getString();
        boolean animate = WFConfig.get(WFConfig.CLIENT.glyphAnimation, true);
        float resolve = animate ? Mth.clamp(age / 1000f, 0, 1) : 1f;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float threshold = (i * 7919 % text.length()) / (float) text.length();
            if (c == ' ' || resolve >= threshold) {
                sb.append(c);
            } else {
                sb.append(GLYPHS.charAt((i + (int) (age / 70)) % GLYPHS.length()));
            }
        }
        float alpha = age < 300 ? age / 300f : age > 6500 ? Math.max(0, 1 - (age - 6500) / 1000f) : 1f;
        Font font = mc.font;
        int y = g.guiHeight() / 2 + 34;
        g.centeredText(font, Component.literal(sb.toString()), g.guiWidth() / 2, y, ARGB.color((int) (255 * alpha), 233, 222, 255));
    }

    // ------------------------------------------------------------------ screen-edge effects
    private static void screenEffects(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        boolean effects = WFConfig.get(WFConfig.CLIENT.tideScreenEffects, true);
        float rise = ClientWorldState.get().riseFlash();
        if (effects && rise > 0) {
            vignette(g, w, h, ARGB.color((int) (90 * rise), 58, 224, 208));
        }
        if (effects && p.hasEffect(WFEffects.VOID_EROSION)) {
            float flick = 0.6f + 0.4f * Mth.sin(p.tickCount * 0.9f);
            vignette(g, w, h, ARGB.color((int) (110 * flick), 30, 44, 48));
        }
        if (StillwaterEffects.isEyeIn(p) && !p.hasEffect(WFEffects.TIDESIGHT)) {
            g.fill(0, 0, w, h, ARGB.color(150, 6, 8, 20));
            vignette(g, w, h, ARGB.color(200, 3, 3, 10));
        }
    }

    private static void vignette(GuiGraphicsExtractor g, int w, int h, int color) {
        int band = Math.max(24, Math.min(w, h) / 6);
        int clear = ARGB.color(0, ARGB.red(color), ARGB.green(color), ARGB.blue(color));
        g.fillGradient(0, 0, w, band, color, clear);
        g.fillGradient(0, h - band, w, h, clear, color);
    }
}
