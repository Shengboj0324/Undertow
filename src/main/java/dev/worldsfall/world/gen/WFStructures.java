package dev.worldsfall.world.gen;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.world.gen.structure.BlueprintPiece;
import dev.worldsfall.world.gen.structure.BlueprintStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Structure types (§11) — procedural blueprints, placed by data structure sets. */
public final class WFStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Worldsfall.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Worldsfall.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<BlueprintStructure>> BLUEPRINT = TYPES.register("blueprint",
        () -> () -> BlueprintStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> BLUEPRINT_PIECE = PIECES.register("blueprint",
        () -> (StructurePieceType) BlueprintPiece::new);

    public static final DeferredHolder<StructureType<?>, StructureType<dev.worldsfall.world.gen.structure.StillwaterBasinStructure>> STILLWATER_BASIN =
        TYPES.register("stillwater_basin", () -> () -> dev.worldsfall.world.gen.structure.StillwaterBasinStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> STILLWATER_BASIN_PIECE = PIECES.register("stillwater_basin",
        () -> (StructurePieceType) dev.worldsfall.world.gen.structure.StillwaterBasinPiece::new);

    private WFStructures() {}

    static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}
