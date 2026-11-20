package dev.worldsfall.client.entity;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.model.JsonModels;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Model layers for the Blender-authored entity models (art/SPEC_MODELS.md). */
public final class WFLayers {
    public static final ModelLayerLocation VOID_SKATE = layer("void_skate");
    public static final ModelLayerLocation CHORUS_GRAZER = layer("chorus_grazer");
    public static final ModelLayerLocation UNDERSIDE_LURKER = layer("underside_lurker");
    public static final ModelLayerLocation HOARDER = layer("hoarder");
    public static final ModelLayerLocation SPIRE_WARDEN = layer("spire_warden");
    public static final ModelLayerLocation ECHO = layer("echo");
    public static final ModelLayerLocation WYRM_HATCHLING = layer("wyrm_hatchling");
    public static final ModelLayerLocation WYRM_FLEDGLING = layer("wyrm_fledgling");
    public static final ModelLayerLocation WYRM_DRAKE = layer("wyrm_drake");
    public static final ModelLayerLocation SHARDLING = layer("shardling");
    public static final ModelLayerLocation UNMADE_SHARD = layer("unmade_shard");
    public static final ModelLayerLocation VOID_CAPSULE = layer("void_capsule");
    public static final ModelLayerLocation RIFT_HOOK = layer("rift_hook");
    public static final ModelLayerLocation TETHER_PYLON = layer("tether_pylon");

    private WFLayers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Worldsfall.MODID, name), "main");
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(VOID_SKATE, () -> JsonModels.load("void_skate", "body", "left_wing", "left_wing_tip", "right_wing",
            "right_wing_tip", "tail_1", "tail_2", "tail_3", "left_fin", "right_fin", "harness"));
        e.registerLayerDefinition(CHORUS_GRAZER, () -> JsonModels.load("chorus_grazer", "body", "neck_1", "neck_2", "neck_3", "head", "frill",
            "leg_front_left", "leg_front_right", "leg_mid_left", "leg_mid_right", "leg_back_left", "leg_back_right", "tail"));
        e.registerLayerDefinition(UNDERSIDE_LURKER, () -> JsonModels.load("underside_lurker", "body", "crown", "leg_0", "leg_1", "leg_2",
            "leg_3", "leg_4", "leg_5", "leg_6", "leg_0_lower", "leg_1_lower", "leg_2_lower", "leg_3_lower", "leg_4_lower", "leg_5_lower", "leg_6_lower"));
        e.registerLayerDefinition(HOARDER, () -> JsonModels.load("hoarder", "head", "body", "cloak", "pouch", "right_arm", "left_arm", "right_leg", "left_leg"));
        e.registerLayerDefinition(SPIRE_WARDEN, () -> JsonModels.load("spire_warden", "head", "crest", "body", "right_arm", "right_forearm",
            "left_arm", "left_forearm", "right_leg", "left_leg", "plate_shoulder_right", "plate_shoulder_left", "plate_chest",
            "plate_forearm_right", "plate_forearm_left"));
        e.registerLayerDefinition(ECHO, () -> JsonModels.load("echo", "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"));
        String[] wyrm = {"body", "neck", "head", "horn_left", "horn_right", "jaw", "left_wing", "left_wing_tip", "right_wing", "right_wing_tip",
            "tail_1", "tail_2", "tail_3", "leg_front_left", "leg_front_right", "leg_back_left", "leg_back_right", "crest"};
        e.registerLayerDefinition(WYRM_HATCHLING, () -> JsonModels.load("wyrm_hatchling", wyrm));
        e.registerLayerDefinition(WYRM_FLEDGLING, () -> JsonModels.load("wyrm_fledgling", wyrm));
        e.registerLayerDefinition(WYRM_DRAKE, () -> JsonModels.load("wyrm_drake", wyrm));
        e.registerLayerDefinition(SHARDLING, () -> JsonModels.load("shardling", "core", "shard", "splinter_a", "splinter_b"));
        e.registerLayerDefinition(UNMADE_SHARD, () -> JsonModels.load("unmade_shard", "shard"));
        e.registerLayerDefinition(VOID_CAPSULE, () -> JsonModels.load("void_capsule", "cap_top", "glass", "cap_bottom", "strand"));
        e.registerLayerDefinition(RIFT_HOOK, () -> JsonModels.load("rift_hook", "claw", "pearl", "prong_0", "prong_1", "prong_2"));
        e.registerLayerDefinition(TETHER_PYLON, () -> JsonModels.load("tether_pylon", "crystal"));
    }
}
