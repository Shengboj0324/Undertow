package dev.worldsfall.block.entity;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.gravity.StableFields;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Gravity Anchor: registers a Stable Field of configurable radius while loaded (§9.8). */
public class GravityAnchorBlockEntity extends WFBlockEntity {
    private int radius = 12;

    public GravityAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.GRAVITY_ANCHOR.get(), pos, state);
    }

    public int radius() {
        return radius;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            if (!level.isClientSide()) {
                radius = WFConfig.get(WFConfig.SERVER.anchorRadius, 12);
            }
            StableFields.add(level, worldPosition, radius);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            StableFields.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) {
            StableFields.remove(level, worldPosition);
        }
        super.onChunkUnloaded();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityAnchorBlockEntity be) {
        if (level instanceof ServerLevel server && server.getGameTime() % 60 == 0) {
            server.playSound(null, pos, WFSounds.EVENT_GRAVITY_ANCHOR_HUM.get(), SoundSource.BLOCKS, 0.4f, 1.0f);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("radius", radius);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        radius = in.getIntOr("radius", 12);
        if (level != null) {
            StableFields.add(level, worldPosition, radius);
        }
    }
}
