package dev.worldsfall.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.function.Consumer;

/** All GameTests (§20.12). */
public final class WFTests {
    private WFTests() {}

    static void collect(Map<String, Consumer<GameTestHelper>> out) {
        out.put("smoke", GameTestHelper::succeed);
    }

    static int maxTicks(String name) {
        return 200;
    }
}
