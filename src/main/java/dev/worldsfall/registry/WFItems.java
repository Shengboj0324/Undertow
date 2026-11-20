package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Every item from design doc §9 plus block items and spawn eggs. */
public final class WFItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Worldsfall.MODID);
    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    // ---- materials (§9.14)
    public static final DeferredItem<Item> SKATE_MEMBRANE = simple("skate_membrane");
    public static final DeferredItem<Item> GLOW_GLAND = simple("glow_gland");
    public static final DeferredItem<Item> GRAZER_FRILL = simple("grazer_frill", p -> p.trimMaterial(trim("frill")));
    public static final DeferredItem<Item> VOIDROOT_FIBER = simple("voidroot_fiber");
    public static final DeferredItem<Item> LUMEN_CRYSTAL_SHARD = simple("lumen_crystal_shard", p -> p.trimMaterial(trim("lumen")));
    public static final DeferredItem<Item> MIRROR_SHARD_ITEM = simple("mirror_shard_fragment", p -> p.trimMaterial(trim("still")));
    public static final DeferredItem<Item> ECHO_DUST = simple("echo_dust");
    public static final DeferredItem<Item> LURKER_LENS = simple("lurker_lens");
    public static final DeferredItem<Item> BOTTLE_OF_STILLWATER = simple("bottle_of_stillwater", p -> p.stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));

    // ---- food (§9.12)
    public static final DeferredItem<Item> CHORUS_POD = simple("chorus_pod", p -> p.food(food(3, 1.8f)));
    public static final DeferredItem<Item> ROASTED_CHORUS_POD = simple("roasted_chorus_pod", p -> p.food(food(6, 7.2f)));
    public static final DeferredItem<Item> GRAZER_FLANK = simple("grazer_flank", p -> p.food(food(3, 1.8f)));
    public static final DeferredItem<Item> COOKED_GRAZER_FLANK = simple("cooked_grazer_flank", p -> p.food(food(8, 12.8f)));
    public static final DeferredItem<BlockItem> GLOWBULB = reg("glowbulb", p -> new BlockItem(WFBlocks.GLOWBULB_BLOCK.get(), p),
        p -> p.food(food(2, 1.2f), Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160))).build()).useItemDescriptionPrefix());
    public static final DeferredItem<Item> DRIFT_STEW = simple("drift_stew", p -> p.stacksTo(1).food(food(10, 12.0f)).usingConvertsTo(Items.BOWL));
    public static final DeferredItem<StillwaterTeaItem> STILLWATER_TEA = reg("stillwater_tea", StillwaterTeaItem::new,
        p -> p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE).component(net.minecraft.core.component.DataComponents.CONSUMABLE,
            Consumables.defaultDrink().build()));

    // ---- tools & equipment (§9.2–9.9)
    public static final DeferredItem<VoidAnchorItem> VOID_ANCHOR = reg("void_anchor", p -> new VoidAnchorItem(p, false), p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<VoidAnchorItem> CRACKED_VOID_ANCHOR = reg("cracked_void_anchor", p -> new VoidAnchorItem(p, true),
        p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<RiftTetherItem> RIFT_TETHER = reg("rift_tether", RiftTetherItem::new,
        p -> p.durability(96).repairable(Items.ENDER_PEARL));
    public static final DeferredItem<GatewayCompassItem> GATEWAY_COMPASS = reg("gateway_compass", GatewayCompassItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ShulkerSatchelItem> SHULKER_SATCHEL = reg("shulker_satchel", ShulkerSatchelItem::new,
        p -> p.stacksTo(1).component(WFComponents.SATCHEL_SIZE.get(), 9));
    public static final DeferredItem<VoidLensItem> VOID_LENS = reg("void_lens", VoidLensItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SKATE_HARNESS = simple("skate_harness", p -> p.stacksTo(1));

    // ---- keys & progression (§9.10)
    public static final DeferredItem<Item> SPIRE_KEY = simple("spire_key", p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<EchoTabletItem> ECHO_TABLET = reg("echo_tablet", EchoTabletItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> SPIRE_SIGIL = simple("spire_sigil", p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> TIDE_SIGIL = simple("tide_sigil", p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> UNDER_SIGIL = simple("under_sigil", p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> RIFT_KEY = simple("rift_key", p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> HEART_OF_STILLNESS = simple("heart_of_stillness", p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final DeferredItem<Item> STILLNESS_FRAGMENT = simple("stillness_fragment", p -> p.rarity(Rarity.RARE).fireResistant());
    public static final DeferredItem<Item> DRAGON_EGG_FRAGMENT = simple("dragon_egg_fragment", p -> p.stacksTo(4).rarity(Rarity.EPIC));
    public static final DeferredItem<SlumberingEggItem> SLUMBERING_EGG = reg("slumbering_egg", SlumberingEggItem::new,
        p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final DeferredItem<Item> GALE_UPGRADE_TEMPLATE = simple("gale_upgrade_template", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> STILL_UPGRADE_TEMPLATE = simple("still_upgrade_template", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> HOVER_UPGRADE_TEMPLATE = simple("hover_upgrade_template", p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<Item> SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE = simple("spire_armor_trim_smithing_template", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> DRIFT_ARMOR_TRIM_SMITHING_TEMPLATE = simple("drift_armor_trim_smithing_template", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNDERSONG_ARMOR_TRIM_SMITHING_TEMPLATE = simple("undersong_armor_trim_smithing_template", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> MUSIC_DISC_UNMADE = simple("music_disc_unmade", p -> p.stacksTo(1).rarity(Rarity.RARE)
        .jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "unmade"))));
    public static final DeferredItem<BucketItem> STILLWATER_BUCKET = reg("stillwater_bucket", p -> new BucketItem(WFFluids.STILLWATER.get(), p),
        p -> p.stacksTo(1).craftRemainder(Items.BUCKET));
    public static final DeferredItem<BlockItem> BOTTLED_TIDEWISP = reg("bottled_tidewisp", p -> new BlockItem(WFBlocks.TIDEWISP_JAR.get(), p),
        p -> p.useItemDescriptionPrefix());

    private static final List<DeferredItem<Item>> SPAWN_EGGS = new ArrayList<>();
    public static final DeferredItem<Item> VOID_SKATE_SPAWN_EGG = egg("void_skate", WFEntities.VOID_SKATE);
    public static final DeferredItem<Item> CHORUS_GRAZER_SPAWN_EGG = egg("chorus_grazer", WFEntities.CHORUS_GRAZER);
    public static final DeferredItem<Item> UNDERSIDE_LURKER_SPAWN_EGG = egg("underside_lurker", WFEntities.UNDERSIDE_LURKER);
    public static final DeferredItem<Item> HOARDER_SPAWN_EGG = egg("hoarder", WFEntities.HOARDER);
    public static final DeferredItem<Item> SPIRE_WARDEN_SPAWN_EGG = egg("spire_warden", WFEntities.SPIRE_WARDEN);
    public static final DeferredItem<Item> ECHO_SPAWN_EGG = egg("echo", WFEntities.ECHO);
    public static final DeferredItem<Item> WILD_SHULKER_SPAWN_EGG = egg("wild_shulker", WFEntities.WILD_SHULKER);
    public static final DeferredItem<Item> TIDEWISP_SPAWN_EGG = egg("tidewisp", WFEntities.TIDEWISP);
    public static final DeferredItem<Item> SHARDLING_SPAWN_EGG = egg("shardling", WFEntities.SHARDLING);
    public static final DeferredItem<Item> WYRM_SPAWN_EGG = egg("wyrm", WFEntities.WYRM);

    private WFItems() {}

    private static DeferredItem<Item> egg(String name, net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,
        ? extends net.minecraft.world.entity.EntityType<?>> type) {
        DeferredItem<Item> item = ITEMS.registerItem(name + "_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, p -> p.spawnEgg(type.get()));
        SPAWN_EGGS.add(item);
        return item;
    }

    public static List<Item> spawnEggs() {
        return SPAWN_EGGS.stream().map(DeferredItem::get).map(Item.class::cast).toList();
    }

    static ResourceKey<TrimMaterial> trim(String name) {
        return ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.fromNamespaceAndPath(Worldsfall.MODID, name));
    }

    private static FoodProperties food(int nutrition, float saturation) {
        // FoodProperties takes the saturation *modifier*; vanilla saturation = nutrition * modifier * 2.
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation / (nutrition * 2.0f)).build();
    }

    private static DeferredItem<Item> simple(String name) {
        return simple(name, UnaryOperator.identity());
    }

    private static DeferredItem<Item> simple(String name, UnaryOperator<Item.Properties> props) {
        return reg(name, Item::new, props);
    }

    private static <I extends Item> DeferredItem<I> reg(String name, Function<Item.Properties, I> factory, UnaryOperator<Item.Properties> props) {
        DeferredItem<I> item = ITEMS.registerItem(name, factory, props);
        ORDER.add(item);
        return item;
    }

    /** Registers block items for every block that wants one (called once from the mod constructor). */
    public static void registerBlockItems() {
        for (DeferredBlock<? extends Block> block : WFBlocks.itemBlocks()) {
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    public static List<DeferredItem<? extends Item>> ordered() {
        return Collections.unmodifiableList(ORDER);
    }

}
