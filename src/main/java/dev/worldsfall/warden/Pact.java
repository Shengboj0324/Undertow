package dev.worldsfall.warden;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Crystal Pacts (proposal "Crystal Pacts: repeat fights as trials"). A respawn crystal carrying a pact changes the next
 * dragon fight; total Weight (0–12) sets the reward tier. Plain crystals keep the plain vanilla fight.
 */
public enum Pact implements StringRepresentable {
    TIDE(2, 0x3AE0D0),
    SHARDS(2, 0xFFC6EC),
    ECHOES(1, 0xB48CFF),
    HOARD(1, 0xF2A23C),
    MEMORY(3, 0xE8C860),
    STILLNESS(2, 0xD7DEEA),
    LONE(1, 0xFF8A80),
    DEPTHS(4, 0x4A6BFF);

    public static final Codec<Pact> CODEC = StringRepresentable.fromEnum(Pact::values);
    public static final int MAX_WEIGHT = 12;

    public final int weight;
    public final int color;

    Pact(int weight, int color) {
        this.weight = weight;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Pact byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    /** Total Weight of a set of pacts; a fight carries at most four (one per respawn crystal), capped at 12. */
    public static int weight(Collection<Pact> pacts) {
        int w = 0;
        for (Pact p : pacts) {
            w += p.weight;
        }
        return Math.min(MAX_WEIGHT, w);
    }

    public static Set<Pact> none() {
        return EnumSet.noneOf(Pact.class);
    }

    /** Reward tier for a Weight: 0 none, 1 (1–3), 2 (4–7), 3 (8–11), 4 (12). */
    public static int tier(int weight) {
        if (weight <= 0) {
            return 0;
        }
        if (weight <= 3) {
            return 1;
        }
        if (weight <= 7) {
            return 2;
        }
        return weight >= MAX_WEIGHT ? 4 : 3;
    }
}
