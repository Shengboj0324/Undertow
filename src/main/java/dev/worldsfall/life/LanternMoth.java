package dev.worldsfall.life;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.util.WFUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lantern Moth (proposal "New creature roster"): a small magenta moth of the Fallen Spires, drawn to Spire, Causeway and
 * Warden's Lanterns, which it circles. Catch one in a glass bottle to carry it home as a light.
 */
public class LanternMoth extends AmbientCreature {
    public static final int LANTERN_RANGE = 12;
    private Vec3 drift = Vec3.ZERO;
    private @Nullable BlockPos lantern;

    public LanternMoth(EntityType<? extends LanternMoth> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0);
    }

    public static boolean isLantern(BlockState s) {
        return s.is(WFBlocks.SPIRE_LANTERN.get()) || s.is(WFBlocks.CAUSEWAY_LANTERN.get()) || s.is(WFBlocks.WARDENS_LANTERN.get())
            || s.is(WFBlocks.LANTERN_MOTH_JAR.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount % 60 == 0 || lantern != null && !isLantern(level.getBlockState(lantern))) {
            lantern = findLantern(level);
        }
        if (tickCount % 10 == 0) {
            Vec3 goal;
            if (lantern != null) {
                double a = tickCount * 0.09 + getId();
                goal = Vec3.atCenterOf(lantern).add(Math.cos(a) * 1.4, 0.3 + Math.sin(a * 1.7) * 0.5, Math.sin(a) * 1.4);
            } else {
                goal = position().add(random.nextGaussian() * 3, random.nextGaussian() * 1.2, random.nextGaussian() * 3);
            }
            drift = goal.subtract(position()).normalize().scale(lantern != null ? 0.05 : 0.03);
        }
        setDeltaMovement(getDeltaMovement().scale(0.86).add(drift).add(0, Math.sin(tickCount * 0.6) * 0.012, 0));
        if (tickCount % 140 == 0 && random.nextInt(3) == 0) {
            playSound(WFSounds.ENTITY_LANTERN_MOTH_FLUTTER.get(), 0.3f, 0.9f + random.nextFloat() * 0.3f);
        }
    }

    private @Nullable BlockPos findLantern(ServerLevel level) {
        BlockPos here = blockPosition();
        BlockPos best = null;
        double bestD = LANTERN_RANGE * LANTERN_RANGE;
        for (BlockPos p : BlockPos.betweenClosed(here.offset(-LANTERN_RANGE, -6, -LANTERN_RANGE), here.offset(LANTERN_RANGE, 6, LANTERN_RANGE))) {
            if (isLantern(level.getBlockState(p))) {
                double d = p.distSqr(here);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    public @Nullable BlockPos lantern() {
        return lantern;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                WFUtil.give(player, new ItemStack(WFItems.BOTTLED_LANTERN_MOTH.get()));
                playSound(SoundEvents.BOTTLE_FILL, 1.0f, 1.6f);
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return lantern == null;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
