package dev.worldsfall.block.entity;

import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.world.reassembly.ReassemblyPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds the Reassembly plan a Spire Key unlocks (§4.3). */
public class KeystoneSocketBlockEntity extends WFBlockEntity {
    private ReassemblyPlan plan = ReassemblyPlan.EMPTY;
    private boolean grand;

    public KeystoneSocketBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.KEYSTONE_SOCKET.get(), pos, state);
    }

    public ReassemblyPlan plan() {
        return plan;
    }

    public boolean grand() {
        return grand;
    }

    public void setPlan(ReassemblyPlan plan, boolean grand) {
        this.plan = plan;
        this.grand = grand;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("plan", ReassemblyPlan.CODEC, plan);
        out.putBoolean("grand", grand);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        plan = in.read("plan", ReassemblyPlan.CODEC).orElse(ReassemblyPlan.EMPTY);
        grand = in.getBooleanOr("grand", false);
    }
}
