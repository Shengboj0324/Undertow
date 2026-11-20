package dev.worldsfall.entity;

import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tidewisp (§8.7): a soft teal-white light that drifts in loose swarms, gathers and pulses brighter as the
 * tide approaches, and despawns once High Tide begins. Capture one with a glass bottle.
 */
public class Tidewisp extends AmbientCreature {
    private Vec3 drift = Vec3.ZERO;

    public Tidewisp(EntityType<? extends Tidewisp> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0);
    }

    /** 0..1 pulse intensity: brighter as the tide approaches (client and server agree). */
    public float pulse(float partial) {
        float base = 0.45f + 0.25f * (float) Math.sin((tickCount + partial) * 0.15 + getId());
        return Tides.isStirring(level()) ? Math.min(1, base + 0.4f) : base;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (random.nextInt(3) == 0) {
                level().addParticle(WFParticles.TIDEWISP_TRAIL.get(), getX(), getY() + 0.2, getZ(), 0, 0, 0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (Tides.appliesTo(level) && Tides.phase(level) == TideCycle.Phase.HIGH && random.nextInt(40) == 0) {
            discard();
            return;
        }
        if (tickCount % 40 == 0) {
            Player p = level.getNearestPlayer(this, 24);
            Vec3 goal = p != null && Tides.isStirring(level) ? p.position().add(random.nextGaussian() * 4, 2 + random.nextGaussian(), random.nextGaussian() * 4)
                : position().add(random.nextGaussian() * 3, random.nextGaussian(), random.nextGaussian() * 3);
            drift = goal.subtract(position()).normalize().scale(0.04);
            if (tickCount % 120 == 0) {
                playSound(WFSounds.ENTITY_TIDEWISP_PULSE.get(), 0.25f, 0.9f + random.nextFloat() * 0.3f);
            }
        }
        setDeltaMovement(getDeltaMovement().scale(0.9).add(drift));
        if (Tides.appliesTo(level) && getY() < Tides.height(level) + 1) {
            setDeltaMovement(getDeltaMovement().add(0, 0.03, 0));
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                dev.worldsfall.util.WFUtil.give(player, new ItemStack(WFItems.BOTTLED_TIDEWISP.get()));
                playSound(net.minecraft.sounds.SoundEvents.BOTTLE_FILL, 1.0f, 1.4f);
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
