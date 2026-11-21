package dev.worldsfall.life;

import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.gravity.GravityZones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gravity Plate (proposal "Building and redstone", post-boss): stepping on it opens a small Light Zone (radius 3) for ten
 * seconds: a low-gravity jump pad for builders. Works in the End, where gravity zones apply.
 */
public class GravityPlateBlock extends Block {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final int RADIUS = 3, TICKS = 200;
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 1, 15);

    public GravityPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (state.getValue(POWERED) || !(level instanceof ServerLevel server) || !(entity instanceof LivingEntity)) {
            return;
        }
        GravityZones.addLight(server, Vec3.atBottomCenterOf(pos).add(0, 1.5, 0), RADIUS, TICKS);
        server.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        server.scheduleTick(pos, this, TICKS);
        server.playSound(null, pos, WFSounds.BLOCK_GRAVITY_PLATE_LIFT.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        server.sendParticles(WFParticles.UP_MOTE.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 1.5, 0.3, 1.5, 0.02);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED) && random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * RADIUS;
            level.addParticle(WFParticles.UP_MOTE.get(), pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.2, pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.05, 0);
        }
    }
}
