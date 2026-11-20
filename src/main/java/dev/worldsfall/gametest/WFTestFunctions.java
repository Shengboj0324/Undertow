package dev.worldsfall.gametest;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Registers GameTest functions into the test function registry. */
public final class WFTestFunctions {
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Worldsfall.MODID);
    private static final Map<ResourceKey<Consumer<GameTestHelper>>, Consumer<GameTestHelper>> ALL = new LinkedHashMap<>();

    static {
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        WFTests.collect(tests);
        tests.forEach((name, fn) -> FUNCTIONS.register(name, () -> fn));
    }

    private WFTestFunctions() {}

    static void put(ResourceKey<Consumer<GameTestHelper>> key, Consumer<GameTestHelper> fn) {
        ALL.put(key, fn);
    }
}
