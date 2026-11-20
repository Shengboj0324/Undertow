package dev.worldsfall.entity;

import dev.worldsfall.block.PaleLoamBlock;
import dev.worldsfall.registry.WFBiomes;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.IShearable;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Chorus Grazer (§8.3): the living part of the Chorus Reach food web. Eats fallen Chorus Pods, leaves Pale
 * Loam behind (1 block per minute while walking on end stone in Chorus Reach), shearable frill regrows in
 * 5 minutes, breeds with pods.
 */
public class ChorusGrazer extends Animal implements IShearable {
    private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(ChorusGrazer.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GRAZE_TICKS = SynchedEntityData.defineId(ChorusGrazer.class, EntityDataSerializers.INT);
    public static final int FRILL_REGROW = 5 * 1200;
    public static final int LOAM_INTERVAL = 1200;
    private int regrow;
    private int loamTimer;

    public ChorusGrazer(EntityType<? extends ChorusGrazer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.TEMPT_RANGE, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHEARED, false);
        builder.define(GRAZE_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.15, s -> s.is(WFTags.Items.GRAZER_FOOD), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(5, new EatFallenPodGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public boolean isSheared() {
        return entityData.get(SHEARED);
    }

    /** Remaining ticks of the "neck lowered" grazing pose, for the renderer. */
    public int grazeTicks() {
        return entityData.get(GRAZE_TICKS);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(WFTags.Items.GRAZER_FOOD);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return WFEntities.CHORUS_GRAZER.get().create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        super.spawnChildFromBreeding(level, partner);
        ServerPlayer breeder = getLoveCause();
        if (breeder != null) {
            Progress.award(breeder, "pale_pasture");
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server) {
            if (isSheared() && ++regrow >= FRILL_REGROW) {
                regrow = 0;
                entityData.set(SHEARED, false);
            }
            if (entityData.get(GRAZE_TICKS) > 0) {
                entityData.set(GRAZE_TICKS, entityData.get(GRAZE_TICKS) - 1);
            }
            if (getDeltaMovement().horizontalDistanceSqr() > 1.0e-4 && ++loamTimer >= LOAM_INTERVAL) {
                loamTimer = 0;
                BlockPos below = blockPosition().below();
                BlockState under = server.getBlockState(below);
                if (under.is(Blocks.END_STONE) && server.getBiome(below).is(WFBiomes.CHORUS_REACH) && !PaleLoamBlock.isCovered(server, below)) {
                    server.setBlockAndUpdate(below, dev.worldsfall.registry.WFBlocks.PALE_LOAM.get().defaultBlockState());
                } else if (under.is(dev.worldsfall.registry.WFBlocks.PALE_LOAM.get())) {
                    PaleLoamBlock.spreadFrom(server, below, random);
                }
            }
        }
    }

    void graze() {
        entityData.set(GRAZE_TICKS, 40);
        playSound(WFSounds.ENTITY_CHORUS_GRAZER_EAT.get(), 1.0f, 1.0f);
        heal(2.0f);
    }

    // ---------------------------------------------------------------- shearing (§8.3)
    @Override
    public boolean isShearable(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
        return isAlive() && !isSheared() && !isBaby();
    }

    @Override
    public List<ItemStack> onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
        level.playSound(null, this, net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 1, 1.2f);
        entityData.set(SHEARED, true);
        regrow = 0;
        return List.of(new ItemStack(WFItems.GRAZER_FRILL.get(), 1 + random.nextInt(2)));
    }

    // ---------------------------------------------------------------- sounds
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return WFSounds.ENTITY_CHORUS_GRAZER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return WFSounds.ENTITY_CHORUS_GRAZER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_CHORUS_GRAZER_DEATH.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putBoolean("sheared", isSheared());
        out.putInt("regrow", regrow);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(SHEARED, in.getBooleanOr("sheared", false));
        regrow = in.getIntOr("regrow", 0);
    }

    /** Walks to a fallen Chorus Pod item and eats it (§4.1 food web). */
    static final class EatFallenPodGoal extends Goal {
        private final ChorusGrazer grazer;
        private ItemEntity target;

        EatFallenPodGoal(ChorusGrazer grazer) {
            this.grazer = grazer;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (grazer.getRandom().nextInt(40) != 0) {
                return false;
            }
            List<ItemEntity> pods = grazer.level().getEntitiesOfClass(ItemEntity.class, grazer.getBoundingBox().inflate(12, 3, 12),
                e -> e.getItem().is(WFItems.CHORUS_POD.get()));
            if (pods.isEmpty()) {
                return false;
            }
            target = pods.get(grazer.getRandom().nextInt(pods.size()));
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && grazer.distanceToSqr(target) > 0.5 && !grazer.getNavigation().isDone();
        }

        @Override
        public void start() {
            grazer.getNavigation().moveTo(target, 0.9);
        }

        @Override
        public void tick() {
            grazer.getLookControl().setLookAt(target);
            if (grazer.distanceToSqr(target) < 2.25 && target.isAlive()) {
                target.getItem().shrink(1);
                if (target.getItem().isEmpty()) {
                    target.discard();
                }
                grazer.graze();
                target = null;
            }
        }

        @Override
        public void stop() {
            target = null;
        }
    }
}
