package dev.worldsfall.block.entity;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.registry.WFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player reward container (Spire Vaults, Driftwreck caches, Sigil pedestals) modelled on vanilla trial
 * vaults: each player may claim once (§11.1, §11.3, §12.2, D6). A sealed vault cannot be claimed until a
 * Reassembly opens it.
 */
public class PerPlayerLootBlockEntity extends WFBlockEntity {
    private ResourceKey<LootTable> lootTable;
    private final Set<UUID> claimed = new HashSet<>();
    private boolean sealed;

    public PerPlayerLootBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.PER_PLAYER_LOOT.get(), pos, state);
        this.lootTable = defaultTable(state);
    }

    private static ResourceKey<LootTable> defaultTable(BlockState state) {
        String path = state.is(dev.worldsfall.registry.WFBlocks.DRIFT_CACHE.get()) ? "vault/driftwreck"
            : state.is(dev.worldsfall.registry.WFBlocks.SIGIL_PEDESTAL.get()) ? "vault/spire_sigil" : "vault/spire_vault";
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, path));
    }

    public void configure(ResourceKey<LootTable> table, boolean sealed) {
        this.lootTable = table;
        this.sealed = sealed;
        sync();
    }

    public ResourceKey<LootTable> lootTable() {
        return lootTable;
    }

    public boolean sealed() {
        return sealed;
    }

    public void unseal() {
        sealed = false;
        sync();
    }

    public boolean hasClaimed(UUID player) {
        return claimed.contains(player);
    }

    public int claimCount() {
        return claimed.size();
    }

    /** Claims the reward for a player. Returns the dropped stacks (empty if not allowed). */
    public List<ItemStack> claim(ServerPlayer player) {
        if (sealed || claimed.contains(player.getUUID()) || !(level instanceof ServerLevel server)) {
            return List.of();
        }
        claimed.add(player.getUUID());
        LootTable table = server.getServer().reloadableRegistries().getLootTable(lootTable);
        LootParams params = new LootParams.Builder(server)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .withLuck(player.getLuck())
            .create(LootContextParamSets.CHEST);
        List<ItemStack> items = table.getRandomItems(params);
        sync();
        return items;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("loot_table", lootTable.identifier().toString());
        out.putBoolean("sealed", sealed);
        out.store("claimed", UUIDUtil.CODEC_SET, Set.copyOf(claimed));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.getString("loot_table").map(Identifier::tryParse)
            .ifPresent(id -> lootTable = ResourceKey.create(Registries.LOOT_TABLE, id));
        sealed = in.getBooleanOr("sealed", false);
        claimed.clear();
        in.read("claimed", UUIDUtil.CODEC_SET).ifPresent(claimed::addAll);
    }
}
