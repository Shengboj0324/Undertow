package dev.worldsfall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.world.fracture.FractureArena;
import dev.worldsfall.world.gravity.GravityZones;
import dev.worldsfall.world.sundering.Sundering;
import dev.worldsfall.world.sundering.SunderingData;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.TideData;
import dev.worldsfall.world.tide.TideState;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.Locale;

/** /worldsfall debug commands (§20.11), operator level 2. */
public final class WFCommands {
    private WFCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        d.register(Commands.literal("worldsfall").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("tide")
                .then(Commands.literal("get").executes(WFCommands::tideGet))
                .then(Commands.literal("set").then(Commands.argument("phase", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(TideCycle.Phase.values()).map(p -> p.id), b))
                    .executes(c -> tideSet(c, StringArgumentType.getString(c, "phase")))))
                .then(Commands.literal("pause").executes(c -> tideUpdate(c, s -> s.pause(c.getSource().getServer().overworld().getGameTime()))))
                .then(Commands.literal("resume").executes(c -> tideUpdate(c, s -> s.resume(c.getSource().getServer().overworld().getGameTime())))))
            .then(Commands.literal("sundering")
                .then(Commands.literal("status").executes(WFCommands::sunderingStatus))
                .then(Commands.literal("trigger").executes(WFCommands::sunderingTrigger)))
            .then(Commands.literal("gravity").then(Commands.literal("debug").executes(WFCommands::gravityDebug)))
            .then(Commands.literal("wyrm").then(Commands.literal("stage").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Wyrm.Stage.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), b))
                    .executes(WFCommands::wyrmStage)))))
            .then(Commands.literal("boss").then(Commands.literal("reset").executes(WFCommands::bossReset)))
            .then(Commands.literal("locate").then(Commands.argument("structure", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"fallen_spire", "hoard", "driftwreck", "skate_roost",
                    "echo_shrine", "root_hollow", "deep_root_hollow"}, b))
                .executes(c -> {
                    c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(),
                        "locate structure #worldsfall:" + StringArgumentType.getString(c, "structure"));
                    return 1;
                }))));
    }

    private static int tideGet(CommandContext<CommandSourceStack> c) {
        ServerLevel end = c.getSource().getServer().getLevel(Level.END);
        if (end == null) {
            return 0;
        }
        TideCycle cycle = Tides.cycle(end);
        long t = Tides.effectiveTime(end);
        c.getSource().sendSuccess(() -> Component.translatable("command.worldsfall.tide.get", Tides.phase(end).id,
            String.format(Locale.ROOT, "%.1f", Tides.height(end)), cycle.ticksUntilNextPhase(t) / 20, Tides.state(end).paused()), false);
        return 1;
    }

    private static int tideSet(CommandContext<CommandSourceStack> c, String phaseId) {
        TideCycle.Phase phase;
        try {
            phase = TideCycle.Phase.byId(phaseId);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.translatable("command.worldsfall.tide.bad_phase", phaseId));
            return 0;
        }
        ServerLevel end = c.getSource().getServer().getLevel(Level.END);
        TideCycle cycle = end == null ? TideCycle.DEFAULT : Tides.cycle(end);
        long target = cycle.phaseStart(phase);
        return tideUpdate(c, s -> s.jumpTo(c.getSource().getServer().overworld().getGameTime(), target));
    }

    private static int tideUpdate(CommandContext<CommandSourceStack> c, java.util.function.UnaryOperator<TideState> f) {
        TideData data = TideData.get(c.getSource().getServer());
        data.set(f.apply(data.state()));
        WFNetwork.syncTidesToAll(c.getSource().getServer());
        return tideGet(c);
    }

    private static int sunderingStatus(CommandContext<CommandSourceStack> c) {
        SunderingData d = SunderingData.get(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.translatable("command.worldsfall.sundering.status", d.done(), d.descent().toShortString(),
            d.riftFrame().toShortString()), false);
        return 1;
    }

    private static int sunderingTrigger(CommandContext<CommandSourceStack> c) {
        ServerLevel end = c.getSource().getServer().getLevel(Level.END);
        if (end == null) {
            return 0;
        }
        SunderingData d = SunderingData.get(c.getSource().getServer());
        if (d.done()) {
            c.getSource().sendFailure(Component.translatable("command.worldsfall.sundering.already"));
            return 0;
        }
        boolean ok = Sundering.start(end, d);
        c.getSource().sendSuccess(() -> Component.translatable(ok ? "command.worldsfall.sundering.started" : "command.worldsfall.sundering.busy"), true);
        return ok ? 1 : 0;
    }

    private static int gravityDebug(CommandContext<CommandSourceStack> c) {
        Entity e = c.getSource().getEntity();
        if (!(e instanceof LivingEntity living)) {
            return 0;
        }
        GravityZones.Zone zone = GravityZones.zoneAt(living.level(), living);
        double gravity = living.getAttributeValue(Attributes.GRAVITY);
        double fall = living.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        c.getSource().sendSuccess(() -> Component.translatable("command.worldsfall.gravity.debug", zone.name(),
            String.format(Locale.ROOT, "%.4f", gravity), String.format(Locale.ROOT, "%.2f", fall)), false);
        return 1;
    }

    private static int wyrmStage(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Wyrm.Stage stage;
        try {
            stage = Wyrm.Stage.valueOf(StringArgumentType.getString(c, "stage").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return 0;
        }
        int n = 0;
        for (Wyrm w : player.level().getEntitiesOfClass(Wyrm.class, new AABB(player.blockPosition()).inflate(64), w -> w.isOwnedBy(player))) {
            w.debugSetStage(stage);
            n++;
        }
        final int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.worldsfall.wyrm.stage", count, stage.name()), true);
        return n;
    }

    private static int bossReset(CommandContext<CommandSourceStack> c) {
        ServerLevel fracture = c.getSource().getServer().getLevel(WFDimensions.THE_FRACTURE);
        if (fracture == null) {
            return 0;
        }
        for (TheUnmade boss : fracture.getEntitiesOfClass(TheUnmade.class, new AABB(FractureArena.CENTER).inflate(160))) {
            boss.discard();
        }
        FractureArena.rebuild(fracture);
        FractureArena.ensureBoss(fracture);
        FractureArena.setExitRift(fracture, true);
        c.getSource().sendSuccess(() -> Component.translatable("command.worldsfall.boss.reset"), true);
        return 1;
    }
}
