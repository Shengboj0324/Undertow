package dev.worldsfall.zenith;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Leviathan Drifter (proposal "A fifth layer above"): an enormous, slow sky creature that migrates along Sky Currents
 * carrying a small garden on its back. Players can land on it and ride it as a moving waypoint; its back is solid and
 * carries whoever stands there. It sings a long falling note two minutes before every tide rise, audible from 300 blocks.
 * Barnacles on its back can be scraped off with shears and regrow.
 */
public class LeviathanDrifter extends Mob {
    public static final double SPEED = 0.12;
    public static final int MAX_BARNACLES = 6, BARNACLE_REGROW = 2400, SONG_LEAD = 2400;
    private static final EntityDataAccessor<Integer> BARNACLES = SynchedEntityData.defineId(LeviathanDrifter.class, EntityDataSerializers.INT);
    private @Nullable Vec3 waypoint;
    private int regrow;

    public LeviathanDrifter(EntityType<? extends LeviathanDrifter> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 400.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BARNACLES, MAX_BARNACLES);
    }

    public int barnacles() {
        return entityData.get(BARNACLES);
    }

    // ------------------------------------------------------------------ a solid back

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /** True if {@code e} stands on this Leviathan's back. */
    public boolean carries(Entity e) {
        AABB top = getBoundingBox();
        AABB feet = e.getBoundingBox();
        return feet.minY >= top.maxY - 0.6 && feet.minY <= top.maxY + 0.35 && feet.maxX > top.minX && feet.minX < top.maxX
            && feet.maxZ > top.minZ && feet.minZ < top.maxZ;
    }

    // ------------------------------------------------------------------ flight

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        Vec3 delta = position().subtract(before);
        if (delta.lengthSqr() > 0 && !level().isClientSide()) {
            // carry whatever stands on the back (players move themselves, client-side)
            for (Entity e : level().getEntities(this, getBoundingBox().inflate(0, 1.5, 0), e -> !(e instanceof Player) && !e.isPassenger())) {
                if (carries(e)) {
                    e.setPos(e.getX() + delta.x, e.getY() + delta.y, e.getZ() + delta.z);
                }
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (waypoint == null || waypoint.distanceToSqr(position()) < 24 * 24 || tickCount % 400 == 0) {
            waypoint = nextWaypoint(level);
        }
        Vec3 to = waypoint.subtract(position());
        double targetYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        float yaw = Mth.approachDegrees(getYRot(), (float) targetYaw, 0.6f);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        double rad = Math.toRadians(yaw);
        double climb = Mth.clamp(to.y * 0.01, -0.05, 0.05);
        setDeltaMovement(-Math.sin(rad) * SPEED, climb, Math.cos(rad) * SPEED);
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (barnacles() < MAX_BARNACLES && ++regrow >= BARNACLE_REGROW) {
            regrow = 0;
            entityData.set(BARNACLES, barnacles() + 1);
        }
        song(level);
        if (tickCount % 600 == 0 && random.nextInt(4) == 0) {
            playSound(WFSounds.ENTITY_LEVIATHAN_AMBIENT.get(), 12.0f, 0.8f + random.nextFloat() * 0.2f);
        }
        if (tickCount % 2400 == 0) {
            seedBelow(level);
        }
    }

    /** All movement is the steady glide in {@link #aiStep}; no physics-driven travel. */
    @Override
    public void travel(Vec3 input) {
    }

    /** Follows the nearest Sky Current within 300 blocks; otherwise wanders in long straight legs at its altitude. */
    private Vec3 nextWaypoint(ServerLevel level) {
        List<SkyCurrents.Current> currents = SkyCurrents.currentsNear(level.getSeed(), getX(), getZ());
        SkyCurrents.Hit hit = SkyCurrents.nearest(currents, position());
        if (hit != null && hit.distance() < 300) {
            Vec3 heading = Vec3.directionFromRotation(0, getYRot());
            Vec3 dir = !hit.current().oneWay() && heading.dot(hit.dir()) < 0 ? hit.dir().scale(-1) : hit.dir();
            return hit.point().add(dir.scale(120)).add(0, -14, 0);
        }
        double a = Math.toRadians(getYRot()) + (random.nextDouble() - 0.5) * 0.8;
        return position().add(-Math.sin(a) * 200, Mth.clamp(195 - getY(), -10, 10), Math.cos(a) * 200);
    }

    /** A long falling note two minutes before every tide rise. */
    private void song(ServerLevel level) {
        if (Tides.appliesTo(level) && Tides.phase(level) == TideCycle.Phase.LOW
            && Tides.cycle(level).ticksUntilNextPhase(Tides.effectiveTime(level)) == SONG_LEAD) {
            level.playSound(null, blockPosition(), WFSounds.ENTITY_LEVIATHAN_SONG.get(), SoundSource.AMBIENT, 20.0f, 1.0f);
        }
    }

    /** Leviathans carry Grazer-trodden loam: passing over an island, now and then a little Pale Loam takes root below. */
    private void seedBelow(ServerLevel level) {
        int x = getBlockX() + random.nextInt(9) - 4, z = getBlockZ() + random.nextInt(9) - 4;
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos top = new BlockPos(x, y, z);
        if (y > level.getMinY() + 8 && getY() - y < 140 && level.getBlockState(top).is(net.minecraft.world.level.block.Blocks.END_STONE)
            && level.getBlockState(top.above()).isAir()) {
            level.setBlockAndUpdate(top, WFBlocks.PALE_LOAM.get().defaultBlockState());
            dev.worldsfall.block.PaleLoamBlock.spreadFrom(level, top, random);
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.SHEARS)) {
            if (barnacles() <= 0) {
                return InteractionResult.FAIL;
            }
            if (level() instanceof ServerLevel level) {
                entityData.set(BARNACLES, barnacles() - 1);
                spawnAtLocation(level, new ItemStack(WFItems.LEVIATHAN_BARNACLE.get(), 1 + random.nextInt(2)), 1.0f);
                level.playSound(null, player.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0f, 0.7f);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                if (player instanceof ServerPlayer sp) {
                    Progress.award(sp, "barnacle_scraper");
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** Only something that ignores invulnerability (the void, /kill) harms a Leviathan. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("barnacles", barnacles());
        out.putInt("regrow", regrow);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(BARNACLES, in.getIntOr("barnacles", MAX_BARNACLES));
        regrow = in.getIntOr("regrow", 0);
    }
}
