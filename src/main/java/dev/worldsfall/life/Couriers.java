package dev.worldsfall.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.Hoarder;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hoarder Couriers (proposal "Hoarder Couriers and reputation"): a Courier Token set on two Courier Caches up to 2,000
 * blocks apart makes a contract. Every five minutes a Hoarder appears at the first cache, takes one stack and carries it
 * to the second. Stealing from a Hoard cancels the thief's contracts.
 */
public final class Couriers extends SavedData {
    public static final int INTERVAL = 6000, MAX_DISTANCE = 2000, VISIT_TICKS = 40;
    public static final String TAG = "worldsfall_courier";

    public record Contract(UUID owner, BlockPos from, BlockPos to, long next, ItemStack carrying) {
        public static final Codec<Contract> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Contract::owner), BlockPos.CODEC.fieldOf("from").forGetter(Contract::from),
            BlockPos.CODEC.fieldOf("to").forGetter(Contract::to), Codec.LONG.fieldOf("next").forGetter(Contract::next),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("carrying", ItemStack.EMPTY).forGetter(Contract::carrying)
        ).apply(i, Contract::new));
    }

    public static final Codec<Couriers> CODEC = Contract.CODEC.listOf().xmap(Couriers::new, c -> List.copyOf(c.contracts));
    public static final SavedDataType<Couriers> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "couriers"),
        Couriers::new, CODEC);

    private final List<Contract> contracts = new ArrayList<>();
    /** Courier Hoarders currently visible, with the tick they leave. Not saved: leftovers are removed on load. */
    private static final Map<UUID, Long> VISITORS = new HashMap<>();

    public Couriers() {}

    private Couriers(List<Contract> list) {
        contracts.addAll(list);
    }

    public static Couriers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Contract> contracts() {
        return List.copyOf(contracts);
    }

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.couriers, true);
    }

    public void add(Contract c) {
        contracts.removeIf(o -> o.from.equals(c.from));
        contracts.add(c);
        setDirty();
    }

    /** Theft from a Hoard cancels every contract the thief holds. */
    public static void cancelFor(ServerLevel level, ServerPlayer thief) {
        Couriers data = get(level.getServer());
        if (data.contracts.removeIf(c -> c.owner.equals(thief.getUUID()))) {
            data.setDirty();
            thief.sendSystemMessage(Component.translatable("message.worldsfall.courier.cancelled"));
        }
    }

    // ------------------------------------------------------------------ ticking (End level tick)

    public static void tick(ServerLevel end) {
        long now = end.getServer().overworld().getGameTime();
        if (!VISITORS.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = VISITORS.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (now >= e.getValue()) {
                    var ent = end.getEntity(e.getKey());
                    if (ent != null) {
                        end.sendParticles(ParticleTypes.PORTAL, ent.getX(), ent.getY() + 1.2, ent.getZ(), 40, 0.3, 0.8, 0.3, 0.3);
                        ent.discard();
                    }
                    it.remove();
                }
            }
        }
        if (now % 20 != 0 || !enabled()) {
            return;
        }
        Couriers data = get(end.getServer());
        for (int i = 0; i < data.contracts.size(); i++) {
            Contract c = data.contracts.get(i);
            if (now < c.next) {
                continue;
            }
            Contract after = run(end, c, now);
            if (after == null) {
                data.contracts.remove(i--);
            } else {
                data.contracts.set(i, after);
            }
            data.setDirty();
        }
    }

    /** One delivery. Returns the updated contract, or null when one of its caches is gone. */
    public static Contract run(ServerLevel end, Contract c, long now) {
        end.getChunk(c.from);
        end.getChunk(c.to);
        if (!(end.getBlockEntity(c.from) instanceof CourierCacheBlockEntity from) || !(end.getBlockEntity(c.to) instanceof CourierCacheBlockEntity to)) {
            return null;
        }
        ItemStack load = c.carrying.isEmpty() ? from.takeFirst() : c.carrying;
        if (load.isEmpty()) {
            return new Contract(c.owner, c.from, c.to, now + INTERVAL, ItemStack.EMPTY);
        }
        visit(end, c.from);
        visit(end, c.to);
        ItemStack rest = HopperBlockEntity.addItem(null, to, load, null);
        to.setChanged();
        end.playSound(null, c.to, WFSounds.EVENT_COURIER_DELIVER.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        end.playSound(null, c.from, WFSounds.EVENT_COURIER_DELIVER.get(), SoundSource.BLOCKS, 0.8f, 0.8f);
        return new Contract(c.owner, c.from, c.to, now + INTERVAL, rest);
    }

    /** A Hoarder briefly appears beside a cache, then blinks away. */
    private static void visit(ServerLevel end, BlockPos cache) {
        if (!end.isLoaded(cache)) {
            return;
        }
        Hoarder h = WFEntities.HOARDER.get().create(end, EntitySpawnReason.EVENT);
        if (h == null) {
            return;
        }
        h.snapTo(cache.getX() + 1.5, cache.getY(), cache.getZ() + 0.5, end.getRandom().nextFloat() * 360, 0);
        h.setNoAi(true);
        h.setSilent(true);
        h.entityTags().add(TAG);
        end.addFreshEntity(h);
        end.sendParticles(ParticleTypes.REVERSE_PORTAL, h.getX(), h.getY() + 1.2, h.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        VISITORS.put(h.getUUID(), end.getServer().overworld().getGameTime() + VISIT_TICKS);
    }
}
