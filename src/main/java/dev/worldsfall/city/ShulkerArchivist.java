package dev.worldsfall.city;

import dev.worldsfall.item.EchoTabletItem;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.warden.Pact;
import dev.worldsfall.world.Progress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Shulker Archivist (proposal "End Cities and End Ships"): a rare, peaceful Shulker in End City rooms with a pale, dusty
 * shell. It never attacks. Shown an Echo Tablet it opens, reads, and trades City Charts, Shulker Shells, Pact Glyphs or
 * the Echo Tablet you are missing. Every tablet it reads counts as read for you.
 */
public class ShulkerArchivist extends Shulker implements Merchant {
    private @Nullable Player tradingPlayer;
    private @Nullable MerchantOffers offers;

    public ShulkerArchivist(EntityType<? extends ShulkerArchivist> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f, 0.02f, true));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % 10 == 0) {
            int peek = tradingPlayer != null ? 100 : level().getNearestPlayer(this, 5) != null ? 30 : 0;
            if (entityData.get(DATA_PEEK_ID) != peek) {
                entityData.set(DATA_PEEK_ID, (byte) peek);
            }
        }
        if (tradingPlayer != null && !stillValid(tradingPlayer)) {
            setTradingPlayer(null);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(WFItems.ECHO_TABLET.get())) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.worldsfall.archivist.waits"));
            }
            return InteractionResult.SUCCESS;
        }
        if (tradingPlayer != null && tradingPlayer != player) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer sp) {
            Progress.readTablet(sp, EchoTabletItem.index(held));
            playSound(WFSounds.ENTITY_SHULKER_ARCHIVIST_READ.get(), 1.0f, 1.0f);
            offers = offersFor(sp);
            setTradingPlayer(sp);
            openTradingScreen(sp, getDisplayName(), 1);
            Progress.award(sp, "the_archivist");
        }
        return InteractionResult.SUCCESS;
    }

    /** The offers for one player: built when they open the screen, so the missing tablet and glyph are theirs. */
    public MerchantOffers offersFor(ServerPlayer player) {
        MerchantOffers out = new MerchantOffers();
        ItemCost tablet = new ItemCost(WFItems.ECHO_TABLET.get());
        out.add(new MerchantOffer(tablet, new ItemStack(WFItems.CITY_CHART.get()), 3, 0, 0.0f));
        out.add(new MerchantOffer(tablet, Optional.of(new ItemCost(WFItems.ECHO_DUST.get(), 4)), new ItemStack(Items.SHULKER_SHELL), 4, 0, 0.0f));
        Pact[] craftable = {Pact.TIDE, Pact.SHARDS, Pact.ECHOES, Pact.HOARD, Pact.MEMORY, Pact.STILLNESS, Pact.LONE};
        Pact glyph = craftable[Math.floorMod(player.getUUID().hashCode() + (int) (level().getOverworldClockTime() / 24000L), craftable.length)];
        out.add(new MerchantOffer(tablet, Optional.of(new ItemCost(WFItems.MIRROR_SHARD_ITEM.get(), 2)), new ItemStack(WFItems.glyph(glyph)), 1, 0, 0.0f));
        int missing = missingTablet(player.getData(WFAttachments.TABLETS_READ));
        if (missing > 0) {
            out.add(new MerchantOffer(tablet, Optional.of(new ItemCost(WFItems.ECHO_DUST.get(), 8)), EchoTabletItem.of(missing), 1, 0, 0.0f));
        }
        return out;
    }

    /** The lowest tablet number not read yet, or 0 when all twelve are. */
    public static int missingTablet(Set<Integer> read) {
        for (int i = 1; i <= EchoTabletItem.FRAGMENTS; i++) {
            if (!read.contains(i)) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ peaceful

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (tradingPlayer != null) {
            setTradingPlayer(null);
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    // ------------------------------------------------------------------ Merchant

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        tradingPlayer = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) {
            offers = new MerchantOffers();
        }
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        playSound(WFSounds.ENTITY_SHULKER_ARCHIVIST_TRADE.get(), 1.0f, 1.0f);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return WFSounds.ENTITY_SHULKER_ARCHIVIST_TRADE.get();
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide();
    }

    @Override
    public boolean stillValid(Player player) {
        return tradingPlayer == player && isAlive() && player.distanceToSqr(this) < 64;
    }
}
