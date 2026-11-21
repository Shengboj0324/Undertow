package dev.worldsfall.undertow;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A Restoration Mural panel: dark undershale that fills with warm purpur as the End is restored (one panel per 5%). */
public class MuralPanelBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public MuralPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
