package dev.worldsfall.entity;

import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A block flying along a curved, eased arc with a slowing spin (Reassembly §11.1, the Sundering §5.4, the
 * Unmade's island reforming §13.7). Position is a pure function of age, so clients render it smoothly with
 * no per-tick packets. On arrival it places its block unless the spot is occupied (never overwrites).
 */
public class FlyingBlock extends Entity {
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(FlyingBlock.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<org.joml.Vector3fc> FROM = SynchedEntityData.defineId(FlyingBlock.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3fc> TO = SynchedEntityData.defineId(FlyingBlock.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(FlyingBlock.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ARC = SynchedEntityData.defineId(FlyingBlock.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(FlyingBlock.class, EntityDataSerializers.BYTE);
    /** Place the block on arrival. */
    public static final byte MODE_PLACE = 0;
    /** Pure visual: fall away and vanish (Sundering debris). */
    public static final byte MODE_VANISH = 1;

    private int age;

    public FlyingBlock(EntityType<? extends FlyingBlock> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static FlyingBlock create(ServerLevel level, BlockState state, Vec3 from, BlockPos to, int duration, float arc, byte mode) {
        FlyingBlock fb = new FlyingBlock(WFEntities.FLYING_BLOCK.get(), level);
        fb.entityData.set(STATE, state);
        fb.entityData.set(FROM, from.toVector3f());
        fb.entityData.set(TO, new Vector3f(to.getX() + 0.5f, to.getY(), to.getZ() + 0.5f));
        fb.entityData.set(DURATION, Math.max(2, duration));
        fb.entityData.set(ARC, arc);
        fb.entityData.set(MODE, mode);
        fb.setPos(from);
        return fb;
    }

    public static FlyingBlock createFall(ServerLevel level, BlockState state, BlockPos from, double drop, int duration) {
        Vec3 start = Vec3.atBottomCenterOf(from);
        FlyingBlock fb = create(level, state, start, BlockPos.containing(start.x, start.y - drop, start.z), duration, 0, MODE_VANISH);
        return fb;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, Blocks.PURPUR_BLOCK.defaultBlockState());
        builder.define(FROM, new Vector3f());
        builder.define(TO, new Vector3f());
        builder.define(DURATION, 40);
        builder.define(ARC, 3.0f);
        builder.define(MODE, MODE_PLACE);
    }

    public BlockState blockState() {
        return entityData.get(STATE);
    }

    public int duration() {
        return entityData.get(DURATION);
    }

    public int flightAge() {
        return age;
    }

    public byte mode() {
        return entityData.get(MODE);
    }

    /** Normalized progress with ease-in-out (§18.5). */
    public static double ease(double t) {
        double c = Math.max(0, Math.min(1, t));
        return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
    }

    /** Position along the quadratic Bezier arc at a given (fractional) age. */
    public Vec3 positionAt(double ageTicks) {
        org.joml.Vector3fc a = entityData.get(FROM);
        org.joml.Vector3fc b = entityData.get(TO);
        double t = ease(ageTicks / duration());
        if (mode() == MODE_VANISH) {
            // accelerating fall straight down with a slight drift
            double fall = t * t;
            return new Vec3(a.x() + (b.x() - a.x()) * fall, a.y() + (b.y() - a.y()) * fall, a.z() + (b.z() - a.z()) * fall);
        }
        double cx = (a.x() + b.x()) / 2.0;
        double cy = Math.max(a.y(), b.y()) + entityData.get(ARC);
        double cz = (a.z() + b.z()) / 2.0;
        double u = 1 - t;
        return new Vec3(u * u * a.x() + 2 * u * t * cx + t * t * b.x(), u * u * a.y() + 2 * u * t * cy + t * t * b.y(),
            u * u * a.z() + 2 * u * t * cz + t * t * b.z());
    }

    /** Spin angle in degrees: slows to zero so the block lands aligned. */
    public float spinAt(double ageTicks) {
        double t = Math.min(1, ageTicks / duration());
        double remaining = 1 - ease(t);
        return (float) (remaining * 540.0 + (getId() % 7) * 10 * remaining);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 p = positionAt(age);
        setPos(p.x, p.y, p.z);
        Level level = level();
        if (level.isClientSide()) {
            if (mode() == MODE_PLACE && random.nextInt(2) == 0) {
                level.addParticle(WFParticles.SOCKET_SPARK.get(), p.x + random.nextGaussian() * 0.3, p.y + 0.5, p.z + random.nextGaussian() * 0.3, 0, 0, 0);
            }
            return;
        }
        if (age >= duration()) {
            land((ServerLevel) level);
        }
    }

    private void land(ServerLevel level) {
        org.joml.Vector3fc to = entityData.get(TO);
        BlockPos pos = BlockPos.containing(to.x(), to.y() + 0.01, to.z());
        BlockState state = blockState();
        if (mode() == MODE_PLACE) {
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced()) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                level.playSound(null, pos, WFSounds.EVENT_REASSEMBLY_CLICK.get(), SoundSource.BLOCKS, 0.7f, 0.9f + random.nextFloat() * 0.25f);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            }
        } else {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.5, getZ(), 6, 0.3, 0.3, 0.3, 0.05);
        }
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        age = in.getIntOr("age", 0);
        in.read("state", BlockState.CODEC).ifPresent(s -> entityData.set(STATE, s));
        in.read("from", Vec3.CODEC).ifPresent(v -> entityData.set(FROM, v.toVector3f()));
        in.read("to", Vec3.CODEC).ifPresent(v -> entityData.set(TO, v.toVector3f()));
        entityData.set(DURATION, in.getIntOr("duration", 40));
        entityData.set(ARC, in.getFloatOr("arc", 3));
        entityData.set(MODE, in.getByteOr("mode", MODE_PLACE));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("age", age);
        out.store("state", BlockState.CODEC, blockState());
        out.store("from", Vec3.CODEC, new Vec3(entityData.get(FROM)));
        out.store("to", Vec3.CODEC, new Vec3(entityData.get(TO)));
        out.putInt("duration", duration());
        out.putFloat("arc", entityData.get(ARC));
        out.putByte("mode", mode());
    }
}
