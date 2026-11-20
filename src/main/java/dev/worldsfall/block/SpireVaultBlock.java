package dev.worldsfall.block;

import dev.worldsfall.block.entity.PerPlayerLootBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Spire Vault / Drift Cache / Sigil Pedestal: per-player reward blocks (§11). */
public class SpireVaultBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 15, 15);

    public SpireVaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PerPlayerLootBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)
            || !(level.getBlockEntity(pos) instanceof PerPlayerLootBlockEntity vault)) {
            return InteractionResult.SUCCESS;
        }
        if (vault.sealed()) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.vault.sealed"));
            server.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 1, 0.8f);
            return InteractionResult.CONSUME;
        }
        if (vault.hasClaimed(sp.getUUID())) {
            sp.sendOverlayMessage(Component.translatable("message.worldsfall.vault.claimed"));
            server.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.CONSUME;
        }
        for (ItemStack stack : vault.claim(sp)) {
            ItemEntity item = new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
            item.setDeltaMovement(server.getRandom().nextGaussian() * 0.05, 0.25, server.getRandom().nextGaussian() * 0.05);
            item.setDefaultPickUpDelay();
            server.addFreshEntity(item);
        }
        dev.worldsfall.world.Progress.onVaultClaim(sp, state);
        server.playSound(null, pos, SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 1, 1);
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.05);
        return InteractionResult.CONSUME;
    }
}
