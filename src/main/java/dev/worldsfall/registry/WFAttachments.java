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
    /** What a placed End Crystal carries: "" (plain), "pact:<id>" or "stillness" (the Warden Cycle). */
    public static final Supplier<AttachmentType<String>> CRYSTAL_KIND = ATTACHMENTS.register("crystal_kind",
        () -> AttachmentType.builder(() -> "").serialize(com.mojang.serialization.Codec.STRING.fieldOf("kind")).build());
    /** Dragon pose flags for the renderer: bit 0 perched upside down on the Warden's Gallery ceiling, bit 1 calm (Release). */
    public static final Supplier<AttachmentType<Integer>> DRAGON_POSE = ATTACHMENTS.register("dragon_pose",
        () -> AttachmentType.builder(() -> 0).sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    /** Items a pact Hoarder has stolen during a Warden fight; returned (plus a bonus) when it dies. */
    public static final Supplier<AttachmentType<List<net.minecraft.world.item.ItemStack>>> STOLEN = ATTACHMENTS.register("stolen",
        () -> AttachmentType.<List<net.minecraft.world.item.ItemStack>>builder(() -> List.of())
            .serialize(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items")).build());
    /** How many pillar glyphs a player has read in order (0–10). */
    public static final Supplier<AttachmentType<Integer>> PILLARS_READ = ATTACHMENTS.register("pillars_read",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("read")).sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
            .copyOnDeath().build());
    /** Wardensong lines a player has heard (bit per line, §1 of the Warden Cycle). */
    public static final Supplier<AttachmentType<Integer>> WARDENSONG = ATTACHMENTS.register("wardensong",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("lines")).copyOnDeath().build());
    /** Which shoulders hold a Hatchling Wyrm (bit 0 left, bit 1 right), synced so every client can draw it (§10.3). */
    public static final Supplier<AttachmentType<Integer>> WYRM_SHOULDERS = ATTACHMENTS.register("wyrm_shoulders",
        () -> AttachmentType.builder(() -> 0).sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
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
    /** Where this player's most recent Void Capsule was sealed (expansion 2.0: the Undertow Pearl). */
    public static final Supplier<AttachmentType<Optional<net.minecraft.core.GlobalPos>>> LAST_CAPSULE = ATTACHMENTS.register("last_capsule",
        () -> AttachmentType.<Optional<net.minecraft.core.GlobalPos>>builder(Optional::empty)
            .serialize(net.minecraft.core.GlobalPos.CODEC.optionalFieldOf("pos")).copyOnDeath().build());
    /** Archive Codices this player has read (1–6). */
    public static final Supplier<AttachmentType<Set<Integer>>> CODICES_READ = ATTACHMENTS.register("codices_read",
        () -> AttachmentType.<Set<Integer>>builder(() -> new HashSet<>())
            .serialize(Codec.INT.listOf().<Set<Integer>>xmap(HashSet::new, List::copyOf).fieldOf("codices")).copyOnDeath().build());
    /** Standing with Hoarders, −100..100 (expansion 1.5). */
    public static final Supplier<AttachmentType<Integer>> HOARD_STANDING = ATTACHMENTS.register("hoard_standing",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("standing")).copyOnDeath().build());
    public static final Supplier<AttachmentType<Set<String>>> HINTS_SHOWN = ATTACHMENTS.register("hints_shown",
        () -> AttachmentType.<Set<String>>builder(() -> new HashSet<>())
            .serialize(Codec.STRING.listOf().<Set<String>>xmap(HashSet::new, List::copyOf).fieldOf("hints")).copyOnDeath().build());

    private WFAttachments() {}
}
