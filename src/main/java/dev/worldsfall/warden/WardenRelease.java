package dev.worldsfall.warden;

import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.network.WardenFxPayload;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.fracture.FractureData;
import dev.worldsfall.world.gen.structure.BlueprintPiece;
import dev.worldsfall.world.tide.TideData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Release (proposal §4): once The Unmade is defeated and the Heart of Stillness sits in the Still Throne, four
 * Stillness Crystals summon a calm dragon. Keep all four crystals intact for sixty seconds while it listens and it lands,
 * lowers its head to the nearest player and flies off to become the Elder Warden on the tallest Fallen Spire. Hitting it or
 * losing a crystal turns it into a normal fight. Respawning is then no longer possible in that world.
 */
public final class WardenRelease {
    public static final int VIGIL_TICKS = 1200, BOW_TICKS = 110, DEPART_TICKS = 220;

    private WardenRelease() {}

    public static boolean ready(ServerLevel end) {
        return FractureData.get(end.getServer()).defeated() && TideData.get(end.getServer()).state().calmed();
    }

    static void begin(ServerLevel end, EnderDragon dragon, WardenData data) {
        data.setRelease(WardenData.RELEASE_LISTENING);
        dragon.setData(WFAttachments.DRAGON_POSE, 2);
        dragon.setCustomName(Component.translatable("entity.worldsfall.listening_dragon"));
        // the respawn sequence consumed the crystals; four Stillness Crystals stand again for the vigil
        BlockPos portal = WardenFight.portalTop(end);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = new BlockPos(0, portal.getY() - 3, 0).relative(d, 3);
            while (!end.getBlockState(p.below()).isSolid() && p.getY() > 30) {
                p = p.below();
            }
            EndCrystal c = new EndCrystal(end, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            c.setShowBottom(false);
            c.setData(WFAttachments.CRYSTAL_KIND, WardenFight.KIND_STILLNESS);
            end.addFreshEntity(c);
        }
        for (ServerPlayer p : end.players()) {
            p.sendSystemMessage(Component.translatable("message.worldsfall.warden.listening"));
        }
    }

    static void abort(ServerLevel end, EnderDragon dragon, WardenData data, boolean struck) {
        data.setRelease(WardenData.RELEASE_NONE);
        dragon.setData(WFAttachments.DRAGON_POSE, 0);
        dragon.setCustomName(null);
        dragon.setNoAi(false);
        for (ServerPlayer p : end.players()) {
            p.sendSystemMessage(Component.translatable(struck ? "message.worldsfall.warden.vigil_struck" : "message.worldsfall.warden.vigil_broken"));
        }
    }

    private static List<EndCrystal> stillnessCrystals(ServerLevel end) {
        return WardenFight.respawnCrystals(end).stream().filter(WardenFight::isStillness).toList();
    }

    static void tick(ServerLevel end, WardenData data) {
        int state = data.release();
        if (state == WardenData.RELEASE_DONE) {
            ElderWarden.ensure(end, data);
            return;
        }
        if (state == WardenData.RELEASE_NONE) {
            return;
        }
        EnderDragon dragon = WardenFight.currentDragon(end);
        if (dragon == null) {
            data.setRelease(WardenData.RELEASE_NONE);
            return;
        }
        int t = data.tickRelease();
        BlockPos portal = WardenFight.portalTop(end);
        switch (state) {
            case WardenData.RELEASE_LISTENING -> {
                if (stillnessCrystals(end).size() < 4) {
                    abort(end, dragon, data, false);
                    return;
                }
                double k = Math.min(1.0, t / (double) VIGIL_TICKS);
                double r = 70 - 48 * k, a = t * 0.02;
                WardenFight.steer(dragon, new Vec3(Math.cos(a) * r, portal.getY() + 34 - 12 * k, Math.sin(a) * r));
                if (t % 100 == 0) {
                    WardenFight.playAt(end, dragon.position(), WFSounds.ENTITY_ELDER_WARDEN_CALL.get(), 8.0f, 0.8f);
                }
                if (t >= VIGIL_TICKS) {
                    WardenFight.playAt(end, Vec3.atCenterOf(portal), WFSounds.EVENT_WARDEN_RELEASE_CHIME.get(), 10.0f, 1.0f);
                    for (EndCrystal c : stillnessCrystals(end)) {
                        end.sendParticles(ParticleTypes.END_ROD, c.getX(), c.getY() + 1, c.getZ(), 60, 0.4, 1.2, 0.4, 0.08);
                        c.setBeamTarget(portal.above(3));
                    }
                    for (ServerPlayer p : end.players()) {
                        WFNetwork.send(p, new WardenFxPayload(WardenFxPayload.RELEASE, portal, 0));
                    }
                    data.setRelease(WardenData.RELEASE_BOWING);
                }
            }
            case WardenData.RELEASE_BOWING -> {
                Vec3 perch = new Vec3(0.5, portal.getY() + 1.5, 0.5);
                if (t < 60) {
                    WardenFight.steer(dragon, perch.add(0, 4, 0));
                } else {
                    net.minecraft.world.entity.player.Player nearest = end.getNearestPlayer(dragon, 128);
                    float yaw = nearest == null ? dragon.getYRot()
                        : (float) (Math.atan2(nearest.getZ() - perch.z, nearest.getX() - perch.x) * 180 / Math.PI) + 90;
                    WardenFight.hold(dragon, perch, yaw);
                    if (t == 60) {
                        WardenFight.playAt(end, perch, WFSounds.EVENT_WARDEN_BOW.get(), 6.0f, 1.0f);
                    }
                }
                if (t >= 60 + BOW_TICKS) {
                    dragon.setNoAi(false);
                    data.setRelease(WardenData.RELEASE_DEPARTING);
                    if (data.elderPerch() == null) {
                        data.setElderPerch(findPerch(end));
                    }
                }
            }
            case WardenData.RELEASE_DEPARTING -> {
                BlockPos perch = data.elderPerch();
                Vec3 dir = new Vec3(perch.getX(), 0, perch.getZ()).normalize();
                WardenFight.steer(dragon, new Vec3(dir.x * 600, 170, dir.z * 600));
                if (t >= DEPART_TICKS) {
                    complete(end, dragon, data);
                }
            }
            default -> { }
        }
    }

    private static void complete(ServerLevel end, EnderDragon dragon, WardenData data) {
        EnderDragonFight fight = end.getDragonFight();
        if (fight != null) {
            fight.setDragonKilled(dragon); // the exit portal reopens and a gateway appears, exactly as after a kill
        }
        dragon.discard();
        for (EndCrystal c : stillnessCrystals(end)) {
            c.discard();
        }
        data.setRelease(WardenData.RELEASE_DONE);
        for (ServerPlayer p : end.players()) {
            Progress.award(p, "warden_released");
            p.sendSystemMessage(Component.translatable("message.worldsfall.warden.released_toast"));
        }
        dev.worldsfall.restoration.Restoration.award(end.getServer(), null, dev.worldsfall.restoration.Restoration.Act.RELEASE_WARDEN);
    }

    /** The tallest Fallen Spire's top, searched outward from the Far Rim; a fixed far point if none is found. */
    static BlockPos findPerch(ServerLevel end) {
        BlockPos best = null;
        int bestTop = Integer.MIN_VALUE;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            BlockPos from = new BlockPos((int) (Math.cos(a) * 3000), 64, (int) (Math.sin(a) * 3000));
            BlockPos p = end.findNearestMapStructure(WFTags.Structures.FALLEN_SPIRES, from, 50, false);
            if (p == null) {
                continue;
            }
            var chunk = end.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
            for (var start : chunk.getAllStarts().values()) {
                for (var piece : start.getPieces()) {
                    if (piece instanceof BlueprintPiece bp && "fallen_spire".equals(bp.generator())) {
                        BoundingBox box = bp.getBoundingBox();
                        if (box.maxY() > bestTop) {
                            bestTop = box.maxY();
                            best = new BlockPos((box.minX() + box.maxX()) / 2, box.maxY() + 3, (box.minZ() + box.maxZ()) / 2);
                        }
                    }
                }
            }
        }
        return best != null ? best : new BlockPos(2600, 120, 0);
    }

    /** After the Release a fresh crystal on the exit portal is refused (and handed back). */
    public static boolean refusesCrystal(ServerLevel end, EndCrystal crystal) {
        if (!WardenData.get(end.getServer()).released()) {
            return false;
        }
        double dx = crystal.getX() - 0.5, dz = crystal.getZ() - 0.5;
        return dx * dx + dz * dz < 25 && Math.abs(crystal.getY() - WardenFight.portalTop(end).getY()) < 8;
    }

    /** Within 128 blocks of the Elder Warden's perch Echoes do not spawn and the tide never rises above Y 20. */
    public static boolean nearElder(ServerLevel level, double x, double z) {
        if (level.dimension() != net.minecraft.world.level.Level.END) {
            return false;
        }
        WardenData data = WardenData.get(level.getServer());
        BlockPos p = data.elderPerch();
        if (!data.released() || p == null) {
            return false;
        }
        double dx = x - p.getX(), dz = z - p.getZ();
        return dx * dx + dz * dz < 128 * 128;
    }

    static int perchSurface(ServerLevel end, BlockPos perch) {
        return end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, perch.getX(), perch.getZ());
    }

    static EntitySpawnReason reason() {
        return EntitySpawnReason.EVENT;
    }

    static net.minecraft.world.entity.EntityType<ElderWarden> type() {
        return WFEntities.ELDER_WARDEN.get();
    }
}
