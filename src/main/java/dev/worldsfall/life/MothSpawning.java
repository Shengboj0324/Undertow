package dev.worldsfall.life;

import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/** Lantern Moths gather around the Fallen Spires: while a player is inside one, a few appear near its lanterns. */
public final class MothSpawning {
    public static final int CAP = 6, INTERVAL = 200;

    private MothSpawning() {}

    public static void tick(ServerLevel end) {
        if (end.getGameTime() % INTERVAL != 13) {
            return;
        }
        RandomSource r = end.getRandom();
        for (ServerPlayer p : end.players()) {
            if (p.isSpectator() || !end.structureManager().getStructureWithPieceAt(p.blockPosition(), WFTags.Structures.FALLEN_SPIRES).isValid()) {
                continue;
            }
            if (end.getEntitiesOfClass(LanternMoth.class, new AABB(p.blockPosition()).inflate(32)).size() >= CAP) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                BlockPos at = p.blockPosition().offset(r.nextInt(25) - 12, r.nextInt(9) - 2, r.nextInt(25) - 12);
                if (end.getBlockState(at).isAir() && end.getBlockState(at.above()).isAir()) {
                    LanternMoth m = WFEntities.LANTERN_MOTH.get().create(end, EntitySpawnReason.NATURAL);
                    if (m != null) {
                        m.snapTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, r.nextFloat() * 360, 0);
                        end.addFreshEntity(m);
                    }
                }
            }
        }
    }
}
