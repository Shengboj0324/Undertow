package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Bell Winch (proposal "Getting in"): set on an island edge, it recalls your Void Bells from the End or the Undertow to
 * hang beside it, emptied of passengers.
 */
public class BellWinchBlock extends Block {
    public BellWinchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        int recalled = recall(server, pos, sp);
        server.playSound(null, pos, WFSounds.ENTITY_VOID_BELL_CREAK.get(), SoundSource.BLOCKS, 1.5f, 0.6f);
        sp.sendOverlayMessage(Component.translatable(recalled > 0 ? "message.worldsfall.winch.recalled" : "message.worldsfall.winch.none", recalled));
        return InteractionResult.SUCCESS;
    }

    /** Brings every loaded Void Bell the player owns (in the End and the Undertow) beside the winch. */
    public static int recall(ServerLevel level, BlockPos winch, ServerPlayer player) {
        List<VoidBell> bells = new ArrayList<>();
        for (ServerLevel l : List.of(level, level.getServer().getLevel(dev.worldsfall.registry.WFDimensions.THE_UNDERTOW))) {
            if (l == null) {
                continue;
            }
            for (Entity e : l.getAllEntities()) {
                if (e instanceof VoidBell b && player.getUUID().equals(b.owner())) {
                    bells.add(b);
                }
            }
        }
        int n = 0;
        for (VoidBell b : bells) {
            Vec3 at = Vec3.atBottomCenterOf(winch).add(1.5 + n * 3, 0, 0);
            for (Entity p : new ArrayList<>(b.getPassengers())) {
                p.stopRiding();
            }
            if (b.level() == level) {
                b.snapTo(at.x, at.y, at.z, b.getYRot(), 0);
                b.setDeltaMovement(Vec3.ZERO);
            } else {
                UndertowTravel.move(b, level, at);
            }
            n++;
        }
        return n;
    }
}
