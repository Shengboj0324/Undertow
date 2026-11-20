package dev.worldsfall.world.reassembly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * What a Keystone Socket restores (§4.3, §11.1): the missing blocks of a sealed vault room, the rubble
 * blocks that fly into place, the lanterns to ignite and the vault to unseal.
 */
public record ReassemblyPlan(List<Target> targets, List<BlockPos> rubble, List<BlockPos> lanterns, Optional<BlockPos> vault, List<BlockPos> shell) {
    /** Hard cap from §16.5 / §20.8. */
    public static final int MAX_FLYING = 120;

    public record Target(BlockPos pos, BlockState state) {
        public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Target::pos),
            BlockState.CODEC.fieldOf("state").forGetter(Target::state)
        ).apply(i, Target::new));
    }

    public static final Codec<ReassemblyPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
        Target.CODEC.listOf().fieldOf("targets").forGetter(ReassemblyPlan::targets),
        BlockPos.CODEC.listOf().fieldOf("rubble").forGetter(ReassemblyPlan::rubble),
        BlockPos.CODEC.listOf().fieldOf("lanterns").forGetter(ReassemblyPlan::lanterns),
        BlockPos.CODEC.optionalFieldOf("vault").forGetter(ReassemblyPlan::vault),
        BlockPos.CODEC.listOf().optionalFieldOf("shell", List.of()).forGetter(ReassemblyPlan::shell)
    ).apply(i, ReassemblyPlan::new));

    public static final ReassemblyPlan EMPTY = new ReassemblyPlan(List.of(), List.of(), List.of(), Optional.empty(), List.of());

    public ReassemblyPlan offset(BlockPos by) {
        return new ReassemblyPlan(
            targets.stream().map(t -> new Target(t.pos.offset(by), t.state)).toList(),
            rubble.stream().map(p -> p.offset(by)).toList(),
            lanterns.stream().map(p -> p.offset(by)).toList(),
            vault.map(p -> p.offset(by)),
            shell.stream().map(p -> p.offset(by)).toList());
    }
}
