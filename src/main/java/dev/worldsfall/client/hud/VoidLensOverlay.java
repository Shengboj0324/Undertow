package dev.worldsfall.client.hud;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.VoidLensItem;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Client half of the Void Lens (§9.6). While scoped through a Void Lens: Echoes and hidden Lurkers get a teal outline
 * (set by their renderers through {@link #outlineFor}), Driftwrecks within 128 blocks are traced as dotted boxes with
 * their distance, and the tide plane's height is drawn across the lens as a line with its Y level and direction.
 * World points are projected with the camera matrices captured at the end of the level pass.
 */
public final class VoidLensOverlay {
    public static final int TEAL = 0x2FC9BB;
    public static final Identifier SCOPE = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "textures/misc/void_lens_scope.png");
    private static List<BoundingBox> wrecks = List.of();
    private static long wrecksAt;
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean captured;

    private VoidLensOverlay() {}

    public static void acceptWrecks(List<BoundingBox> boxes) {
        wrecks = List.copyOf(boxes);
        wrecksAt = System.currentTimeMillis();
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && p.isScoping() && p.getUseItem().getItem() instanceof VoidLensItem && mc.options.getCameraType().isFirstPerson();
    }

    /** Outline colour an entity renderer should use for {@code e}, or 0 when the lens does not reveal it. */
    public static int outlineFor(Entity e, int vanillaOutline) {
        if (vanillaOutline != 0 || !active()) {
            return vanillaOutline;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.distanceToSqr(e) < 128 * 128 ? ARGB.opaque(TEAL) : 0;
    }

    public static void captureCamera(RenderLevelStageEvent.AfterLevel event) {
        CameraRenderState cam = event.getLevelRenderState().cameraRenderState;
        VIEW_PROJ.set(cam.projectionMatrix).mul(cam.viewRotationMatrix);
        camPos = cam.pos;
        captured = true;
    }

    public static IClientItemExtensions itemExtensions() {
        return new IClientItemExtensions() {
            @Override
            public Identifier getScopeOverlayTexture(net.minecraft.world.item.ItemStack stack) {
                return SCOPE;
            }
        };
    }

    /** Screen position of a world point, or null when it is behind the camera. */
    private static float @Nullable [] project(double x, double y, double z, int w, int h) {
        Vector4f v = new Vector4f((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1.0f);
        VIEW_PROJ.transform(v);
        if (v.w <= 0.05f) {
            return null;
        }
        return new float[] {(v.x / v.w * 0.5f + 0.5f) * w, (1.0f - (v.y / v.w * 0.5f + 0.5f)) * h};
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!captured || mc.level == null || !active()) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h / 2f, radius = Math.min(w, h) * 0.47f;
        float pulse = 0.75f + 0.25f * Mth.sin((mc.player.tickCount + dt.getGameTimeDeltaPartialTick(false)) * 0.15f);
        // Driftwrecks: dotted boxes, fading if the server has not refreshed them for a while
        if (System.currentTimeMillis() - wrecksAt < 3000) {
            for (BoundingBox b : wrecks) {
                drawBox(g, b, w, h, cx, cy, radius, ARGB.color((int) (210 * pulse), 120, 255, 236));
            }
        }
        // tide plane height
        if (Tides.appliesTo(mc.level)) {
            drawTideLine(g, mc, w, h, cx, cy, radius);
        }
    }

    private static void drawBox(GuiGraphicsExtractor g, BoundingBox b, int w, int h, float cx, float cy, float r, int color) {
        double[] xs = {b.minX(), b.maxX() + 1}, ys = {b.minY(), b.maxY() + 1}, zs = {b.minZ(), b.maxZ() + 1};
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++) {
            c[i] = project(xs[i & 1], ys[(i >> 1) & 1], zs[(i >> 2) & 1], w, h);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            if (c[e[0]] != null && c[e[1]] != null) {
                dotted(g, c[e[0]], c[e[1]], cx, cy, r, color);
            }
        }
        // lock-on brackets around the wreck's screen rectangle, clamped into the lens so a wreck larger than the zoomed
        // view still reads as "this one", with its distance above the top-left bracket
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (float[] p : c) {
            if (p != null) {
                x0 = Math.min(x0, p[0]);
                y0 = Math.min(y0, p[1]);
                x1 = Math.max(x1, p[0]);
                y1 = Math.max(y1, p[1]);
            }
        }
        if (x0 > x1) {
            return;
        }
        float lim = r * 0.68f;
        x0 = Mth.clamp(x0, cx - lim, cx + lim);
        x1 = Mth.clamp(x1, cx - lim, cx + lim);
        y0 = Mth.clamp(y0, cy - lim, cy + lim);
        y1 = Mth.clamp(y1, cy - lim, cy + lim);
        if (x1 - x0 < 12 || y1 - y0 < 12) {
            return;
        }
        int arm = (int) Math.max(6, Math.min(18, Math.min(x1 - x0, y1 - y0) * 0.2f));
        bracket(g, (int) x0, (int) y0, arm, 1, 1, color);
        bracket(g, (int) x1, (int) y0, arm, -1, 1, color);
        bracket(g, (int) x0, (int) y1, arm, 1, -1, color);
        bracket(g, (int) x1, (int) y1, arm, -1, -1, color);
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            int dist = (int) Math.sqrt(p.distanceToSqr(b.getCenter().getX(), b.getCenter().getY(), b.getCenter().getZ()));
            g.text(Minecraft.getInstance().font, Component.translatable("hud.worldsfall.void_lens.wreck", dist), (int) x0, (int) y0 - 11,
                ARGB.color(255, 184, 255, 247), true);
        }
    }

    /** An L-shaped corner mark at (x, y) whose arms run in the (sx, sy) directions. */
    private static void bracket(GuiGraphicsExtractor g, int x, int y, int arm, int sx, int sy, int color) {
        int hx0 = sx > 0 ? x : x - arm, hx1 = sx > 0 ? x + arm : x;
        int vy0 = sy > 0 ? y : y - arm, vy1 = sy > 0 ? y + arm : y;
        g.fill(hx0, sy > 0 ? y : y - 2, hx1, sy > 0 ? y + 2 : y, color);
        g.fill(sx > 0 ? x : x - 2, vy0, sx > 0 ? x + 2 : x, vy1, color);
    }

    private static void drawTideLine(GuiGraphicsExtractor g, Minecraft mc, int w, int h, float cx, float cy, float r) {
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double tideY = Tides.height(mc.level, partial);
        Vec3 look = mc.player.getViewVector(partial);
        Vec3 fwd = new Vec3(look.x, 0, look.z);
        if (fwd.lengthSqr() < 1.0e-4) {
            return;
        }
        fwd = fwd.normalize();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        double d = 192;
        float[] a = project(camPos.x + fwd.x * d, tideY, camPos.z + fwd.z * d, w, h);
        float[] b = project(camPos.x + fwd.x * d + right.x * 24, tideY, camPos.z + fwd.z * d + right.z * 24, w, h);
        if (a == null || b == null || Math.abs(b[0] - a[0]) < 1.0e-3f) {
            return;
        }
        float slope = (b[1] - a[1]) / (b[0] - a[0]);
        float x0 = cx - r, x1 = cx + r;
        float[] p0 = {x0, a[1] + slope * (x0 - a[0])}, p1 = {x1, a[1] + slope * (x1 - a[0])};
        int col = ARGB.color(235, 58, 224, 208);
        dotted(g, p0, p1, cx, cy, r, col);
        float ly = a[1] + slope * (cx + r * 0.45f - a[0]);
        if (inside(cx + r * 0.45f, ly, cx, cy, r * 0.95f)) {
            TideCycle.Phase phase = Tides.phase(mc.level);
            String arrow = switch (phase) {
                case RISING -> "▲";
                case FALLING -> "▼";
                default -> "■";
            };
            Component label = Component.translatable("hud.worldsfall.void_lens.tide", (int) Math.floor(tideY), arrow);
            g.text(mc.font, label, (int) (cx + r * 0.45f), (int) ly - 11, ARGB.color(255, 184, 255, 247), true);
        }
    }

    private static boolean inside(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** A dotted line, clipped to the lens circle; two-pixel dashes every five pixels. */
    private static void dotted(GuiGraphicsExtractor g, float[] a, float[] b, float cx, float cy, float r, int color) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1 || len > 20000) {
            return;
        }
        int steps = (int) (len / 5);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) Math.max(1, steps);
            float x = a[0] + dx * t, y = a[1] + dy * t;
            if (inside(x, y, cx, cy, r)) {
                g.fill((int) x, (int) y, (int) x + 2, (int) y + 1, color);
            }
        }
    }
}
