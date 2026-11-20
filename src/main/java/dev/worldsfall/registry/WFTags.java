package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Data hooks for pack makers (§20.10). */
public final class WFTags {
    private WFTags() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Worldsfall.MODID, path);
    }

    public static final class Items {
        public static final TagKey<Item> HOARD_ACCEPTS = tag("hoard_accepts");
        public static final TagKey<Item> HOARD_REFUSES = tag("hoard_refuses");
        public static final TagKey<Item> HOARD_VALUE_1 = tag("hoard_value_1");
        public static final TagKey<Item> HOARD_VALUE_2 = tag("hoard_value_2");
        public static final TagKey<Item> HOARD_VALUE_4 = tag("hoard_value_4");
        public static final TagKey<Item> HOARD_VALUE_6 = tag("hoard_value_6");
        public static final TagKey<Item> HOARD_VALUE_10 = tag("hoard_value_10");
        public static final TagKey<Item> SKATE_FOOD = tag("skate_food");
        public static final TagKey<Item> SKATE_BREEDING = tag("skate_breeding");
        public static final TagKey<Item> GRAZER_FOOD = tag("grazer_food");
        public static final TagKey<Item> WYRM_FOOD = tag("wyrm_food");
        public static final TagKey<Item> SHULKER_LURE = tag("shulker_lure");
        public static final TagKey<Item> SATCHEL_FORBIDDEN = tag("satchel_forbidden");
        public static final TagKey<Item> RIFT_OPENERS = tag("rift_openers");
        public static final TagKey<Item> VOID_ANCHORS = tag("void_anchors");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, id(name));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> TETHER_UNLATCHABLE = tag("tether_unlatchable");
        public static final TagKey<Block> STILLWATER_CONVERTS = tag("stillwater_converts");
        public static final TagKey<Block> LARGE_ISLAND_SURFACE = tag("large_island_surface");
        public static final TagKey<Block> HOARD_PILE = tag("hoard_pile");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, id(name));
        }
    }

    public static final class Entities {
        public static final TagKey<EntityType<?>> GRAVITY_IMMUNE = tag("gravity_immune");
        public static final TagKey<EntityType<?>> EROSION_IMMUNE = tag("erosion_immune");
        public static final TagKey<EntityType<?>> STILLWATER_SENSITIVE = tag("stillwater_sensitive");
        public static final TagKey<EntityType<?>> NEW_END_MOBS = tag("new_end_mobs");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, id(name));
        }
    }

    public static final class Structures {
        public static final TagKey<Structure> DRIFTWRECKS = TagKey.create(Registries.STRUCTURE, id("driftwrecks"));
        public static final TagKey<Structure> FALLEN_SPIRES = TagKey.create(Registries.STRUCTURE, id("fallen_spires"));
    }
}
