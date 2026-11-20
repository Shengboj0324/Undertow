package dev.worldsfall.fluid;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.registry.WFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCauldronInteractionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Stillwater in cauldrons and bottles (§7.5): an empty cauldron takes a Stillwater bucket; a Stillwater cauldron gives
 * Bottles of Stillwater (one level each) or a full bucket back; a glass bottle also fills straight from a Stillwater source.
 */
public final class StillwaterCauldron {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "stillwater");
    private static final Identifier EMPTY = Identifier.withDefaultNamespace("empty");
    /** Interactions of the Stillwater cauldron itself; filled in by {@link #interactions}. */
    public static final CauldronInteraction.Dispatcher INTERACTIONS = new CauldronInteraction.Dispatcher();

    private StillwaterCauldron() {
    }

    public static void dispatchers(RegisterCauldronInteractionEvent.Dispatcher event) {
        event.register(ID, INTERACTIONS);
    }

    public static void interactions(RegisterCauldronInteractionEvent.Interaction event) {
        event.register(EMPTY, WFItems.STILLWATER_BUCKET.get(), (state, level, pos, player, hand, stack) -> CauldronInteractions.emptyBucket(level, pos,
            player, hand, stack, WFBlocks.STILLWATER_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY));
        event.register(EMPTY, WFItems.BOTTLE_OF_STILLWATER.get(), (state, level, pos, player, hand, stack) ->
            pour(level, pos, player, hand, stack, WFBlocks.STILLWATER_CAULDRON.get().defaultBlockState()));
        event.register(ID, WFItems.STILLWATER_BUCKET.get(), (state, level, pos, player, hand, stack) -> CauldronInteractions.emptyBucket(level, pos,
            player, hand, stack, state.setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY));
        event.register(ID, Items.BUCKET, (state, level, pos, player, hand, stack) -> CauldronInteractions.fillBucket(state, level, pos, player, hand,
            stack, new ItemStack(WFItems.STILLWATER_BUCKET.get()), s -> s.getValue(LayeredCauldronBlock.LEVEL) == 3, SoundEvents.BUCKET_FILL));
        event.register(ID, Items.GLASS_BOTTLE, StillwaterCauldron::draw);
        event.register(ID, WFItems.BOTTLE_OF_STILLWATER.get(), (state, level, pos, player, hand, stack) ->
            state.getValue(LayeredCauldronBlock.LEVEL) == 3 ? InteractionResult.TRY_WITH_EMPTY_HAND
                : pour(level, pos, player, hand, stack, state.cycle(LayeredCauldronBlock.LEVEL)));
    }

    private static InteractionResult draw(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide()) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(WFItems.BOTTLE_OF_STILLWATER.get())));
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 0.7f);
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult pour(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, BlockState result) {
        if (!level.isClientSide()) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            level.setBlockAndUpdate(pos, result);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.7f);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** A glass bottle used on a Stillwater source fills to a Bottle of Stillwater (vanilla only handles water). */
    public static void bottleFromSource(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return;
        }
        Level level = event.getLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (level.getFluidState(pos).getType() != WFFluids.STILLWATER.get() || !level.mayInteract(player, pos)) {
            return;
        }
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 0.7f);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(stack, player, new ItemStack(WFItems.BOTTLE_OF_STILLWATER.get())));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
