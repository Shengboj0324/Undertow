package dev.worldsfall.life;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Casting the Tide (proposal "Living systems"). At High Tide a bobber that falls to the tide plane floats there and bites
 * like water; fishing in Stillwater is twice as slow but silent and safe. Each has its own loot table. The float is applied
 * on both sides so the bobber rests steadily; bites and catches are server-side.
 */
public final class TideFishing {
    public static final ResourceKey<LootTable> TIDE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "gameplay/tide_fishing"));
    public static final ResourceKey<LootTable> STILL_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "gameplay/still_angling"));
    public static final int BITE_TICKS = 30;

    public enum Surface { TIDE, STILL }

    /** Server-side state of one floating bobber. */
    static final class Angle {
        final Surface surface;
        int wait;
        int bite;

        Angle(Surface surface, int wait) {
            this.surface = surface;
            this.wait = wait;
        }
    }

    private static final Map<FishingHook, Angle> ANGLES = new WeakHashMap<>();

    private TideFishing() {}

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.tideFishing, true);
    }

    /** The surface a bobber rests on and its height, or null if it is on neither. */
    public static @Nullable Surface surfaceOf(Level level, FishingHook hook) {
        if (level.dimension() != Level.END || hook.getHookedIn() != null || !enabled()) {
            return null;
        }
        BlockPos pos = hook.blockPosition();
        FluidState fluid = level.getFluidState(pos);
        if (fluid.getType().isSame(WFFluids.STILLWATER.get())) {
            return Surface.STILL;
        }
        if (Tides.appliesTo(level) && Tides.phase(level) == TideCycle.Phase.HIGH && !hook.isInWater() && hook.getY() <= Tides.height(level) + 0.05) {
            return Surface.TIDE;
        }
        return null;
    }

    static double surfaceY(Level level, FishingHook hook, Surface s) {
        if (s == Surface.TIDE) {
            return Tides.height(level) - 0.12;
        }
        BlockPos pos = hook.blockPosition();
        return pos.getY() + level.getFluidState(pos).getHeight(level, pos) - 0.12;
    }

    /** Wait before a bite: tide 5–25 s, Stillwater twice that; Lure shortens it like vanilla. */
    static int waitFor(Surface s, int lure, RandomSource r) {
        int base = 100 + r.nextInt(400) - lure * 100;
        return Math.max(40, s == Surface.STILL ? base * 2 : base);
    }

    /** Every tick, on both sides: keep a bobber that reached a fishing surface floating on it. */
    public static void tickHook(FishingHook hook) {
        Level level = hook.level();
        Surface s = surfaceOf(level, hook);
        if (s == null) {
            if (!level.isClientSide()) {
                ANGLES.remove(hook);
            }
            return;
        }
        double y = surfaceY(level, hook, s);
        Vec3 v = hook.getDeltaMovement();
        hook.setDeltaMovement(v.x * 0.85, 0, v.z * 0.85);
        hook.setPos(hook.getX(), y, hook.getZ());
        if (!(level instanceof ServerLevel server) || !(hook.getPlayerOwner() instanceof ServerPlayer owner)) {
            return;
        }
        Angle a = ANGLES.get(hook);
        if (a == null || a.surface != s) {
            ItemStack rod = rodOf(owner);
            int lure = rod == null ? 0 : (int) (EnchantmentHelper.getFishingTimeReduction(server, rod, owner) / 5.0f);
            ANGLES.put(hook, new Angle(s, waitFor(s, lure, server.getRandom())));
            return;
        }
        if (a.bite > 0) {
            if (--a.bite == 0) {
                a.wait = waitFor(s, 0, server.getRandom());
            }
            return;
        }
        if (--a.wait <= 0) {
            a.bite = BITE_TICKS;
            server.sendParticles(s == Surface.TIDE ? WFParticles.TIDE_CREST.get() : WFParticles.STILL_RING.get(), hook.getX(), y + 0.1, hook.getZ(),
                12, 0.25, 0.02, 0.25, 0.02);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, hook.getX(), y + 0.15, hook.getZ(), 6, 0.1, 0.0, 0.1, 0.05);
            if (s == Surface.TIDE) {
                server.playSound(null, hook.blockPosition(), WFSounds.EVENT_TIDE_FISHING_BITE.get(), SoundSource.NEUTRAL, 0.8f, 0.9f + server.getRandom().nextFloat() * 0.2f);
            }
        } else if (a.wait < 40 && a.wait % 8 == 0) {
            server.sendParticles(WFParticles.UP_MOTE.get(), hook.getX(), y + 0.1, hook.getZ(), 2, 0.3, 0.0, 0.3, 0.0);
        }
    }

    public static boolean biting(FishingHook hook) {
        Angle a = ANGLES.get(hook);
        return a != null && a.bite > 0;
    }

    private static @Nullable ItemStack rodOf(ServerPlayer p) {
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        return main.getItem() instanceof net.minecraft.world.item.FishingRodItem ? main : off.getItem() instanceof net.minecraft.world.item.FishingRodItem ? off : null;
    }

    /**
     * Reeling in while a bobber on the tide or Stillwater is biting: roll the surface's table, pull the catch to the player.
     * Returns true when the catch was handled (the caller cancels the vanilla retrieve).
     */
    public static boolean reel(ServerPlayer player, ItemStack rod, EquipmentSlot slot) {
        FishingHook hook = player.fishing;
        if (hook == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Angle a = ANGLES.get(hook);
        if (a == null || a.bite <= 0) {
            return false;
        }
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, hook.position())
            .withParameter(LootContextParams.TOOL, rod).withParameter(LootContextParams.THIS_ENTITY, hook)
            .withLuck(EnchantmentHelper.getFishingLuckBonus(level, rod, player) + player.getLuck()).create(LootContextParamSets.FISHING);
        List<ItemStack> items = level.getServer().reloadableRegistries().getLootTable(a.surface == Surface.TIDE ? TIDE_LOOT : STILL_LOOT).getRandomItems(params);
        if (a.surface == Surface.TIDE && level.getRandom().nextFloat() < LostThings.FISHING_CHANCE) {
            ItemStack lost = LostThings.take(level.getServer());
            if (!lost.isEmpty()) {
                items = new java.util.ArrayList<>(items);
                items.add(lost);
            }
        }
        for (ItemStack stack : items) {
            ItemEntity e = new ItemEntity(level, hook.getX(), hook.getY() + 0.2, hook.getZ(), stack);
            double xa = player.getX() - hook.getX(), ya = player.getY() - hook.getY(), za = player.getZ() - hook.getZ();
            e.setDeltaMovement(xa * 0.1, ya * 0.1 + Math.sqrt(Math.sqrt(xa * xa + ya * ya + za * za)) * 0.08, za * 0.1);
            level.addFreshEntity(e);
            level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5, player.getZ() + 0.5, level.getRandom().nextInt(6) + 1));
            if (stack.is(ItemTags.FISHES)) {
                player.awardStat(Stats.FISH_CAUGHT, 1);
            }
        }
        Progress.award(player, a.surface == Surface.TIDE ? "tide_angler" : "still_angler");
        ANGLES.remove(hook);
        hook.discard();
        rod.hurtAndBreak(1, player, slot);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0f,
            0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        player.awardStat(Stats.ITEM_USED.get(rod.getItem()));
        return true;
    }
}
