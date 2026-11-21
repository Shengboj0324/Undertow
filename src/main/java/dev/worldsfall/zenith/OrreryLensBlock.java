package dev.worldsfall.zenith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The Orrery's dark lens. It focuses (and shines a beam upward) when all three rings are aligned. */
public class OrreryLensBlock extends Block {
    public static final BooleanProperty FOCUSED = BooleanProperty.create("focused");

    public OrreryLensBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FOCUSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOCUSED);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FOCUSED)) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1 + random.nextDouble() * 6, pos.getZ() + 0.5, 0, 0.25, 0);
            }
        }
    }
}
