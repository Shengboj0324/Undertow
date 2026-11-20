package dev.worldsfall.block.entity;

import dev.worldsfall.block.EggCradleBlock;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Dragon Egg hatching ritual (§10.2): Attune (posts light over 30 s) → Nourish (8 Glow Glands, one per
 * 5 min, a crack per feeding) → Vigil (30 min of loaded time with a player within 32 blocks; 15 min in the
 * End) → Hatch. In the End the cradle works twice as fast. Also restores Slumbering Eggs in 10 minutes (§10.4).
 */
public class EggCradleBlockEntity extends WFBlockEntity {
    public enum Stage implements StringRepresentable {
        EMPTY, ATTUNING, NOURISHING, VIGIL, HATCHING, SLUMBER;
        public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final int ATTUNE_TICKS = 600;
    public static final int GLANDS_REQUIRED = 8;
    public static final int FEED_COOLDOWN = 6000;
    public static final int VIGIL_TICKS = 36000;
    public static final int HATCH_ANIM_TICKS = 80;
    public static final int RESTORE_TICKS = 12000;
    public static final double VIGIL_RANGE = 32;

    private Stage stage = Stage.EMPTY;
    private int progress;
    private int glands;
    private long lastFeed = Long.MIN_VALUE / 2;
    private final Map<UUID, Integer> feeders = new HashMap<>();
    private CompoundTag slumberingWyrm = new CompoundTag();

    public EggCradleBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.EGG_CRADLE.get(), pos, state);
    }

    public Stage stage() { return stage; }
    public int progress() { return progress; }
    public int glands() { return glands; }

    /** 1 normally, 2 in the End (§10.2 step 1). */
    public int speed() {
        return level != null && level.dimension() == Level.END ? 2 : 1;
    }

    public int vigilRequired() {
        return VIGIL_TICKS;
    }

    public boolean placeDragonEgg() {
        if (stage != Stage.EMPTY) {
            return false;
        }
        stage = Stage.ATTUNING;
        progress = 0;
        glands = 0;
        feeders.clear();
        sync();
        return true;
    }

    public boolean placeSlumberingEgg(CompoundTag wyrmData) {
        if (stage != Stage.EMPTY) {
            return false;
        }
        stage = Stage.SLUMBER;
        progress = 0;
        slumberingWyrm = wyrmData.copy();
        sync();
        return true;
    }

    public enum FeedResult { WRONG_STAGE, TOO_SOON, FED, COMPLETE }

    public FeedResult feed(ServerPlayer player) {
        if (stage != Stage.NOURISHING) {
            return FeedResult.WRONG_STAGE;
        }
        long now = level.getGameTime();
        if (glands > 0 && now - lastFeed < FEED_COOLDOWN / speed()) {
            return FeedResult.TOO_SOON;
        }
        lastFeed = now;
        glands++;
        feeders.merge(player.getUUID(), 1, Integer::sum);
        level.playSound(null, worldPosition, WFSounds.EVENT_EGG_CRACK.get(), SoundSource.BLOCKS, 1.0f, 0.9f + glands * 0.03f);
        if (glands >= GLANDS_REQUIRED) {
            stage = Stage.VIGIL;
            progress = 0;
        }
        sync();
        return stage == Stage.VIGIL ? FeedResult.COMPLETE : FeedResult.FED;
    }

    public long ticksUntilNextFeed() {
        if (level == null || glands == 0) {
            return 0;
        }
        return Math.max(0, FEED_COOLDOWN / speed() - (level.getGameTime() - lastFeed));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EggCradleBlockEntity be) {
        if (level instanceof ServerLevel server) {
            be.tickServer(server, pos, state);
        }
    }

    private void tickServer(ServerLevel server, BlockPos pos, BlockState state) {
        int posts = state.getValue(EggCradleBlock.POSTS);
        int wantPosts = posts;
        switch (stage) {
            case EMPTY -> wantPosts = 0;
            case ATTUNING -> {
                progress += speed();
                wantPosts = Math.min(4, progress * 4 / ATTUNE_TICKS);
                if (progress >= ATTUNE_TICKS) {
                    stage = Stage.NOURISHING;
                    progress = 0;
                    wantPosts = 4;
                    sync();
                }
            }
            case NOURISHING -> {
                wantPosts = 4;
                if (server.getGameTime() % 100 == 0) {
                    server.playSound(null, pos, WFSounds.EVENT_EGG_PULSE.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
                }
            }
            case VIGIL -> {
                wantPosts = 4;
                boolean watched = server.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, VIGIL_RANGE, false) != null;
                if (watched) {
                    progress += speed();
                    if (server.getGameTime() % 60 == 0) {
                        server.playSound(null, pos, WFSounds.EVENT_EGG_PULSE.get(), SoundSource.BLOCKS, 0.8f, 1.1f);
                    }
                    if (progress % 200 == 0) {
                        sync();
                    }
                }
                if (progress >= vigilRequired()) {
                    stage = Stage.HATCHING;
                    progress = 0;
                    sync();
                }
            }
            case HATCHING -> {
                progress++;
                if (progress == 1) {
                    server.playSound(null, pos, WFSounds.EVENT_EGG_HATCH.get(), SoundSource.BLOCKS, 1.5f, 1.0f);
                }
                if (progress >= HATCH_ANIM_TICKS) {
                    hatch(server, pos);
                }
            }
            case SLUMBER -> {
                progress++;
                if (progress >= RESTORE_TICKS) {
                    restore(server, pos);
                }
            }
        }
        if (wantPosts != posts) {
            server.setBlock(pos, state.setValue(EggCradleBlock.POSTS, wantPosts).setValue(EggCradleBlock.EGG, stage != Stage.EMPTY), Block.UPDATE_ALL);
        } else if (state.getValue(EggCradleBlock.EGG) != (stage != Stage.EMPTY)) {
            server.setBlock(pos, state.setValue(EggCradleBlock.EGG, stage != Stage.EMPTY), Block.UPDATE_ALL);
        }
    }

    public Optional<UUID> topFeeder() {
        return feeders.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    private void hatch(ServerLevel server, BlockPos pos) {
        Wyrm wyrm = WFEntities.WYRM.get().create(server, EntitySpawnReason.TRIGGERED);
        if (wyrm != null) {
            wyrm.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, server.getRandom().nextFloat() * 360, 0);
            UUID owner = topFeeder().orElse(null);
            ServerPlayer ownerPlayer = owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer == null) {
                ownerPlayer = (ServerPlayer) server.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 64, false);
            }
            if (ownerPlayer != null) {
                wyrm.tame(ownerPlayer);
                Progress.award(ownerPlayer, "warm_hands");
            }
            server.addFreshEntity(wyrm);
        }
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 0.4, 0.5, 0.4, 0.12);
        server.sendParticles(dev.worldsfall.registry.WFParticles.SHELL_FRAGMENT.get(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
            24, 0.3, 0.3, 0.3, 0.15);
        reset();
    }

    private void restore(ServerLevel server, BlockPos pos) {
        Wyrm wyrm = WFEntities.WYRM.get().create(server, EntitySpawnReason.TRIGGERED);
        if (wyrm != null) {
            wyrm.restoreFromSlumber(server, slumberingWyrm);
            wyrm.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, server.getRandom().nextFloat() * 360, 0);
            server.addFreshEntity(wyrm);
        }
        server.playSound(null, pos, WFSounds.EVENT_EGG_HATCH.get(), SoundSource.BLOCKS, 1.0f, 1.2f);
        reset();
    }

    private void reset() {
        stage = Stage.EMPTY;
        progress = 0;
        glands = 0;
        feeders.clear();
        slumberingWyrm = new CompoundTag();
        sync();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("stage", Stage.CODEC, stage);
        out.putInt("progress", progress);
        out.putInt("glands", glands);
        out.putLong("last_feed", lastFeed);
        out.store("feeders", Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT), Map.copyOf(feeders));
        out.store("slumbering", CompoundTag.CODEC, slumberingWyrm);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        stage = in.read("stage", Stage.CODEC).orElse(Stage.EMPTY);
        progress = in.getIntOr("progress", 0);
        glands = in.getIntOr("glands", 0);
        lastFeed = in.getLongOr("last_feed", Long.MIN_VALUE / 2);
        feeders.clear();
        in.read("feeders", Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)).ifPresent(feeders::putAll);
        slumberingWyrm = in.read("slumbering", CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }
}
