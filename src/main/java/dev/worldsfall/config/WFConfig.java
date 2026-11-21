package dev.worldsfall.config;

import dev.worldsfall.world.tide.TideCycle;
import net.neoforged.neoforge.common.ModConfigSpec;

/** All configuration keys from design doc §21. SERVER config is per-world and synced to clients. */
public final class WFConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Server SERVER;
    public static final Client CLIENT;

    static {
        var server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        var client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private WFConfig() {}

    public static final class Server {
        public final ModConfigSpec.BooleanValue sunderingEnabled;
        public final ModConfigSpec.BooleanValue sunderingRetroactive;
        public final ModConfigSpec.BooleanValue tidesEnabled;
        public final ModConfigSpec.IntValue tideLowY;
        public final ModConfigSpec.IntValue tideHighY;
        public final ModConfigSpec.IntValue tideLowMinutes;
        public final ModConfigSpec.IntValue tideHighMinutes;
        public final ModConfigSpec.IntValue tideTransitionMinutes;
        public final ModConfigSpec.IntValue erosionMaxDamagePerSecond;
        public final ModConfigSpec.DoubleValue gravityUnderside;
        public final ModConfigSpec.BooleanValue reducedFloatiness;
        public final ModConfigSpec.IntValue stillwaterXpDrain;
        public final ModConfigSpec.IntValue maxFloatingStacksPerChunk;
        public final ModConfigSpec.IntValue capsuleMinutes;
        public final ModConfigSpec.BooleanValue publicCapsules;
        public final ModConfigSpec.IntValue skateMaxPerPlayer;
        public final ModConfigSpec.BooleanValue echoScramble;
        public final ModConfigSpec.IntValue wildShulkerMoltMinutes;
        public final ModConfigSpec.BooleanValue ambientHoarding;
        public final ModConfigSpec.EnumValue<EggFragments> eggFragments;
        public final ModConfigSpec.DoubleValue wyrmFledglingHours;
        public final ModConfigSpec.DoubleValue wyrmDrakeHours;
        public final ModConfigSpec.IntValue bossMaxHealth;
        public final ModConfigSpec.BooleanValue bossPlayerScaling;
        public final ModConfigSpec.BooleanValue bossBlockDecay;
        public final ModConfigSpec.IntValue anchorRadius;
        public final ModConfigSpec.EnumValue<AdditiveMode> additiveMode;
        public final ModConfigSpec.BooleanValue vanillaBiomeAdditions;
        public final ModConfigSpec.BooleanValue hintToasts;
        // expansion 1.2–2.0
        public final ModConfigSpec.BooleanValue dragonPacts;
        public final ModConfigSpec.BooleanValue dragonDescent;
        public final ModConfigSpec.BooleanValue dragonRelease;
        public final ModConfigSpec.BooleanValue dragonRespawnAfterRelease;
        public final ModConfigSpec.BooleanValue causeway;
        public final ModConfigSpec.IntValue latticeMaxNodes;
        public final ModConfigSpec.BooleanValue sinkingBullets;
        public final ModConfigSpec.BooleanValue tideFishing;
        public final ModConfigSpec.BooleanValue couriers;
        public final ModConfigSpec.BooleanValue eventStarfall;
        public final ModConfigSpec.BooleanValue eventKingTide;
        public final ModConfigSpec.BooleanValue eventEclipse;
        public final ModConfigSpec.BooleanValue eventMigration;
        public final ModConfigSpec.BooleanValue zenith;
        public final ModConfigSpec.BooleanValue undertow;
        public final ModConfigSpec.IntValue lostThingsCap;
        public final ModConfigSpec.BooleanValue restorationEnabled;
        public final ModConfigSpec.IntValue restorationPointsFor100;
        public final ModConfigSpec.BooleanValue restorationStageEffects;

        Server(ModConfigSpec.Builder b) {
            b.push("sundering");
            sunderingEnabled = b.comment("If false, a Rift island replaces the Descent (§5.4)").define("enabled", true);
            sunderingRetroactive = b.comment("Trigger on first End entry if the dragon is already dead").define("retroactive", true);
            b.pop().push("tides");
            tidesEnabled = b.comment("Turn Void Tides off entirely (plane stays at Low)").define("enabled", true);
            tideLowY = b.defineInRange("lowY", 0, -16, 64);
            tideHighY = b.defineInRange("highY", 36, -16, 64);
            tideLowMinutes = b.defineInRange("lowMinutes", 10, 1, 120);
            tideHighMinutes = b.defineInRange("highMinutes", 10, 1, 120);
            tideTransitionMinutes = b.defineInRange("transitionMinutes", 3, 1, 120);
            b.pop().push("erosion");
            erosionMaxDamagePerSecond = b.defineInRange("maxDamagePerSecond", 4, 1, 20);
            b.pop().push("gravity");
            gravityUnderside = b.comment("Gravity multiplier in the Underside").defineInRange("underside", 0.45, 0.1, 1.0);
            reducedFloatiness = b.comment("Accessibility: halves the strength of all gravity zones").define("reducedFloatiness", false);
            b.pop().push("stillwater");
            stillwaterXpDrain = b.comment("XP points drained per second while submerged").defineInRange("xpDrain", 1, 0, 5);
            maxFloatingStacksPerChunk = b.defineInRange("maxFloatingStacksPerChunk", 64, 0, 512);
            b.pop().push("voidAnchor");
            capsuleMinutes = b.comment("Capsule lifetime in loaded minutes; 0 = infinite").defineInRange("capsuleMinutes", 60, 0, 100000);
            publicCapsules = b.define("publicCapsules", false);
            b.pop().push("mobs");
            skateMaxPerPlayer = b.defineInRange("skateMaxPerPlayer", 6, 0, 20);
            echoScramble = b.comment("Echoes swap hotbar slots").define("echoScramble", true);
            wildShulkerMoltMinutes = b.defineInRange("wildShulkerMoltMinutes", 40, 5, 240);
            ambientHoarding = b.comment("Endermen bring blocks to Hoards").define("ambientHoarding", true);
            b.pop().push("wyrm");
            eggFragments = b.comment("Egg Fragments from repeat dragon kills").defineEnum("eggFragments", EggFragments.SERVERS);
            wyrmFledglingHours = b.defineInRange("fledglingHours", 2.0, 0.01, 100.0);
            wyrmDrakeHours = b.defineInRange("drakeHours", 6.0, 0.01, 100.0);
            b.pop().push("boss");
            bossMaxHealth = b.defineInRange("maxHealth", 720, 200, 5000);
            bossPlayerScaling = b.define("playerScaling", true);
            bossBlockDecay = b.comment("Placed blocks decay in the Fracture during the fight").define("blockDecay", true);
            b.pop().push("anchor");
            anchorRadius = b.comment("Stable Field radius").defineInRange("radius", 12, 8, 24);
            b.pop().push("compat");
            additiveMode = b.comment("Additive worldgen with other End mods").defineEnum("additiveMode", AdditiveMode.AUTO);
            vanillaBiomeAdditions = b.comment("Light-touch additions to vanilla End biomes").define("vanillaBiomeAdditions", true);
            hintToasts = b.comment("One-time onboarding hints").define("hintToasts", true);
            b.pop().push("dragon");
            dragonPacts = b.comment("Pact Crystals turn repeat dragon fights into trials").define("pacts", true);
            dragonDescent = b.comment("The Pact of Depths (the Descending Warden)").define("descent", true);
            dragonRelease = b.comment("Stillness Crystals can release the dragon for good").define("release", true);
            dragonRespawnAfterRelease = b.comment("Allow respawning the dragon after the Release").define("respawnAfterRelease", false);
            b.pop().push("gap");
            causeway = b.comment("The Pilgrim's Causeway generates and can be restored").define("causeway", true);
            b.pop().push("navigation");
            latticeMaxNodes = b.comment("Gateway Lattice nodes per world").defineInRange("latticeMaxNodes", 32, 2, 256);
            sinkingBullets = b.comment("City Shulkers fire Sinking Bullets at High Tide").define("sinkingBullets", true);
            b.pop().push("living");
            tideFishing = b.comment("Fishing on the tide plane and in Stillwater").define("tideFishing", true);
            couriers = b.comment("Hoarder Couriers move stacks between Courier Caches").define("couriers", true);
            eventStarfall = b.define("starfall", true);
            eventKingTide = b.define("kingTide", true);
            eventEclipse = b.define("eclipseOfStillness", true);
            eventMigration = b.define("skateMigration", true);
            b.pop().push("zenith");
            zenith = b.comment("The Zenith sky layer (Y 150–250)").define("enabled", true);
            b.pop().push("undertow");
            undertow = b.comment("Tide Maws and the Undertow dimension").define("enabled", true);
            lostThingsCap = b.comment("Void-destroyed items remembered for the Undertow and tide fishing").defineInRange("lostThingsCap", 500, 0, 5000);
            b.pop().push("restoration");
            restorationEnabled = b.define("enabled", true);
            restorationPointsFor100 = b.defineInRange("pointsFor100", 200, 10, 100000);
            restorationStageEffects = b.comment("Stage effects (spreading loam, longer low tides, sky changes)").define("stageEffects", true);
            b.pop();
        }

        public TideCycle tideCycle() {
            try {
                int transition = tideTransitionMinutes.get() * 1200;
                int low = tideLowY.get();
                int high = Math.max(low, tideHighY.get());
                return new TideCycle(low, high, tideLowMinutes.get() * 1200, transition, tideHighMinutes.get() * 1200, transition);
            } catch (IllegalStateException notLoaded) {
                return TideCycle.DEFAULT;
            }
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue tideScreenEffects;
        public final ModConfigSpec.EnumValue<HudMode> tideHud;
        public final ModConfigSpec.BooleanValue stillwaterSoundMuffle;
        public final ModConfigSpec.BooleanValue reducedFlashing;
        public final ModConfigSpec.BooleanValue customSky;
        public final ModConfigSpec.BooleanValue reflections;
        public final ModConfigSpec.BooleanValue glyphAnimation;

        Client(ModConfigSpec.Builder b) {
            tideScreenEffects = b.comment("Vignette when the tide rises").define("tideScreenEffects", true);
            tideHud = b.comment("Tide HUD indicator").defineEnum("tideHud", HudMode.AUTO);
            stillwaterSoundMuffle = b.comment("Accessibility: muffle sounds near submerged players").define("stillwaterSoundMuffle", true);
            reducedFlashing = b.comment("Caps bright flashes to a soft fade").define("reducedFlashing", false);
            customSky = b.comment("Use the layered End sky").define("customSky", true);
            reflections = b.comment("Planar reflections on Stillwater (expensive)").define("reflections", false);
            glyphAnimation = b.comment("Echo Tablet glyph-reveal animation").define("glyphAnimation", true);
        }
    }

    public enum EggFragments { OFF, SERVERS, ALWAYS }
    public enum AdditiveMode { AUTO, ON, OFF }
    public enum HudMode { OFF, AUTO, ALWAYS }

    /** Safe accessor for server config values that may be read before the config loads (e.g. in tests). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
