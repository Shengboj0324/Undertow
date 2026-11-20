package dev.worldsfall.world.reassembly;

import dev.worldsfall.block.entity.PerPlayerLootBlockEntity;
import dev.worldsfall.entity.FlyingBlock;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs Reassembly sequences (§11.1): chord + tremble (2 s) → staggered flights (~8 s) → lanterns ignite one
 * by one → vault unseals. Total ≤ 15 s, ≤ 120 flying blocks (§20.8). Ticked from the server tick.
 */
public final class Reassembly {
    private static final List<Sequence> ACTIVE = new ArrayList<>();
    public static final int TREMBLE_TICKS = 40;
    public static final int LAUNCH_WINDOW = 150;
    public static final int FLIGHT_MIN = 30;
    public static final int FLIGHT_MAX = 46;

    private Reassembly() {}

    public static void start(ServerLevel level, BlockPos socket, ReassemblyPlan plan, ServerPlayer trigger) {
        level.playSound(null, socket, WFSounds.EVENT_REASSEMBLY_CHORD.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        synchronized (ACTIVE) {
            ACTIVE.add(new Sequence(level, socket, plan));
        }
    }

    public static int activeCount() {
        synchronized (ACTIVE) {
            return ACTIVE.size();
        }
    }

    public static void tick(ServerLevel level) {
        synchronized (ACTIVE) {
            Iterator<Sequence> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Sequence s = it.next();
                if (s.level == level && s.tick()) {
                    it.remove();
                }
            }
        }
    }

    /** Completes all running sequences immediately (server stopping). */
    public static void finishAll() {
        synchronized (ACTIVE) {
            for (Sequence s : ACTIVE) {
                s.finishInstantly();
            }
            ACTIVE.clear();
        }
    }

    static final class Sequence {
        final ServerLevel level;
        final BlockPos socket;
        final ReassemblyPlan plan;
        final List<ReassemblyPlan.Target> pending;
        final List<BlockPos> sources;
        int age;
        int launched;
        int lanternIndex;
        int endAge = -1;

        Sequence(ServerLevel level, BlockPos socket, ReassemblyPlan plan) {
            this.level = level;
            this.socket = socket;
            this.plan = plan;
            this.pending = new ArrayList<>(plan.targets().subList(0, Math.min(plan.targets().size(), ReassemblyPlan.MAX_FLYING)));
            this.sources = new ArrayList<>(plan.rubble());
        }

        /** Returns true when finished. */
        boolean tick() {
            age++;
            if (age <= TREMBLE_TICKS) {
                if (age % 4 == 0) {
                    for (BlockPos r : sources) {
                        BlockState st = level.getBlockState(r);
                        if (!st.isAir()) {
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), r.getX() + 0.5, r.getY() + 0.6, r.getZ() + 0.5,
                                1, 0.25, 0.05, 0.25, 0.0);
                        }
                    }
                }
                return false;
            }
            int t = age - TREMBLE_TICKS;
            int total = pending.size();
            int shouldHaveLaunched = total == 0 ? 0 : Math.min(total, (int) Math.ceil(total * (t / (double) LAUNCH_WINDOW)));
            while (launched < shouldHaveLaunched) {
                launch(pending.get(launched), launched);
                launched++;
            }
            if (launched >= total && endAge < 0) {
                endAge = age + FLIGHT_MAX + 4;
            }
            if (endAge > 0 && age >= endAge) {
                if (lanternIndex < plan.lanterns().size()) {
                    if ((age - endAge) % 5 == 0) {
                        igniteLantern(plan.lanterns().get(lanternIndex++));
                    }
                    return false;
                }
                complete();
                return true;
            }
            return false;
        }

        private void launch(ReassemblyPlan.Target target, int index) {
            BlockPos dest = target.pos();
            BlockState existing = level.getBlockState(dest);
            if (!existing.isAir() && !existing.canBeReplaced()) {
                return; // occupied (possibly by a player block): skip, never overwrite (§11.1 step 5)
            }
            Vec3 from;
            if (!sources.isEmpty()) {
                BlockPos src = sources.remove(level.getRandom().nextInt(sources.size()));
                if (level.getBlockState(src).is(WFBlocks.PURPUR_RUBBLE.get())) {
                    level.removeBlock(src, false);
                }
                from = Vec3.atBottomCenterOf(src);
            } else {
                double ang = level.getRandom().nextDouble() * Math.PI * 2;
                from = Vec3.atBottomCenterOf(socket).add(Math.cos(ang) * 10, -2 - level.getRandom().nextInt(4), Math.sin(ang) * 10);
            }
            int duration = FLIGHT_MIN + level.getRandom().nextInt(FLIGHT_MAX - FLIGHT_MIN + 1);
            float arc = 2.0f + level.getRandom().nextFloat() * 4.0f;
            level.addFreshEntity(FlyingBlock.create(level, target.state(), from, dest, duration, arc, FlyingBlock.MODE_PLACE));
            if (index % 3 == 0) {
                level.playSound(null, BlockPos.containing(from), WFSounds.EVENT_REASSEMBLY_FLY.get(), SoundSource.BLOCKS, 0.6f,
                    0.85f + level.getRandom().nextFloat() * 0.3f);
            }
        }

        private void igniteLantern(BlockPos pos) {
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || st.canBeReplaced()) {
                level.setBlock(pos, WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING,
                    !level.getBlockState(pos.above()).isAir()), Block.UPDATE_ALL);
            }
            level.playSound(null, pos, WFSounds.EVENT_REASSEMBLY_LANTERN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.02);
        }

        private void complete() {
            boolean shellComplete = plan.shell().stream().noneMatch(p -> level.getBlockState(p).isAir());
            plan.vault().ifPresent(v -> {
                if (shellComplete && level.getBlockEntity(v) instanceof PerPlayerLootBlockEntity vault) {
                    vault.unseal();
                }
            });
            for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().closerThan(socket, 48))) {
                Progress.award(p, "ladders_to_nowhere");
            }
        }

        void finishInstantly() {
            for (ReassemblyPlan.Target target : pending.subList(Math.min(launched, pending.size()), pending.size())) {
                BlockState existing = level.getBlockState(target.pos());
                if (existing.isAir() || existing.canBeReplaced()) {
                    level.setBlock(target.pos(), target.state(), Block.UPDATE_ALL);
                }
            }
            for (int i = lanternIndex; i < plan.lanterns().size(); i++) {
                igniteLantern(plan.lanterns().get(i));
            }
            complete();
        }
    }
}
