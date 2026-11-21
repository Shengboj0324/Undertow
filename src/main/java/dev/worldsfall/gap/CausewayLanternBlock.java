package dev.worldsfall.gap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The Causeway's lanterns: magenta while the road is broken, calm silver once the last span is restored. */
public class CausewayLanternBlock extends LanternBlock {
    public static final BooleanProperty CALM = BooleanProperty.create("calm");

    public CausewayLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CALM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CALM);
    }
}
