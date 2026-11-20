package dev.worldsfall.gametest;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** GameTest registration (§20.12). Test bodies live in {@link WFTests}. */
public final class WFGameTests {
    private WFGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "default"),
            new TestEnvironmentDefinition.AllOf());
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        WFTests.collect(tests);
        for (Map.Entry<String, Consumer<GameTestHelper>> e : tests.entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(Worldsfall.MODID, e.getKey());
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id);
            WFTestFunctions.put(fn, e.getValue());
            var dim = WFTests.endDimension(e.getKey()) ? net.minecraft.world.level.Level.END : net.minecraft.world.level.Level.OVERWORLD;
            event.registerTest(id, new FunctionGameTestInstance(fn, new TestData<>(env, dim, Identifier.withDefaultNamespace("empty"),
                WFTests.maxTicks(e.getKey()), 1, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, false, 2)));
        }
    }
}
