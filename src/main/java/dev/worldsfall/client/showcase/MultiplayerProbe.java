package dev.worldsfall.client.showcase;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.worldsfall.network.TideSyncPayload;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Dev-only multiplayer probe (enabled by {@code -Dworldsfall.mpprobe=<dir>}). A client started with
 * {@code --quickPlayMultiplayer} joins a real server; once a second it writes {@code <dir>/<name>.txt} describing what
 * this client sees (mod channel, tide, every other player's equipment, trims, elytra upgrade and shoulder Wyrm), takes a
 * screenshot to {@code <dir>/<name>_<tag>.png} whenever {@code <dir>/<name>.shoot} appears (its content is the tag), and
 * quits when {@code <dir>/quit} appears. tools/mptest/mp_test.py drives it.
 */
public final class MultiplayerProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path dir;
    private int ticks;

    private MultiplayerProbe(Path dir) {
        this.dir = dir;
    }

    public static void init() {
        String d = System.getProperty("worldsfall.mpprobe");
        if (d == null || d.isBlank()) {
            return;
        }
        NeoForge.EVENT_BUS.register(new MultiplayerProbe(Path.of(d).toAbsolutePath()));
        LOGGER.info("[mpprobe] enabled -> {}", d);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        String name = mc.getUser().getName();
        try {
            Files.createDirectories(dir);
            if (Files.exists(dir.resolve("quit"))) {
                LOGGER.info("[mpprobe] quit requested");
                mc.stop();
                return;
            }
            if (mc.player == null || mc.level == null || mc.getConnection() == null) {
                return;
            }
            if (++ticks % 20 == 0) {
                Files.writeString(dir.resolve(name + ".txt"), report(mc));
            }
            Path shoot = dir.resolve(name + ".shoot");
            if (Files.exists(shoot)) {
                String tag = Files.readString(shoot).trim();
                Files.delete(shoot);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Path file = dir.resolve(name + "_" + (tag.isEmpty() ? "shot" : tag) + ".png");
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, (NativeImage image) -> {
                    try (image) {
                        image.writeToFile(file);
                        LOGGER.info("[mpprobe] wrote {}", file);
                    } catch (IOException e) {
                        LOGGER.error("[mpprobe] screenshot failed", e);
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.error("[mpprobe] io failure", e);
        }
    }

    private static String report(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        var conn = mc.getConnection();
        sb.append("connected=true\n");
        sb.append("brand=").append(conn.serverBrand()).append('\n');
        sb.append("channel=").append(conn.hasChannel(TideSyncPayload.TYPE)).append('\n');
        sb.append("dimension=").append(mc.level.dimension().identifier()).append('\n');
        sb.append(String.format(Locale.ROOT, "pos=%.1f,%.1f,%.1f%n", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (Tides.appliesTo(mc.level)) {
            sb.append("tide_phase=").append(Tides.phase(mc.level).id).append('\n');
            sb.append(String.format(Locale.ROOT, "tide_y=%.1f%n", Tides.height(mc.level)));
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) {
                continue;
            }
            sb.append("player=").append(p.getGameProfile().name());
            sb.append(String.format(Locale.ROOT, " dist=%.1f", p.distanceTo(mc.player)));
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = p.getItemBySlot(slot);
                sb.append(' ').append(slot.getName()).append('=').append(BuiltInRegistries.ITEM.getKey(st.getItem()));
                var trim = st.get(DataComponents.TRIM);
                if (trim != null) {
                    sb.append("[trim:").append(trim.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse("?")).append('/')
                        .append(trim.material().unwrapKey().map(k -> k.identifier().toString()).orElse("?")).append(']');
                }
                var up = st.get(WFComponents.ELYTRA_UPGRADE.get());
                if (up != null) {
                    var eq = st.get(DataComponents.EQUIPPABLE);
                    sb.append("[upgrade:").append(up.getSerializedName()).append(" asset:")
                        .append(eq == null ? "none" : eq.assetId().map(k -> k.identifier().toString()).orElse("none")).append(']');
                }
            }
            Integer shoulders = p.getExistingDataOrNull(WFAttachments.WYRM_SHOULDERS);
            sb.append(" wyrm_shoulders=").append(shoulders == null ? 0 : shoulders).append('\n');
        }
        return sb.toString();
    }
}
