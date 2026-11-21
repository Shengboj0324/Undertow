package dev.worldsfall.block.entity;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.entity.Hoarder;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Hoard Cache barter state (§4.4, §16.4). Players insert accepted Overworld blocks; every 32 "hoard value"
 * the Hoarders leave one barter result rolled from {@code worldsfall:barter/hoard}.
 */
public class HoardCacheBlockEntity extends WFBlockEntity {
    public static final int VALUE_PER_BARTER = 32;
    public static final int RECENT_BARTER_TICKS = 5 * 1200;
    public static final int MAX_PILE = 300;
    public static final ResourceKey<LootTable> BARTER_TABLE = ResourceKey.create(Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Worldsfall.MODID, "barter/hoard"));

    private int storedValue;
    private long lastBarterTime = Long.MIN_VALUE / 2;
    private boolean spireKeyGiven;
    private int pileSize;
    private int barters;

    public HoardCacheBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.HOARD_CACHE.get(), pos, state);
    }

    /** Hoard value of one item of this stack, or 0 if Hoarders refuse it (§16.4). */
    public static int valueOf(ItemStack stack) {
        if (stack.isEmpty() || stack.is(WFTags.Items.HOARD_REFUSES)) {
            return 0;
        }
        if (stack.is(WFTags.Items.HOARD_VALUE_10)) return 10;
        if (stack.is(WFTags.Items.HOARD_VALUE_6)) return 6;
        if (stack.is(WFTags.Items.HOARD_VALUE_4)) return 4;
        if (stack.is(WFTags.Items.HOARD_VALUE_2)) return 2;
        if (stack.is(WFTags.Items.HOARD_VALUE_1)) return 1;
        return 0;
    }

    public int storedValue() {
        return storedValue;
    }

    public int barters() {
        return barters;
    }

    public boolean recentlyBartered(long gameTime) {
        return gameTime - lastBarterTime < RECENT_BARTER_TICKS;
    }

    public boolean acceptsPile() {
        return pileSize < MAX_PILE;
    }

    public void addToPile(int count) {
        pileSize = Math.min(MAX_PILE, pileSize + count);
        setChanged();
    }

    /**
     * Inserts the stack (consuming as much as is accepted). Returns the barter results produced (possibly
     * empty when value is still below the threshold, or when Hoarders "shrug").
     */
    public InsertResult insert(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int per = valueOf(stack);
        if (per == 0) {
            return new InsertResult(false, List.of(), 0);
        }
        int count = stack.getCount();
        storedValue += per * count;
        stack.shrink(count);
        List<ItemStack> results = new ArrayList<>();
        int done = 0;
        while (storedValue >= VALUE_PER_BARTER) {
            storedValue -= VALUE_PER_BARTER;
            done++;
            results.addAll(roll(level, player));
        }
        if (done > 0) {
            lastBarterTime = level.getGameTime();
            barters += done;
            int standing = dev.worldsfall.life.HoardStanding.add(player, done * dev.worldsfall.life.HoardStanding.PER_BARTER);
            if (standing >= dev.worldsfall.life.HoardStanding.COURIER && level.getRandom().nextFloat() < 0.25f) {
                results.add(new ItemStack(WFItems.COURIER_TOKEN.get()));
            }
            notifyHoarders(level, player, !results.isEmpty());
        }
        setChanged();
        return new InsertResult(true, results, done);
    }

    private List<ItemStack> roll(ServerLevel level, ServerPlayer player) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BARTER_TABLE);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .withLuck(dev.worldsfall.life.HoardStanding.luck(player))
            .create(LootContextParamSets.CHEST);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : table.getRandomItems(params)) {
            if (s.is(WFItems.SPIRE_KEY.get())) {
                if (spireKeyGiven) {
                    out.add(new ItemStack(Items.ENDER_PEARL, 2));
                    continue;
                }
                spireKeyGiven = true;
            }
            out.add(s);
        }
        return out;
    }

    private void notifyHoarders(ServerLevel level, ServerPlayer player, boolean accepted) {
        for (Hoarder h : level.getEntitiesOfClass(Hoarder.class, new AABB(worldPosition).inflate(24))) {
            h.onBarter(worldPosition, player, accepted);
        }
        level.playSound(null, worldPosition, accepted ? WFSounds.ENTITY_HOARDER_BARTER_ACCEPT.get() : WFSounds.ENTITY_HOARDER_BARTER_REFUSE.get(),
            SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    /** Angers every Hoarder of this Hoard for 2 minutes (§8.5.1). Anger is per Hoard, not per player (§17.5). */
    public void anger(ServerLevel level, ServerPlayer culprit) {
        dev.worldsfall.life.HoardStanding.add(culprit, dev.worldsfall.life.HoardStanding.THEFT);
        dev.worldsfall.life.Couriers.cancelFor(level, culprit);
        List<Hoarder> hoarders = level.getEntitiesOfClass(Hoarder.class, new AABB(worldPosition).inflate(24));
        for (Hoarder h : hoarders) {
            h.angerAt(culprit, 2 * 1200);
        }
        if (!hoarders.isEmpty()) {
            level.playSound(null, worldPosition, WFSounds.ENTITY_HOARDER_ANGER.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
            dev.worldsfall.world.Progress.award(culprit, "light_fingers");
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("value", storedValue);
        out.putLong("last_barter", lastBarterTime);
        out.putBoolean("key_given", spireKeyGiven);
        out.putInt("pile", pileSize);
        out.putInt("barters", barters);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        storedValue = in.getIntOr("value", 0);
        lastBarterTime = in.getLongOr("last_barter", Long.MIN_VALUE / 2);
        spireKeyGiven = in.getBooleanOr("key_given", false);
        pileSize = in.getIntOr("pile", 0);
        barters = in.getIntOr("barters", 0);
    }

    public record InsertResult(boolean accepted, List<ItemStack> results, int barters) {}
}
