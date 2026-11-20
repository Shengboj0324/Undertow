package dev.worldsfall.entity;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Void Capsule (§9.2): sealed belongings of a player who died in the void with a Void Anchor. Floats at the
 * tide plane + 2 (or Y 2), rising with the tide; owner (and team) only unless public; expires after the
 * configured loaded minutes. A tall teal beam marks it from far away.
 */
public class VoidCapsule extends Entity {
    private final List<ItemStack> items = new ArrayList<>();
    private int experience;
    private UUID owner = UUIDUtil.uuidFromIntArray(new int[] {0, 0, 0, 0});
    private int age;

    public VoidCapsule(EntityType<? extends VoidCapsule> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void fill(UUID owner, List<ItemStack> stacks, int xp) {
        this.owner = owner;
        this.items.clear();
        stacks.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).forEach(items::add);
        this.experience = xp;
    }

    public List<ItemStack> items() {
        return items;
    }

    public UUID owner() {
        return owner;
    }

    public int experience() {
        return experience;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public static double restingY(Level level) {
        return Tides.appliesTo(level) ? Tides.height(level) + 2 : level.getMinY() + 2;
    }

    @Override
    public void tick() {
        super.tick();
        double target = restingY(level()) + Math.sin(tickCount * 0.05) * 0.15;
        setPos(getX(), getY() + (target - getY()) * 0.1, getZ());
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        age++;
        int minutes = WFConfig.get(WFConfig.SERVER.capsuleMinutes, 60);
        if (minutes > 0 && age > minutes * 1200) {
            discard();
            return;
        }
        if (age % 80 == 0) {
            server.playSound(null, blockPosition(), WFSounds.ITEM_CAPSULE_HUM.get(), SoundSource.NEUTRAL, 0.6f, 1.0f);
        }
    }

    public boolean mayOpen(Player player) {
        if (WFConfig.get(WFConfig.SERVER.publicCapsules, false) || player.getUUID().equals(owner) || player.isCreative()) {
            return true;
        }
        Player ownerPlayer = level().getPlayerByUUID(owner);
        return ownerPlayer != null && ownerPlayer.isAlliedTo(player);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!mayOpen(player)) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.capsule.not_yours"));
            return InteractionResult.FAIL;
        }
        for (ItemStack stack : items) {
            dev.worldsfall.util.WFUtil.give(player, stack);
        }
        items.clear();
        player.giveExperiencePoints(experience);
        experience = 0;
        level().playSound(null, blockPosition(), WFSounds.ITEM_VOID_ANCHOR_TRIGGER.get(), SoundSource.PLAYERS, 1.0f, 1.3f);
        discard();
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        items.clear();
        in.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> list.stream().filter(s -> !s.isEmpty()).forEach(items::add));
        experience = in.getIntOr("xp", 0);
        Optional<UUID> o = in.read("owner", UUIDUtil.CODEC);
        o.ifPresent(u -> owner = u);
        age = in.getIntOr("age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        out.putInt("xp", experience);
        out.store("owner", UUIDUtil.CODEC, owner);
        out.putInt("age", age);
    }
}
