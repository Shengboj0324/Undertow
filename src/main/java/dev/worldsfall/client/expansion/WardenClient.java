package dev.worldsfall.client.expansion;

import dev.worldsfall.client.fx.SunderingFx;
import dev.worldsfall.client.hud.WFHud;
import dev.worldsfall.network.WardenFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

/** Client handling of {@link WardenFxPayload}. */
public final class WardenClient {
    private WardenClient() {}

    public static void handle(WardenFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        switch (p.kind()) {
            case WardenFxPayload.SONG -> WFHud.showLine("wardensong.worldsfall." + p.value(), 0xF1D9A0);
            case WardenFxPayload.CRACKS -> SunderingFx.preview(p.pos(), p.value());
            case WardenFxPayload.PULSE -> SunderingFx.pulse(p.pos());
            case WardenFxPayload.RELEASE -> {
                if (mc.level != null) {
                    for (int i = 0; i < 160; i++) {
                        double a = i * Math.PI * 2 / 160;
                        mc.level.addParticle(ParticleTypes.END_ROD, p.pos().getX() + 0.5 + Math.cos(a) * 4, p.pos().getY() + 1,
                            p.pos().getZ() + 0.5 + Math.sin(a) * 4, Math.cos(a) * 0.15, 0.08, Math.sin(a) * 0.15);
                    }
                }
            }
            default -> { }
        }
    }
}
