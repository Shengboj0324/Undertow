package dev.worldsfall.warden;

import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.fracture.FractureData;
import dev.worldsfall.world.tide.TideData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A Pact Crystal or a Stillness Crystal: placed exactly like a vanilla End Crystal (on obsidian or bedrock), but the placed
 * crystal remembers what it carries so the next dragon respawn can read it. A Stillness Crystal can only be placed once
 * The Unmade is defeated and the Heart of Stillness sits in the Still Throne; the first one shows the "this ends the cycle"
 * warning toast.
 */
public class WardenCrystalItem extends Item {
    private final @Nullable Pact pact;

    public WardenCrystalItem(Properties properties, @Nullable Pact pact) {
        super(properties);
        this.pact = pact;
    }

    public @Nullable Pact pact() {
        return pact;
    }

    public String kind() {
        return pact == null ? WardenFight.KIND_STILLNESS : WardenFight.KIND_PACT + pact.id();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.OBSIDIAN) && !level.getBlockState(pos).is(Blocks.BEDROCK)) {
            return InteractionResult.FAIL;
        }
        BlockPos above = pos.above();
        if (!level.isEmptyBlock(above)) {
            return InteractionResult.FAIL;
        }
        List<Entity> entities = level.getEntities(null, new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0,
            above.getZ() + 1.0));
        if (!entities.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            WardenData data = WardenData.get(server.getServer());
            if (data.released()) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.translatable("message.worldsfall.warden.released"));
                }
                return InteractionResult.FAIL;
            }
            if (pact == null) {
                boolean ready = FractureData.get(server.getServer()).defeated() && TideData.get(server.getServer()).state().calmed();
                if (!ready) {
                    if (context.getPlayer() != null) {
                        context.getPlayer().sendOverlayMessage(Component.translatable("message.worldsfall.warden.not_ready"));
                    }
                    return InteractionResult.FAIL;
                }
                if (context.getPlayer() instanceof ServerPlayer sp && !Progress.has(sp, "cycle_warning")) {
                    // the one-time warning toast: the Release is permanent
                    Progress.award(sp, "cycle_warning");
                }
            }
            EndCrystal crystal = new EndCrystal(level, above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
            crystal.setShowBottom(false);
            crystal.setData(WFAttachments.CRYSTAL_KIND, kind());
            level.addFreshEntity(crystal);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, above);
            WardenFight.readRespawnCrystals(server);
            EnderDragonFight fight = server.getDragonFight();
            if (fight != null) {
                fight.tryRespawn();
            }
        }
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
