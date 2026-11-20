package dev.worldsfall.entity;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Wild Shulker (§8.6): less aggressive (6-block range), molts every 40 loaded minutes (drops a shell, stays
 * exposed and peaceful for 5 minutes), and can be lured with a Chorus Pod. At most 8 molting per 32 blocks.
 */
public class WildShulker extends Shulker {
    private static final EntityDataAccessor<Integer> MOLTED = SynchedEntityData.defineId(WildShulker.class, EntityDataSerializers.INT);
    public static final int MOLT_EXPOSED_TICKS = 5 * 1200;
    public static final int MOLT_RADIUS_CAP = 8;

    private int moltTimer;

    public WildShulker(EntityType<? extends WildShulker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createWildAttributes() {
        return Shulker.createAttributes().add(Attributes.FOLLOW_RANGE, 6.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOLTED, 0);
    }

    public boolean isMolted() {
        return entityData.get(MOLTED) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        int molted = entityData.get(MOLTED);
        if (molted > 0) {
            entityData.set(MOLTED, molted - 1);
            setTarget(null);
            return;
        }
        int period = WFConfig.get(WFConfig.SERVER.wildShulkerMoltMinutes, 40) * 1200;
        if (++moltTimer >= period) {
            moltTimer = 0;
            long nearbyMolting = server.getEntitiesOfClass(WildShulker.class, new AABB(blockPosition()).inflate(32), WildShulker::isMolted).size();
            if (nearbyMolting < MOLT_RADIUS_CAP) {
                molt(server);
            }
        }
    }

    public void molt(ServerLevel level) {
        entityData.set(MOLTED, MOLT_EXPOSED_TICKS);
        spawnAtLocation(level, new ItemStack(Items.SHULKER_SHELL));
        playSound(WFSounds.ENTITY_WILD_SHULKER_MOLT.get(), 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.3, 0.3, 0.02);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isMolted() && super.canAttack(target);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(WFTags.Items.SHULKER_LURE)) {
            if (level() instanceof ServerLevel server) {
                stack.consume(1, player);
                for (int i = 0; i < 24; i++) {
                    BlockPos p = player.blockPosition().offset(random.nextInt(17) - 8, random.nextInt(5) - 2, random.nextInt(17) - 8);
                    if (server.getBlockState(p).isAir() && server.getBlockState(p.below()).isFaceSturdy(server, p.below(), net.minecraft.core.Direction.UP)) {
                        server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.5, getZ(), 16, 0.3, 0.3, 0.3, 0.2);
                        teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                        break;
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("molt_timer", moltTimer);
        out.putInt("molted", entityData.get(MOLTED));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        moltTimer = in.getIntOr("molt_timer", random.nextInt(48000));
        entityData.set(MOLTED, in.getIntOr("molted", 0));
    }
}
