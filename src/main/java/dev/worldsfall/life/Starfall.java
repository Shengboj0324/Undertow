package dev.worldsfall.life;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Starfall (design §5.6, proposal "World events"): a rising chime and a streak across the sky, then a glowing shard
 * cluster lands within 200 blocks of a player. The shards stay five minutes; whatever is not mined fades.
 */
final class Starfall {
    private Starfall() {}

    static boolean begin(ServerLevel end, WorldEventData data) {
        ServerPlayer p = WorldEvents.anyPlayer(end);
        if (p == null) {
            return false;
        }
        BlockPos site = WorldEvents.islandTop(end, p.blockPosition(), 40, 200, end.getRandom());
        if (site == null) {
            site = WorldEvents.islandTop(end, p.blockPosition(), 8, 60, end.getRandom());
        }
        if (site == null) {
            return false;
        }
        data.setSite(site);
        WorldEvents.playToAll(end, WFSounds.EVENT_STARFALL_STREAK.get(), 1.0f, 1.0f);
        return true;
    }

    /** Where the streak starts: high in the sky, off to one side, so it crosses the view. */
    static Vec3 streakFrom(BlockPos site) {
        return new Vec3(site.getX() - 160, site.getY() + 150, site.getZ() - 70);
    }

    static void tick(ServerLevel end, WorldEventData data, long now) {
        long t = now - data.start();
        BlockPos site = data.site();
        if (t < WorldEvents.STARFALL_TELEGRAPH) {
            double k = t / (double) WorldEvents.STARFALL_TELEGRAPH;
            Vec3 from = streakFrom(site), to = Vec3.atCenterOf(site);
            Vec3 head = from.lerp(to, k * k);
            for (ServerPlayer p : end.players()) {
                for (int i = 0; i < 6; i++) {
                    Vec3 trail = head.lerp(from, i * 0.012);
                    end.sendParticles(p, WFParticles.STARFALL_SPARK.get(), true, true, trail.x, trail.y, trail.z, 2, 0.4, 0.4, 0.4, 0.0);
                }
            }
            return;
        }
        if (t == WorldEvents.STARFALL_TELEGRAPH) {
            land(end, data, site);
        }
        if (now >= data.end()) {
            end(end, data);
            WorldEvents.finish(end, data);
        } else if (t % 40 == 0) {
            end.sendParticles(WFParticles.STARFALL_SPARK.get(), site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 6, 1.5, 1.0, 1.5, 0.01);
        }
    }

    private static void land(ServerLevel end, WorldEventData data, BlockPos site) {
        RandomSource r = end.getRandom();
        end.playSound(null, site, WFSounds.EVENT_STARFALL_LAND.get(), SoundSource.AMBIENT, 6.0f, 1.0f);
        end.sendParticles(ParticleTypes.EXPLOSION_EMITTER, site.getX() + 0.5, site.getY() + 0.5, site.getZ() + 0.5, 1, 0, 0, 0, 0);
        end.sendParticles(WFParticles.STARFALL_SPARK.get(), site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 120, 2.5, 1.5, 2.5, 0.2);
        int shards = 5 + r.nextInt(4);
        for (int i = 0; i < shards * 4 && data.placed().size() < shards; i++) {
            BlockPos p = site.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos q = p.above(dy);
                if (end.getBlockState(q).isAir() && end.getBlockState(q.below()).isFaceSturdy(end, q.below(), net.minecraft.core.Direction.UP)) {
                    end.setBlock(q, WFBlocks.STARFALL_SHARD.get().defaultBlockState(), Block.UPDATE_ALL);
                    data.addPlaced(q);
                    break;
                }
            }
        }
    }

    /** The shards that nobody mined fade back into the sky. */
    static void end(ServerLevel end, WorldEventData data) {
        for (BlockPos p : data.placed()) {
            if (end.isLoaded(p) && end.getBlockState(p).is(WFBlocks.STARFALL_SHARD.get())) {
                end.removeBlock(p, false);
                end.sendParticles(WFParticles.STARFALL_SPARK.get(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }
}
