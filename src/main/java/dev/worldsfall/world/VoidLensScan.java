package dev.worldsfall.world;

import dev.worldsfall.item.VoidLensItem;
import dev.worldsfall.network.VoidLensPayload;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server half of the Void Lens (§9.6): finds Driftwrecks within 128 blocks of a scoped player in loaded chunks. */
public final class VoidLensScan {
    public static final int RANGE = 128;

    private VoidLensScan() {}

    public static void tick(ServerPlayer player) {
        if (Boolean.getBoolean("worldsfall.debugLens") && player.tickCount % 20 == 0) {
            org.slf4j.LoggerFactory.getLogger("worldsfall").info("[lens] using={} item={} scoping={} wrecks={}", player.isUsingItem(),
                player.getUseItem(), player.isScoping(), wrecksNear(player.level(), player.getBlockX(), player.getBlockZ()).size());
        }
        if (player.tickCount % 20 != 0 || !player.isScoping() || !(player.getUseItem().getItem() instanceof VoidLensItem)) {
            return;
        }
        ServerLevel level = player.level();
        java.util.List<net.minecraft.core.BlockPos> pillars = java.util.List.of();
        if (level.dimension() == net.minecraft.world.level.Level.END
            && player.getX() * player.getX() + player.getZ() * player.getZ() < dev.worldsfall.gap.PillarGlyphs.RANGE * dev.worldsfall.gap.PillarGlyphs.RANGE) {
            pillars = dev.worldsfall.gap.PillarGlyphs.marks(level).stream().map(dev.worldsfall.gap.PillarGlyphs.Mark::top).toList();
            dev.worldsfall.gap.PillarGlyphs.read(level, player);
        }
        WFNetwork.send(player, new VoidLensPayload(wrecksNear(level, player.getBlockX(), player.getBlockZ()), pillars));
    }

    public static List<BoundingBox> wrecksNear(ServerLevel level, int x, int z) {
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<BoundingBox> out = new ArrayList<>();
        int r = (RANGE >> 4) + 2;
        int cx = x >> 4, cz = z >> 4;
        for (int dx = -r; dx <= r && out.size() < 32; dx++) {
            for (int dz = -r; dz <= r && out.size() < 32; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    if (!e.getValue().isValid() || !structures.wrapAsHolder(e.getKey()).is(WFTags.Structures.DRIFTWRECKS)) {
                        continue;
                    }
                    BoundingBox b = e.getValue().getBoundingBox();
                    double ddx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
                    double ddz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
                    if (ddx * ddx + ddz * ddz <= RANGE * RANGE) {
                        out.add(b);
                    }
                }
            }
        }
        return out;
    }
}
