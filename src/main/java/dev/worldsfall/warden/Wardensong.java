package dev.worldsfall.warden;

import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.network.WardenFxPayload;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.sundering.Sundering;
import dev.worldsfall.world.sundering.SunderingData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * Wardensong (proposal §1): the first fight keeps every vanilla rule and gains three story beats. Each End Crystal that
 * breaks releases a glyph burst and one line of the Warden's memory (ten crystals, ten lines, in order); at 50 % and 25 %
 * health hairline cracks spread across the island along the Sundering's paths; and when the dragon perches below 15 %
 * the exit portal hums and the cracks pulse with its breathing.
 */
public final class Wardensong {
    public static final int LINES = 10;
    static final int CRACK_HALF = 1, CRACK_QUARTER = 2;

    private Wardensong() {}

    static void onPillarCrystal(ServerLevel end, EndCrystal crystal, WardenData data) {
        int line = data.songLines();
        if (line >= LINES) {
            return;
        }
        data.setSongLines(line + 1);
        Vec3 at = crystal.position().add(0, 1, 0);
        end.sendParticles(WFParticles.WARDENSONG_GLYPH.get(), at.x, at.y, at.z, 40, 1.2, 1.2, 1.2, 0.04);
        WardenFight.playAt(end, at, WFSounds.EVENT_WARDEN_CRYSTAL_ECHO.get(), 6.0f, 1.0f);
        for (ServerPlayer p : end.players()) {
            if (p.distanceToSqr(at) < 256 * 256) {
                WFNetwork.send(p, new WardenFxPayload(WardenFxPayload.SONG, crystal.blockPosition(), line));
                int heard = p.getData(WFAttachments.WARDENSONG) | (1 << line);
                p.setData(WFAttachments.WARDENSONG, heard);
                if (heard == (1 << LINES) - 1) {
                    Progress.award(p, "wardensong");
                }
            }
        }
    }

    static void tick(ServerLevel end, EnderDragon dragon, WardenData data) {
        float ratio = dragon.getHealth() / dragon.getMaxHealth();
        BlockPos descent = descent(end);
        if (ratio <= 0.5f && !data.crackFlag(CRACK_HALF)) {
            data.setCrackFlag(CRACK_HALF);
            cracks(end, descent, 1);
        }
        if (ratio <= 0.25f && !data.crackFlag(CRACK_QUARTER)) {
            data.setCrackFlag(CRACK_QUARTER);
            cracks(end, descent, 2);
        }
        if (ratio < 0.15f && dragon.getPhaseManager().getCurrentPhase().isSitting() && end.getGameTime() % 40 == 0) {
            Vec3 portal = Vec3.atCenterOf(WardenFight.portalTop(end));
            WardenFight.playAt(end, portal, WFSounds.EVENT_WARDEN_PORTAL_HUM.get(), 3.0f, 0.9f);
            for (ServerPlayer p : end.players()) {
                WFNetwork.send(p, new WardenFxPayload(WardenFxPayload.PULSE, descent, 0));
            }
        }
    }

    private static void cracks(ServerLevel end, BlockPos descent, int stage) {
        WardenFight.playAt(end, Vec3.atCenterOf(WardenFight.portalTop(end)), WFSounds.EVENT_WARDEN_CRACKS.get(), 6.0f, stage == 1 ? 1.0f : 0.85f);
        for (ServerPlayer p : end.players()) {
            WFNetwork.send(p, new WardenFxPayload(WardenFxPayload.CRACKS, descent, stage));
        }
    }

    /** Where the Descent will open (or has opened): the cracks trace exactly the paths the Sundering will take. */
    static BlockPos descent(ServerLevel end) {
        SunderingData s = SunderingData.get(end.getServer());
        if (s.done() || !s.descent().equals(BlockPos.ZERO)) {
            return s.descent();
        }
        BlockPos chosen = Sundering.chooseDescent(end);
        return chosen != null ? chosen : new BlockPos(28, 64, 28);
    }
}
