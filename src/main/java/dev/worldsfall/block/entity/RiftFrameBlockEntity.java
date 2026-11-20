package dev.worldsfall.block.entity;

import dev.worldsfall.block.RiftFrameBlock;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Keeps a Fracture Rift open for 10 minutes after activation (§12.2, §13.2), then closes it. */
public class RiftFrameBlockEntity extends WFBlockEntity {
    public static final int OPEN_TICKS = 10 * 1200;
    private long openUntil = -1;

    public RiftFrameBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.RIFT_FRAME.get(), pos, state);
    }

    public boolean isOpen() {
        return openUntil >= 0;
    }

    public void open(ServerLevel level) {
        openUntil = level.getGameTime() + OPEN_TICKS;
        for (BlockPos p : surface(worldPosition)) {
            if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
                level.setBlock(p, WFBlocks.FRACTURE_RIFT.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.playSound(null, worldPosition, WFSounds.EVENT_RIFT_OPEN.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        sync();
    }

    public void close(ServerLevel level) {
        openUntil = -1;
        for (BlockPos p : surface(worldPosition)) {
            if (level.getBlockState(p).is(WFBlocks.FRACTURE_RIFT.get())) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockState state = getBlockState();
        if (state.hasProperty(RiftFrameBlock.SIGILS)) {
            level.setBlock(worldPosition, state.setValue(RiftFrameBlock.SIGILS, 0), Block.UPDATE_ALL);
        }
        level.playSound(null, worldPosition, WFSounds.EVENT_RIFT_CLOSE.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        sync();
    }

    /** The 3×3 rift surface one block above the frame. */
    public static Iterable<BlockPos> surface(BlockPos frame) {
        return BlockPos.betweenClosed(frame.offset(-1, 1, -1), frame.offset(1, 1, 1));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftFrameBlockEntity be) {
        if (level instanceof ServerLevel server && be.openUntil >= 0 && server.getGameTime() > be.openUntil) {
            be.close(server);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("open_until", openUntil);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        openUntil = in.getLongOr("open_until", -1);
    }
}
