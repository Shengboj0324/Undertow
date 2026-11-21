package dev.worldsfall.warden;

import dev.worldsfall.registry.WFItems;
import dev.worldsfall.util.WFUtil;
import dev.worldsfall.world.Progress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Rewards by total Weight, given to every player present at the kill (like the Spire Vaults). Dragon Scales follow
 * Pillar 2: reach and control, never damage.
 */
public final class WardenRewards {
    private WardenRewards() {}

    /** Dragon Scales for a tier: 2–4 at tier 1, 4–8 from tier 2. */
    public static int scales(int tier, RandomSource r) {
        return switch (tier) {
            case 0 -> 0;
            case 1 -> 2 + r.nextInt(3);
            default -> 4 + r.nextInt(5);
        };
    }

    static void grant(ServerLevel end, WardenData data) {
        int weight = data.weight();
        int tier = Pact.tier(weight);
        if (tier == 0) {
            return;
        }
        for (ServerPlayer p : WardenFight.fighters(end)) {
            RandomSource r = p.getRandom();
            WFUtil.give(p, new ItemStack(WFItems.DRAGON_SCALE.get(), scales(tier, r)));
            WFUtil.give(p, new ItemStack(WFItems.WARDEN_BANNER_PATTERN.get()));
            if (tier >= 2) {
                WFUtil.give(p, new ItemStack(WFItems.DRAGON_EGG_FRAGMENT.get()));
            }
            if (tier >= 3) {
                WFUtil.give(p, new ItemStack(WFItems.WARDENBORN_SEAL.get()));
            }
            if (tier >= 4) {
                WFUtil.give(p, new ItemStack(WFItems.MUSIC_DISC_WARDEN.get()));
                Progress.award(p, "all_four_seals");
            }
            Progress.award(p, "warden_trial");
            if (data.has(Pact.DEPTHS)) {
                Progress.award(p, "the_long_way_down");
            }
            p.sendSystemMessage(Component.translatable("message.worldsfall.warden.reward", weight));
        }
    }
}
