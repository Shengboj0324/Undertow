package dev.worldsfall.registry;

import com.mojang.serialization.Codec;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.world.gravity.GravityWells;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Data attachments (§20.5): gravity wells per chunk, player-placed tracking near the origin, per-player data. */
public final class WFAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Worldsfall.MODID);

    public static final Supplier<AttachmentType<List<GravityWells.Well>>> GRAVITY_WELLS = ATTACHMENTS.register("gravity_wells",
        () -> AttachmentType.<List<GravityWells.Well>>builder(() -> List.of()).serialize(GravityWells.Well.CODEC.listOf().fieldOf("wells"))
            .sync(net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(GravityWells.Well.CODEC.listOf()))
            .build());
    /** Player-placed block positions within 250 blocks of the End origin (Sundering safety, §5.4, §20.5). */
    public static final Supplier<AttachmentType<Set<Long>>> PLAYER_PLACED = ATTACHMENTS.register("player_placed",
        () -> AttachmentType.<Set<Long>>builder(() -> new HashSet<>())
            .serialize(Codec.LONG.listOf().<Set<Long>>xmap(HashSet::new, List::copyOf).fieldOf("positions")).build());
    public static final Supplier<AttachmentType<Set<BlockPos>>> USED_GATEWAYS = ATTACHMENTS.register("used_gateways",
        () -> AttachmentType.<Set<BlockPos>>builder(() -> new HashSet<>())
            .serialize(BlockPos.CODEC.listOf().<Set<BlockPos>>xmap(HashSet::new, List::copyOf).fieldOf("gateways")).copyOnDeath().build());
    public static final Supplier<AttachmentType<Optional<BlockState>>> LAST_PLACED = ATTACHMENTS.register("last_placed",
        () -> AttachmentType.<Optional<BlockState>>builder(Optional::empty)
            .serialize(BlockState.CODEC.optionalFieldOf("state")).copyOnDeath().build());
    public static final Supplier<AttachmentType<Set<Integer>>> TABLETS_READ = ATTACHMENTS.register("tablets_read",
        () -> AttachmentType.<Set<Integer>>builder(() -> new HashSet<>())
            .serialize(Codec.INT.listOf().<Set<Integer>>xmap(HashSet::new, List::copyOf).fieldOf("tablets")).copyOnDeath().build());
    public static final Supplier<AttachmentType<Set<String>>> HINTS_SHOWN = ATTACHMENTS.register("hints_shown",
        () -> AttachmentType.<Set<String>>builder(() -> new HashSet<>())
            .serialize(Codec.STRING.listOf().<Set<String>>xmap(HashSet::new, List::copyOf).fieldOf("hints")).copyOnDeath().build());

    private WFAttachments() {}
}
